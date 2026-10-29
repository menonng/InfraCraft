package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.content.ModTags
import dev.menonng.infracraft.msg
import dev.menonng.infracraft.net.Payloads
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LanternBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.SlabType
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 다리 키트. 오른손에 들면 놓일 자리(입자)와 필요한 재료가 보이고, 우클릭하면 놓는다(내구도 1).
 * 상판 높이는 플레이어 발밑(y-1), 방향은 바라보는 쪽. 근처에 길(도로 등급 블록)이 있으면 그 길의 가운데로 옆 위치를 맞추고,
 * 앞쪽 땅이 끝나는 곳에서 다리가 시작해 건너편 땅(상판 높이)에 닿는 곳에서 끝난다.
 * 바닥까지 높이가 [Config.bridgePierHeight] 이상인 자리에는 기둥을 세운다.
 * 디자인은 키트마다 다르지만 소모하는 재료는 인벤토리의 원재료 한 종류뿐이다(놓이는 블록 하나당 원재료 하나).
 */
object Bridges {
    const val HALF = 2  // 상판 폭 5(가운데 ±2)

    enum class Kind(val spacing: Int) { TIMBER(6), STONE(10), COPPER(12) }

    fun kind(stack: ItemStack): Kind? = when {
        stack.`is`(ModItems.TIMBER_BRIDGE_KIT) -> Kind.TIMBER
        stack.`is`(ModItems.STONE_BRIDGE_KIT) -> Kind.STONE
        stack.`is`(ModItems.COPPER_BRIDGE_KIT) -> Kind.COPPER
        else -> null
    }

    /**
     * 원재료 하나에서 나온 블록 묶음. 모두 [raw] 아이템으로 값을 치른다(놓이는 블록 하나에 원재료 하나, 랜턴도 마찬가지).
     * [stripped] 껍질 벗긴 통나무, [weathered] 이끼·금 간 변형(오래된 석교의 질감), [trim] 조각된 블록, [lantern]·[chain] 조명.
     */
    class Family(
        val raw: Item, val full: BlockState, val slab: Block?, val stairs: Block?, val rail: Block?, val post: Block?, val accent: Block?,
        val stripped: Block? = null, val weathered: List<Block> = emptyList(), val trim: Block? = null,
        val lantern: Block = Blocks.LANTERN, val chain: Block? = null,
    )

    private fun block(ns: String, path: String): Block? = Compat.blockById(ns, path)?.takeIf { it != Blocks.AIR }

    /** 이 키트가 원재료로 받는 블록이면 그 묶음 */
    fun family(kind: Kind, stack: ItemStack): Family? {
        val b = (stack.item as? BlockItem)?.block ?: return null
        val key = BuiltInRegistries.BLOCK.getKey(b)
        val ns = key.namespace
        val path = key.path
        return when (kind) {
            Kind.TIMBER -> {
                if (!path.endsWith("_planks")) return null
                val wood = path.removeSuffix("_planks")
                val post = block(ns, "${wood}_log") ?: block(ns, "${wood}_stem") ?: block(ns, "${wood}_block") ?: b
                val stripped = block(ns, "stripped_${wood}_log") ?: block(ns, "stripped_${wood}_stem") ?: block(ns, "stripped_${wood}_block")
                Family(stack.item, b.defaultBlockState(), block(ns, "${wood}_slab"), block(ns, "${wood}_stairs"), block(ns, "${wood}_fence") ?: return null, post, null,
                    stripped = stripped)
            }
            Kind.STONE -> {
                // stone_bricks → stone_brick_wall, deepslate_tiles → deepslate_tile_wall, cobblestone → cobblestone_wall
                val base = if (path.endsWith("bricks") || path.endsWith("tiles")) path.dropLast(1) else path
                val wall = block(ns, "${base}_wall") ?: return null
                val stairs = block(ns, "${base}_stairs") ?: return null
                if (path.endsWith("_planks")) return null
                Family(stack.item, b.defaultBlockState(), block(ns, "${base}_slab"), stairs, wall, b, null,
                    weathered = listOfNotNull(block(ns, "mossy_$path"), block(ns, "cracked_$path")), trim = block(ns, "chiseled_$path"))
            }
            Kind.COPPER -> {
                // cut_copper, exposed_cut_copper, waxed_oxidized_cut_copper … 녹스는 정도가 다리의 표정이 된다
                if (!path.endsWith("cut_copper")) return null
                val pre = path.removeSuffix("cut_copper")
                Family(stack.item, b.defaultBlockState(), block(ns, "${pre}cut_copper_slab"), block(ns, "${pre}cut_copper_stairs"),
                    block(ns, "${pre}copper_bars") ?: Blocks.IRON_BARS, b, block(ns, "${pre}copper_grate"),
                    trim = block(ns, "${pre}chiseled_copper"), lantern = block(ns, "${pre}copper_lantern") ?: Blocks.LANTERN,
                    chain = block(ns, "${pre}copper_chain") ?: Blocks.IRON_CHAIN)
            }
        }
    }

    /** 왼손을 먼저, 그다음 핫바·인벤토리 순으로 이 키트가 받는 첫 원재료 */
    fun findRaw(player: ServerPlayer, kind: Kind): Pair<ItemStack, Family>? {
        family(kind, player.offhandItem)?.let { return player.offhandItem to it }
        val inv = player.inventory
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (s.isEmpty) continue
            family(kind, s)?.let { return s to it }
        }
        return null
    }

    // ───────────── 배치 계산 ─────────────

    class Plan(
        val base: BlockPos, val dir: Direction, val side: Direction,
        val start: Int, val end: Int, val landed: Boolean, val snapped: Boolean,
        val piers: List<Int>, val blocks: List<Pair<BlockPos, BlockState>>,
    ) {
        val length get() = end - start + 1
        fun at(k: Int, o: Int, h: Int): BlockPos = base.relative(dir, k).relative(side, o).above(h)
    }

    private fun open(level: ServerLevel, p: BlockPos): Boolean {
        val s = level.getBlockState(p)
        if (level.getBlockEntity(p) != null) return false
        return s.isAir || s.canBeReplaced() || s.block is LiquidBlock
    }

    private fun isRoad(level: ServerLevel, p: BlockPos): Boolean {
        val s = level.getBlockState(p)
        return s.`is`(ModTags.ROAD_TIER_1) || s.`is`(ModTags.ROAD_TIER_2) || s.`is`(ModTags.ROAD_TIER_3)
    }

    /** 발밑 근처(뒤 4칸~앞 1칸, 옆 ±4칸)의 길에서 가운데 줄까지의 옆 칸 수. 길이 없으면 null */
    private fun roadOffset(level: ServerLevel, deck: BlockPos, dir: Direction, side: Direction): Int? {
        for (k in listOf(0, -1, 1, -2, -3, -4)) for (dy in listOf(0, 1, -1)) {
            val row = (-4..4).filter { isRoad(level, deck.relative(dir, k).relative(side, it).above(dy)) }
            if (row.isEmpty()) continue
            // 0 에 가장 가까운 칸이 속한 연속 구간
            val near = row.minBy { abs(it) }
            var lo = near
            var hi = near
            while (lo - 1 in row) lo--
            while (hi + 1 in row) hi++
            return Math.floorDiv(lo + hi, 2)
        }
        return null
    }

    fun plan(level: ServerLevel, feet: BlockPos, dir: Direction, kind: Kind, fam: Family): Plan? {
        val side = dir.clockWise
        var deck = feet.below()
        val off = roadOffset(level, deck, dir, side)
        if (off != null && off != 0) deck = deck.relative(side, off)
        fun d(k: Int) = deck.relative(dir, k)
        // 시작: 앞쪽 땅이 끊기는 첫 칸(최대 8칸 앞까지)
        val start = (0..8).firstOrNull { open(level, d(it)) } ?: return null
        val maxLen = Config.c.bridgeKitMaxLength
        var end = start
        var landed = false
        for (k in start until start + maxLen) {
            if (!open(level, d(k))) { landed = true; break }
            end = k
        }
        // 바닥까지의 높이(물도 빈 칸으로 센다)
        val depthCap = Config.c.bridgePierMaxDepth
        fun drop(k: Int): Int {
            var n = 0
            while (n < depthCap && open(level, d(k).below(n + 1))) n++
            return n
        }
        val len = end - start + 1
        val n = (len - 1) / kind.spacing
        val piers = (1..n).map { start - 1 + (it * (len + 1).toDouble() / (n + 1)).roundToInt() }
            .filter { it in start..end && drop(it) >= Config.c.bridgePierHeight }
        val p = Plan(deck, dir, side, start, end, landed, off != null, piers, emptyList())
        val blocks = when (kind) {
            Kind.TIMBER -> timber(p, fam, ::drop)
            Kind.STONE -> stone(p, fam, ::drop)
            Kind.COPPER -> copper(p, fam, ::drop)
        }
        val wanted = LinkedHashMap<BlockPos, BlockState>()
        for ((pos, st) in blocks) if (open(level, pos) && level.getBlockState(pos) != st) wanted[pos] = st
        return Plan(deck, dir, side, start, end, landed, off != null, piers, wanted.toList())
    }

    // ───────────── 디자인 ─────────────
    // 참고: 유튜브·웹 다리 건축 튜토리얼(중세 목조교, 다경간 석조 아치교, 구리·강철 트러스교)의 공통 문법을 블록 규칙으로 옮겼다.
    //  - 목조: 통나무 테두리 보, 울타리 난간과 4칸마다 껍질 벗긴 통나무 기둥, 8칸마다 랜턴 기둥, 양 끝 지붕 달린 문, 교각 위 까치발
    //  - 석조: 원호 아치(경간이 넓으면 납작한 원호), 아치 꼭대기 쐐기돌, 상판 옆 처마 돌림띠, 랜턴 기둥이 있는 담장 난간,
    //          3칸 두께 교각과 물가름 받침, 물에 가까운 아래쪽은 이끼·금 간 돌
    //  - 구리: 4칸 패널 Warren 트러스(위 현, 수직재, 번갈아 기우는 격자 사재), 머리 위 가로보와 매달린 구리 랜턴, 양 끝 등탑, 지그재그 가새 교각

    private fun axis(b: Block, a: Direction.Axis): BlockState {
        val s = b.defaultBlockState()
        return if (s.hasProperty(RotatedPillarBlock.AXIS)) s.setValue(RotatedPillarBlock.AXIS, a) else s
    }

    private fun slab(f: Family, type: SlabType): BlockState =
        f.slab?.defaultBlockState()?.setValue(SlabBlock.TYPE, type) ?: f.full

    private fun stairs(f: Family, facing: Direction, half: Half): BlockState =
        f.stairs?.defaultBlockState()?.setValue(StairBlock.FACING, facing)?.setValue(StairBlock.HALF, half) ?: f.full

    private fun lantern(f: Family, hanging: Boolean): BlockState {
        val s = f.lantern.defaultBlockState()
        return if (s.hasProperty(LanternBlock.HANGING)) s.setValue(LanternBlock.HANGING, hanging) else s
    }

    /** 위치마다 정해진 0..99 값(같은 자리는 늘 같은 모양이 되도록) */
    private fun noise(p: BlockPos): Int = Math.floorMod(p.x * 73856093 xor p.y * 19349663 xor p.z * 83492791, 100)

    /** 오래된 돌: 확률(%)만큼 이끼·금 간 변형으로 */
    private fun aged(f: Family, p: BlockPos, percent: Int): BlockState {
        if (f.weathered.isEmpty() || noise(p) >= percent) return f.full
        return f.weathered[noise(p.above(7)) % f.weathered.size].defaultBlockState()
    }

    /** 상판 바깥(o=±3)에서 상판을 향한 계단: half 가 TOP 이면 처마(돌림띠), BOTTOM 이면 경사진 머리 */
    private fun outward(p: Plan, o: Int): Direction = if (o > 0) p.side else p.side.opposite

    /** 기둥 한 줄: h=from 에서 바닥까지 */
    private fun MutableList<Pair<BlockPos, BlockState>>.column(p: Plan, k: Int, o: Int, from: Int, depth: Int, state: (Int) -> BlockState) {
        for (h in from downTo -depth) add(p.at(k, o, h) to state(h))
    }

    /** 목조 다리 */
    private fun timber(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        val log = f.post ?: f.full.block
        val stripped = f.stripped ?: log
        val fence = f.rail!!.defaultBlockState()
        for (k in p.start..p.end) {
            val i = k - p.start
            // 상판: 가운데 판자, 양옆은 다리 방향으로 누운 통나무 테두리 보, 아래에 세로 보 두 줄
            for (o in -1..1) out.add(p.at(k, o, 0) to f.full)
            for (o in listOf(-HALF, HALF)) out.add(p.at(k, o, 0) to axis(log, p.dir.axis))
            for (o in listOf(-1, 1)) out.add(p.at(k, o, -1) to axis(log, p.dir.axis))
            // 난간: 울타리, 4칸마다 껍질 벗긴 통나무 기둥, 8칸마다 랜턴을 얹은 높은 기둥
            for (o in listOf(-HALF, HALF)) when {
                i % 8 == 0 -> {
                    out.add(p.at(k, o, 1) to axis(stripped, Direction.Axis.Y))
                    out.add(p.at(k, o, 2) to fence)
                    out.add(p.at(k, o, 3) to lantern(f, false))
                }
                i % 4 == 0 -> out.add(p.at(k, o, 1) to axis(stripped, Direction.Axis.Y))
                else -> out.add(p.at(k, o, 1) to fence)
            }
        }
        // 양 끝 문: 통나무 기둥 셋 높이, 가로 들보, 다리 방향으로 세 칸 깊이의 박공 지붕, 들보 아래 매달린 랜턴
        val gates = listOf(p.start) + if (p.landed && p.length >= 4) listOf(p.end) else emptyList()
        for (k in gates) {
            for (o in listOf(-HALF, HALF)) for (h in 1..3) out.add(p.at(k, o, h) to axis(log, Direction.Axis.Y))
            for (o in -3..3) out.add(p.at(k, o, 4) to axis(log, p.side.axis))
            out.add(p.at(k, 0, 3) to lantern(f, true))
            for (dk in -1..1) {
                // 처마(o=±3)에서 용마루(o=0)로 오르는 계단, 그 안은 판자, 용마루는 반 블록
                for (o in listOf(-3, 3)) out.add(p.at(k + dk, o, 5) to stairs(f, outward(p, o).opposite, Half.BOTTOM))
                for (o in listOf(-2, 2)) out.add(p.at(k + dk, o, 6) to stairs(f, outward(p, o).opposite, Half.BOTTOM))
                for (o in listOf(-1, 1)) out.add(p.at(k + dk, o, 7) to stairs(f, outward(p, o).opposite, Half.BOTTOM))
                for (o in -2..2) out.add(p.at(k + dk, o, 5) to f.full)
                for (o in -1..1) out.add(p.at(k + dk, o, 6) to f.full)
                out.add(p.at(k + dk, 0, 7) to slab(f, SlabType.BOTTOM))
            }
            // 박공 아래 처마 끝을 받치는 까치발
            for (o in listOf(-3, 3)) out.add(p.at(k, o, 3) to stairs(f, outward(p, o).opposite, Half.TOP))
        }
        // 교각(가대): 통나무 기둥 세 줄, 4칸마다 가로 보, 상판 아래 까치발
        for (k in p.piers) {
            val depth = drop(k)
            for (o in listOf(-HALF, 0, HALF)) out.column(p, k, o, if (o == 0) -2 else -1, depth) { axis(log, Direction.Axis.Y) }
            for (h in -2 downTo -depth step 4) for (o in listOf(-1, 1)) out.add(p.at(k, o, h) to axis(log, p.side.axis))
            for (o in listOf(-HALF, HALF)) {
                out.add(p.at(k - 1, o, -1) to stairs(f, p.dir, Half.TOP))
                out.add(p.at(k + 1, o, -1) to stairs(f, p.dir.opposite, Half.TOP))
            }
        }
        return out
    }

    /** 석조 아치교 */
    private fun stone(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        val wall = f.rail!!.defaultBlockState()
        for (k in p.start..p.end) {
            val i = k - p.start
            for (o in -HALF..HALF) out.add(p.at(k, o, 0) to f.full)
            // 처마 돌림띠: 상판 바깥으로 거꾸로 놓은 계단
            for (o in listOf(-3, 3)) out.add(p.at(k, o, 0) to stairs(f, outward(p, o).opposite, Half.TOP))
            // 담장 난간, 4칸마다 돌기둥, 8칸마다 랜턴 기둥
            for (o in listOf(-HALF, HALF)) when {
                i % 8 == 0 -> {
                    out.add(p.at(k, o, 1) to (f.trim?.defaultBlockState() ?: f.full))
                    out.add(p.at(k, o, 2) to wall)
                    out.add(p.at(k, o, 3) to lantern(f, false))
                }
                i % 4 == 0 -> out.add(p.at(k, o, 1) to f.full)
                else -> out.add(p.at(k, o, 1) to wall)
            }
        }
        // 받침: 강둑(시작 앞, 도착 뒤)과 교각(3칸 두께). 받침 사이마다 원호 아치를 건다.
        val faces = ArrayList<Pair<Int, Int>>()  // (왼쪽 받침의 오른쪽 면, 오른쪽 받침의 왼쪽 면)
        var left = p.start - 1
        for (k in p.piers) {
            faces.add(left to k - 1)
            left = k + 1
        }
        faces.add(left to p.end + 1)
        for ((a, b) in faces) {
            val lo = a + 1
            val hi = b - 1
            if (hi < lo) continue
            // 건너편 땅에 닿지 않은 마지막 경간은 받칠 곳이 없으므로 아치 대신 납작한 돌림띠만
            if (!p.landed && b == p.end + 1) {
                for (k in lo..hi) for (o in -HALF..HALF) out.add(p.at(k, o, -1) to slab(f, SlabType.TOP))
                continue
            }
            val n = hi - lo + 1
            val c = (lo + hi) / 2.0
            val r = (n + 1) / 2.0
            // 아치 높이: 반원이 기본, 넓으면 8칸으로 눌러 납작한 원호. 바닥(물 밑)보다 깊어지지 않게.
            val ground = (lo..hi).minOf { drop(it) }
            val rise = minOf(r, 8.0, (ground - 1).toDouble())
            if (rise < 1.0) {
                for (k in lo..hi) for (o in -HALF..HALF) out.add(p.at(k, o, -1) to slab(f, SlabType.TOP))
                continue
            }
            val rho = (r * r + rise * rise) / (2 * rise)
            fun depthAt(k: Int): Int {
                val x = k - c
                val y = Math.sqrt(maxOf(0.0, rho * rho - x * x)) - (rho - rise)
                return (rise - y).roundToInt().coerceAtLeast(0)  // 상판 아래(h=-1)부터 아치 안쪽 면까지의 채움 깊이
            }
            for (k in lo..hi) {
                val d = depthAt(k)
                val towardA = k - c < 0
                val support = if (towardA) p.dir.opposite else p.dir
                val nearer = depthAt(if (towardA) k - 1 else k + 1)
                for (o in -HALF..HALF) {
                    for (j in 0..d) {
                        val h = -1 - j
                        val pos = p.at(k, o, h)
                        val face = o == -HALF || o == HALF
                        // 아치 안쪽 맨 아래 칸: 받침 쪽이 더 깊으면 거꾸로 놓은 계단으로 곡선을 매끄럽게
                        val st = if (j == d && nearer > d) stairs(f, support, Half.TOP)
                        else if (face) aged(f, pos, 12) else f.full
                        out.add(pos to st)
                    }
                }
                // 쐐기돌
                if (k == c.roundToInt() && f.trim != null) for (o in listOf(-HALF, HALF)) out.add(p.at(k, o, -1) to f.trim.defaultBlockState())
            }
        }
        // 교각: 3칸 두께, 상판 폭, 물가름 받침(양옆 o=±3, 위는 경사 계단), 아래쪽 세 칸은 이끼·금 간 돌이 많다
        for (k in p.piers) for (dk in -1..1) {
            val depth = drop(k + dk)
            for (o in -HALF..HALF) out.column(p, k + dk, o, -1, depth) { h -> aged(f, p.at(k + dk, o, h), if (h <= -depth + 2) 45 else 10) }
            if (dk == 0) for (o in listOf(-3, 3)) {
                val top = -minOf(depth, 4)
                out.column(p, k, o, top - 1, depth) { h -> aged(f, p.at(k, o, h), if (h <= -depth + 2) 45 else 10) }
                out.add(p.at(k, o, top) to stairs(f, outward(p, o).opposite, Half.BOTTOM))
            }
        }
        return out
    }

    /** 구리 트러스교 */
    private fun copper(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        val grate = f.accent?.defaultBlockState() ?: f.full
        val bars = f.rail!!.defaultBlockState()
        for (k in p.start..p.end) {
            val i = k - p.start
            val m = i % 4
            val panel = i / 4
            for (o in -HALF..HALF) out.add(p.at(k, o, 0) to f.full)
            // 바닥 아래: 패널 마디마다 가로보, 그 사이 양옆 세로 보는 격자(아래가 비쳐 가벼워 보인다)
            if (m == 0) for (o in -HALF..HALF) out.add(p.at(k, o, -1) to f.full)
            else for (o in listOf(-HALF, HALF)) out.add(p.at(k, o, -1) to grate)
            for (o in listOf(-HALF, HALF)) {
                out.add(p.at(k, o, 4) to (if (m != 0) slab(f, SlabType.BOTTOM) else f.full))  // 위 현
                if (m == 0) for (h in 1..3) out.add(p.at(k, o, h) to f.full)  // 수직재
                else {
                    // Warren 사재: 패널마다 기울기가 바뀌는 통짜 부재, 나머지 아래 칸은 창살 난간
                    val dh = if (panel % 2 == 0) 4 - m else m
                    out.add(p.at(k, o, dh) to f.full)
                    if (dh != 1) out.add(p.at(k, o, 1) to bars)
                }
            }
            if (m == 0) {
                // 머리 위 가로보, 두 마디마다 매달린 구리 랜턴
                for (o in -1..1) out.add(p.at(k, o, 4) to slab(f, SlabType.BOTTOM))
                if (panel % 2 == 0) out.add(p.at(k, 0, 3) to lantern(f, true))
            }
        }
        // 양 끝 등탑: 조각된 구리 위에 사슬과 랜턴
        val towers = listOf(p.start) + if (p.landed) listOf(p.end) else emptyList()
        for (k in towers) for (o in listOf(-HALF, HALF)) {
            out.add(p.at(k, o, 5) to (f.trim?.defaultBlockState() ?: f.full))
            f.chain?.let { out.add(p.at(k, o, 6) to axis(it, Direction.Axis.Y)) }
            out.add(p.at(k, o, if (f.chain != null) 7 else 6) to lantern(f, false))
        }
        // 교각: 네 다리(다리 방향 ±1, 양옆)의 탑. 4칸마다 둘레 띠, 그 사이 옆면은 X 가새, 앞뒤 면은 지그재그 격자
        for (k in p.piers) {
            val depth = minOf(drop(k - 1), drop(k), drop(k + 1))
            for (dk in listOf(-1, 1)) for (o in listOf(-HALF, HALF)) out.column(p, k + dk, o, -1, drop(k + dk)) { f.full }
            var top = -2
            var flip = false
            while (top > -depth) {
                for (dk in -1..1) for (o in -HALF..HALF) if (dk != 0 || abs(o) == HALF) out.add(p.at(k + dk, o, top) to f.full)
                for (j in 1..3) if (top - j > -depth) {
                    for (o in listOf(-HALF, HALF)) if (j == 2) out.add(p.at(k, o, top - j) to grate)
                    for (dk in listOf(-1, 1)) out.add(p.at(k + dk, if (flip) 2 - j else j - 2, top - j) to grate)
                }
                top -= 4
                flip = !flip
            }
        }
        return out
    }

    // ───────────── 사용 ─────────────

    fun use(player: ServerPlayer, hand: InteractionHand) {
        val kit = player.getItemInHand(hand)
        val kind = kind(kit) ?: return
        val level = player.level() as ServerLevel
        val (rawStack, fam) = findRaw(player, kind) ?: run {
            Compat.overlay(player, msg("bridge_kit.need_raw.${kind.name.lowercase()}", Palette.ORANGE)); return
        }
        val plan = plan(level, player.blockPosition(), player.direction, kind, fam) ?: run {
            Compat.overlay(player, msg("bridge_kit.no_gap", Palette.ORANGE)); return
        }
        val need = plan.blocks.size
        if (need == 0) {
            Compat.overlay(player, msg("bridge_kit.nothing", Palette.ORANGE)); return
        }
        val have = Inventories.count(player) { it.`is`(fam.raw) }
        if (!player.isCreative && have < need) {
            Compat.overlay(player, msg("bridge_kit.short", Palette.RED, rawStack.hoverName, need.toString(), have.toString())); return
        }
        Inventories.consume(player, need) { it.`is`(fam.raw) }
        build(level, plan)
        cache.remove(player)
        clearHologram(player)
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.3f)
        player.awardStat(ModStats.BRIDGE_BLOCKS, need)
        if (!player.isCreative) kit.hurtAndBreak(1, player, if (hand == InteractionHand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND)
        Compat.chat(player, msg(if (plan.landed) "bridge_kit.built" else "bridge_kit.built_open", Palette.GREEN,
            plan.length.toString(), plan.piers.size.toString(), need.toString(), rawStack.hoverName))
    }

    fun build(level: ServerLevel, plan: Plan) {
        for ((pos, st) in plan.blocks) level.setBlock(pos, st, 3)
        // 울타리·담장·창살이 서로 이어지도록 모양을 다시 맞춘다
        for ((pos, _) in plan.blocks) {
            val s = level.getBlockState(pos)
            val u = Block.updateFromNeighbourShapes(s, level, pos)
            if (u != s) level.setBlock(pos, u, 2)
        }
    }

    /** CI 연기 시험: 하늘에 띄운 두 강둑 사이에 석조 다리를 놓아 본다. 문제가 없으면 null */
    fun selfTest(level: ServerLevel, feet: BlockPos): String? {
        val bank = Blocks.STONE.defaultBlockState()
        for (x in -3..0) for (z in -4..4) level.setBlock(feet.offset(x, -1, z), bank, 3)
        for (x in 14..17) for (z in -4..4) level.setBlock(feet.offset(x, -1, z), bank, 3)
        val fam = family(Kind.STONE, ItemStack(Blocks.STONE_BRICKS)) ?: return "no stone family"
        val p = plan(level, feet, Direction.EAST, Kind.STONE, fam) ?: return "no plan"
        if (!p.landed || p.start != 1 || p.end != 13) return "span ${p.start}..${p.end} landed=${p.landed}"
        if (p.piers != listOf(7)) return "piers ${p.piers}"
        // 첫 경간(1..5) 아치: 받침 쪽 아래 칸에 거꾸로 놓은 계단, 가운데(3) 위에 쐐기돌
        val arch = p.blocks.count { (pos, st) -> st.block is StairBlock && st.getValue(StairBlock.HALF) == Half.TOP && pos.y < feet.y - 2 }
        if (arch < 4) return "arch stairs $arch"
        build(level, p)
        val stoneSet = setOf(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS)
        val deck = level.getBlockState(feet.offset(5, -1, 0))
        val wall = level.getBlockState(feet.offset(6, 0, 2))
        val key = level.getBlockState(feet.offset(3, -2, 2))
        val lamp = level.getBlockState(feet.offset(9, 2, 2))
        if (!deck.`is`(Blocks.STONE_BRICKS) || !wall.`is`(Blocks.STONE_BRICK_WALL)) return "deck=$deck wall=$wall"
        if (!key.`is`(Blocks.CHISELED_STONE_BRICKS)) return "keystone=$key"
        if (!lamp.`is`(Blocks.LANTERN)) return "lamp=$lamp"
        for (dk in -1..1) {
            val pier = level.getBlockState(feet.offset(7 + dk, -10, 0))
            if (pier.block !in stoneSet) return "pier=$pier"
        }
        // 같은 자리에 한 번 더: 이미 다 놓였으므로 필요한 블록 0
        val again = plan(level, feet, Direction.EAST, Kind.STONE, fam)
        if (again != null && again.blocks.isNotEmpty()) return "rebuild needs ${again.blocks.size}"
        return null
    }

    /** CI 연기 시험: 목조·구리 다리 계획이 랜턴과 교각을 갖추는지(놓지는 않는다). 결과는 (블록 수, 오류) */
    fun designTest(level: ServerLevel, feet: BlockPos, kind: Kind, raw: Block): Pair<Int, String?> {
        val bank = Blocks.STONE.defaultBlockState()
        for (x in -3..0) for (z in -4..4) level.setBlock(feet.offset(x, -1, z), bank, 3)
        for (x in 26..29) for (z in -4..4) level.setBlock(feet.offset(x, -1, z), bank, 3)
        val fam = family(kind, ItemStack(raw)) ?: return 0 to "no family"
        val p = plan(level, feet, Direction.EAST, kind, fam) ?: return 0 to "no plan"
        if (!p.landed || p.length != 25) return 0 to "span ${p.start}..${p.end}"
        if (p.piers.isEmpty()) return 0 to "no piers"
        val lamps = p.blocks.count { it.second.block is LanternBlock }
        if (lamps < 2) return p.blocks.size to "lanterns $lamps"
        if (p.blocks.any { it.second.isAir }) return p.blocks.size to "air in plan"
        return p.blocks.size to null
    }

    // ───────────── 들고 있을 때 미리보기 ─────────────

    private class Cache(val key: List<Any>, val plan: Plan?, val at: Long)
    private val cache = WeakHashMap<ServerPlayer, Cache>()
    /** 마지막으로 보낸 홀로그램(계획, 재료 충분 여부). 같으면 다시 보내지 않는다 */
    private val sent = WeakHashMap<ServerPlayer, Pair<Plan?, Boolean>>()

    private fun hologram(player: ServerPlayer) = ServerPlayNetworking.canSend(player, Payloads.BridgePreview.TYPE)

    private fun clearHologram(player: ServerPlayer) {
        if (sent.remove(player) != null && hologram(player)) ServerPlayNetworking.send(player, Payloads.BridgePreview(emptyList(), true))
    }

    /** 미리보기 블록마다 역할: 교각(교각 자리 ±1 칸, 상판 보 아래)이면 1, 나머지 0 */
    private fun ghosts(plan: Plan): List<Payloads.Ghost> = plan.blocks.map { (pos, st) ->
        val k = (pos.x - plan.base.x) * plan.dir.stepX + (pos.z - plan.base.z) * plan.dir.stepZ
        val h = pos.y - plan.base.y
        Payloads.Ghost(pos, Block.getId(st), if (plan.piers.any { abs(it - k) <= 1 } && h < -1) 1 else 0)
    }

    fun tickHeld(player: ServerPlayer) {
        val kind = kind(player.mainHandItem)
        if (kind == null) {
            cache.remove(player); clearHologram(player); return
        }
        val level = player.level() as ServerLevel
        if (level.gameTime % 4 != 0L) return
        val raw = findRaw(player, kind)
        if (raw == null) {
            clearHologram(player)
            Compat.overlay(player, msg("bridge_kit.need_raw.${kind.name.lowercase()}", Palette.ORANGE)); return
        }
        val (rawStack, fam) = raw
        val key = listOf(player.blockPosition(), player.direction, kind, fam.raw)
        val old = cache[player]
        val plan = if (old != null && old.key == key && level.gameTime - old.at < 20) old.plan
        else plan(level, player.blockPosition(), player.direction, kind, fam).also { cache[player] = Cache(key, it, level.gameTime) }
        if (plan == null) {
            clearHologram(player)
            Compat.overlay(player, msg("bridge_kit.no_gap", Palette.ORANGE)); return
        }
        val need = plan.blocks.size
        val have = Inventories.count(player) { it.`is`(fam.raw) }
        val enough = player.isCreative || have >= need
        if (hologram(player)) {
            // 리터매티카처럼 놓일 블록을 반투명 홀로그램으로(색: 충분하면 하늘, 모자라면 빨강, 기둥은 노랑)
            val now = plan to enough
            if (sent[player]?.first !== plan || sent[player]?.second != enough) {
                ServerPlayNetworking.send(player, Payloads.BridgePreview(ghosts(plan), enough))
                sent[player] = now
            }
        } else {
            // 모드가 없는 클라이언트: 입자 점선
            val color = if (enough) Palette.SKY else Palette.RED
            val dust = DustParticleOptions(color, 1.0f)
            fun spark(pos: BlockPos, dy: Double) =
                level.sendParticles(player, dust, true, false, pos.x + 0.5, pos.y + dy, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
            for (k in plan.start..plan.end) for (o in listOf(-HALF, HALF)) spark(plan.at(k, o, 0), 1.05)
            val pierDust = DustParticleOptions(Palette.YELLOW, 1.2f)
            for (k in plan.piers) for (h in 0 downTo -24 step 2) {
                val pos = plan.at(k, 0, h)
                if (h < 0 && !open(level, pos)) break
                level.sendParticles(player, pierDust, true, false, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        val tail = if (plan.landed) "" else " ↷"
        val snap = if (plan.snapped) msg("bridge_kit.snapped", Palette.GREEN) else null
        val line = msg("bridge_kit.preview", if (enough) Palette.SKY else Palette.RED,
            plan.length.toString() + tail, plan.piers.size.toString(), rawStack.hoverName, need.toString(), have.toString())
        Compat.overlay(player, if (snap != null) snap.append(" ").append(line) else line)
    }
}
