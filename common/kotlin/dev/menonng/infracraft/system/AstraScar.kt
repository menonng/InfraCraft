package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.compat.Compat
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * 아스트라가 땅에 남기는 흔적: 크레이터(가장자리는 흑요석·우는 흑요석), 별핵 수정(별빛 색 유리 첨탑과 자수정),
 * 땅에 불타 새겨진 별자리 선. 설정 [Config.astraReshapesTerrain] 이 꺼져 있거나, 쓴 사람이 고칠 수 없는 자리
 * (스폰 보호·보호 구역·모험 모드), 부술 수 없는 블록, 블록 엔티티(상자 등)는 건드리지 않는다.
 */
object AstraScar {
    private fun can(level: ServerLevel, player: ServerPlayer, pos: BlockPos, st: BlockState): Boolean {
        if (!Config.c.astraReshapesTerrain || !player.mayBuild() || !level.mayInteract(player, pos)) return false
        if (st.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false
        return true
    }

    private fun noise(p: BlockPos): Int = Math.floorMod(p.x * 73856093 xor p.y * 19349663 xor p.z * 83492791, 100)

    /** 사발 모양 크레이터. 반지름 r(가로), 깊이 0.6r. 가장자리 껍질은 그을린 흑요석으로 */
    fun crater(level: ServerLevel, player: ServerPlayer, center: Vec3, r: Double) {
        val c = BlockPos.containing(center)
        val ri = ceil(r * 1.25).toInt()
        val shell = ArrayList<BlockPos>()
        for (dx in -ri..ri) for (dz in -ri..ri) for (dy in -ri..ri) {
            val ny = if (dy < 0) dy / (r * 0.6) else dy / (r * 1.3)
            val d = (dx * dx + dz * dz) / (r * r) + ny * ny
            if (d > 1.5) continue
            val pos = c.offset(dx, dy, dz)
            val st = level.getBlockState(pos)
            if (st.isAir) continue
            if (d <= 1.0) {
                if (st.fluidState.isEmpty && can(level, player, pos, st)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
            } else if (dy <= 0) shell.add(pos)
        }
        for (pos in shell) {
            val st = level.getBlockState(pos)
            if (st.isAir || !st.fluidState.isEmpty || !st.isSolidRender || !can(level, player, pos, st)) continue
            val n = noise(pos)
            when {
                n < 14 -> level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3)
                n < 52 -> level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3)
            }
        }
    }

    private val GLASS = mapOf(
        "altair" to "white", "tarazed" to "orange", "alshain" to "yellow", "deneb_el_okab" to "light_blue", "bezek" to "yellow",
        "chesus" to "cyan", "libertas" to "lime", "phonix" to "red", "petra" to "purple",
    )

    private fun place(level: ServerLevel, player: ServerPlayer, pos: BlockPos, st: BlockState) {
        val old = level.getBlockState(pos)
        if ((old.isAir || old.canBeReplaced()) && can(level, player, pos, if (old.isAir) Blocks.STONE.defaultBlockState() else old)) level.setBlock(pos, st, 3)
    }

    /** 크레이터 바닥의 별핵 수정: 자수정 받침 위에 별빛 색 유리 첨탑, 꼭대기에 빛나는 막대, 둘레에 자수정 송이 */
    fun crystal(level: ServerLevel, player: ServerPlayer, center: Vec3, star: Int, big: Boolean) {
        var floor = BlockPos.containing(center)
        var n = 0
        while (n < 10 && level.getBlockState(floor).let { it.isAir || !it.fluidState.isEmpty }) { floor = floor.below(); n++ }
        val key = Astra.STARS[star].key
        val glass = Compat.blockById("minecraft", "${GLASS[key] ?: "purple"}_stained_glass")?.defaultBlockState() ?: Blocks.TINTED_GLASS.defaultBlockState()
        val st = level.getBlockState(floor)
        if (can(level, player, floor, st)) level.setBlock(floor, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3)
        val height = if (big) 6 else 3
        for (h in 1..height) place(level, player, floor.above(h), glass)
        if (big) for (d in Direction.Plane.HORIZONTAL) for (h in 1..3) place(level, player, floor.above(h).relative(d), if (h == 3) Blocks.TINTED_GLASS.defaultBlockState() else glass)
        place(level, player, floor.above(height + 1), Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP))
        val cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)
        for (dx in listOf(-1, 1)) for (dz in listOf(-1, 1)) {
            val at = floor.offset(dx * (if (big) 2 else 1), 0, dz * (if (big) 2 else 1))
            if (level.getBlockState(at).isSolidRender) place(level, player, at.above(), cluster)
        }
    }

    /** 두 착탄점 사이 땅 위에 불타 새겨진 별자리 선(흑요석, 세 칸마다 우는 흑요석) */
    fun burnLine(level: ServerLevel, player: ServerPlayer, a: Vec3, b: Vec3) {
        val len = a.distanceTo(b)
        var d = 0.0
        var i = 0
        val seen = HashSet<Long>()
        while (d <= len) {
            val q = a.add(b.subtract(a).scale(d / len))
            val x = kotlin.math.floor(q.x).toInt()
            val z = kotlin.math.floor(q.z).toInt()
            d += 0.5
            if (!seen.add(BlockPos.asLong(x, 0, z))) continue
            val top = BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z)
            val st = level.getBlockState(top)
            if (st.isAir || !st.fluidState.isEmpty || !st.isSolidRender || !can(level, player, top, st)) continue
            level.setBlock(top, (if (i++ % 3 == 0) Blocks.CRYING_OBSIDIAN else Blocks.OBSIDIAN).defaultBlockState(), 3)
        }
    }
}
