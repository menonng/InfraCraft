package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.compat.HorseEntity
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModAttributes
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.content.ModTags
import dev.menonng.infracraft.content.id
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.WeakHashMap
import kotlin.math.sqrt

/**
 * 도로 등급 보너스. 연속된 도로 위에서 속도 보너스가 서서히 쌓이고(관성), 도로를 벗어나면 서서히 빠진다.
 * 걷는 플레이어와 타고 있는 말 모두에 적용된다.
 */
object RoadSystem {
    private val MODIFIER_ID = id("road_speed")

    private class Momentum(var bonus: Double = 0.0, var lastX: Double = 0.0, var lastZ: Double = 0.0)

    private val momentum = WeakHashMap<LivingEntity, Momentum>()
    private val lastMount = WeakHashMap<ServerPlayer, LivingEntity>()

    fun roadTier(entity: LivingEntity): Int {
        val level = entity.level()
        val pos = entity.blockPosition()
        for (p in listOf(pos, pos.below())) {
            val s = level.getBlockState(p)
            if (s.`is`(ModTags.ROAD_TIER_3)) return 3
            if (s.`is`(ModTags.ROAD_TIER_2)) return 2
            if (s.`is`(ModTags.ROAD_TIER_1)) return 1
            if (!s.isAir) return 0
        }
        return 0
    }

    /** 플레이어 로드러너(속성), 말 편자 등급 + 기수 인챈트 */
    private fun affinity(mover: LivingEntity, rider: ServerPlayer): Double = when (mover) {
        rider -> rider.getAttribute(ModAttributes.ROAD_AFFINITY)?.value ?: 0.0
        is HorseEntity -> 0.25 * (mover.getAttached(ModAttachments.HORSESHOE) ?: 0) + 0.15 * Enchants.level(rider, ModEnchantments.EQUESTRIAN)
        else -> 0.0
    }

    fun tick(player: ServerPlayer) {
        val mover: LivingEntity = (player.vehicle as? HorseEntity) ?: player
        lastMount[player]?.let { if (it !== mover) clear(it) }
        lastMount[player] = mover

        val m = momentum.getOrPut(mover) { Momentum(lastX = mover.x, lastZ = mover.z) }
        val tier = if (player.abilities.flying || mover.isInWater) 0 else roadTier(mover)
        val target = if (tier == 0) 0.0 else Config.c.roadTierBonus.getOrElse(tier - 1) { 0.0 } * (1.0 + affinity(mover, player))

        m.bonus = if (m.bonus < target) minOf(target, m.bonus + Config.c.roadRampPerTick)
        else {
            val keep = 1.0 - 0.35 * Enchants.level(player, ModEnchantments.MOMENTUM)
            maxOf(target, m.bonus - Config.c.roadDecayPerTick * keep)
        }
        apply(mover, m.bonus)

        val dx = mover.x - m.lastX
        val dz = mover.z - m.lastZ
        m.lastX = mover.x
        m.lastZ = mover.z
        if (tier > 0) {
            val cm = (sqrt(dx * dx + dz * dz) * 100).toInt()
            if (cm in 1..999) player.awardStat(ModStats.ROAD_ONE_CM, cm)
        }
    }

    private fun apply(entity: LivingEntity, bonus: Double) {
        val attr = entity.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
        val current = attr.getModifier(MODIFIER_ID)
        if (bonus < 0.001) {
            if (current != null) attr.removeModifier(MODIFIER_ID)
            return
        }
        if (current != null && kotlin.math.abs(current.amount() - bonus) < 0.0005) return
        attr.removeModifier(MODIFIER_ID)
        attr.addTransientModifier(AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE))
    }

    fun clear(entity: LivingEntity) {
        entity.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
        momentum.remove(entity)
    }
}
