package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.color
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.msg
import dev.menonng.infracraft.net.Payloads
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.DustColorTransitionOptions
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 이름을 글자마다 두 색 사이로 물들인다. EnderShower 의 Gradient 생성기(https://endershower.github.io/Gradient/)와 같은 계산:
 * 첫 글자 = 시작색, 마지막 글자 = 끝색, 가운데 i 번째 글자는 시작 + i·(끝 − 시작)/(n − 1) (소수점 버림).
 * 번역된 이름 글자가 필요하므로 클라이언트가 [resolver] 를 채운다. 비어 있으면(전용 서버) 시작색 한 가지로 칠한다.
 */
object Gradient {
    @Volatile var resolver: ((String) -> String)? = null

    fun text(s: String, start: Int, end: Int): MutableComponent {
        val out = Component.empty()
        val n = s.length
        for ((i, ch) in s.withIndex()) {
            val c = when {
                n == 1 || i == 0 -> start
                i == n - 1 -> end
                else -> {
                    fun ch(shift: Int): Int {
                        val a = (start shr shift) and 0xFF
                        val b = (end shr shift) and 0xFF
                        return (a + i * (b - a) / (n - 1).toDouble()).toInt()
                    }
                    (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
                }
            }
            out.append(Component.literal(ch.toString()).color(c))
        }
        return out
    }
}

/** 이름이 그라데이션으로 빛나는 아스트라 관련 아이템 */
class AstraItem(props: Item.Properties, val start: Int, val end: Int) : Item(props) {
    override fun getName(stack: ItemStack): Component {
        val base = super.getName(stack)
        val key = (base.contents as? TranslatableContents)?.key ?: return base
        val r = Gradient.resolver ?: return Component.translatable(key).color(start)
        return Gradient.text(r(key), start, end)
    }
}

/**
 * 히든 아이템: 아스트라 오브리터레이터(독수리자리, Aquila).
 *  - 일반 공격: 칼날이 공간을 반달 모양으로 가르고, 그 틈(너머로 우주가 보인다)이 앞으로 날아가며 닿는 것을 모두 벤다(관통).
 *  - 오른손 우클릭: 시선 앞 하늘에 아홉 별이 떠올라 별자리를 이루고, 알타이르에서 별빛 광선이 뻗는다.
 *  - 왼손에 들고 우클릭: 조준 모드. 시야의 한 지점을 좌클릭하거나, M 으로 지금까지 불러온 지도를 열어 원하는 곳을 좌클릭하면
 *    그 하늘에 독수리자리가 떠올라 별빛이 낙하한다(조준은 클라이언트, 판정은 서버).
 * 지형은 부수지 않는다.
 */
object Astra {
    /** 별 이름(아이템 ID 꼬리), 별자리 평면 좌표(가로, 세로), 색 */
    class Star(val key: String, val x: Double, val y: Double, val color: Int)

    val STARS = listOf(
        Star("altair", 0.0, 0.0, 0xEAF4FF),
        Star("tarazed", -0.7, 1.0, 0xFF9A3C),
        Star("alshain", 0.7, -1.0, 0xFFE27A),
        Star("deneb_el_okab", -2.9, 3.3, 0xBFD7FF),
        Star("bezek", 2.3, -2.4, 0xFFC531),
        Star("chesus", 3.6, 1.4, 0xA8F0FF),
        Star("libertas", -0.6, -3.0, 0x9BFFB0),
        Star("phonix", -1.9, -1.1, 0xFF4E6A),
        Star("petra", -3.8, 0.1, 0xC8B6FF),
    )
    /** 별자리 선(STARS 인덱스 쌍) */
    val LINES = listOf(1 to 0, 0 to 2, 0 to 7, 7 to 8, 7 to 3, 0 to 4, 4 to 5, 7 to 6)

    /** 발전과제 원 위의 순서(반시계, 30°부터 40°씩; 알타이르는 맨 아래 270°). 이름 그라데이션도 이 순서로 이어진다. */
    val RING = listOf("chesus", "tarazed", "deneb_el_okab", "petra", "phonix", "libertas", "altair", "alshain", "bezek")

    private fun star(key: String) = STARS.first { it.key == key }

    /** 별조각 이름: 자기 별빛 → 원에서 다음 별의 빛 */
    fun shardGradient(key: String): Pair<Int, Int> {
        val i = RING.indexOf(key)
        return star(key).color to star(RING[(i + 1) % RING.size]).color
    }
    val CORE_GRADIENT = 0xC8B6FF to 0xFFE27A
    val WEAPON_GRADIENT = 0x8A4DFF to 0xF2F7FF

    private const val VOID = 0x14002E      // 암흑 별빛의 바탕
    private const val VIOLET = 0x8A4DFF
    private const val STARLIGHT = 0xF2F7FF

    // 조정값(대폭 강화)
    private const val ATTACK = 26.0
    private const val BEAM_RANGE = 160.0
    private const val BEAM_DAMAGE = 120f
    private const val BEAM_RADIUS = 4.0
    private const val SLASH_DAMAGE = 24f
    private const val SLASH_RANGE = 16
    private const val STRIKE_DAMAGE = 180f
    private const val STRIKE_RADIUS = 12.0
    private const val VIEW_RANGE = 320.0
    private const val COOLDOWN = 160
    /** 쓴 사람 말고 효과가 보이는 거리(입자 기준) */
    private const val NEAR = 64.0

    // ───────────── 아이템 속성 ─────────────

    private fun lore(vararg keys: String): ItemLore = ItemLore(keys.map { k ->
        Component.translatable("item.infracraft.lore.$k").withStyle { it.withItalic(false).withColor(loreColor(k)) }
    })

    private fun loreColor(k: String): Int = STARS.firstOrNull { k.startsWith(it.key) }?.color ?: 0xC8B6FF

    fun shardProperties(star: Star): Item.Properties = Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)
        .component(DataComponents.LORE, lore(star.key, "shard_hint"))

    fun coreProperties(): Item.Properties = Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        .component(DataComponents.LORE, lore("core", "core_hint"))

    fun weaponProperties(): Item.Properties = Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)
        .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        .component(DataComponents.LORE, ItemLore(
            listOf(Component.translatable("item.infracraft.lore.weapon").withStyle { it.withItalic(false).withColor(0xEAF4FF) }) +
                STARS.map { s -> Component.literal("  ✦ ").withStyle { it.withItalic(false).withColor(s.color) }
                    .append(Component.translatable("item.infracraft.star_${s.key}.short")) } +
                listOf("weapon_attack", "weapon_use", "weapon_aim", "weapon_map").map { k ->
                    Component.translatable("item.infracraft.lore.$k").withStyle { it.withItalic(false).withColor(0xFFB4CC) }
                }))
        .attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, AttributeModifier(id("astra_damage"), ATTACK, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, AttributeModifier(id("astra_speed"), -2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build())

    // ───────────── 효과 도구 ─────────────

    private fun dust(color: Int, size: Float): ParticleOptions = DustParticleOptions(color, size)
    private fun fade(from: Int, to: Int, size: Float): ParticleOptions = DustColorTransitionOptions(from, to, size)

    /** 효과를 보는 사람: 쓴 사람(멀리서 지도로 쏘아도 보인다)과 그 입자 가까이(반경 [NEAR])에 있는 플레이어만 */
    private fun sees(pl: ServerPlayer, caster: ServerPlayer, at: Vec3) =
        pl === caster || (pl.level() === caster.level() && pl.distanceToSqr(at) <= NEAR * NEAR)

    private fun fx(level: ServerLevel, caster: ServerPlayer, p: ParticleOptions, at: Vec3, n: Int, spread: Double, speed: Double) {
        for (pl in level.players()) {
            if (!sees(pl, caster, at)) continue
            level.sendParticles(pl, p, true, false, at.x, at.y, at.z, n, spread, spread, spread, speed)
        }
    }


    private fun sound(level: ServerLevel, at: Vec3, e: SoundEvent, vol: Float, pitch: Float) =
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch)

    private fun sound(level: ServerLevel, at: Vec3, e: net.minecraft.core.Holder<SoundEvent>, vol: Float, pitch: Float) =
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch)


    private fun friendly(e: LivingEntity, caster: ServerPlayer) = e === caster || (e is TamableAnimal && e.isOwnedBy(caster))

    private fun hurt(level: ServerLevel, e: LivingEntity, caster: ServerPlayer, amount: Float, push: Vec3) {
        if (e is net.minecraft.world.entity.player.Player && !dev.menonng.infracraft.Config.c.astraHitsPlayers) return
        e.hurtServer(level, level.damageSources().playerAttack(caster), amount)
        e.addEffect(MobEffectInstance(MobEffects.GLOWING, 100, 0))
        e.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 2))
        e.push(push.x, push.y, push.z)
        Compat.syncVelocity(e)
    }

    // ───────────── 연출 전달 ─────────────

    /** 연출을 보는 사람: 쓴 사람과, 기준점(시작·끝·목표) 가까이(반경 [FX_NEAR])에 있는 플레이어 */
    private const val FX_NEAR = 96.0

    private fun broadcast(level: ServerLevel, caster: ServerPlayer, kind: Int, data: DoubleArray, anchors: List<Vec3>) {
        val p = Payloads.AstraFx(kind, caster.id, level.gameTime, data)
        for (pl in level.players()) {
            if (pl !== caster && anchors.none { pl.distanceToSqr(it) <= FX_NEAR * FX_NEAR }) continue
            if (ServerPlayNetworking.canSend(pl, Payloads.AstraFx.TYPE)) ServerPlayNetworking.send(pl, p)
        }
    }

    // ───────────── 1) 일반 공격: 암흑 별빛 검격(공간을 가른 반달 모양의 틈) ─────────────

    private class Wave(val player: UUID, val level: ServerLevel, val origin: Vec3, val dir: Vec3, val a: Vec3, val b: Vec3,
                       var age: Int = 0, val hit: HashSet<Int> = HashSet())

    private val waves = ArrayList<Wave>()
    private val lastSlash = HashMap<UUID, Long>()
    private val slashSide = HashMap<UUID, Boolean>()

    fun slash(player: ServerPlayer) {
        if (!player.mainHandItem.`is`(ModItems.ASTRA_OBLITERATOR)) return
        val level = player.level() as ServerLevel
        val last = lastSlash[player.uuid] ?: 0L
        if (level.gameTime - last < 8 || player.getAttackStrengthScale(0.5f) < 0.85f) return
        lastSlash[player.uuid] = level.gameTime
        val look = player.lookAngle.normalize()
        val (right, up) = AstraShape.basis(look)
        val side = !(slashSide[player.uuid] ?: false)
        slashSide[player.uuid] = side
        // 베는 방향이 번갈아 바뀐다(왼쪽 위 → 오른쪽 아래, 그다음 반대)
        val roll = if (side) 0.6 else -0.6
        val (a, b) = AstraShape.slashAxes(right, up, roll)
        val origin = player.eyePosition.add(look.scale(1.0)).add(up.scale(-0.3))
        waves.add(Wave(player.uuid, level, origin, look, a, b))
        broadcast(level, player, Payloads.AstraFx.SLASH, doubleArrayOf(origin.x, origin.y, origin.z, look.x, look.y, look.z, roll),
            listOf(origin, origin.add(look.scale(AstraShape.SLASH_LIFE * 1.15))))
        sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.55f)
        sound(level, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f)
        sound(level, origin, SoundEvents.ENDER_EYE_DEATH, 0.6f, 0.6f)
    }

    private fun tickWave(w: Wave, caster: ServerPlayer): Boolean {
        val level = w.level
        val age = w.age.toDouble()
        val c = AstraShape.slashCenter(w.origin, w.dir, age)
        val box = AABB(c, c).inflate(AstraShape.slashRadius(age) + 2.0)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, caster) && it.id !in w.hit }) {
            val mid = e.position().add(0.0, e.bbHeight / 2.0, 0.0)
            if (!AstraShape.slashHits(c, w.dir, w.a, w.b, age, mid)) continue
            w.hit.add(e.id)
            hurt(level, e, caster, SLASH_DAMAGE, w.dir.scale(0.6).add(0.0, 0.2, 0.0))
            fx(level, caster, ParticleTypes.CRIT, mid, 8, 0.3, 0.4)
            sound(level, e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f)
        }
        // 벽에 부딪히면 사라진다(클라이언트도 같은 자리에서 칼날을 지운다)
        val bp = BlockPos.containing(c)
        if (level.getBlockState(bp).isSuffocating(level, bp)) {
            sound(level, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 0.7f)
            return false
        }
        w.age++
        return w.age < AstraShape.SLASH_LIFE
    }

    // ───────────── 2) 오른손 우클릭: AS-01 별자리 레일건 ─────────────

    private class Beam(val player: UUID, val level: ServerLevel, val eye: Vec3, val look: Vec3, val end: Vec3, val start: Long)
    private val beams = ArrayList<Beam>()

    fun cast(player: ServerPlayer, stack: ItemStack) {
        if (player.cooldowns.isOnCooldown(stack)) return
        player.cooldowns.addCooldown(stack, COOLDOWN)
        val level = player.level() as ServerLevel
        val eye = player.eyePosition
        val look = player.lookAngle.normalize()
        val target = eye.add(look.scale(BEAM_RANGE))
        val hit = level.clip(ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        val end = if (hit.type == HitResult.Type.MISS) target else hit.location
        beams.add(Beam(player.uuid, level, eye, look, end, level.gameTime))
        // 제목·충전률은 화면 연출(HUD)이 보여 준다
        broadcast(level, player, Payloads.AstraFx.RAILGUN, doubleArrayOf(eye.x, eye.y, eye.z, look.x, look.y, look.z, end.x, end.y, end.z), listOf(eye, end))
    }

    private fun tickBeam(b: Beam, t: Int, player: ServerPlayer) {
        val level = b.level
        if (t < 18 && t % 2 == 0) sound(level, AstraShape.railStar(b.eye, b.look, t / 2), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f + t * 0.06f)
        if (t in 18..33 && t % 4 == 0) sound(level, AstraShape.railStar(b.eye, b.look, 0), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6f, 1.2f + (t - 18) * 0.05f)
        if (t == 34) sound(level, AstraShape.railStar(b.eye, b.look, 0), SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.4f)
        if (t == AstraShape.RAIL_FIRE) fireBeam(b, player)
    }

    private fun fireBeam(b: Beam, player: ServerPlayer) {
        val level = b.level
        val from = AstraShape.railStar(b.eye, b.look, 0)
        val path = b.end.subtract(from)
        val len = path.length()
        val dir = path.normalize()
        sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.3f)
        sound(level, from, SoundEvents.BEACON_ACTIVATE, 3.0f, 1.8f)
        sound(level, b.end, SoundEvents.GENERIC_EXPLODE, 4.0f, 1.1f)
        val box = AABB(from, b.end).inflate(BEAM_RADIUS)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, player) }) {
            val rel = e.position().add(0.0, e.bbHeight / 2.0, 0.0).subtract(from)
            val along = rel.dot(dir).coerceIn(0.0, len)
            if (rel.subtract(dir.scale(along)).length() > BEAM_RADIUS) continue
            hurt(level, e, player, BEAM_DAMAGE, Vec3(dir.x * 1.6, 0.5, dir.z * 1.6))
        }
        if (len < BEAM_RANGE - 1) AstraScar.crater(level, player, b.end, 3.0)
    }

    // ───────────── 3) 조준·지도: AS-09 아홉 별 ─────────────

    private class Fall(val player: UUID, val level: ServerLevel, val ground: Vec3, val points: List<Vec3>, val start: Long)
    private val falls = ArrayList<Fall>()

    private fun holding(player: ServerPlayer): ItemStack? = when {
        player.offhandItem.`is`(ModItems.ASTRA_OBLITERATOR) -> player.offhandItem
        player.mainHandItem.`is`(ModItems.ASTRA_OBLITERATOR) -> player.mainHandItem
        else -> null
    }

    fun strike(player: ServerPlayer, p: Payloads.AstraStrike) {
        val stack = holding(player) ?: return
        if (player.cooldowns.isOnCooldown(stack)) {
            Compat.overlay(player, msg("astra.cooldown", Palette.ORANGE)); return
        }
        val level = player.level() as ServerLevel
        val x = kotlin.math.floor(p.x).toInt()
        val z = kotlin.math.floor(p.z).toInt()
        val ground: Vec3 = when (p.mode) {
            Payloads.AstraStrike.VIEW -> {
                val at = Vec3(p.x, p.y, p.z)
                if (at.distanceTo(player.eyePosition) > VIEW_RANGE + 2) return
                at
            }
            Payloads.AstraStrike.MAP -> {
                if (!Explored.contains(player, x, z)) {
                    Compat.overlay(player, msg("astra.unexplored", Palette.ORANGE)); return
                }
                Compat.keepChunkLoaded(level, BlockPos(x, 0, z))
                level.getChunk(x shr 4, z shr 4)
                Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z).toDouble(), z + 0.5)
            }
            else -> return
        }
        // 아홉 별이 떨어질 자리: 독수리자리 모양으로, 각자의 지면 높이(목표와 너무 다르면 목표 높이)
        val points = Astra.STARS.indices.map { i ->
            val (dx, dz) = AstraShape.groundOffset(i)
            val px = ground.x + dx
            val pz = ground.z + dz
            level.getChunk(kotlin.math.floor(px).toInt() shr 4, kotlin.math.floor(pz).toInt() shr 4)
            val h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, kotlin.math.floor(px).toInt(), kotlin.math.floor(pz).toInt()).toDouble()
            Vec3(px, if (kotlin.math.abs(h - ground.y) > 24) ground.y else h, pz)
        }
        player.cooldowns.addCooldown(stack, COOLDOWN)
        falls.add(Fall(player.uuid, level, ground, points, level.gameTime))
        val data = DoubleArray(3 + 27)
        data[0] = ground.x; data[1] = ground.y; data[2] = ground.z
        for ((i, q) in points.withIndex()) { data[3 + i * 3] = q.x; data[4 + i * 3] = q.y; data[5 + i * 3] = q.z }
        broadcast(level, player, Payloads.AstraFx.NINE_STARS, data, listOf(ground, player.position()))
        sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f)
    }

    private fun tickFall(f: Fall, t: Int, player: ServerPlayer) {
        val level = f.level
        if (t % 20 == 0) Compat.keepChunkLoaded(level, BlockPos.containing(f.ground))
        val altair = AstraShape.skyStar(f.ground, 0)
        // 투영: 별이 하나씩 켜지고 선이 이어진다
        if (t < 18 && t % 2 == 0) sound(level, AstraShape.skyStar(f.ground, t / 2), SoundEvents.AMETHYST_BLOCK_CHIME, 6.0f, 0.5f + t * 0.06f)
        if (t in 18..30 && t % 3 == 0) sound(level, altair, SoundEvents.AMETHYST_CLUSTER_PLACE, 6.0f, 1.0f + (t - 18) * 0.03f)
        if (t == 30) sound(level, altair, SoundEvents.BEACON_POWER_SELECT, 8.0f, 0.8f)
        if (t == AstraShape.ALTAIR_IMPACT - 16) sound(level, altair, SoundEvents.WARDEN_SONIC_CHARGE, 8.0f, 1.2f)
        for (i in Astra.STARS.indices) {
            val at = AstraShape.impactTick(i)
            if (t == at - 6) sound(level, AstraShape.skyStar(f.ground, i), SoundEvents.FIREWORK_ROCKET_LAUNCH, 6.0f, 0.6f)
            if (t == at) impact(f, i, player)
        }
    }

    /** 별 하나가 땅에 닿았다. 알타이르(0)는 마지막으로 가장 크게 */
    private fun impact(f: Fall, i: Int, player: ServerPlayer) {
        val level = f.level
        val p = f.points[i]
        val big = i == 0
        val radius = if (big) STRIKE_RADIUS else 4.5
        val damage = if (big) STRIKE_DAMAGE else STRIKE_DAMAGE * 0.35f
        val box = AABB(p, p).inflate(radius, 8.0, radius)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, player) }) {
            val rel = e.position().subtract(p)
            val dist = sqrt(rel.x * rel.x + rel.z * rel.z)
            if (dist > radius) continue
            val k = 1.0 - 0.4 * dist / radius
            val out = if (dist < 0.1) Vec3(0.0, 0.0, 0.0) else Vec3(rel.x / dist, 0.0, rel.z / dist)
            hurt(level, e, player, (damage * k).toFloat(), out.scale(if (big) 1.4 else 0.8).add(0.0, if (big) 0.9 else 0.5, 0.0))
            if (big) e.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 200, 1))
        }
        if (big) {
            sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 8.0f, 0.7f)
            sound(level, p, SoundEvents.TRIDENT_THUNDER, 8.0f, 1.3f)
            sound(level, p, SoundEvents.END_PORTAL_SPAWN, 4.0f, 1.6f)
        } else {
            sound(level, p, SoundEvents.GENERIC_EXPLODE, 5.0f, 1.2f + i * 0.04f)
            sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 5.0f, 0.8f)
        }
        AstraScar.crater(level, player, p, if (big) 4.5 else 2.3)
        AstraScar.crystal(level, player, p, i, big)
        // 두 끝이 모두 떨어진 별자리 선은 땅에 불타 새겨진다
        for ((a, c) in LINES) {
            if (a != i && c != i) continue
            val other = if (a == i) c else a
            if (AstraShape.impactTick(other) > AstraShape.impactTick(i)) continue
            AstraScar.burnLine(level, player, f.points[a], f.points[c])
        }
    }

    // ───────────── 매 틱 ─────────────

    fun tick(server: MinecraftServer) {
        if (waves.isEmpty() && beams.isEmpty() && falls.isEmpty()) return
        run {
            val it = waves.iterator()
            while (it.hasNext()) {
                val w = it.next()
                val p = server.playerList.getPlayer(w.player)
                if (p == null || !tickWave(w, p)) it.remove()
            }
        }
        run {
            val it = beams.iterator()
            while (it.hasNext()) {
                val b = it.next()
                val t = (b.level.gameTime - b.start).toInt()
                val p = server.playerList.getPlayer(b.player)
                if (p == null || t > AstraShape.RAIL_END) { it.remove(); continue }
                tickBeam(b, t, p)
            }
        }
        run {
            val it = falls.iterator()
            while (it.hasNext()) {
                val f = it.next()
                val t = (f.level.gameTime - f.start).toInt()
                val p = server.playerList.getPlayer(f.player)
                if (p == null || t > AstraShape.NINE_END) { it.remove(); continue }
                tickFall(f, t, p)
            }
        }
    }

    /** 손에 쥐고 있으면 몸 둘레로 별빛이 맴돈다(왼손이면 반대로 돈다) */
    fun tickHeld(player: ServerPlayer) {
        val main = player.mainHandItem.`is`(ModItems.ASTRA_OBLITERATOR)
        val off = player.offhandItem.`is`(ModItems.ASTRA_OBLITERATOR)
        if (player.tickCount % 2 != 0 || (!main && !off)) return
        val level = player.level() as ServerLevel
        val i = (player.tickCount / 2) % STARS.size
        val ang = player.tickCount * (if (main) 0.18 else -0.18)
        val p = player.position().add(cos(ang) * 1.0, 1.0 + sin(ang * 0.5) * 0.45, sin(ang) * 1.0)
        level.sendParticles(dust(STARS[i].color, 0.9f), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0)
        if (player.tickCount % 6 == 0) {
            val q = player.position().add(cos(ang + PI) * 0.8, 0.3, sin(ang + PI) * 0.8)
            level.sendParticles(fade(VIOLET, VOID, 0.8f), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
    }
}
