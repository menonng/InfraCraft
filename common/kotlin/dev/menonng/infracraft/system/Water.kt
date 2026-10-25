package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.block.CurrentBlock
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.compat.BoatEntity
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap

/** 물살 블록, 보트 리프트(갑문), 항구, 얼음 보트 속도 상한 */
object Water {
    fun tickCurrent(level: ServerLevel, pos: BlockPos, key: String, data: NetworkData) {
        val state = level.getBlockState(pos)
        if (state.block !is CurrentBlock) {
            data.tickers[TickedBlock.CURRENT]?.remove(key); return
        }
        val dir = state.getValue(CurrentBlock.FACING)
        val box = AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, pos.y + 3.0, pos.z + 1.0)
        for (e in level.getEntitiesOfClass(Entity::class.java, box)) {
            if (!e.isInWater || e.isPassenger) continue
            val v = e.deltaMovement
            val nx = v.x + dir.stepX * Config.c.currentStrength
            val nz = v.z + dir.stepZ * Config.c.currentStrength
            val h = Math.sqrt(nx * nx + nz * nz)
            val k = if (h > Config.c.currentMaxSpeed) Config.c.currentMaxSpeed / h else 1.0
            e.deltaMovement = Vec3(nx * k, v.y, nz * k)
            e.hurtMarked = true
        }
    }

    fun tickLift(level: ServerLevel, pos: BlockPos, key: String, data: NetworkData) {
        if (!level.getBlockState(pos).`is`(ModBlocks.BOAT_LIFT)) {
            data.tickers[TickedBlock.LIFT]?.remove(key); return
        }
        // 리프트 위로 이어진 물기둥 높이
        var top = pos.y
        for (y in 1..Config.c.liftMaxHeight) {
            if (level.getFluidState(pos.above(y)).isEmpty) break
            top = pos.y + y
        }
        if (top == pos.y) return
        val box = AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, top + 1.5, pos.z + 1.0)
        for (e in level.getEntitiesOfClass(Entity::class.java, box)) {
            if (e.isPassenger) continue
            val sneaking = (e.controllingPassenger as? Player ?: e as? Player)?.isShiftKeyDown == true
            val v = e.deltaMovement
            e.deltaMovement = Vec3(v.x * 0.6, if (sneaking) -Config.c.liftSpeed else Config.c.liftSpeed, v.z * 0.6)
            e.hurtMarked = true
        }
    }

    private val inHarbor = WeakHashMap<Entity, String>()

    fun tickBoat(level: ServerLevel, boat: BoatEntity) {
        // 항구: 보트 아래 1~3칸에 역 블록이 있으면 그 역에 도착한 것으로 본다
        var found: BlockPos? = null
        for (d in 1..3) {
            val p = boat.blockPosition().below(d)
            if (level.getBlockState(p).`is`(ModBlocks.STATION)) { found = p; break }
        }
        if (found != null) {
            val st = Network.stationAt(level, found)
            if (st != null && inHarbor[boat] != st.key) {
                inHarbor[boat] = st.key
                Network.arriveAt(level, boat, st, "water")
            }
        } else {
            inHarbor.remove(boat)
            Network.trackMovement(boat)
        }

        val cap = Config.c.iceBoatSpeedCap
        if (cap > 0.0 && level.getBlockState(boat.blockPosition().below()).`is`(ModTags.ICE)) {
            val v = boat.deltaMovement
            val h = v.horizontalDistance()
            if (h > cap) {
                boat.deltaMovement = Vec3(v.x * cap / h, v.y, v.z * cap / h)
                boat.hurtMarked = true
            }
        }
    }
}
