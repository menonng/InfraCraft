package dev.menonng.infracraft.compat

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.util.ProblemReporter
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.Vec3

// Minecraft 1.21.11 (Mojang 매핑)
typealias Id = Identifier
typealias Minecart = net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
typealias BoatEntity = net.minecraft.world.entity.vehicle.boat.AbstractBoat
typealias HorseEntity = net.minecraft.world.entity.animal.equine.AbstractHorse

object Compat : CompatApi {
    override fun id(namespace: String, path: String): Id = Identifier.fromNamespaceAndPath(namespace, path)

    override fun dimensionId(level: Level): String = level.dimension().identifier().toString()

    override fun level(server: MinecraftServer, dimensionId: String): ServerLevel? =
        Identifier.tryParse(dimensionId)?.let { server.getLevel(ResourceKey.create(Registries.DIMENSION, it)) }

    override fun chat(player: Player, message: Component) {
        (player as? ServerPlayer)?.sendSystemMessage(message)
    }
    override fun overlay(player: Player, message: Component) = player.displayClientMessage(message, true)
    override fun sendSuccess(source: CommandSourceStack, message: Component) = source.sendSuccess({ message }, false)

    override fun teleport(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double) {
        player.teleport(TeleportTransition(level, Vec3(x, y, z), Vec3.ZERO, player.yRot, player.xRot, TeleportTransition.DO_NOTHING))
    }

    override fun saveEntity(entity: Entity): CompoundTag? {
        val out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess())
        return if (entity.save(out)) out.buildResult() else null
    }

    override fun loadEntity(level: ServerLevel, tag: CompoundTag): Entity? =
        EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level, EntitySpawnReason.LOAD).orElse(null)

    override fun keepChunkLoaded(level: ServerLevel, pos: BlockPos) {
        level.chunkSource.addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos(pos), 2)
    }

    override fun syncVelocity(entity: Entity) {
        entity.hurtMarked = true
    }

    override fun dropItem(player: Player, stack: ItemStack) {
        player.drop(stack, false)
    }

    override val playerType: EntityType<out net.minecraft.world.entity.LivingEntity> get() = EntityType.PLAYER

    override fun customName(stack: ItemStack): String? = stack.get(DataComponents.CUSTOM_NAME)?.string

    override fun <T : Block> block(name: String, props: BlockBehaviour.Properties, factory: (BlockBehaviour.Properties) -> T): T {
        val key = id("infracraft", name)
        val block = Registry.register(BuiltInRegistries.BLOCK, key, factory(props.setId(ResourceKey.create(Registries.BLOCK, key))))
        Registry.register(
            BuiltInRegistries.ITEM, key,
            BlockItem(block, Item.Properties().setId(ResourceKey.create(Registries.ITEM, key)).useBlockDescriptionPrefix()),
        )
        return block
    }

    override fun item(name: String, props: Item.Properties): Item {
        val key = id("infracraft", name)
        return Registry.register(BuiltInRegistries.ITEM, key, Item(props.setId(ResourceKey.create(Registries.ITEM, key))))
    }

    override fun toolProperties(durability: Int): Item.Properties = Item.Properties().durability(durability).enchantable(10)

    override fun creativeTab(name: String, icon: () -> ItemStack, items: () -> List<ItemLike>) {
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB, id("infracraft", name),
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.infracraft.$name"))
                .icon(icon)
                .displayItems { _, output -> items().forEach { output.accept(it) } }
                .build(),
        )
    }

    override fun onUseItem(handler: (Player, Level, InteractionHand) -> InteractionResult?) {
        UseItemCallback.EVENT.register { player, level, hand -> handler(player, level, hand) ?: InteractionResult.PASS }
    }

    override fun onLevelTick(handler: (ServerLevel) -> Unit) {
        ServerTickEvents.END_WORLD_TICK.register { handler(it) }
    }

    override fun onPlayerChangedLevel(handler: (ServerPlayer, ServerLevel, ServerLevel) -> Unit) {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, origin, destination -> handler(player, origin, destination) }
    }

    override fun halt(server: MinecraftServer) = server.halt(false)
}
