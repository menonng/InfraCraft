package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import kotlin.math.abs

/**
 * 지상 역사 설계도와 항구 설계도. 지하 역사(Subway.buildStation)와 같은 좌표 표기를 쓴다:
 * dir = 진행(바라보는) 방향, side = 오른쪽, k = 진행 방향 칸, o = 옆 칸, h = 기준 높이.
 */
object Stations {
    private fun at(base: BlockPos, dir: Direction, side: Direction, k: Int, o: Int, h: Int): BlockPos =
        base.relative(dir, k).relative(side, o).above(h)

    private fun place(level: ServerLevel, plan: List<Pair<BlockPos, BlockState>>) {
        for ((p, want) in plan) {
            val s = level.getBlockState(p)
            if (s == want || !Subway.replaceable(level, p, s)) continue
            level.setBlock(p, want, 3)
        }
    }

    // ───────────── 지상 역사 ─────────────

    /**
     * 두 선로 사이 가운데 줄에 서서 진행 방향을 보고 사용한다. 길이 16 의 상대식 복선 역:
     * 양옆 두 선로(바닐라면 아래에 역 블록 둘), 선로마다 차량이 지나는 폭 5, 바깥 양쪽에 선로보다 한 칸 높은 승강장(폭 3),
     * 바깥쪽 기둥과 가문비나무 지붕, 벤치, 매달린 등불, 이정표.
     */
    fun buildGround(player: ServerPlayer, hand: InteractionHand) {
        val level = player.level() as ServerLevel
        val dir = player.direction
        val side = dir.clockWise
        val base = player.blockPosition()
        val createTrack = Subway.useCreateTrack(player)
        val trackState = createTrack?.let { CreateCompat.trackState(it, dir, 0) } ?: Blocks.RAIL.defaultBlockState()
        val plan = ArrayList<Pair<BlockPos, BlockState>>()
        val air = Blocks.AIR.defaultBlockState()
        val bricks = Blocks.STONE_BRICKS.defaultBlockState()
        val roof = Blocks.SPRUCE_SLAB.defaultBlockState()
        val edge = 6   // 승강장 안전선
        val outer = 8  // 승강장 바깥 끝(기둥·벤치)
        var tracksPlaced = 0
        for (k in -7..8) for (o in -outer..outer) {
            val ao = abs(o)
            val outward = if (o > 0) side else side.opposite
            if (ao < edge) {
                // 두 선로와 차량 통로
                for (h in 0..4) {
                    val p = at(base, dir, side, k, o, h)
                    if (h == 0 && ao == Subway.TRACK_O) {
                        if (!Subway.isTrack(level.getBlockState(p))) { plan.add(p to trackState); tracksPlaced++ }
                    } else plan.add(p to air)
                }
                if (ao == Subway.TRACK_O && k == 0 && createTrack == null) plan.add(at(base, dir, side, 0, o, -1) to ModBlocks.STATION.defaultBlockState())
                continue
            }
            // 승강장 기초: 땅에 닿을 때까지(최대 8칸)
            for (d in 1..8) {
                val p = at(base, dir, side, k, o, -d)
                val s = level.getBlockState(p)
                if (!s.isAir && s.fluidState.isEmpty && !s.canBeReplaced()) break
                plan.add(p to bricks)
            }
            plan.add(at(base, dir, side, k, o, 0) to bricks)
            plan.add(at(base, dir, side, k, o, 1) to if (ao == edge) ModBlocks.PLATFORM_EDGE.defaultBlockState() else Blocks.SMOOTH_STONE.defaultBlockState())
            for (h in 2..4) {
                val p = at(base, dir, side, k, o, h)
                plan.add(p to when {
                    ao == outer && Math.floorMod(k, 4) == 2 -> Blocks.SPRUCE_LOG.defaultBlockState()
                    ao == outer && h == 2 && Math.floorMod(k, 4) == 0 ->
                        Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, outward)
                    ao == outer - 1 && h == 4 && Math.floorMod(k, 4) == 0 ->
                        Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)
                    ao == outer - 1 && h == 2 && k == -7 -> ModBlocks.SIGNPOST.defaultBlockState()
                    else -> air
                })
            }
            plan.add(at(base, dir, side, k, o, 5) to roof)
        }
        place(level, plan)
        if (!player.isCreative) {
            player.getItemInHand(hand).shrink(1)
            if (createTrack != null) Inventories.consume(player, tracksPlaced) { it.`is`(createTrack.asItem()) }
        }
        if (createTrack == null) {
            val data = Network.data(level.server)
            val (a, b) = Subway.registerTwin(level, at(base, dir, side, 0, Subway.TRACK_O, -1), at(base, dir, side, 0, -Subway.TRACK_O, -1), "역 ${data.stations.size + 1}")
            Compat.chat(player, msg("ground_station.built", Palette.GREEN, a.name, b.name))
        } else Compat.chat(player, msg("ground_station.built_create", Palette.GREEN))
    }

    // ───────────── 항구 ─────────────

    /**
     * 물가에 서서 물 쪽을 보고 사용한다. 길이 10, 폭 3 의 나무 잔교와 기둥, 한쪽 난간과 등불,
     * 계선주, 잔교 끝 옆 물속의 항구 역(보트가 그 위에 닿으면 도착).
     */
    fun buildHarbor(player: ServerPlayer, hand: InteractionHand) {
        val level = player.level() as ServerLevel
        val dir = player.direction
        val side = dir.clockWise
        val feet = player.blockPosition()
        // 앞쪽 1~5칸, 발 높이 아래 1~2칸에서 수면을 찾는다
        var sea: Int? = null
        search@ for (k in 1..5) for (dy in 1..2) {
            val p = feet.relative(dir, k).below(dy)
            if (level.getFluidState(p).`is`(FluidTags.WATER) && !level.getFluidState(p.above()).`is`(FluidTags.WATER)) { sea = p.y; break@search }
        }
        if (sea == null) {
            Compat.overlay(player, msg("harbor.need_water", Palette.ORANGE)); return
        }
        val base = BlockPos(feet.x, sea, feet.z)
        val berth = at(base, dir, side, 8, 3, 0)
        if (!level.getFluidState(berth).`is`(FluidTags.WATER)) {
            Compat.overlay(player, msg("harbor.need_open_water", Palette.ORANGE)); return
        }
        val plan = ArrayList<Pair<BlockPos, BlockState>>()
        val deck = Blocks.SPRUCE_PLANKS.defaultBlockState()
        val fence = Blocks.SPRUCE_FENCE.defaultBlockState()
        val air = Blocks.AIR.defaultBlockState()
        for (k in 1..10) for (o in -1..1) {
            plan.add(at(base, dir, side, k, o, 0) to deck)
            // 기둥: 바닥까지(최대 12칸)
            if (o != 0 && k % 3 == 0) for (d in 1..12) {
                val p = at(base, dir, side, k, o, -d)
                val s = level.getBlockState(p)
                if (!s.isAir && s.fluidState.isEmpty && !s.canBeReplaced()) break
                plan.add(p to Blocks.SPRUCE_LOG.defaultBlockState())
            }
            for (h in 1..3) plan.add(at(base, dir, side, k, o, h) to when {
                o == -1 && h == 1 -> fence
                o == -1 && h == 2 && k % 4 == 0 -> Blocks.LANTERN.defaultBlockState()
                o == 1 && h == 1 && (k == 4 || k == 8) -> fence
                else -> air
            })
        }
        plan.add(at(base, dir, side, 8, 3, -1) to ModBlocks.STATION.defaultBlockState())
        place(level, plan)
        if (!player.isCreative) player.getItemInHand(hand).shrink(1)
        val data = Network.data(level.server)
        val st = Network.registerStation(level, at(base, dir, side, 8, 3, -1), data.uniqueName("항구 ${data.stations.size + 1}"))
        Compat.chat(player, msg("harbor.built", Palette.GREEN, st.name))
    }
}
