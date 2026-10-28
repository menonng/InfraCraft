package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.msg
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * 히든 아이템: 아스트라 오브리터레이터(독수리자리, Aquila).
 * 우클릭하면 시선 앞 하늘에 독수리자리 아홉 별이 하나씩 떠오르고, 별자리 선이 이어진 뒤, 별빛이 알타이르로 모여
 * 시선 방향으로 별빛 광선이 뻗는다. 지형은 부수지 않는다.
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
    private val LINES = listOf(1 to 0, 0 to 2, 0 to 7, 7 to 8, 7 to 3, 0 to 4, 4 to 5, 7 to 6)

    private const val SCALE = 1.7
    private const val RANGE = 64.0
    private const val DAMAGE = 40f
    private const val COOLDOWN = 140

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
                listOf(Component.translatable("item.infracraft.lore.weapon_use").withStyle { it.withItalic(false).withColor(0xFFB4CC) })))
        .attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, AttributeModifier(id("astra_damage"), 18.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, AttributeModifier(id("astra_speed"), -2.2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build())

    // ───────────── 발동 ─────────────

    private class Strike(val player: UUID, val level: ServerLevel, val eye: Vec3, val look: Vec3, val start: Long)

    private val strikes = ArrayList<Strike>()

    fun cast(player: ServerPlayer, stack: ItemStack) {
        if (player.cooldowns.isOnCooldown(stack)) return
        player.cooldowns.addCooldown(stack, COOLDOWN)
        val level = player.level() as ServerLevel
        strikes.add(Strike(player.uuid, level, player.eyePosition, player.lookAngle.normalize(), level.gameTime))
        Compat.overlay(player, msg("astra.cast", Palette.PINK))
    }

    /** 별자리 평면: 시선 앞 12칸, 위로 3칸. right/up 은 시선에 수직인 축 */
    private fun frame(s: Strike): Triple<Vec3, Vec3, Vec3> {
        val worldUp = Vec3(0.0, 1.0, 0.0)
        var right = s.look.cross(worldUp)
        right = if (right.lengthSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else right.normalize()
        val up = right.cross(s.look).normalize()
        val center = s.eye.add(s.look.scale(12.0)).add(up.scale(3.0))
        return Triple(center, right, up)
    }

    private fun starPos(s: Strike, star: Star): Vec3 {
        val (c, r, u) = frame(s)
        return c.add(r.scale(star.x * SCALE)).add(u.scale(star.y * SCALE))
    }

    private fun dust(color: Int, size: Float): ParticleOptions = DustParticleOptions(color, size)

    private fun puff(level: ServerLevel, p: ParticleOptions, at: Vec3, n: Int, spread: Double, speed: Double) {
        level.sendParticles(p, at.x, at.y, at.z, n, spread, spread, spread, speed)
    }

    private fun sound(level: ServerLevel, at: Vec3, e: SoundEvent, vol: Float, pitch: Float) =
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch)

    fun tick(server: MinecraftServer) {
        if (strikes.isEmpty()) return
        val it = strikes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val t = (s.level.gameTime - s.start).toInt()
            val player = server.playerList.getPlayer(s.player)
            if (player == null || t > 70) { it.remove(); continue }
            step(s, t, player)
        }
    }

    private fun step(s: Strike, t: Int, player: ServerPlayer) {
        val level = s.level
        // 1) 별이 하나씩 떠오른다(2틱 간격)
        if (t < 18 && t % 2 == 0) {
            val star = STARS[t / 2]
            val p = starPos(s, star)
            puff(level, dust(star.color, 3.0f), p, 24, 0.15, 0.0)
            puff(level, ParticleTypes.END_ROD, p, 10, 0.05, 0.08)
            sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f + t * 0.06f)
        }
        // 떠오른 별은 계속 반짝인다
        if (t in 2..40) for ((i, star) in STARS.withIndex()) if (i * 2 <= t) {
            puff(level, dust(star.color, 1.6f + 0.6f * sin((t + i) * 0.7).toFloat()), starPos(s, star), 3, 0.06, 0.0)
        }
        // 2) 별자리 선이 그려진다
        if (t in 18..33) {
            val f = ((t - 18) / 15.0).coerceIn(0.0, 1.0)
            for ((a, b) in LINES) {
                val pa = starPos(s, STARS[a]); val pb = starPos(s, STARS[b])
                val steps = 14
                for (k in 0..(steps * f).toInt()) {
                    val u = k / steps.toDouble()
                    val q = pa.add(pb.subtract(pa).scale(u))
                    val col = if (u < 0.5) STARS[a].color else STARS[b].color
                    puff(level, dust(col, 1.0f), q, 1, 0.02, 0.0)
                }
            }
            if (t % 4 == 0) sound(level, starPos(s, STARS[0]), SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6f, 1.2f + (t - 18) * 0.05f)
        }
        // 3) 별빛이 알타이르로 모인다
        if (t in 34..39) {
            val core = starPos(s, STARS[0])
            for (star in STARS.drop(1)) {
                val p = starPos(s, star)
                val v = core.subtract(p).scale(0.18)
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0)
                level.sendParticles(dust(star.color, 2.0f), p.x + v.x * (t - 33), p.y + v.y * (t - 33), p.z + v.z * (t - 33), 4, 0.1, 0.1, 0.1, 0.0)
            }
            if (t == 34) sound(level, core, SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.4f)
            puff(level, dust(0xFFFFFF, 4.0f), core, 12, 0.2 + (t - 34) * 0.08, 0.0)
        }
        // 4) 별빛 광선
        if (t == 40) fire(s, player)
        // 5) 여운: 시선 축을 따라 아홉 빛깔 고리가 퍼진다
        if (t in 41..70 && t % 2 == 0) {
            val (_, r, u) = frame(s)
            val radius = 0.8 + (t - 40) * 0.12
            for (d in listOf(6.0, 18.0, 30.0)) {
                val c = s.eye.add(s.look.scale(d))
                for (k in 0 until 18) {
                    val ang = k / 18.0 * Math.PI * 2 + t * 0.15
                    val q = c.add(r.scale(cos(ang) * radius)).add(u.scale(sin(ang) * radius))
                    puff(level, dust(STARS[k % STARS.size].color, 1.3f), q, 1, 0.0, 0.0)
                }
            }
        }
    }

    private fun fire(s: Strike, player: ServerPlayer) {
        val level = s.level
        val from = starPos(s, STARS[0])
        // 시선이 닿는 곳(블록)까지, 없으면 RANGE
        val target = s.eye.add(s.look.scale(RANGE))
        val hit = level.clip(ClipContext(s.eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        val end = if (hit.type == HitResult.Type.MISS) target else hit.location
        val path = end.subtract(from)
        val len = path.length()
        val dir = path.normalize()
        var d = 0.0
        while (d < len) {
            val q = from.add(dir.scale(d))
            puff(level, dust(0xEAF4FF, 3.5f), q, 2, 0.08, 0.0)
            puff(level, ParticleTypes.END_ROD, q, 1, 0.12, 0.02)
            if ((d * 2).toInt() % 3 == 0) puff(level, dust(STARS[((d * 2).toInt() / 3) % STARS.size].color, 2.0f), q, 2, 0.35, 0.0)
            if ((d * 2).toInt() % 7 == 0) puff(level, ParticleTypes.ELECTRIC_SPARK, q, 4, 0.3, 0.2)
            d += 0.5
        }
        puff(level, ParticleTypes.SONIC_BOOM, from, 1, 0.0, 0.0)
        puff(level, ParticleTypes.EXPLOSION_EMITTER, end, 1, 0.0, 0.0)
        puff(level, ParticleTypes.FIREWORK, end, 120, 0.6, 0.45)
        for (star in STARS) puff(level, dust(star.color, 3.0f), end, 20, 1.6, 0.0)
        sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.3f)
        sound(level, from, SoundEvents.BEACON_ACTIVATE, 3.0f, 1.8f)
        sound(level, end, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 4.0f, 0.8f)
        sound(level, end, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 4.0f, 1.0f)
        // 광선 둘레 2칸 안의 생명체(시전자 제외)에 피해
        val box = AABB(from, end).inflate(2.5)
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== player && it.isAlive }) {
            val rel = e.position().add(0.0, e.bbHeight / 2.0, 0.0).subtract(from)
            val along = rel.dot(dir).coerceIn(0.0, len)
            if (rel.subtract(dir.scale(along)).length() > 2.5) continue
            e.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE)
            e.push(dir.x * 1.2, 0.4, dir.z * 1.2)
            Compat.syncVelocity(e)
        }
    }

    /** 손에 쥐고 있으면 몸 둘레로 별빛이 맴돈다 */
    fun tickHeld(player: ServerPlayer) {
        if (player.tickCount % 3 != 0 || !player.mainHandItem.`is`(ModItems.ASTRA_OBLITERATOR)) return
        val level = player.level() as ServerLevel
        val i = (player.tickCount / 3) % STARS.size
        val ang = player.tickCount * 0.18
        val p = player.position().add(cos(ang) * 0.9, 1.0 + sin(ang * 0.5) * 0.4, sin(ang) * 0.9)
        level.sendParticles(dust(STARS[i].color, 0.9f), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0)
    }
}
