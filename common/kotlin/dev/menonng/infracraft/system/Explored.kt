package dev.menonng.infracraft.system

import com.mojang.serialization.Codec
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModAttachments
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.server.level.ServerPlayer
import java.util.stream.LongStream

/**
 * 플레이어가 지나며 불러온 땅(32블록 칸 단위)을 서버에 기록한다. 클라이언트 지도는 플레이어가 실제로 불러온 청크로 그려지고,
 * 지도에서 고른 지점이 이 기록 안에 있어야 별빛 낙하가 발동한다.
 */
object Explored {
    const val CELL_SHIFT = 5 // 32블록

    class Cells(val byDim: MutableMap<String, LongOpenHashSet> = HashMap()) {
        companion object {
            val CODEC: Codec<Cells> = Codec.unboundedMap(Codec.STRING, Codec.LONG_STREAM).xmap(
                { m -> Cells(m.mapValuesTo(HashMap()) { (_, v) -> LongOpenHashSet(v.toArray()) }) },
                { c -> c.byDim.mapValues { (_, v) -> LongStream.of(*v.toLongArray()) } },
            )
        }
    }

    private fun pack(x: Int, z: Int): Long = (x.toLong() and 0xFFFFFFFFL) or (z.toLong() shl 32)

    fun tick(player: ServerPlayer) {
        if (player.tickCount % 40 != 0) return
        val cells = player.getAttached(ModAttachments.EXPLORED) ?: Cells()
        val set = cells.byDim.getOrPut(Compat.dimensionId(player.level())) { LongOpenHashSet() }
        val r = (player.level().server.playerList.viewDistance * 16) shr CELL_SHIFT
        val cx = player.blockX shr CELL_SHIFT
        val cz = player.blockZ shr CELL_SHIFT
        var added = false
        for (dx in -r..r) for (dz in -r..r) {
            if (dx * dx + dz * dz > r * r + r) continue
            if (set.add(pack(cx + dx, cz + dz))) added = true
        }
        if (added || !player.hasAttached(ModAttachments.EXPLORED)) player.setAttached(ModAttachments.EXPLORED, cells)
    }

    /** 그 지점(또는 바로 옆 칸)을 이 플레이어가 불러온 적이 있는가 */
    fun contains(player: ServerPlayer, x: Int, z: Int): Boolean {
        val set = player.getAttached(ModAttachments.EXPLORED)?.byDim?.get(Compat.dimensionId(player.level())) ?: return false
        val cx = x shr CELL_SHIFT
        val cz = z shr CELL_SHIFT
        for (dx in -1..1) for (dz in -1..1) if (set.contains(pack(cx + dx, cz + dz))) return true
        return false
    }
}
