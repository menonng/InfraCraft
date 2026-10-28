package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.block.ScreenDoorBlock
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.compat.Minecart
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
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
    // 규격: 기본은 복선(상행·하행 1선씩). 가운데 줄(o=0)에 서서 쓰고, 선로는 양옆 TRACK_O 칸에 깔린다.
    // Create 전동차(폭 5, 바닥은 선로 +1, 천장은 선로 +4)가 두 선로에서 엇갈려도 닿지 않도록 맞춘다.
    const val TRACK_O = 3          // 선로 두 줄: o = -3, +3 (차량은 각각 o 1..5, -5..-1)
    const val TUNNEL_HALF = 5      // 터널 내부 폭 11 (-5..5)
    const val TUNNEL_HEIGHT = 5    // 터널 내부 높이 5 (0..4)
    private const val HALL_HALF = 8        // 역사 내부 폭 17 (-8..8)
    private const val HALL_HEIGHT = 6      // 역사 천장 높이
    private const val PLATFORM_O = 6       // 상대식 승강장: 안전선(그 위에 안전문)이 놓이는 옆 칸 수
    private val TRACKS = listOf(-TRACK_O, TRACK_O)

    private fun at(base: BlockPos, dir: Direction, side: Direction, k: Int, o: Int, h: Int): BlockPos =
        base.relative(dir, k).relative(side, o).above(h)

    /** 바닐라 선로 또는 Create 트랙 */
    internal fun isTrack(s: BlockState) = BaseRailBlock.isRail(s) || (CreateCompat.trackBlock()?.let { s.block == it } ?: false)

    private fun isLining(s: BlockState) = s.`is`(ModBlocks.TUNNEL_LINING) || s.`is`(ModBlocks.TUNNEL_LIGHT)

    /** 바꿔도 되는 블록인가(기반암·블록 엔티티는 건드리지 않는다) */
    internal fun replaceable(level: ServerLevel, p: BlockPos, s: BlockState): Boolean =
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
        val cost = if (thrift > 0) (needed * (1.0 - (0.15 * thrift).coerceAtMost(0.6))).toInt().coerceAtLeast(1) else needed
        return Inventories.consume(player, cost) { liningItem(it) }
    }

    // ───────────── 실드 굴진기 ─────────────

    private fun sliceDone(level: ServerLevel, c: BlockPos, side: Direction): Boolean {
        for (o in -TUNNEL_HALF..TUNNEL_HALF) for (h in 0 until TUNNEL_HEIGHT) {
            val p = c.relative(side, o).above(h)
            val s = level.getBlockState(p)
            if (!(s.isAir || (o in TRACKS && h == 0 && isTrack(s)))) return false
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
            val ceilingState = if (o in TRACKS && wantLight) ModBlocks.TUNNEL_LIGHT.defaultBlockState() else ModBlocks.TUNNEL_LINING.defaultBlockState()
            shell.add(c.relative(side, o).above(TUNNEL_HEIGHT) to ceilingState)
        }
        for (h in 0 until TUNNEL_HEIGHT) for (o in listOf(-half, half)) shell.add(c.relative(side, o).above(h) to ModBlocks.TUNNEL_LINING.defaultBlockState())
        val todo = shell.filter { (p, want) ->
            val s = level.getBlockState(p)
            s.block != want.block && !isLining(s) && replaceable(level, p, s)
        }
        val liningNeeded = todo.count { it.second.`is`(ModBlocks.TUNNEL_LINING) }
        if (!payLining(player, liningNeeded, tool)) return
        val lights = todo.count { it.second.`is`(ModBlocks.TUNNEL_LIGHT) }
        if (lights > 0) Inventories.consume(player, lights) { it.`is`(ModBlocks.TUNNEL_LIGHT.asItem()) }
        for ((p, want) in todo) level.setBlock(p, want, 3)

        // 2) 내부 굴착(지하수·용암은 라이닝으로 막힌 뒤 비운다)
        var mined = 0
        for (o in -TUNNEL_HALF..TUNNEL_HALF) for (h in 0 until TUNNEL_HEIGHT) {
            val p = c.relative(side, o).above(h)
            val s = level.getBlockState(p)
            if (s.isAir || (o in TRACKS && h == 0 && isTrack(s))) continue
            if (!s.fluidState.isEmpty && s.block is net.minecraft.world.level.block.LiquidBlock) { level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); continue }
            if (!replaceable(level, p, s)) continue
            if (level.destroyBlock(p, true, player)) mined++
        }

        // 3) 양옆 두 줄 선로(상행·하행, Create 트랙 또는 바닐라 선로)
        for (o in TRACKS) {
            val t = c.relative(side, o)
            if (level.getBlockState(t).isAir && !placeTrack(level, t, dir, slope, player)) Compat.overlay(player, msg("subway.no_rails", Palette.ORANGE))
        }
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

    /** 설계도가 Create 트랙으로 역사를 지을지: 설정이 허용하고, 트랙이 32개(16칸 × 두 줄) 이상 있거나 창작 모드 */
    internal fun useCreateTrack(player: ServerPlayer): Block? {
        if (Config.c.subwayTrack == "vanilla" || Config.c.subwayTrack == "none") return null
        val track = CreateCompat.trackBlock() ?: return null
        return if (player.isCreative || Inventories.count(player) { it.`is`(track.asItem()) } >= 32) track else null
    }

    private fun isRailItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return (stack.item as? BlockItem)?.block is BaseRailBlock
    }

    // ───────────── 지하 역사 설계도 ─────────────

    /** 복선 역의 두 선로 역 이름: "역 3 1번선"(오른쪽), "역 3 2번선"(왼쪽) */
    internal fun registerTwin(level: ServerLevel, right: BlockPos, left: BlockPos, base: String): Pair<StationRec, StationRec> {
        val data = Network.data(level.server)
        val name = data.uniqueName(base)
        return Network.registerStation(level, right, "$name 1번선") to Network.registerStation(level, left, "$name 2번선")
    }

    /**
     * 두 선로 사이 가운데 줄에 서서 진행 방향을 보고 사용한다.
     * 길이 16, 폭 19(벽 포함), 높이 7 의 상대식 복선 역사:
     *  - 양옆 두 선로(바닐라면 아래에 역 블록 둘), 선로마다 차량이 지나는 폭 5 는 비워 둔다.
     *  - 바깥 양쪽 승강장은 선로보다 한 칸 높다(차량 바닥과 같은 높이라 턱 없이 탄다).
     *  - 승강장 가장자리에 안전선과 두 칸 높이 안전문, 천장 조명.
     *  - 양쪽 승강장 끝에 역 승강기와 지상까지 뚫린 수직구.
     */
    fun buildStation(player: ServerPlayer, hand: InteractionHand) {
        val level = player.level() as ServerLevel
        val blueprint = player.getItemInHand(hand)
        val dir = player.direction
        val side = dir.clockWise
        val base = player.blockPosition()
        val wall = HALL_HALF + 1
        val elevatorK = 6

        val solids = ArrayList<Pair<BlockPos, BlockState>>()
        val clears = ArrayList<BlockPos>()
        val doors = ArrayList<BlockPos>()
        val lining = ModBlocks.TUNNEL_LINING.defaultBlockState()
        // Create 트랙을 쓰면 역은 Create 기차 정거장(트랙 옆)으로 두므로 InfraCraft 역 블록은 놓지 않는다
        val createTrack = useCreateTrack(player)
        val trackState = createTrack?.let { CreateCompat.trackState(it, dir, 0) } ?: Blocks.RAIL.defaultBlockState()
        for (k in -7..8) for (o in -wall..wall) for (h in -1..HALL_HEIGHT) {
            val p = at(base, dir, side, k, o, h)
            val ao = abs(o)
            // 안전문은 선로 쪽(안쪽)을 바라본다
            val towardTrack = if (o > 0) side.opposite else side
            when {
                h == -1 -> solids.add(p to if (k == 0 && ao == TRACK_O && createTrack == null) ModBlocks.STATION.defaultBlockState() else lining)
                h == HALL_HEIGHT -> when {
                    k == elevatorK && ao == HALL_HALF -> clears.add(p)
                    ao == PLATFORM_O + 1 && Math.floorMod(k, 4) == 0 -> solids.add(p to ModBlocks.TUNNEL_LIGHT.defaultBlockState())
                    ao == TRACK_O && Math.floorMod(k, 4) == 2 -> solids.add(p to ModBlocks.TUNNEL_LIGHT.defaultBlockState())
                    else -> solids.add(p to lining)
                }
                ao == wall -> solids.add(p to lining)
                ao < PLATFORM_O -> when {
                    h == 0 && ao == TRACK_O -> if (!isTrack(level.getBlockState(p))) solids.add(p to trackState)
                    else -> clears.add(p)
                }
                h == 0 -> solids.add(p to lining)
                h == 1 -> solids.add(p to when {
                    ao == PLATFORM_O -> ModBlocks.PLATFORM_EDGE.defaultBlockState()
                    k == elevatorK && ao == HALL_HALF -> ModBlocks.STATION_ELEVATOR.defaultBlockState()
                    else -> lining
                })
                ao == PLATFORM_O && (h == 2 || h == 3) -> {
                    val half = if (h == 2) DoubleBlockHalf.LOWER else DoubleBlockHalf.UPPER
                    solids.add(p to ModBlocks.SCREEN_DOOR.defaultBlockState().setValue(ScreenDoorBlock.FACING, towardTrack).setValue(ScreenDoorBlock.HALF, half))
                    if (h == 2) doors.add(p)
                }
                else -> clears.add(p)
            }
        }
        // 승강기 수직구(양쪽 승강장): 역사 천장 위로 하늘이 보일 때까지
        val elevators = listOf(at(base, dir, side, elevatorK, HALL_HALF, 1), at(base, dir, side, elevatorK, -HALL_HALF, 1))
        var depth = 0
        for (o in listOf(HALL_HALF, -HALL_HALF)) {
            var h = HALL_HEIGHT + 1
            while (h < 200) {
                val p = at(base, dir, side, elevatorK, o, h)
                if (level.canSeeSky(p) && level.getBlockState(p).isAir) break
                clears.add(p)
                for (n in listOf(p.relative(dir), p.relative(dir.opposite), p.relative(side), p.relative(side.opposite))) {
                    val s = level.getBlockState(n)
                    if (s.isAir || !s.fluidState.isEmpty || s.canBeReplaced()) solids.add(n to lining)
                }
                h++
            }
            depth = maxOf(depth, h - 1)
        }

        val todo = solids.filter { (p, want) ->
            val s = level.getBlockState(p)
            s != want && replaceable(level, p, s)
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
        val data = Network.data(level.server)
        val dim = Compat.dimensionId(level)
        for (e in elevators) data.addTicker(TickedBlock.ELEVATOR, dim, e)
        for (d in doors) data.addTicker(TickedBlock.DOOR, dim, d)
        if (!player.isCreative) {
            blueprint.shrink(1)
            if (createTrack != null) {
                val placed = todo.count { it.second.block == createTrack }
                Inventories.consume(player, placed) { it.`is`(createTrack.asItem()) }
            }
        }
        if (createTrack == null) {
            val (a, b) = registerTwin(level, at(base, dir, side, 0, TRACK_O, -1), at(base, dir, side, 0, -TRACK_O, -1), "역 ${data.stations.size + 1}")
            Compat.chat(player, msg("station_hall.built", Palette.GREEN, a.name, b.name, depth.toString()))
        } else Compat.chat(player, msg("station_hall.built_create", Palette.GREEN, depth.toString()))
    }

    // ───────────── 승강장 안전문 ─────────────

    /** 아래 칸 안전문이 위 칸까지 함께 연다. 앞 1~3칸 선로에 멈춘 마인카트나 Create 객차가 있으면 열린다. */
    fun tickDoor(level: ServerLevel, pos: BlockPos, key: String, data: NetworkData) {
        if (level.gameTime % 5 != 0L) return
        val state = level.getBlockState(pos)
        if (state.block !is ScreenDoorBlock) {
            data.tickers[TickedBlock.DOOR]?.remove(key); return
        }
        if (state.getValue(ScreenDoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            val below = level.getBlockState(pos.below())
            if (below.block is ScreenDoorBlock && below.getValue(ScreenDoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                data.tickers[TickedBlock.DOOR]?.remove(key); return
            }
        }
        val facing = state.getValue(ScreenDoorBlock.FACING)
        val a = pos.relative(facing, 1).below(2)
        val b = pos.relative(facing, 3).above(2)
        val box = AABB(minOf(a.x, b.x).toDouble(), a.y.toDouble(), minOf(a.z, b.z).toDouble(),
            maxOf(a.x, b.x) + 1.0, b.y + 1.0, maxOf(a.z, b.z) + 1.0)
        val open = level.getEntities(null as Entity?, box) { isVehicle(it) }.any { e ->
            val dx = e.x - e.xo
            val dz = e.z - e.zo
            dx * dx + dz * dz < 0.0004
        }
        if (state.getValue(ScreenDoorBlock.OPEN) == open) return
        level.setBlock(pos, state.setValue(ScreenDoorBlock.OPEN, open), 3)
        val above = level.getBlockState(pos.above())
        if (above.block is ScreenDoorBlock && above.getValue(ScreenDoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            level.setBlock(pos.above(), above.setValue(ScreenDoorBlock.OPEN, open), 3)
        }
        level.playSound(null, pos, if (open) SoundEvents.IRON_DOOR_OPEN else SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f)
    }

    /** 마인카트 또는 Create 객차(클래스 이름으로만 판단해 Create 가 없어도 된다) */
    private fun isVehicle(e: Entity): Boolean = e is Minecart || e.javaClass.simpleName == "CarriageContraptionEntity"

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
}
