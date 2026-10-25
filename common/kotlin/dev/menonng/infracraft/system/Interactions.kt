package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.block.RailSwitchBlock
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.compat.HorseEntity
import dev.menonng.infracraft.compat.Minecart
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult

/** 아이템·블록·엔티티 우클릭을 각 시스템으로 보낸다. 모든 아이템은 바닐라 Item 이고 동작은 여기서 정해진다. */
object Interactions {
    // 클라이언트와 서버 양쪽에서 같은 판단을 내려야 클라이언트가 같은 클릭을 "허공 사용" 으로 한 번 더 보내지 않는다.
    // 그래서 판단은 양쪽에서 하고, 실제 동작은 서버(ServerPlayer)일 때만 한다.

    private inline fun handled(player: Player, action: (ServerPlayer) -> Unit): InteractionResult {
        if (player is ServerPlayer) action(player)
        return InteractionResult.SUCCESS
    }

    // ───────────── 허공에 사용 ─────────────

    fun useItem(player: Player, hand: InteractionHand): InteractionResult? {
        val stack = player.getItemInHand(hand)
        return when {
            stack.`is`(ModItems.ROAD_PAVER) || stack.`is`(ModItems.TRACK_LAYER) -> handled(player) { Tools.toggle(it, stack) }
            stack.`is`(ModItems.TICKET) -> handled(player) { cycleTicket(it.level() as ServerLevel, it, stack) }
            stack.`is`(ModItems.WHISTLE) -> handled(player) {
                if (it.isShiftKeyDown) {
                    stack.remove(ModComponents.BOUND_ENTITY)
                    Compat.overlay(it, msg("whistle.unbound", Palette.ORANGE))
                } else Horses.callHorse(it, stack)
            }
            stack.`is`(ModItems.SURVEYOR) && player.isShiftKeyDown -> handled(player) { Tools.clearSurvey(it, stack) }
            stack.`is`(ModItems.COUPLER) && player.isShiftKeyDown -> handled(player) {
                stack.remove(ModComponents.BOUND_ENTITY)
                Compat.overlay(it, msg("coupler.cleared", Palette.ORANGE))
            }
            else -> null
        }
    }

    /** 승차권: 우클릭할 때마다 다음 역으로, 웅크리고 우클릭하면 비운다. */
    private fun cycleTicket(level: ServerLevel, player: ServerPlayer, stack: ItemStack) {
        if (player.isShiftKeyDown) {
            stack.remove(ModComponents.DESTINATION)
            Compat.overlay(player, msg("ticket.cleared", Palette.ORANGE))
            return
        }
        val names = Network.data(level.server).stations.values.map { it.name }.sorted()
        if (names.isEmpty()) {
            Compat.overlay(player, msg("ticket.no_stations", Palette.ORANGE)); return
        }
        val cur = stack.get(ModComponents.DESTINATION)
        val next = names[(names.indexOfFirst { it == cur } + 1) % names.size]
        stack.set(ModComponents.DESTINATION, next)
        Compat.overlay(player, msg("ticket.set", Palette.GREEN, next))
    }

    // ───────────── 블록에 사용 ─────────────

    fun useBlock(player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult? {
        val level = player.level()
        val pos = hit.blockPos
        val state = level.getBlockState(pos)
        val stack = player.getItemInHand(hand)
        val emptyMain = stack.isEmpty && hand == InteractionHand.MAIN_HAND

        if (stack.`is`(ModItems.TUNNEL_BORER)) return handled(player) { Tools.bore(it, hand, pos, hit.direction) }
        if (stack.`is`(ModItems.SURVEYOR)) return handled(player) { Tools.survey(it, stack, pos) }

        if (state.`is`(ModBlocks.STATION)) {
            return when {
                stack.`is`(ModItems.TICKET) && player.isShiftKeyDown -> handled(player) {
                    val sl = it.level() as ServerLevel
                    val st = Network.stationAt(sl, pos) ?: return@handled
                    val dest = stack.get(ModComponents.DESTINATION)
                    if (dest == null) Compat.overlay(it, msg("ticket.blank", Palette.ORANGE))
                    else Network.startExpress(sl, it, st, dest)
                }
                stack.`is`(ModItems.TICKET) -> handled(player) {
                    val st = Network.stationAt(it.level() as ServerLevel, pos) ?: return@handled
                    stack.set(ModComponents.DESTINATION, st.name)
                    Compat.overlay(it, msg("ticket.set", Palette.GREEN, st.name))
                }
                stack.`is`(Items.NAME_TAG) && Compat.customName(stack) != null -> handled(player) {
                    Network.rename(it.level() as ServerLevel, pos, Compat.customName(stack)!!, it)
                }
                emptyMain -> handled(player) { Network.showStationInfo(it.level() as ServerLevel, pos, it) }
                else -> null
            }
        }
        if (state.block is RailSwitchBlock) {
            if (stack.`is`(ModItems.TICKET)) return handled(player) {
                val sl = it.level() as ServerLevel
                val key = NetworkData.key(Compat.dimensionId(sl), pos)
                val data = Network.data(sl.server)
                val dest = stack.get(ModComponents.DESTINATION)
                if (dest == null) {
                    Compat.overlay(it, msg("ticket.blank", Palette.ORANGE)); return@handled
                }
                data.addTicker(TickedBlock.SWITCH, Compat.dimensionId(sl), pos)
                val set = data.switches.getOrPut(key) { LinkedHashSet() }
                if (set.remove(dest)) Compat.overlay(it, msg("switch.removed", Palette.ORANGE, dest))
                else {
                    set.add(dest); Compat.overlay(it, msg("switch.added", Palette.GREEN, dest))
                }
            }
            if (emptyMain) return handled(player) {
                val sl = it.level() as ServerLevel
                val set = Network.data(sl.server).switches[NetworkData.key(Compat.dimensionId(sl), pos)].orEmpty()
                Compat.chat(it, if (set.isEmpty()) msg("switch.empty", Palette.ORANGE) else msg("switch.list", Palette.SKY, set.joinToString(", ")))
            }
            return null
        }
        if (emptyMain && state.`is`(ModBlocks.SIGNPOST)) return handled(player) { Network.showSignpost(it.level() as ServerLevel, pos, it) }
        if (state.`is`(ModBlocks.STABLE) && hand == InteractionHand.MAIN_HAND && (stack.isEmpty || player.vehicle is HorseEntity)) {
            return handled(player) { Horses.useStable(it.level() as ServerLevel, pos, it) }
        }
        return null
    }

    // ───────────── 엔티티에 사용 ─────────────

    fun useEntity(player: Player, hand: InteractionHand, target: Entity): InteractionResult? {
        val stack = player.getItemInHand(hand)
        return when {
            stack.`is`(ModItems.COUPLER) && (target is Minecart || target is HorseEntity) -> handled(player) { couple(it, stack, target) }
            stack.`is`(ModItems.WHISTLE) && target is HorseEntity -> handled(player) { Horses.bindWhistle(it, stack, target) }
            stack.`is`(ModItems.IRON_HORSESHOE) && target is HorseEntity -> handled(player) { Horses.shoe(it, stack, target, 1) }
            stack.`is`(ModItems.DIAMOND_HORSESHOE) && target is HorseEntity -> handled(player) { Horses.shoe(it, stack, target, 2) }
            stack.`is`(ModItems.SURVEYOR) && target is HorseEntity -> handled(player) { Horses.appraise(it, target) }
            stack.`is`(ModItems.TICKET) && target is Minecart -> handled(player) {
                val dest = stack.get(ModComponents.DESTINATION)
                if (dest == null) Compat.overlay(it, msg("ticket.blank", Palette.ORANGE))
                else {
                    target.setAttached(ModAttachments.CART_DESTINATION, dest)
                    Compat.overlay(it, msg("cart.destination", Palette.GREEN, dest))
                }
            }
            else -> null
        }
    }

    /** 연결기: 앞 차량(또는 말)을 먼저, 뒤 마인카트를 나중에 클릭 */
    private fun couple(player: ServerPlayer, stack: ItemStack, target: Entity) {
        val first = stack.get(ModComponents.BOUND_ENTITY)
        if (first == null || first == target.uuid) {
            stack.set(ModComponents.BOUND_ENTITY, target.uuid)
            Compat.overlay(player, msg("coupler.first", Palette.SKY, target.name))
            return
        }
        stack.remove(ModComponents.BOUND_ENTITY)
        if (target !is Minecart) {
            Compat.overlay(player, msg("coupler.follower_cart", Palette.RED)); return
        }
        val leader = (player.level() as ServerLevel).getEntity(first)
        if (leader == null || leader.distanceTo(target) > 6f) {
            Compat.overlay(player, msg("coupler.too_far", Palette.RED)); return
        }
        if (target.getAttached(ModAttachments.COUPLED_TO) == leader.uuid) {
            target.removeAttached(ModAttachments.COUPLED_TO)
            Compat.overlay(player, msg("coupler.uncoupled", Palette.ORANGE)); return
        }
        target.setAttached(ModAttachments.COUPLED_TO, leader.uuid)
        Compat.overlay(player, msg(if (leader is HorseEntity) "coupler.harnessed" else "coupler.coupled", Palette.GREEN, leader.name, target.name))
    }

    // ───────────── 블록 파괴 ─────────────

    fun afterBreak(level: ServerLevel, pos: BlockPos, wasRail: Boolean, wasStable: Boolean) {
        val dim = Compat.dimensionId(level)
        val data = Network.data(level.server)
        data.removeAt(NetworkData.key(dim, pos))
        if (wasRail) Network.invalidateChunk(level.server, dim, pos)
        if (wasStable) Horses.dropStable(level, pos)
    }
}
