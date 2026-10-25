package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.compat.HorseEntity
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.fmt1
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** 호루라기, 편자, 말 능력치 감정, 마구간 */
object Horses {
    fun bindWhistle(player: ServerPlayer, stack: ItemStack, horse: HorseEntity) {
        if (!horse.isTamed) {
            Compat.overlay(player, msg("whistle.untamed", Palette.ORANGE))
            return
        }
        stack.set(ModComponents.BOUND_ENTITY, horse.uuid)
        Compat.overlay(player, msg("whistle.bound", Palette.GREEN, horse.name))
    }

    fun callHorse(player: ServerPlayer, stack: ItemStack) {
        val id = stack.get(ModComponents.BOUND_ENTITY)
        if (id == null) {
            Compat.overlay(player, msg("whistle.unbound", Palette.ORANGE))
            return
        }
        val level = player.level() as ServerLevel
        val horse = level.getEntity(id) as? HorseEntity
        if (horse == null || horse.distanceTo(player) > Config.c.whistleRange) {
            Compat.overlay(player, msg("whistle.too_far", Palette.RED))
            return
        }
        horse.navigation.moveTo(player.x, player.y, player.z, 1.8)
        player.awardStat(ModStats.HORSES_CALLED, 1)
        Compat.overlay(player, msg("whistle.called", Palette.SKY, horse.name, horse.distanceTo(player).toInt().toString()))
    }

    fun shoe(player: ServerPlayer, stack: ItemStack, horse: HorseEntity, tier: Int) {
        val cur = horse.getAttached(ModAttachments.HORSESHOE) ?: 0
        if (cur >= tier) {
            Compat.overlay(player, msg("horseshoe.already", Palette.ORANGE))
            return
        }
        horse.setAttached(ModAttachments.HORSESHOE, tier)
        if (!player.isCreative) stack.shrink(1)
        Compat.overlay(player, msg("horseshoe.fitted", Palette.GREEN, horse.name))
    }

    /** 측량기로 말을 클릭: 속도(블록/초), 점프 높이, 체력, 편자 */
    fun appraise(player: ServerPlayer, horse: HorseEntity) {
        val speed = (horse.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue ?: 0.0) * 42.16
        val jump = horse.getAttribute(Attributes.JUMP_STRENGTH)?.baseValue ?: 0.0
        // 점프 세기 → 높이 근사식(위키)
        val height = -0.1817584952 * jump * jump * jump + 3.689713992 * jump * jump + 2.128599134 * jump - 0.343930367
        val shoe = horse.getAttached(ModAttachments.HORSESHOE) ?: 0
        Compat.chat(player, msg("horse.stats", Palette.SKY, horse.name, fmt1(speed), fmt1(height), fmt1(horse.maxHealth.toDouble()), msg("horseshoe.tier$shoe", Palette.YELLOW)))
    }

    // ───────────── 마구간 ─────────────

    fun useStable(level: ServerLevel, pos: BlockPos, player: ServerPlayer) {
        val overworld = level.server.overworld()
        val key = NetworkData.key(Compat.dimensionId(level), pos)
        val stables = overworld.getAttached(ModAttachments.STABLES) ?: emptyMap()
        val horse = player.vehicle as? HorseEntity
        if (horse != null) {
            if (stables.containsKey(key)) {
                Compat.overlay(player, msg("stable.occupied", Palette.ORANGE))
                return
            }
            player.stopRiding()
            val tag = Compat.saveEntity(horse) ?: return
            horse.discard()
            overworld.setAttached(ModAttachments.STABLES, stables + (key to tag))
            Compat.overlay(player, msg("stable.stored", Palette.GREEN, horse.name))
            return
        }
        val tag = stables[key]
        if (tag == null) {
            Compat.overlay(player, msg("stable.empty", Palette.ORANGE))
            return
        }
        val entity = Compat.loadEntity(level, tag) ?: return
        entity.setPos(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
        level.addFreshEntity(entity)
        overworld.setAttached(ModAttachments.STABLES, stables - key)
        Compat.overlay(player, msg("stable.released", Palette.GREEN, entity.name))
    }

    fun dropStable(level: Level, pos: BlockPos) {
        if (level !is ServerLevel) return
        val overworld = level.server.overworld()
        val stables = overworld.getAttached(ModAttachments.STABLES) ?: return
        val key = NetworkData.key(Compat.dimensionId(level), pos)
        val tag = stables[key] ?: return
        Compat.loadEntity(level, tag)?.let {
            it.setPos(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            level.addFreshEntity(it)
        }
        overworld.setAttached(ModAttachments.STABLES, stables - key)
    }
}
