package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.content.ModTags
import dev.menonng.infracraft.fmt1
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BaseRailBlock
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.sqrt

/** 도로 포장기, 선로 부설기, 터널 굴착기, 측량기 */
object Tools {
    private val lastBlock = WeakHashMap<Player, BlockPos>()

    fun isActive(stack: ItemStack) = stack.get(ModComponents.ACTIVE) == true

    fun toggle(player: Player, stack: ItemStack) {
        val on = !isActive(stack)
        if (on) stack.set(ModComponents.ACTIVE, true) else stack.remove(ModComponents.ACTIVE)
        Compat.overlay(player, msg(if (on) "tool.on" else "tool.off", if (on) Palette.GREEN else Palette.ORANGE, stack.hoverName))
    }

    private fun thrifty(player: ServerPlayer, tool: ItemStack): Boolean {
        val lvl = Enchants.level(player.level(), tool, ModEnchantments.THRIFT)
        return lvl > 0 && player.random.nextFloat() < (0.15f * lvl).coerceAtMost(0.6f)
    }

    private fun damage(player: ServerPlayer, tool: ItemStack, hand: InteractionHand) {
        if (!player.isCreative) tool.hurtAndBreak(1, player, if (hand == InteractionHand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND)
    }

    // ───────────── 걸으며 작동하는 도구 ─────────────

    fun tick(player: ServerPlayer) {
        val tool = player.mainHandItem
        val paver = tool.`is`(ModItems.ROAD_PAVER)
        val layer = tool.`is`(ModItems.TRACK_LAYER)
        if ((!paver && !layer) || !isActive(tool) || !player.onGround()) return
        val here = player.blockPosition()
        if (lastBlock[player] == here) return
        lastBlock[player] = here
        if (paver) pave(player, tool, here) else layTrack(player, tool, here)
    }

    private fun pave(player: ServerPlayer, tool: ItemStack, feet: BlockPos) {
        val level = player.level() as ServerLevel
        val material = player.offhandItem
        val block = (material.item as? BlockItem)?.block
        if (block == null || material.isEmpty) {
            Compat.overlay(player, msg("paver.need_material", Palette.ORANGE))
            return
        }
        val state = block.defaultBlockState()
        val facing = player.direction
        val side = facing.clockWise
        val width = Config.c.paverBaseWidth + 2 * Enchants.level(level, tool, ModEnchantments.WIDE_PAVING).coerceAtMost(4)
        val half = width / 2
        val y = feet.below()
        var placed = 0
        for (i in -half..(width - 1 - half)) {
            val p = y.relative(side, i)
            val old = level.getBlockState(p)
            if (old.block == block) continue
            if (!(old.isAir || old.canBeReplaced() || old.`is`(ModTags.PAVEABLE))) continue
            if (level.getBlockEntity(p) != null) continue
            if (!player.isCreative && material.isEmpty) break
            level.setBlock(p, state, 3)
            // 길 위의 풀·꽃은 치운다
            for (h in 1..2) {
                val up = p.above(h)
                val s = level.getBlockState(up)
                if (!s.isAir && s.canBeReplaced() && s.fluidState.isEmpty) level.destroyBlock(up, false)
            }
            placed++
            if (!player.isCreative && !thrifty(player, tool)) material.shrink(1)
        }
        if (placed > 0) {
            player.awardStat(ModStats.ROAD_PAVED, placed)
            damage(player, tool, InteractionHand.MAIN_HAND)
        }
    }

    private fun layTrack(player: ServerPlayer, tool: ItemStack, feet: BlockPos) {
        val level = player.level() as ServerLevel
        if (!level.getBlockState(feet).isAir) return
        if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) return
        val off = player.offhandItem
        val railStack = if (isRailItem(off)) off else Inventories.find(player) { isRailItem(it) && it !== tool }
        if (railStack == null) {
            Compat.overlay(player, msg("layer.need_rails", Palette.ORANGE))
            return
        }
        val block = (railStack.item as BlockItem).block
        level.setBlock(feet, block.defaultBlockState(), 3)
        if (!player.isCreative && !thrifty(player, tool)) railStack.shrink(1)
        player.awardStat(ModStats.TRACK_PLACED, 1)
        damage(player, tool, InteractionHand.MAIN_HAND)
    }

    private fun isRailItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val b = (stack.item as? BlockItem)?.block ?: return false
        return b is BaseRailBlock || stack.`is`(ModTags.RAIL_ITEMS)
    }

    // ───────────── 터널 굴착기 ─────────────

    fun bore(player: ServerPlayer, hand: InteractionHand, pos: BlockPos, face: Direction) {
        val level = player.level() as ServerLevel
        val tool = player.getItemInHand(hand)
        val r = Config.c.borerBaseRadius + Enchants.level(level, tool, ModEnchantments.BORE_RADIUS).coerceAtMost(3)
        val axis = face.axis
        var mined = 0
        for (a in -r..r) for (b in -r..r) {
            val p = when (axis) {
                Direction.Axis.X -> pos.offset(0, a, b)
                Direction.Axis.Y -> pos.offset(a, 0, b)
                Direction.Axis.Z -> pos.offset(a, b, 0)
            }
            val s = level.getBlockState(p)
            if (s.isAir || !s.fluidState.isEmpty || level.getBlockEntity(p) != null) continue
            val hardness = s.getDestroySpeed(level, p)
            if (hardness < 0 || hardness > Config.c.borerMaxHardness) continue
            if (BaseRailBlock.isRail(s)) continue
            if (level.destroyBlock(p, true, player)) mined++
        }
        if (mined > 0) {
            player.awardStat(ModStats.BLOCKS_BORED, mined)
            if (!player.isCreative) tool.hurtAndBreak(max(1, mined / 3), player, if (hand == InteractionHand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND)
        }
    }

    // ───────────── 측량기 ─────────────

    fun survey(player: ServerPlayer, stack: ItemStack, pos: BlockPos) {
        val a = stack.get(ModComponents.POINT_A)
        if (a == null || a == pos) {
            stack.set(ModComponents.POINT_A, pos)
            Compat.chat(player, msg("surveyor.point_a", Palette.SKY, "${pos.x}, ${pos.y}, ${pos.z}"))
            return
        }
        val dx = (pos.x - a.x).toDouble()
        val dy = (pos.y - a.y).toDouble()
        val dz = (pos.z - a.z).toDouble()
        val horiz = sqrt(dx * dx + dz * dz)
        val slope = if (horiz < 0.01) 0.0 else dy / horiz * 100.0
        Compat.chat(player, msg("surveyor.result", Palette.SKY, fmt1(horiz), fmt1(dy), fmt1(slope)))
        Compat.chat(player, msg("surveyor.hint", Palette.PINK))
        stack.remove(ModComponents.POINT_A)
    }

    fun clearSurvey(player: Player, stack: ItemStack) {
        stack.remove(ModComponents.POINT_A)
        Compat.overlay(player, msg("surveyor.cleared", Palette.ORANGE))
    }
}
