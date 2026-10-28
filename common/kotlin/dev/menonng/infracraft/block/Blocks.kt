package dev.menonng.infracraft.block

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.system.Network
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.PoweredRailBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

enum class RailTier {
    HIGH_SPEED, EXPRESS, BLAZE;

    fun maxSpeed(): Double = when (this) {
        HIGH_SPEED -> Config.c.highSpeedRailMax
        EXPRESS -> Config.c.expressRailMax
        BLAZE -> Config.c.blazeRailMax
    }
}

/**
 * 고속·특급·블레이즈 선로. 전원 전달과 모양 변경은 바닐라 전동 레일을 그대로 쓰고,
 * 가속·감속과 최고 속도는 RailSystem 이 처리한다(바닐라 가속은 Blocks.POWERED_RAIL 에만 걸려 있다).
 */
class TierRailBlock(props: BlockBehaviour.Properties, val tier: RailTier) : PoweredRailBlock(props)

/** 레벨 틱마다 처리할 위치를 노선망 데이터에 등록하는 블록 */
open class TickedBlock(props: BlockBehaviour.Properties, val kind: String) : Block(props) {
    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level is ServerLevel) Network.data(level.server).addTicker(kind, Compat.dimensionId(level), pos)
    }

    companion object {
        const val SWITCH = "switch"
        const val CURRENT = "current"
        const val LIFT = "lift"
        const val ELEVATOR = "elevator"
        const val DOOR = "door"
    }
}

/** 역(선로 아래·물속에 두면 정차역/항구, 포탈 옆에 두면 포탈 역) */
class StationBlock(props: BlockBehaviour.Properties) : Block(props) {
    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level is ServerLevel) Network.registerStation(level, pos, Compat.customName(stack))
    }
}

/** 분기기: 지나가는 마인카트의 목적지가 등록돼 있으면 레드스톤 신호를 내서 위 선로를 전환한다. */
class RailSwitchBlock(props: BlockBehaviour.Properties) : TickedBlock(props, SWITCH) {
    init {
        registerDefaultState(stateDefinition.any().setValue(POWERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(POWERED)
    }

    override fun isSignalSource(state: BlockState): Boolean = true

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        if (state.getValue(POWERED)) 15 else 0

    companion object {
        val POWERED = BlockStateProperties.POWERED
    }
}

/** 물살 블록: 바로 위 물에 있는 보트·엔티티를 바라보는 방향으로 민다. */
class CurrentBlock(props: BlockBehaviour.Properties) : TickedBlock(props, CURRENT) {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? =
        defaultBlockState().setValue(FACING, context.horizontalDirection)

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
    }
}

/**
 * 승강장 안전문: 승강장 가장자리에 두 칸 높이로 세우는 유리문. FACING 은 선로 쪽.
 * 바로 앞 선로에 멈춘 마인카트나 Create 객차가 있으면 열린다(열리면 양쪽 기둥만 남아 지나갈 수 있다).
 * 아래 칸 위에 놓으면 저절로 윗부분이 된다.
 */
class ScreenDoorBlock(props: BlockBehaviour.Properties) : TickedBlock(props, DOOR) {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(HALF, DoubleBlockHalf.LOWER))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, OPEN, HALF)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val below = context.level.getBlockState(context.clickedPos.below())
        val half = if (below.block is ScreenDoorBlock && below.getValue(HALF) == DoubleBlockHalf.LOWER) DoubleBlockHalf.UPPER else DoubleBlockHalf.LOWER
        val facing = if (half == DoubleBlockHalf.UPPER) below.getValue(FACING) else context.horizontalDirection
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, half)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES[state.getValue(FACING)]!![if (state.getValue(OPEN)) 1 else 0]

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val OPEN = BlockStateProperties.OPEN
        val HALF = BlockStateProperties.DOUBLE_BLOCK_HALF

        /** 방향별 [닫힘(판), 열림(양쪽 기둥)] */
        private val SHAPES: Map<Direction, Array<VoxelShape>> = mapOf(
            Direction.NORTH to arrayOf(Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0), Shapes.or(Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 2.0), Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 2.0))),
            Direction.SOUTH to arrayOf(Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0), Shapes.or(Block.box(0.0, 0.0, 14.0, 2.0, 16.0, 16.0), Block.box(14.0, 0.0, 14.0, 16.0, 16.0, 16.0))),
            Direction.WEST to arrayOf(Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0), Shapes.or(Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 2.0), Block.box(0.0, 0.0, 14.0, 2.0, 16.0, 16.0))),
            Direction.EAST to arrayOf(Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0), Shapes.or(Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 2.0), Block.box(14.0, 0.0, 14.0, 16.0, 16.0, 16.0))),
        )
    }
}
