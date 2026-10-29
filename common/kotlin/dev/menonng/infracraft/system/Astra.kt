package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.color
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.msg
import dev.menonng.infracraft.net.Payloads
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
 *  - 일반 공격: 칼끝에서 암흑빛 별빛 검격(초승달 파동)이 앞으로 날아가며 닿는 것을 모두 벤다(관통).
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

    /** 멀리서도 보이도록 반경 256 안의 플레이어에게 직접 보낸다 */
    private fun fx(level: ServerLevel, p: ParticleOptions, at: Vec3, n: Int, spread: Double, speed: Double) {
        for (pl in level.players()) {
            if (pl.distanceToSqr(at) > 256.0 * 256.0) continue
            level.sendParticles(pl, p, true, false, at.x, at.y, at.z, n, spread, spread, spread, speed)
        }
    }

    /** 방향이 있는 입자 하나(count 0 → 속도 벡터) */
    private fun fly(level: ServerLevel, p: ParticleOptions, at: Vec3, v: Vec3, speed: Double = 1.0) {
        for (pl in level.players()) {
            if (pl.distanceToSqr(at) > 256.0 * 256.0) continue
            level.sendParticles(pl, p, true, false, at.x, at.y, at.z, 0, v.x, v.y, v.z, speed)
        }
    }

    private fun sound(level: ServerLevel, at: Vec3, e: SoundEvent, vol: Float, pitch: Float) =
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch)

    private fun sound(level: ServerLevel, at: Vec3, e: net.minecraft.core.Holder<SoundEvent>, vol: Float, pitch: Float) =
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch)

    private fun basis(look: Vec3): Pair<Vec3, Vec3> {
        var right = look.cross(Vec3(0.0, 1.0, 0.0))
        right = if (right.lengthSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else right.normalize()
        return right to right.cross(look).normalize()
    }

    private fun friendly(e: LivingEntity, caster: ServerPlayer) = e === caster || (e is TamableAnimal && e.isOwnedBy(caster))

    private fun hurt(level: ServerLevel, e: LivingEntity, caster: ServerPlayer, amount: Float, push: Vec3) {
        if (e is net.minecraft.world.entity.player.Player && !dev.menonng.infracraft.Config.c.astraHitsPlayers) return
        e.hurtServer(level, level.damageSources().playerAttack(caster), amount)
        e.addEffect(MobEffectInstance(MobEffects.GLOWING, 100, 0))
        e.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 2))
        e.push(push.x, push.y, push.z)
        Compat.syncVelocity(e)
    }

    // ───────────── 1) 일반 공격: 암흑 별빛 검격 ─────────────

    private class Wave(val player: UUID, val level: ServerLevel, val origin: Vec3, val dir: Vec3, val right: Vec3, val up: Vec3,
                       val tilt: Double, var age: Int = 0, val hit: HashSet<Int> = HashSet())

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
        val (right, up) = basis(look)
        val side = !(slashSide[player.uuid] ?: false)
        slashSide[player.uuid] = side
        val origin = player.eyePosition.add(look.scale(1.2)).add(0.0, -0.25, 0.0)
        waves.add(Wave(player.uuid, level, origin, look, right, up, if (side) 0.55 else -0.55))
        sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.55f)
        sound(level, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f)
        sound(level, origin, SoundEvents.ENDER_EYE_DEATH, 0.6f, 0.6f)
    }

    private fun tickWave(w: Wave, caster: ServerPlayer): Boolean {
        val level = w.level
        val d = 1.0 + w.age * 1.15
        val center = w.origin.add(w.dir.scale(d))
        val half = 2.2 + w.age * 0.12
        // 초승달: 가운데가 앞으로 불룩, 기울기는 번갈아 좌·우
        fun arc(s: Double, ahead: Double): Vec3 = center
            .add(w.dir.scale((1 - s * s) * 0.9 + ahead))
            .add(w.right.scale(s * half))
            .add(w.up.scale(s * half * w.tilt))
        val steps = 22
        for (i in 0..steps) {
            val s = i / steps.toDouble() * 2 - 1
            val edge = 1 - s * s
            fx(level, fade(VIOLET, VOID, 1.6f + 1.2f * edge.toFloat()), arc(s, 0.0), 1, 0.03, 0.0)
            fx(level, fade(VOID, 0x000000, 2.2f), arc(s, -0.45), 1, 0.05, 0.0)
            if (i % 3 == w.age % 3) fx(level, dust(STARLIGHT, 0.7f), arc(s, 0.25), 1, 0.02, 0.0)
            if (i % 5 == 0) fly(level, ParticleTypes.END_ROD, arc(s, 0.2), w.dir.scale(0.05), 1.0)
            if (i % 4 == 2) fx(level, ParticleTypes.REVERSE_PORTAL, arc(s, -0.7), 1, 0.08, 0.02)
        }
        if (w.age % 3 == 0) fx(level, ParticleTypes.SQUID_INK, center.add(w.dir.scale(-0.6)), 2, 0.4, 0.01)
        // 판정: 초승달 판 근처(앞뒤 1.4칸, 옆 half+0.6)
        val box = AABB(center, center).inflate(half + 1.5)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, caster) && it.id !in w.hit }) {
            val rel = e.position().add(0.0, e.bbHeight / 2.0, 0.0).subtract(center)
            if (kotlin.math.abs(rel.dot(w.dir)) > 1.4 || kotlin.math.abs(rel.dot(w.right)) > half + 0.6) continue
            w.hit.add(e.id)
            hurt(level, e, caster, SLASH_DAMAGE, w.dir.scale(0.6).add(0.0, 0.2, 0.0))
            fx(level, dust(STARLIGHT, 2.0f), e.position().add(0.0, e.bbHeight / 2.0, 0.0), 10, 0.3, 0.0)
            fx(level, ParticleTypes.CRIT, e.position().add(0.0, e.bbHeight / 2.0, 0.0), 12, 0.3, 0.4)
            sound(level, e.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f)
        }
        // 벽에 부딪히면 흩어진다
        val bp = BlockPos.containing(center)
        if (level.getBlockState(bp).isSuffocating(level, bp)) {
            fx(level, dust(VIOLET, 2.4f), center, 30, 0.6, 0.0)
            fx(level, ParticleTypes.END_ROD, center, 12, 0.3, 0.12)
            return false
        }
        w.age++
        return w.age < SLASH_RANGE
    }

    // ───────────── 2) 오른손 우클릭: 별자리 광선 ─────────────

    private class Beam(val player: UUID, val level: ServerLevel, val eye: Vec3, val look: Vec3, val start: Long)
    private val beams = ArrayList<Beam>()

    fun cast(player: ServerPlayer, stack: ItemStack) {
        if (player.cooldowns.isOnCooldown(stack)) return
        player.cooldowns.addCooldown(stack, COOLDOWN)
        val level = player.level() as ServerLevel
        beams.add(Beam(player.uuid, level, player.eyePosition, player.lookAngle.normalize(), level.gameTime))
        Compat.overlay(player, msg("astra.cast", Palette.PINK))
    }

    private fun beamFrame(b: Beam): Triple<Vec3, Vec3, Vec3> {
        val (r, u) = basis(b.look)
        return Triple(b.eye.add(b.look.scale(12.0)).add(u.scale(3.5)), r, u)
    }

    private fun beamStar(b: Beam, s: Star): Vec3 {
        val (c, r, u) = beamFrame(b)
        return c.add(r.scale(s.x * 1.9)).add(u.scale(s.y * 1.9))
    }

    private fun tickBeam(b: Beam, t: Int, player: ServerPlayer) {
        val level = b.level
        if (t < 18 && t % 2 == 0) {
            val s = STARS[t / 2]
            val p = beamStar(b, s)
            fx(level, dust(s.color, 3.2f), p, 28, 0.18, 0.0)
            fx(level, ParticleTypes.END_ROD, p, 12, 0.05, 0.1)
            sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f + t * 0.06f)
        }
        if (t in 2..40) for ((i, s) in STARS.withIndex()) if (i * 2 <= t) {
            fx(level, dust(s.color, 1.8f + 0.7f * sin((t + i) * 0.7).toFloat()), beamStar(b, s), 3, 0.07, 0.0)
        }
        if (t in 18..33) {
            val f = ((t - 18) / 15.0).coerceIn(0.0, 1.0)
            for ((a, c) in LINES) {
                val pa = beamStar(b, STARS[a]); val pb = beamStar(b, STARS[c])
                for (k in 0..(16 * f).toInt()) {
                    val u = k / 16.0
                    fx(level, dust(if (u < 0.5) STARS[a].color else STARS[c].color, 1.1f), pa.add(pb.subtract(pa).scale(u)), 1, 0.02, 0.0)
                }
            }
            if (t % 4 == 0) sound(level, beamStar(b, STARS[0]), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6f, 1.2f + (t - 18) * 0.05f)
        }
        if (t in 34..39) {
            val core = beamStar(b, STARS[0])
            for (s in STARS.drop(1)) {
                val p = beamStar(b, s)
                fly(level, ParticleTypes.END_ROD, p, core.subtract(p).scale(0.18))
                fx(level, dust(s.color, 2.2f), p.add(core.subtract(p).scale((t - 33) / 6.0)), 4, 0.1, 0.0)
            }
            if (t == 34) sound(level, core, SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.4f)
            fx(level, dust(0xFFFFFF, 4.0f), core, 14, 0.2 + (t - 34) * 0.1, 0.0)
        }
        if (t == 40) fireBeam(b, player)
        if (t in 41..72 && t % 2 == 0) {
            val (_, r, u) = beamFrame(b)
            val radius = 1.0 + (t - 40) * 0.16
            for (d in listOf(6.0, 20.0, 34.0, 48.0)) {
                val c = b.eye.add(b.look.scale(d))
                for (k in 0 until 24) {
                    val ang = k / 24.0 * PI * 2 + t * 0.15
                    fx(level, dust(STARS[k % STARS.size].color, 1.4f), c.add(r.scale(cos(ang) * radius)).add(u.scale(sin(ang) * radius)), 1, 0.0, 0.0)
                }
            }
        }
    }

    private fun fireBeam(b: Beam, player: ServerPlayer) {
        val level = b.level
        val from = beamStar(b, STARS[0])
        val target = b.eye.add(b.look.scale(BEAM_RANGE))
        val hit = level.clip(ClipContext(b.eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        val end = if (hit.type == HitResult.Type.MISS) target else hit.location
        val path = end.subtract(from)
        val len = path.length()
        val dir = path.normalize()
        val (r, u) = basis(dir)
        var d = 0.0
        while (d < len) {
            val q = from.add(dir.scale(d))
            fx(level, dust(STARLIGHT, 4.0f), q, 2, 0.1, 0.0)
            fx(level, fade(VIOLET, VOID, 2.6f), q, 1, 0.45, 0.0)
            fx(level, ParticleTypes.END_ROD, q, 1, 0.15, 0.02)
            // 광선을 감고 도는 아홉 빛깔 나선
            val ang = d * 0.9
            fx(level, dust(STARS[(d.toInt()) % STARS.size].color, 1.6f), q.add(r.scale(cos(ang) * 1.3)).add(u.scale(sin(ang) * 1.3)), 1, 0.0, 0.0)
            if (d.toInt() % 6 == 0) fx(level, ParticleTypes.ELECTRIC_SPARK, q, 4, 0.4, 0.2)
            d += 0.5
        }
        fx(level, ParticleTypes.SONIC_BOOM, from, 1, 0.0, 0.0)
        impactBurst(level, end, 1.0)
        sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.3f)
        sound(level, from, SoundEvents.BEACON_ACTIVATE, 3.0f, 1.8f)
        val box = AABB(from, end).inflate(BEAM_RADIUS)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, player) }) {
            val rel = e.position().add(0.0, e.bbHeight / 2.0, 0.0).subtract(from)
            val along = rel.dot(dir).coerceIn(0.0, len)
            if (rel.subtract(dir.scale(along)).length() > BEAM_RADIUS) continue
            hurt(level, e, player, BEAM_DAMAGE, Vec3(dir.x * 1.6, 0.5, dir.z * 1.6))
        }
    }

    /** 폭발처럼 보이는 별빛 폭발(지형은 그대로) */
    private fun impactBurst(level: ServerLevel, at: Vec3, scale: Double) {
        fx(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0.0, 0.0)
        fx(level, ParticleTypes.FIREWORK, at, (160 * scale).toInt(), 0.8 * scale, 0.5 * scale)
        for (s in STARS) fx(level, dust(s.color, 3.2f), at, (24 * scale).toInt(), 1.8 * scale, 0.0)
        fx(level, fade(VIOLET, VOID, 3.0f), at, (60 * scale).toInt(), 2.5 * scale, 0.0)
        sound(level, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 4.0f, 0.8f)
        sound(level, at, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 4.0f, 1.0f)
    }

    // ───────────── 3) 조준 낙하: 시야의 한 점 또는 지도에서 고른 곳 ─────────────

    private class Fall(val player: UUID, val level: ServerLevel, val ground: Vec3, val start: Long)
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
        player.cooldowns.addCooldown(stack, COOLDOWN)
        falls.add(Fall(player.uuid, level, ground, level.gameTime))
        sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f)
        Compat.overlay(player, msg("astra.strike", Palette.PINK, ground.x.toInt().toString(), ground.y.toInt().toString(), ground.z.toInt().toString()))
    }

    private const val SKY = 28.0
    private const val SPREAD = 3.4

    /** 낙하 지점 위 하늘(수평면)에 펼친 독수리자리 */
    private fun fallStar(f: Fall, s: Star, spin: Double): Vec3 {
        val a = spin
        val x = s.x * cos(a) - s.y * sin(a)
        val z = s.x * sin(a) + s.y * cos(a)
        return f.ground.add(x * SPREAD, SKY, z * SPREAD)
    }

    private fun tickFall(f: Fall, t: Int, player: ServerPlayer) {
        val level = f.level
        if (t % 20 == 0) Compat.keepChunkLoaded(level, BlockPos.containing(f.ground))
        val spin = t * 0.01
        val altair = fallStar(f, STARS[0], spin)
        // 땅의 예고 원: 바깥 고리 + 안쪽 고리(반대로 회전) + 아홉 갈래 바퀴살
        if (t in 0..44 && t % 2 == 0) {
            val g = f.ground.add(0.0, 0.15, 0.0)
            val grow = (t / 20.0).coerceAtMost(1.0)
            for (k in 0 until 64) {
                val a = k / 64.0 * PI * 2 + t * 0.05
                fx(level, fade(VIOLET, VOID, 1.4f), g.add(cos(a) * STRIKE_RADIUS * grow, 0.0, sin(a) * STRIKE_RADIUS * grow), 1, 0.0, 0.0)
            }
            for (k in 0 until 36) {
                val a = k / 36.0 * PI * 2 - t * 0.08
                fx(level, dust(STARLIGHT, 0.9f), g.add(cos(a) * STRIKE_RADIUS * 0.55 * grow, 0.0, sin(a) * STRIKE_RADIUS * 0.55 * grow), 1, 0.0, 0.0)
            }
            for ((i, s) in STARS.withIndex()) {
                val a = i / 9.0 * PI * 2 + t * 0.03
                for (k in 1..6) fx(level, dust(s.color, 0.8f), g.add(cos(a) * k * STRIKE_RADIUS / 6 * grow, 0.0, sin(a) * k * STRIKE_RADIUS / 6 * grow), 1, 0.0, 0.0)
            }
        }
        // 하늘에 별이 하나씩
        if (t < 18 && t % 2 == 0) {
            val s = STARS[t / 2]
            val p = fallStar(f, s, spin)
            fx(level, dust(s.color, 4.0f), p, 36, 0.3, 0.0)
            fx(level, ParticleTypes.END_ROD, p, 16, 0.1, 0.12)
            sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 4.0f, 0.5f + t * 0.06f)
        }
        if (t in 2..46) for ((i, s) in STARS.withIndex()) if (i * 2 <= t) {
            fx(level, dust(s.color, 2.6f + sin((t + i) * 0.6).toFloat()), fallStar(f, s, spin), 4, 0.12, 0.0)
        }
        // 별자리 선
        if (t in 18..46) {
            val fr = ((t - 18) / 12.0).coerceIn(0.0, 1.0)
            for ((a, c) in LINES) {
                val pa = fallStar(f, STARS[a], spin); val pb = fallStar(f, STARS[c], spin)
                val n = (pa.distanceTo(pb) * 2).toInt()
                for (k in 0..(n * fr).toInt()) {
                    val u = k / n.toDouble()
                    fx(level, dust(if (u < 0.5) STARS[a].color else STARS[c].color, 1.4f), pa.add(pb.subtract(pa).scale(u)), 1, 0.03, 0.0)
                }
            }
            if (t % 3 == 0) sound(level, altair, SoundEvents.AMETHYST_CLUSTER_PLACE, 3.0f, 1.0f + (t - 18) * 0.03f)
        }
        // 별빛이 알타이르로 모여 커지는 빛구슬
        if (t in 30..47) {
            for (s in STARS.drop(1)) {
                val p = fallStar(f, s, spin)
                fly(level, ParticleTypes.END_ROD, p, altair.subtract(p).scale(0.12))
            }
            val r = 0.3 + (t - 30) * 0.12
            fx(level, dust(0xFFFFFF, 4.0f), altair, 20, r, 0.0)
            fx(level, fade(VIOLET, VOID, 3.0f), altair, 14, r * 1.6, 0.0)
            if (t == 30) sound(level, altair, SoundEvents.BEACON_POWER_SELECT, 5.0f, 0.8f)
            if (t == 44) sound(level, altair, SoundEvents.WARDEN_SONIC_CHARGE, 5.0f, 1.2f)
        }
        // 낙하: 하늘에서 땅까지 별빛 기둥
        if (t == 48) {
            val down = f.ground.subtract(altair)
            var d = 0.0
            val len = down.length()
            val dir = down.normalize()
            while (d < len) {
                val q = altair.add(dir.scale(d))
                fx(level, dust(STARLIGHT, 4.0f), q, 4, 0.35, 0.0)
                fx(level, fade(VIOLET, VOID, 3.0f), q, 3, 1.1, 0.0)
                fx(level, ParticleTypes.END_ROD, q, 2, 0.3, 0.05)
                d += 0.4
            }
            impactBurst(level, f.ground.add(0.0, 1.0, 0.0), 2.2)
            fx(level, ParticleTypes.SONIC_BOOM, f.ground.add(0.0, 1.0, 0.0), 3, 1.0, 0.0)
            sound(level, f.ground, SoundEvents.WARDEN_SONIC_BOOM, 6.0f, 0.7f)
            sound(level, f.ground, SoundEvents.TRIDENT_THUNDER, 6.0f, 1.3f)
            sound(level, f.ground, SoundEvents.END_PORTAL_SPAWN, 3.0f, 1.6f)
            // 피해: 가운데일수록 크게(가장자리 60%), 바깥으로 튕겨 올린다
            val box = AABB(f.ground, f.ground).inflate(STRIKE_RADIUS, 8.0, STRIKE_RADIUS)
            for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !friendly(it, player) }) {
                val rel = e.position().subtract(f.ground)
                val dist = sqrt(rel.x * rel.x + rel.z * rel.z)
                if (dist > STRIKE_RADIUS) continue
                val k = 1.0 - 0.4 * dist / STRIKE_RADIUS
                val out = if (dist < 0.1) Vec3(0.0, 0.0, 0.0) else Vec3(rel.x / dist, 0.0, rel.z / dist)
                hurt(level, e, player, (STRIKE_DAMAGE * k).toFloat(), out.scale(1.4).add(0.0, 0.9, 0.0))
                e.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 200, 1))
            }
        }
        // 여운: 땅을 따라 퍼지는 충격파 고리, 바깥으로 날아가는 여덟 갈래 암흑 검격, 위로 솟는 별가루
        if (t in 49..80) {
            val k = t - 48
            val g = f.ground.add(0.0, 0.3, 0.0)
            val radius = k * 0.9
            if (radius < STRIKE_RADIUS * 1.6) for (i in 0 until 72) {
                val a = i / 72.0 * PI * 2
                fx(level, if (i % 2 == 0) dust(STARLIGHT, 1.6f) else fade(VIOLET, VOID, 2.0f), g.add(cos(a) * radius, 0.0, sin(a) * radius), 1, 0.05, 0.0)
            }
            if (k <= 14) for (j in 0 until 8) {
                val a = j / 8.0 * PI * 2 + 0.2
                val dir = Vec3(cos(a), 0.0, sin(a))
                val side = Vec3(-sin(a), 0.0, cos(a))
                val c = g.add(dir.scale(2.0 + k * 1.1)).add(0.0, 0.8, 0.0)
                for (q in -6..6) {
                    val s = q / 6.0
                    fx(level, fade(VIOLET, VOID, 1.8f), c.add(dir.scale((1 - s * s) * 0.7)).add(side.scale(s * 1.8)), 1, 0.02, 0.0)
                }
            }
            if (t % 2 == 0) {
                fx(level, ParticleTypes.END_ROD, g, 10, STRIKE_RADIUS * 0.4, 0.15)
                for (s in STARS) fx(level, dust(s.color, 1.2f), g.add(0.0, k * 0.4, 0.0), 2, 2.5, 0.0)
            }
            if (t == 60) sound(level, f.ground, SoundEvents.AMETHYST_BLOCK_RESONATE, 4.0f, 0.6f)
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
                if (p == null || t > 72) { it.remove(); continue }
                tickBeam(b, t, p)
            }
        }
        run {
            val it = falls.iterator()
            while (it.hasNext()) {
                val f = it.next()
                val t = (f.level.gameTime - f.start).toInt()
                val p = server.playerList.getPlayer(f.player)
                if (p == null || t > 80) { it.remove(); continue }
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
