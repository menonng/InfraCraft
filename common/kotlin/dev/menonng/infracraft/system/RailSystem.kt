package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.block.RailSwitchBlock
import dev.menonng.infracraft.block.RailTier
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.block.TierRailBlock
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.compat.Minecart
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.PoweredRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil

/** 마인카트(바닐라·Create 마인카트 장치): 등급 선로 가속, 커브 감속, 역 정차, 분기기, 블레이즈 연료 */
object RailSystem {
    private class CartState {
        var stopTicks = 0
        var currentStation: String? = null
        var fuelBlocks = 0.0
    }

    private val states = WeakHashMap<Entity, CartState>()

    // ───────────── 최고 속도(믹스인에서 호출) ─────────────

    fun railStateAt(level: Level, pos: BlockPos): Pair<BlockPos, BlockState>? {
        val s = level.getBlockState(pos)
        if (BaseRailBlock.isRail(s)) return pos to s
        val below = pos.below()
        val b = level.getBlockState(below)
        if (BaseRailBlock.isRail(b)) return below to b
        return null
    }

    private fun shapeName(state: BlockState): String? {
        val block = state.block as? BaseRailBlock ?: return null
        return state.getValue(block.shapeProperty).name
    }

    private fun isBendOrSlope(state: BlockState): Boolean {
        val n = shapeName(state) ?: return true
        return n != "NORTH_SOUTH" && n != "EAST_WEST"
    }

    /** 이 선로가 지금 속도를 올려 주는 등급 선로면 그 등급 */
    fun activeTier(level: Level, state: BlockState): RailTier? {
        val block = state.block as? TierRailBlock ?: return null
        if (!state.getValue(PoweredRailBlock.POWERED)) return null
        if (block.tier == RailTier.BLAZE && Compat.dimensionId(level) != "minecraft:the_nether") return null
        return block.tier
    }

    @JvmStatic
    fun maxSpeed(cart: Entity, original: Double): Double {
        val level = cart.level()
        val (pos, state) = railStateAt(level, cart.blockPosition()) ?: return original
        val tier = activeTier(level, state) ?: return original
        if (tier == RailTier.BLAZE && (states[cart]?.fuelBlocks ?: 0.0) <= 0.0 && cart.getAttached(ModAttachments.BLAZE_FUEL) == null) return original
        var max = tier.maxSpeed()
        val rider = cart.firstPassenger
        if (rider is LivingEntity) max *= 1.0 + 0.1 * Enchants.level(rider, ModEnchantments.CONDUCTOR)
        if (max > Config.c.curveSpeedCap && bendAhead(cart, level, pos, state, max)) max = maxOf(original, Config.c.curveSpeedCap)
        return max
    }

    /** 현재 위치나 앞쪽 몇 칸 안에 커브·경사·선로 끝이 있는지 */
    private fun bendAhead(cart: Entity, level: Level, pos: BlockPos, state: BlockState, speed: Double): Boolean {
        if (isBendOrSlope(state)) return true
        val v = cart.deltaMovement
        val stepX = if (abs(v.x) > abs(v.z)) (if (v.x > 0) 1 else -1) else 0
        val stepZ = if (stepX == 0) (if (v.z > 0) 1 else if (v.z < 0) -1 else 0) else 0
        if (stepX == 0 && stepZ == 0) return false
        val look = ceil(speed * 4).toInt().coerceIn(2, 6)
        for (i in 1..look) {
            val p = pos.offset(stepX * i, 0, stepZ * i)
            val rail = railStateAt(level, p) ?: railStateAt(level, p.above()) ?: return true
            if (isBendOrSlope(rail.second)) return true
        }
        return false
    }

    // ───────────── 레벨 틱 ─────────────

    fun tick(level: ServerLevel, cart: Minecart) {
        val st = states.getOrPut(cart) { CartState() }
        val railInfo = railStateAt(level, cart.blockPosition())

        if (railInfo != null) {
            val (railPos, rail) = railInfo
            boost(level, cart, st, rail)
            handleStation(level, cart, st, railPos)
        }

        if (st.stopTicks > 0) {
            st.stopTicks--
            cart.deltaMovement = Vec3(0.0, cart.deltaMovement.y, 0.0)
        }

        Network.trackMovement(cart)

        if (Config.c.cartChunkLoading && level.gameTime % 20 == 0L && cart.deltaMovement.horizontalDistance() > 0.15 &&
            cart.passengers.none { it is Player }) {
            Compat.keepChunkLoaded(level, cart.blockPosition())
        }
    }

    private fun boost(level: ServerLevel, cart: Minecart, st: CartState, rail: BlockState) {
        val block = rail.block as? TierRailBlock ?: return
        val v = cart.deltaMovement
        val speed = v.horizontalDistance()
        if (!rail.getValue(PoweredRailBlock.POWERED)) {
            // 전원이 없으면 바닐라 전동 레일처럼 브레이크
            cart.deltaMovement = if (speed < 0.03) Vec3(0.0, v.y, 0.0) else Vec3(v.x * 0.5, v.y, v.z * 0.5)
            return
        }
        if (activeTier(level, rail) == null) return
        if (block.tier == RailTier.BLAZE && !burnFuel(cart, st, speed)) return
        if (speed > 0.01) {
            val add = Config.c.railBoostPerTick / speed
            cart.deltaMovement = Vec3(v.x + v.x * add, v.y, v.z + v.z * add)
        }
    }

    /** 블레이즈 선로: 연료가 떨어지면 탑승자의 블레이즈 가루를 하나 태운다. */
    private fun burnFuel(cart: Minecart, st: CartState, speed: Double): Boolean {
        if (st.fuelBlocks <= 0.0) {
            val saved = cart.getAttached(ModAttachments.BLAZE_FUEL)
            if (saved != null && saved > 0) {
                st.fuelBlocks = saved.toDouble()
                cart.removeAttached(ModAttachments.BLAZE_FUEL)
            }
        }
        if (st.fuelBlocks <= 0.0) {
            val rider = cart.firstPassenger as? Player ?: return false
            if (!Inventories.consume(rider, 1) { it.`is`(Items.BLAZE_POWDER) }) {
                if (cart.level().gameTime % 40 == 0L) Compat.overlay(rider, msg("rail.need_fuel", Palette.ORANGE))
                return false
            }
            st.fuelBlocks = Config.c.blazeFuelBlocks.toDouble()
        }
        st.fuelBlocks -= speed
        if (st.fuelBlocks > 0 && cart.level().gameTime % 100 == 0L) cart.setAttached(ModAttachments.BLAZE_FUEL, st.fuelBlocks.toInt())
        return true
    }

    // ───────────── 역과 목적지 ─────────────

    /** 탑승자 승차권의 목적지, 없으면 화물 운송장으로 정한 목적지 */
    fun destinationOf(vehicle: Entity): String? {
        for (p in vehicle.passengers) if (p is Player) {
            Inventories.find(p) { it.`is`(ModItems.TICKET) && it.get(ModComponents.DESTINATION) != null }
                ?.let { return it.get(ModComponents.DESTINATION) }
        }
        return vehicle.getAttached(ModAttachments.CART_DESTINATION)
    }

    private fun handleStation(level: ServerLevel, cart: Minecart, st: CartState, railPos: BlockPos) {
        val stationPos = railPos.below()
        if (!level.getBlockState(stationPos).`is`(ModBlocks.STATION)) {
            st.currentStation = null
            return
        }
        val station = Network.stationAt(level, stationPos) ?: return
        if (st.currentStation == station.key) return
        st.currentStation = station.key
        Network.arriveAt(level, cart, station, "rail")
        val dest = destinationOf(cart)
        val rider = cart.firstPassenger as? Player
        if (dest != null && dest.equals(station.name, ignoreCase = true)) {
            st.stopTicks = 60
            cart.deltaMovement = Vec3.ZERO
            cart.removeAttached(ModAttachments.CART_DESTINATION)
            rider?.let { Compat.overlay(it, msg("rail.arrived", Palette.GREEN, station.name)) }
        } else if (rider != null) {
            Compat.overlay(rider, msg("rail.passing", Palette.SKY, station.name))
        }
    }

    // ───────────── 분기기 ─────────────

    fun tickSwitch(level: ServerLevel, pos: BlockPos, key: String, data: NetworkData) {
        val state = level.getBlockState(pos)
        if (state.block !is RailSwitchBlock) {
            data.tickers[TickedBlock.SWITCH]?.remove(key)
            data.switches.remove(key)
            return
        }
        val targets = data.switches[key].orEmpty()
        var divert = false
        if (targets.isNotEmpty()) {
            val box = AABB(pos.x - 3.5, pos.y + 0.0, pos.z - 3.5, pos.x + 4.5, pos.y + 3.0, pos.z + 4.5)
            divert = level.getEntitiesOfClass(Minecart::class.java, box).any { cart ->
                val d = destinationOf(cart)
                d != null && targets.any { it.equals(d, ignoreCase = true) }
            }
        }
        if (state.getValue(RailSwitchBlock.POWERED) != divert) level.setBlock(pos, state.setValue(RailSwitchBlock.POWERED, divert), 3)
    }
}
