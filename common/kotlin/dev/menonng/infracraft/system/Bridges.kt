package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.content.ModTags
import dev.menonng.infracraft.msg
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

    enum class Kind(val spacing: Int) { TIMBER(6), STONE(8), COPPER(10) }

    fun kind(stack: ItemStack): Kind? = when {
        stack.`is`(ModItems.TIMBER_BRIDGE_KIT) -> Kind.TIMBER
        stack.`is`(ModItems.STONE_BRIDGE_KIT) -> Kind.STONE
        stack.`is`(ModItems.COPPER_BRIDGE_KIT) -> Kind.COPPER
        else -> null
    }

    /** 원재료 하나에서 나온 블록 묶음. 모두 [raw] 아이템으로 값을 치른다. */
    class Family(val raw: Item, val full: BlockState, val slab: Block?, val stairs: Block?, val rail: Block?, val post: Block?, val accent: Block?)

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
                Family(stack.item, b.defaultBlockState(), block(ns, "${wood}_slab"), block(ns, "${wood}_stairs"), block(ns, "${wood}_fence") ?: return null, post, null)
            }
            Kind.STONE -> {
                // stone_bricks → stone_brick_wall, deepslate_tiles → deepslate_tile_wall, cobblestone → cobblestone_wall
                val base = if (path.endsWith("bricks") || path.endsWith("tiles")) path.dropLast(1) else path
                val wall = block(ns, "${base}_wall") ?: return null
                val stairs = block(ns, "${base}_stairs") ?: return null
                if (path.endsWith("_planks")) return null
                Family(stack.item, b.defaultBlockState(), block(ns, "${base}_slab"), stairs, wall, b, null)
            }
            Kind.COPPER -> {
                // cut_copper, exposed_cut_copper, waxed_oxidized_cut_copper … 녹스는 정도가 다리의 표정이 된다
                if (!path.endsWith("cut_copper")) return null
                val pre = path.removeSuffix("cut_copper")
                Family(stack.item, b.defaultBlockState(), block(ns, "${pre}cut_copper_slab"), block(ns, "${pre}cut_copper_stairs"),
                    block(ns, "${pre}copper_bars") ?: Blocks.IRON_BARS, b, block(ns, "${pre}copper_grate"))
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

    private fun axis(b: Block, a: Direction.Axis): BlockState {
        val s = b.defaultBlockState()
        return if (s.hasProperty(RotatedPillarBlock.AXIS)) s.setValue(RotatedPillarBlock.AXIS, a) else s
    }

    private fun slab(f: Family, type: SlabType): BlockState =
        f.slab?.defaultBlockState()?.setValue(SlabBlock.TYPE, type) ?: f.full

    private fun stairs(f: Family, facing: Direction, half: Half): BlockState =
        f.stairs?.defaultBlockState()?.setValue(StairBlock.FACING, facing)?.setValue(StairBlock.HALF, half) ?: f.full

    /** 기둥 한 줄: h=from 에서 바닥까지 */
    private fun MutableList<Pair<BlockPos, BlockState>>.column(p: Plan, k: Int, o: Int, from: Int, depth: Int, state: (Int) -> BlockState) {
        for (h in from downTo -depth) add(p.at(k, o, h) to state(h))
    }

    /** 목조 다리: 판자 상판, 울타리 난간과 통나무 난간 기둥, 상판 아래 통나무 보, 높은 곳에는 통나무 가대(트레슬) */
    private fun timber(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        val post = f.post ?: f.full.block
        for (k in p.start..p.end) {
            val i = k - p.start
            for (o in -HALF..HALF) out.add(p.at(k, o, 0) to f.full)
            for (o in listOf(-HALF, HALF)) {
                out.add(p.at(k, o, 1) to if (i % 3 == 0) axis(post, Direction.Axis.Y) else f.rail!!.defaultBlockState())
                out.add(p.at(k, o, -1) to axis(post, p.dir.axis))
            }
        }
        for (k in p.piers) {
            val depth = drop(k)
            for (o in listOf(-HALF, HALF)) out.column(p, k, o, -2, depth) { axis(post, Direction.Axis.Y) }
            // 가로 보: 상판 바로 아래와 4칸마다
            for (h in -2 downTo -depth step 4) for (o in -1..1) out.add(p.at(k, o, h) to axis(post, p.side.axis))
        }
        return out
    }

    /** 석조 아치교: 상판과 담장 난간, 받침(기둥·강둑) 사이를 잇는 아치, 상판 폭 그대로의 석조 교각 */
    private fun stone(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        for (k in p.start..p.end) {
            for (o in -HALF..HALF) out.add(p.at(k, o, 0) to f.full)
            for (o in listOf(-HALF, HALF)) out.add(p.at(k, o, 1) to f.rail!!.defaultBlockState())
        }
        val supports = listOf(p.start - 1) + p.piers + if (p.landed) listOf(p.end + 1) else emptyList()
        for ((a, b) in supports.zipWithNext()) for (k in a + 1 until b) {
            val dA = k - a
            val dB = b - k
            val dist = minOf(dA, dB)
            val toward = if (dA <= dB) p.dir.opposite else p.dir
            val col: List<BlockState> = when (dist) {
                1 -> listOf(f.full, f.full, stairs(f, toward, Half.TOP))
                2 -> listOf(f.full, stairs(f, toward, Half.TOP))
                3 -> listOf(stairs(f, toward, Half.TOP))
                else -> listOf(slab(f, SlabType.TOP))
            }
            if (col.size >= drop(k)) continue  // 아치가 바닥에 닿으면 생략
            for (o in -HALF..HALF) col.forEachIndexed { j, s -> out.add(p.at(k, o, -1 - j) to s) }
        }
        for (k in p.piers) {
            val depth = drop(k)
            for (o in -HALF..HALF) out.column(p, k, o, -1, depth) { f.full }
        }
        return out
    }

    /** 구리 트러스교: 4칸마다 세운 기둥과 위 현(반 블록), 창살 난간, 격자 장식, 머리 위 문형 보, 격자 가새를 댄 교각 */
    private fun copper(p: Plan, f: Family, drop: (Int) -> Int): List<Pair<BlockPos, BlockState>> {
        val out = ArrayList<Pair<BlockPos, BlockState>>()
        val accent = f.accent?.defaultBlockState() ?: f.full
        for (k in p.start..p.end) {
            val i = (k - p.start) % 4
            for (o in -HALF..HALF) out.add(p.at(k, o, 0) to f.full)
            for (o in listOf(-HALF, HALF)) {
                out.add(p.at(k, o, -1) to accent)
                if (i == 0) {
                    for (h in 1..3) out.add(p.at(k, o, h) to f.full)
                } else {
                    out.add(p.at(k, o, 1) to f.rail!!.defaultBlockState())
                    if (i == 2) out.add(p.at(k, o, 2) to accent)
                    out.add(p.at(k, o, 3) to slab(f, SlabType.BOTTOM))
                }
            }
            if (i == 0) for (o in -1..1) out.add(p.at(k, o, 4) to slab(f, SlabType.BOTTOM))
        }
        for (k in p.piers) {
            val depth = drop(k)
            for (o in listOf(-HALF, HALF)) out.column(p, k, o, -2, depth) { f.full }
            for (h in -4 downTo -depth step 4) for (o in -1..1) out.add(p.at(k, o, h) to accent)
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
        for (x in -3..0) for (z in -3..3) level.setBlock(feet.offset(x, -1, z), bank, 3)
        for (x in 10..13) for (z in -3..3) level.setBlock(feet.offset(x, -1, z), bank, 3)
        val fam = family(Kind.STONE, ItemStack(Blocks.STONE_BRICKS)) ?: return "no stone family"
        val p = plan(level, feet, Direction.EAST, Kind.STONE, fam) ?: return "no plan"
        if (!p.landed || p.start != 1 || p.end != 9) return "span ${p.start}..${p.end} landed=${p.landed}"
        if (p.piers.size != 1) return "piers ${p.piers}"
        build(level, p)
        val deck = level.getBlockState(feet.offset(5, -1, 0))
        val wall = level.getBlockState(feet.offset(5, 0, 2))
        if (!deck.`is`(Blocks.STONE_BRICKS) || !wall.`is`(Blocks.STONE_BRICK_WALL)) return "deck=$deck wall=$wall"
        val pier = level.getBlockState(feet.offset(p.piers[0], -10, 0))
        if (!pier.`is`(Blocks.STONE_BRICKS)) return "pier=$pier"
        // 같은 자리에 한 번 더: 이미 다 놓였으므로 필요한 블록 0
        val again = plan(level, feet, Direction.EAST, Kind.STONE, fam)
        if (again != null && again.blocks.isNotEmpty()) return "rebuild needs ${again.blocks.size}"
        return null
    }

    // ───────────── 들고 있을 때 미리보기 ─────────────

    private class Cache(val key: List<Any>, val plan: Plan?, val at: Long)
    private val cache = WeakHashMap<ServerPlayer, Cache>()

    fun tickHeld(player: ServerPlayer) {
        val kind = kind(player.mainHandItem)
        if (kind == null) {
            cache.remove(player); return
        }
        val level = player.level() as ServerLevel
        if (level.gameTime % 4 != 0L) return
        val raw = findRaw(player, kind)
        if (raw == null) {
            Compat.overlay(player, msg("bridge_kit.need_raw.${kind.name.lowercase()}", Palette.ORANGE)); return
        }
        val (rawStack, fam) = raw
        val key = listOf(player.blockPosition(), player.direction, kind, fam.raw)
        val old = cache[player]
        val plan = if (old != null && old.key == key && level.gameTime - old.at < 20) old.plan
        else plan(level, player.blockPosition(), player.direction, kind, fam).also { cache[player] = Cache(key, it, level.gameTime) }
        if (plan == null) {
            Compat.overlay(player, msg("bridge_kit.no_gap", Palette.ORANGE)); return
        }
        val need = plan.blocks.size
        val have = Inventories.count(player) { it.`is`(fam.raw) }
        val enough = player.isCreative || have >= need
        val color = if (enough) Palette.SKY else Palette.RED
        val dust = DustParticleOptions(color, 1.0f)
        fun spark(pos: BlockPos, dy: Double) =
            level.sendParticles(player, dust, true, false, pos.x + 0.5, pos.y + dy, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        // 상판 양쪽 가장자리와 양 끝
        for (k in plan.start..plan.end) for (o in listOf(-HALF, HALF)) spark(plan.at(k, o, 0), 1.05)
        for (o in -HALF + 1 until HALF) {
            spark(plan.at(plan.start, o, 0), 1.05); spark(plan.at(plan.end, o, 0), 1.05)
        }
        // 기둥 자리: 세로 점선
        val pierDust = DustParticleOptions(Palette.YELLOW, 1.2f)
        for (k in plan.piers) for (h in 0 downTo -24 step 2) {
            val pos = plan.at(k, 0, h)
            if (h < 0 && !open(level, pos)) break
            level.sendParticles(player, pierDust, true, false, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        val tail = if (plan.landed) "" else " ↷"
        val snap = if (plan.snapped) msg("bridge_kit.snapped", Palette.GREEN) else null
        val line = msg("bridge_kit.preview", if (enough) Palette.SKY else Palette.RED,
            plan.length.toString() + tail, plan.piers.size.toString(), rawStack.hoverName, need.toString(), have.toString())
        Compat.overlay(player, if (snap != null) snap.append(" ").append(line) else line)
    }
}
