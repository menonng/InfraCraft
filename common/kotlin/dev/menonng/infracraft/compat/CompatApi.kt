package dev.menonng.infracraft.compat

import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * 마인크래프트 버전마다 달라지는 API 를 감싸는 규약.
 *
 * 각 버전 서브프로젝트(versions/<버전>)는 같은 패키지에 다음을 제공한다.
 *  - `typealias Id` (ResourceLocation 또는 Identifier)
 *  - `typealias Minecart`, `BoatEntity`, `HorseEntity` (버전마다 패키지가 다르다)
 *  - 이 인터페이스를 구현하는 `object Compat`
 */
interface CompatApi {
    fun id(namespace: String, path: String): Id
    fun dimensionId(level: Level): String
    fun level(server: MinecraftServer, dimensionId: String): ServerLevel?

    fun chat(player: Player, message: Component)
    fun overlay(player: Player, message: Component)
    fun sendSuccess(source: CommandSourceStack, message: Component)

    fun teleport(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double)
    fun saveEntity(entity: Entity): CompoundTag?
    fun loadEntity(level: ServerLevel, tag: CompoundTag): Entity?
    fun keepChunkLoaded(level: ServerLevel, pos: BlockPos)
    fun customName(stack: ItemStack): String?
    fun keyId(key: net.minecraft.resources.ResourceKey<*>): String
    fun blockById(namespace: String, path: String): Block?
    /** 서버가 바꾼 속도를 클라이언트에 바로 보내도록 표시한다(옛 hurtMarked). */
    fun syncVelocity(entity: Entity)
    fun dropItem(player: Player, stack: ItemStack)
    val playerType: EntityType<out LivingEntity>

    /** 블록과 그 BlockItem 을 함께 등록한다. */
    fun <T : Block> block(name: String, props: BlockBehaviour.Properties, factory: (BlockBehaviour.Properties) -> T): T
    fun item(name: String, props: Item.Properties): Item
    /** 내구도가 있고 인챈트 가능한 1개짜리 도구 속성 */
    fun toolProperties(durability: Int): Item.Properties
    fun creativeTab(name: String, icon: () -> ItemStack, items: () -> List<ItemLike>)

    /** null 을 돌려주면 다음 처리기로 넘긴다(PASS). */
    fun onUseItem(handler: (Player, Level, InteractionHand) -> InteractionResult?)
    fun onLevelTick(handler: (ServerLevel) -> Unit)
    fun onPlayerChangedLevel(handler: (ServerPlayer, ServerLevel, ServerLevel) -> Unit)
    fun halt(server: MinecraftServer)
}
