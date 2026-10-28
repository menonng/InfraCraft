package dev.menonng.infracraft.system

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.lit
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.AABB

/**
 * 차량 도면: Create 트랙 위에 Create 블록으로 차체를 짓고 강력 접착제를 붙인다. 차량 자체는 Create 기차다.
 * 대차는 Create 방식대로 플레이어가 트랙에 기차 케이스를 대어 놓고, Create 역의 조립 모드로 조립한다.
 *
 * 규격(진행 방향 k, 옆 o, 선로 기준 높이 h): 길이 9(k 0..8) · 폭 5(o -2..2) · 높이 h1(바닥)~h4(지붕).
 * 대차 자리는 k=1, k=7 의 트랙. 지하 역사 승강장(선로 +1)과 바닥 높이가 같다.
 */
object CreateCars {
    enum class Style(val floor: String, val wall: String, val window: String, val roof: String, val seat: String) {
        /** 전동차: 산업용 철 차체, 철 창, 파란색 롱시트, 천장 조명 */
        METRO("create:industrial_iron_block", "create:industrial_iron_block", "create:industrial_iron_window", "create:industrial_iron_block", "create:blue_seat"),
        /** 객차: 가문비나무 차체, 나무 창, 빨간색 좌석, 등불 */
        COACH("minecraft:spruce_planks", "minecraft:spruce_planks", "create:spruce_window", "minecraft:dark_oak_planks", "create:red_seat"),
    }

    const val LENGTH = 9
    private val BOGEYS = setOf(1, 7)
    private val DOORS = setOf(2, 6)

    fun requiredBlocks(): List<String> = listOf("industrial_iron_block", "industrial_iron_window", "spruce_window",
        "blue_seat", "red_seat", "train_door", "controls", "track")

    private fun block(id: String): BlockState? {
        val (ns, path) = id.split(':', limit = 2)
        return Compat.blockById(ns, path)?.defaultBlockState()
    }

    private fun at(base: BlockPos, dir: Direction, side: Direction, k: Int, o: Int, h: Int): BlockPos =
        base.relative(dir, k).relative(side, o).above(h)

    /** 이 스타일의 차체 설계: 위치 → 블록 상태 */
    private fun design(style: Style, base: BlockPos, dir: Direction, side: Direction): Map<BlockPos, BlockState>? {
        val floor = block(style.floor) ?: return null
        val wall = block(style.wall) ?: return null
        val window = block(style.window) ?: return null
        val roof = block(style.roof) ?: return null
        val seat = block(style.seat) ?: return null
        val door = block("create:train_door") ?: return null
        val controls = block("create:controls") ?: return null
        val out = LinkedHashMap<BlockPos, BlockState>()
        for (k in 0 until LENGTH) for (o in -2..2) {
            val end = k == 0 || k == LENGTH - 1
            out[at(base, dir, side, k, o, 1)] = floor
            // 지붕: 전동차는 가운데 줄에 조명
            out[at(base, dir, side, k, o, 4)] =
                if (style == Style.METRO && o == 0 && k % 2 == 0 && !end) ModBlocks.TUNNEL_LIGHT.defaultBlockState() else roof
            for (h in 2..3) {
                val p = at(base, dir, side, k, o, h)
                when {
                    end -> out[p] = if (h == 3 && o != -2 && o != 2) window else wall
                    o == -2 || o == 2 -> out[p] = when {
                        k in DOORS -> {
                            // 문짝은 차체 바깥 면에 붙는다(DoorBlock 은 FACING 반대쪽 면에 판이 온다)
                            val outward = if (o > 0) side else side.opposite
                            var d = CreateCompat.withProperty(door, "facing", outward.opposite.serializedName)
                            d = CreateCompat.withProperty(d, "half", if (h == 2) "lower" else "upper")
                            d
                        }
                        h == 3 -> window
                        else -> wall
                    }
                    h == 2 && o == 0 && (k == 1 || k == LENGTH - 2) ->
                        // 양쪽 끝 운전대(앞뒤 어느 쪽으로도 운전)
                        out[p] = CreateCompat.withProperty(controls, "facing", (if (k == 1) dir.opposite else dir).serializedName)
                    h == 2 && (o == -1 || o == 1) && k !in DOORS && k != 1 && k != LENGTH - 2 -> out[p] = seat
                    h == 3 && o == 0 && style == Style.COACH && (k == 3 || k == 5) ->
                        out[p] = CreateCompat.withProperty(Blocks.LANTERN.defaultBlockState(), "hanging", "true")
                    else -> out[p] = Blocks.AIR.defaultBlockState()
                }
            }
        }
        return out
    }

    /** 설계대로 블록을 놓고 접착제를 붙인다. 접착제를 모두 붙였으면 true. */
    private fun place(level: ServerLevel, plan: Map<BlockPos, BlockState>, base: BlockPos, dir: Direction, side: Direction): Boolean {
        for ((p, st) in plan) level.setBlock(p, st, 3)
        // 접착제: 차체(바닥~지붕) 전체와, 대차 자리 두 칸(트랙 → 대차)과 그 위 바닥만.
        // 대차 사이 트랙까지 덮으면 조립할 때 트랙을 함께 들어 올리려 할 수 있어 빼 둔다.
        var glued = CreateCompat.spawnGlue(level, box(at(base, dir, side, 0, -2, 1), at(base, dir, side, LENGTH - 1, 2, 4)))
        for (k in BOGEYS) glued = CreateCompat.spawnGlue(level, box(at(base, dir, side, k, 0, 0), at(base, dir, side, k, 0, 1))) && glued
        return glued
    }

    /** CI 스모크 테스트: 빈 곳에 트랙을 깔고 전동차 차체를 지어 블록과 접착제가 실제로 놓이는지 본다. */
    fun selfTest(level: ServerLevel, base: BlockPos): String? {
        val track = CreateCompat.trackBlock() ?: return "no track"
        val dir = Direction.EAST
        val side = dir.clockWise
        for (k in 0 until LENGTH) level.setBlock(at(base, dir, side, k, 0, 0), CreateCompat.trackState(track, dir, 0), 3)
        val plan = design(Style.METRO, base, dir, side) ?: return "design failed (missing blocks)"
        if (!place(level, plan, base, dir, side)) return "glue not spawned"
        val wrong = plan.entries.count { (p, st) -> level.getBlockState(p).block != st.block }
        if (wrong > 0) return "$wrong blocks differ"
        val glue = level.getEntities(null as net.minecraft.world.entity.Entity?, box(at(base, dir, side, 0, -2, 0), at(base, dir, side, LENGTH - 1, 2, 4))) {
            it.javaClass.simpleName == "SuperGlueEntity"
        }
        return if (glue.size == 1 + BOGEYS.size) null else "glue entities: ${glue.size}"
    }

    /** 두 모서리 블록을 포함하는 상자 */
    private fun box(a: BlockPos, b: BlockPos) = AABB(minOf(a.x, b.x).toDouble(), minOf(a.y, b.y).toDouble(), minOf(a.z, b.z).toDouble(),
        maxOf(a.x, b.x) + 1.0, maxOf(a.y, b.y) + 1.0, maxOf(a.z, b.z) + 1.0)

    fun build(player: ServerPlayer, hand: InteractionHand, style: Style) {
        val level = player.level() as ServerLevel
        val track = CreateCompat.trackBlock()
        if (track == null) {
            Compat.overlay(player, msg("car.need_create", Palette.ORANGE)); return
        }
        val dir = player.direction
        val side = dir.clockWise
        // 플레이어 앞 칸부터 차체가 놓인다. 발밑 높이가 선로 높이.
        val base = player.blockPosition().relative(dir, 1)
        for (k in 0 until LENGTH) {
            val s = level.getBlockState(at(base, dir, side, k, 0, 0))
            if (s.block != track) {
                Compat.overlay(player, msg("car.need_track", Palette.ORANGE, LENGTH.toString())); return
            }
        }
        val plan = design(style, base, dir, side)
        if (plan == null) {
            Compat.overlay(player, msg("car.need_create", Palette.ORANGE)); return
        }
        for (p in plan.keys) {
            val s = level.getBlockState(p)
            if (!s.isAir && !s.canBeReplaced()) {
                Compat.overlay(player, msg("car.blocked", Palette.ORANGE, p.x.toString(), p.y.toString(), p.z.toString())); return
            }
        }
        // 재료: 문 위아래는 문 하나, 공기는 재료 없음
        val needed = LinkedHashMap<Item, Int>()
        for ((_, st) in plan) {
            if (st.isAir) continue
            if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue
            val item = st.block.asItem()
            needed[item] = (needed[item] ?: 0) + 1
        }
        if (!player.isCreative) {
            val missing = needed.filter { (item, n) -> Inventories.count(player) { it.`is`(item) } < n }
            if (missing.isNotEmpty()) {
                Compat.chat(player, msg("car.need_materials", Palette.ORANGE))
                for ((item, n) in missing) {
                    val have = Inventories.count(player) { it.`is`(item) }
                    Compat.chat(player, lit("  ", Palette.ORANGE).append(ItemStack(item).hoverName).append(lit("  $have / $n", Palette.YELLOW)))
                }
                return
            }
            for ((item, n) in needed) Inventories.consume(player, n) { it.`is`(item) }
            player.getItemInHand(hand).shrink(1)
        }
        val glued = place(level, plan, base, dir, side)
        val bogeys = BOGEYS.map { at(base, dir, side, it, 0, 0) }
        Compat.chat(player, msg(if (glued) "car.built" else "car.built_no_glue", Palette.GREEN,
            bogeys.joinToString("  ") { "${it.x}, ${it.y}, ${it.z}" }))
    }
}
