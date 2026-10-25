package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * 지하철 제작 키트: 실드 굴진기(터널 단면), 지하 역사 설계도(승강장 + 승강기 수직구), 역 승강기.
 * 차량은 만들지 않는다. 선로는 Create 트랙을 우선 쓰고(Create Fly 와 Steam 'n' Rails 기차가 달린다), 없으면 바닐라 선로를 쓴다.
 *
 * 좌표 표기: dir = 진행 방향, side = 진행 방향의 오른쪽, o = 옆 칸 수, h = 선로 높이 기준 높이, k = 진행 방향 칸 수.
 */
object Subway {
    private const val TUNNEL_HALF = 2      // 터널 내부 폭 5 (-2..2)
    private const val TUNNEL_HEIGHT = 4    // 터널 내부 높이 4 (0..3)
    private const val HALL_HALF = 4        // 역사 내부 폭 9 (-4..4)
    private const val HALL_HEIGHT = 5      // 역사 천장 높이

    private fun at(base: BlockPos, dir: Direction, side: Direction, k: Int, o: Int, h: Int): BlockPos =
        base.relative(dir, k).relative(side, o).above(h)

    /** 바닐라 선로 또는 Create 트랙 */
    private fun isTrack(s: BlockState) = BaseRailBlock.isRail(s) || (CreateCompat.trackBlock()?.let { s.block == it } ?: false)

    private fun isLining(s: BlockState) = s.`is`(ModBlocks.TUNNEL_LINING) || s.`is`(ModBlocks.TUNNEL_LIGHT)

    /** 바꿔도 되는 블록인가(기반암·블록 엔티티는 건드리지 않는다) */
    private fun replaceable(level: ServerLevel, p: BlockPos, s: BlockState): Boolean =
        s.getDestroySpeed(level, p) >= 0 && level.getBlockEntity(p) == null

    private fun liningItem(stack: ItemStack) = stack.`is`(ModBlocks.TUNNEL_LINING.asItem())

    private fun damage(player: ServerPlayer, tool: ItemStack, hand: InteractionHand) {
        if (!player.isCreative) tool.hurtAndBreak(1, player, if (hand == InteractionHand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND)
    }

    private fun payLining(player: ServerPlayer, needed: Int, tool: ItemStack?): Boolean {
        if (player.isCreative || needed == 0) return true
        val have = Inventories.count(player) { liningItem(it) }
        if (have < needed) {
            Compat.overlay(player, msg("subway.need_lining", Palette.ORANGE, needed.toString(), have.toString()))
            return false
        }
        val thrift = tool?.let { Enchants.level(player.level(), it, ModEnchantments.THRIFT) } ?: 0
        val cost = if (thrift > 0) (needed * (1.0 - 0.15 * thrift)).toInt().coerceAtLeast(1) else needed
        return Inventories.consume(player, cost) { liningItem(it) }
    }

    // ───────────── 실드 굴진기 ─────────────

    private fun sliceDone(level: ServerLevel, c: BlockPos, side: Direction): Boolean {
        for (o in -TUNNEL_HALF..TUNNEL_HALF) for (h in 0 until TUNNEL_HEIGHT) {
            val p = c.relative(side, o).above(h)
            val s = level.getBlockState(p)
            if (!(s.isAir || (o == 0 && h == 0 && isTrack(s)))) return false
        }
        return isLining(level.getBlockState(c.below()))
    }

    fun shield(player: ServerPlayer, hand: InteractionHand) {
        val level = player.level() as ServerLevel
        val tool = player.getItemInHand(hand)
        val dir = player.direction
        val side = dir.clockWise
        // 아래를 보면 내려가는 경사, 위를 보면 올라가는 경사(선로 경사와 같은 1:1)
        val slope = when {
            player.xRot > 40f -> -1
            player.xRot < -40f -> 1
            else -> 0
        }
        val feet = player.blockPosition()
        var c: BlockPos? = null
        for (k in 1..4) {
            val candidate = feet.relative(dir, k).above(slope * k)
            if (!sliceDone(level, candidate, side)) { c = candidate; break }
        }
        if (c == null) {
            Compat.overlay(player, msg("subway.ahead_done", Palette.SKY)); return
        }

        // 1) 껍데기(라이닝) 계획: 바닥·천장·양쪽 벽
        val along = if (dir.axis == Direction.Axis.X) c.x else c.z
        val wantLight = Math.floorMod(along, 8) == 0 && Inventories.find(player) { it.`is`(ModBlocks.TUNNEL_LIGHT.asItem()) } != null
        val shell = ArrayList<Pair<BlockPos, BlockState>>()
        val half = TUNNEL_HALF + 1
        for (o in -half..half) {
            shell.add(c.relative(side, o).below() to ModBlocks.TUNNEL_LINING.defaultBlockState())
            val ceilingState = if (o == 0 && wantLight) ModBlocks.TUNNEL_LIGHT.defaultBlockState() else ModBlocks.TUNNEL_LINING.defaultBlockState()
            shell.add(c.relative(side, o).above(TUNNEL_HEIGHT) to ceilingState)
        }
        for (h in 0 until TUNNEL_HEIGHT) for (o in listOf(-half, half)) shell.add(c.relative(side, o).above(h) to ModBlocks.TUNNEL_LINING.defaultBlockState())
        val todo = shell.filter { (p, want) ->
            val s = level.getBlockState(p)
            s.block != want.block && !isLining(s) && replaceable(level, p, s)
        }
        val liningNeeded = todo.count { it.second.`is`(ModBlocks.TUNNEL_LINING) }
        if (!payLining(player, liningNeeded, tool)) return
        if (wantLight && todo.any { it.second.`is`(ModBlocks.TUNNEL_LIGHT) }) Inventories.consume(player, 1) { it.`is`(ModBlocks.TUNNEL_LIGHT.asItem()) }
        for ((p, want) in todo) level.setBlock(p, want, 3)

        // 2) 내부 굴착(지하수·용암은 라이닝으로 막힌 뒤 비운다)
        var mined = 0
        for (o in -TUNNEL_HALF..TUNNEL_HALF) for (h in 0 until TUNNEL_HEIGHT) {
            val p = c.relative(side, o).above(h)
            val s = level.getBlockState(p)
            if (s.isAir || (o == 0 && h == 0 && isTrack(s))) continue
            if (!s.fluidState.isEmpty && s.block is net.minecraft.world.level.block.LiquidBlock) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); continue }
            if (!replaceable(level, p, s)) continue
            if (level.destroyBlock(p, true, player)) mined++
        }

        // 3) 바닥 가운데 선로(Create 트랙 또는 바닐라 선로)
        if (level.getBlockState(c).isAir && !placeTrack(level, c, dir, slope, player)) Compat.overlay(player, msg("subway.no_rails", Palette.ORANGE))
        if (mined > 0) player.awardStat(ModStats.BLOCKS_BORED, mined)
        damage(player, tool, hand)
    }

    /** 설정과 인벤토리에 따라 Create 트랙을 우선, 없으면 바닐라 선로를 놓는다. */
    private fun placeTrack(level: ServerLevel, pos: BlockPos, dir: Direction, slope: Int, player: ServerPlayer): Boolean {
        val mode = Config.c.subwayTrack
        if (mode == "none") return true
        if (mode != "vanilla") {
            val track = CreateCompat.trackBlock()
            if (track != null) {
                val stack = Inventories.find(player) { it.`is`(track.asItem()) }
                if (stack != null || player.isCreative) {
                    level.setBlock(pos, CreateCompat.trackState(track, dir, slope), 3)
                    if (!player.isCreative) stack?.shrink(1)
                    player.awardStat(ModStats.TRACK_PLACED, 1)
                    return true
                }
            }
            if (mode == "create") return false
        }
        val off = player.offhandItem
        val railStack = (if (isRailItem(off)) off else Inventories.find(player) { isRailItem(it) }) ?: return false
        level.setBlock(pos, (railStack.item as BlockItem).block.defaultBlockState(), 3)
        if (!player.isCreative) railStack.shrink(1)
        player.awardStat(ModStats.TRACK_PLACED, 1)
        return true
    }

    /** 설계도가 Create 트랙으로 역사를 지을지: 설정이 허용하고, 트랙이 16개 이상 있거나 창작 모드 */
    private fun useCreateTrack(player: ServerPlayer): Block? {
        if (Config.c.subwayTrack == "vanilla" || Config.c.subwayTrack == "none") return null
        val track = CreateCompat.trackBlock() ?: return null
        return if (player.isCreative || Inventories.count(player) { it.`is`(track.asItem()) } >= 16) track else null
    }

    private fun isRailItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return (stack.item as? BlockItem)?.block is BaseRailBlock
    }

    // ───────────── 지하 역사 설계도 ─────────────

    /**
     * 선로 위에 서서 진행 방향을 보고 사용한다.
     * 길이 16, 폭 11(벽 포함), 높이 6 의 역사: 가운데 선로와 역 블록, 양쪽 승강장(안전선),
     * 천장 조명, 그리고 승강장 끝의 역 승강기와 지상까지 뚫린 수직구.
     */
    fun buildStation(player: ServerPlayer, hand: InteractionHand) {
        val level = player.level() as ServerLevel
        val blueprint = player.getItemInHand(hand)
        val dir = player.direction
        val side = dir.clockWise
        val base = player.blockPosition()
        val wall = HALL_HALF + 1
        val elevatorK = 6
        val elevatorO = HALL_HALF

        val solids = ArrayList<Pair<BlockPos, BlockState>>()
        val clears = ArrayList<BlockPos>()
        val lining = ModBlocks.TUNNEL_LINING.defaultBlockState()
        // Create 트랙을 쓰면 역은 Create 기차역(트랙 옆)으로 두므로 InfraCraft 역 블록은 놓지 않는다
        val createTrack = useCreateTrack(player)
        val trackState = createTrack?.let { CreateCompat.trackState(it, dir, 0) } ?: Blocks.RAIL.defaultBlockState()
        for (k in -7..8) for (o in -wall..wall) for (h in -1..HALL_HEIGHT) {
            val p = at(base, dir, side, k, o, h)
            val ao = abs(o)
            when {
                h == -1 -> solids.add(p to if (k == 0 && o == 0 && createTrack == null) ModBlocks.STATION.defaultBlockState() else lining)
                h == HALL_HEIGHT -> when {
                    k == elevatorK && o == elevatorO -> clears.add(p)
                    ao == 3 && Math.floorMod(k, 4) == 0 -> solids.add(p to ModBlocks.TUNNEL_LIGHT.defaultBlockState())
                    else -> solids.add(p to lining)
                }
                ao == wall -> solids.add(p to lining)
                h == 0 -> when (ao) {
                    0 -> {
                        val cur = level.getBlockState(p)
                        if (!isTrack(cur)) solids.add(p to trackState)
                    }
                    1 -> clears.add(p)
                    2 -> solids.add(p to ModBlocks.PLATFORM_EDGE.defaultBlockState())
                    else -> solids.add(p to if (k == elevatorK && o == elevatorO) ModBlocks.STATION_ELEVATOR.defaultBlockState() else lining)
                }
                else -> clears.add(p)
            }
        }
        // 승강기 수직구: 역사 천장 위로 하늘이 보일 때까지
        val elevatorPos = at(base, dir, side, elevatorK, elevatorO, 0)
        var y = HALL_HEIGHT + 1
        while (y < 200) {
            val p = elevatorPos.above(y)
            if (level.canSeeSky(p) && level.getBlockState(p).isAir) break
            clears.add(p)
            for (n in listOf(p.relative(dir), p.relative(dir.opposite), p.relative(side), p.relative(side.opposite))) {
                val s = level.getBlockState(n)
                if (s.isAir || !s.fluidState.isEmpty || s.canBeReplaced()) solids.add(n to lining)
            }
            y++
        }

        val todo = solids.filter { (p, want) ->
            val s = level.getBlockState(p)
            s.block != want.block && replaceable(level, p, s)
        }
        val liningNeeded = todo.count { it.second.`is`(ModBlocks.TUNNEL_LINING) }
        if (!payLining(player, liningNeeded, null)) {
            Compat.chat(player, msg("station_hall.need_lining", Palette.ORANGE, liningNeeded.toString())); return
        }
        for ((p, want) in todo) level.setBlock(p, want, 3)
        for (p in clears) {
            val s = level.getBlockState(p)
            if (s.isAir || !replaceable(level, p, s)) continue
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3)
        }
        Network.data(level.server).addTicker(TickedBlock.ELEVATOR, Compat.dimensionId(level), elevatorPos)
        if (!player.isCreative) {
            blueprint.shrink(1)
            if (createTrack != null) {
                val placed = todo.count { it.second.block == createTrack }
                Inventories.consume(player, placed) { it.`is`(createTrack.asItem()) }
            }
        }
        if (createTrack == null) {
            val st = Network.registerStation(level, at(base, dir, side, 0, 0, -1), null)
            Compat.chat(player, msg("station_hall.built", Palette.GREEN, st.name, (y - 1).toString()))
        } else Compat.chat(player, msg("station_hall.built_create", Palette.GREEN, (y - 1).toString()))
    }

    // ───────────── 역 승강기 ─────────────

    /** 승강기 위 빈 기둥에 있는 플레이어를 올린다(웅크리면 내린다). 하늘이 보이는 칸이 꼭대기다. */
    fun tickElevator(level: ServerLevel, pos: BlockPos, key: String, data: NetworkData) {
        if (!level.getBlockState(pos).`is`(ModBlocks.STATION_ELEVATOR)) {
            data.tickers[TickedBlock.ELEVATOR]?.remove(key); return
        }
        var top = pos.y
        for (dy in 1..Config.c.elevatorMaxHeight) {
            val p = pos.above(dy)
            if (!level.getBlockState(p).isAir) break
            top = p.y
            if (level.canSeeSky(p)) break
        }
        if (top == pos.y) return
        val box = AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, top + 1.0, pos.z + 1.0)
        for (p in level.getEntitiesOfClass(Player::class.java, box)) {
            val v = p.deltaMovement
            val vy = when {
                p.isShiftKeyDown -> -Config.c.elevatorSpeed
                p.y < top - 0.1 -> Config.c.elevatorSpeed
                else -> 0.08 // 꼭대기에서 떠 있게
            }
            p.deltaMovement = Vec3(v.x * 0.5, vy, v.z * 0.5)
            p.resetFallDistance()
            Compat.syncVelocity(p)
        }
    }

    // ───────────── 가이드 ─────────────

    const val GUIDE_LINES = 10
}
