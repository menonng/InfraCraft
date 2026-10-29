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
import net.minecraft.world.level.block.BaseRailBlock
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
            stack.`is`(ModItems.SUBWAY_SHIELD) -> handled(player) { Subway.shield(it, hand) }
            Bridges.kind(stack) != null -> handled(player) { Bridges.use(it, hand) }
            stack.`is`(ModItems.ASTRA_OBLITERATOR) -> handled(player) { Astra.cast(it, stack) }
            stack.`is`(ModItems.STATION_BLUEPRINT) -> handled(player) { Subway.buildStation(it, hand) }
            stack.`is`(ModItems.GROUND_STATION_BLUEPRINT) -> handled(player) { Stations.buildGround(it, hand) }
            stack.`is`(ModItems.HARBOR_BLUEPRINT) -> handled(player) { Stations.buildHarbor(it, hand) }
            stack.`is`(ModItems.METRO_CAR_BLUEPRINT) -> handled(player) { CreateCars.build(it, hand, CreateCars.Style.METRO) }
            stack.`is`(ModItems.COACH_BLUEPRINT) -> handled(player) { CreateCars.build(it, hand, CreateCars.Style.COACH) }
            stack.`is`(ModItems.TICKET) -> handled(player) { cycleTicket(it.level() as ServerLevel, it, stack) }
            stack.`is`(ModItems.WHISTLE) -> handled(player) {
                if (it.isShiftKeyDown) {
                    stack.remove(ModComponents.BOUND_ENTITY)
                    Compat.overlay(it, msg("whistle.unbound", Palette.ORANGE))
                } else Horses.callHorse(it, stack)
            }
            stack.`is`(ModItems.SURVEYOR) && player.isShiftKeyDown -> handled(player) { Tools.clearSurvey(it, stack) }
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
        // 역 블록 위에 선로가 깔려 있으면 선로를 눌러도 역으로 다룬다
        val clicked = hit.blockPos
        val pos = if (BaseRailBlock.isRail(level.getBlockState(clicked)) && level.getBlockState(clicked.below()).`is`(ModBlocks.STATION)) clicked.below() else clicked
        val state = level.getBlockState(pos)
        val stack = player.getItemInHand(hand)
        val emptyMain = stack.isEmpty && hand == InteractionHand.MAIN_HAND

        if (stack.`is`(ModItems.TUNNEL_BORER)) return handled(player) { Tools.bore(it, hand, pos, hit.direction) }
        if (stack.`is`(ModItems.SUBWAY_SHIELD)) return handled(player) { Subway.shield(it, hand) }
        if (Bridges.kind(stack) != null) return handled(player) { Bridges.use(it, hand) }
        if (stack.`is`(ModItems.STATION_BLUEPRINT)) return handled(player) { Subway.buildStation(it, hand) }
        if (stack.`is`(ModItems.GROUND_STATION_BLUEPRINT)) return handled(player) { Stations.buildGround(it, hand) }
        if (stack.`is`(ModItems.HARBOR_BLUEPRINT)) return handled(player) { Stations.buildHarbor(it, hand) }
        if (stack.`is`(ModItems.METRO_CAR_BLUEPRINT)) return handled(player) { CreateCars.build(it, hand, CreateCars.Style.METRO) }
        if (stack.`is`(ModItems.COACH_BLUEPRINT)) return handled(player) { CreateCars.build(it, hand, CreateCars.Style.COACH) }
        if (stack.`is`(ModItems.SURVEYOR)) return handled(player) { Tools.survey(it, stack, pos) }

        // Create 기차 정거장: 빈손 클릭은 Create 역 화면에 맡기고, 승차권일 때만 가로챈다
        if (stack.`is`(ModItems.TICKET) && CreateCompat.isStationBlock(state)) {
            return handled(player) {
                val sl = it.level() as ServerLevel
                val st = Network.data(sl.server).station(NetworkData.key(Compat.dimensionId(sl), pos))
                if (st == null) Compat.overlay(it, msg("create.station_pending", Palette.ORANGE))
                else useTicketAt(sl, it, stack, st)
            }
        }
        if (state.`is`(ModBlocks.STATION)) {
            return when {
                stack.`is`(ModItems.TICKET) -> handled(player) {
                    val sl = it.level() as ServerLevel
                    val st = Network.stationAt(sl, pos) ?: return@handled
                    useTicketAt(sl, it, stack, st)
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
        if (emptyMain && state.`is`(ModBlocks.MILESTONE)) return handled(player) { Network.showSignpost(it.level() as ServerLevel, pos, it) }
        if (emptyMain && state.`is`(ModBlocks.SIGNPOST)) return handled(player) {
            if (it.isShiftKeyDown) Network.showNetwork(it.level() as ServerLevel, pos, it)
            else Network.showSignpost(it.level() as ServerLevel, pos, it)
        }
        if (state.`is`(ModBlocks.STABLE) && hand == InteractionHand.MAIN_HAND && (stack.isEmpty || player.vehicle is HorseEntity)) {
            return handled(player) { Horses.useStable(it.level() as ServerLevel, pos, it) }
        }
        return null
    }

    /** 역에서 승차권 사용: 목적지가 있으면 이 역에서 가는 경로를 보여 주고, 비어 있거나 이 역이면 이 역을 목적지로 정한다 */
    private fun useTicketAt(level: ServerLevel, player: ServerPlayer, stack: ItemStack, st: StationRec) {
        val dest = stack.get(ModComponents.DESTINATION)
        if (dest == null || dest.equals(st.name, ignoreCase = true)) {
            stack.set(ModComponents.DESTINATION, st.name)
            Compat.overlay(player, msg("ticket.set", Palette.GREEN, st.name))
            return
        }
        Network.showRoute(level, player, st, dest)
    }

    // ───────────── 엔티티에 사용 ─────────────

    fun useEntity(player: Player, hand: InteractionHand, target: Entity): InteractionResult? {
        val stack = player.getItemInHand(hand)
        return when {
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

    // ───────────── 블록 파괴 ─────────────

    fun afterBreak(level: ServerLevel, pos: BlockPos, wasRail: Boolean, wasStable: Boolean) {
        val dim = Compat.dimensionId(level)
        val data = Network.data(level.server)
        data.removeAt(NetworkData.key(dim, pos))
        if (wasRail) Network.invalidateChunk(level.server, dim, pos)
        if (wasStable) Horses.dropStable(level, pos)
    }
}
