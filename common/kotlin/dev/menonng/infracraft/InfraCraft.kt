package dev.menonng.infracraft

import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.compat.BoatEntity
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.compat.Minecart
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModAttributes
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModComponents
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.content.ModEnchantments
import dev.menonng.infracraft.content.ModTags
import dev.menonng.infracraft.system.Enchants
import dev.menonng.infracraft.system.CreateCompat
import dev.menonng.infracraft.system.ElytraBan
import dev.menonng.infracraft.system.Interactions
import dev.menonng.infracraft.system.Network
import dev.menonng.infracraft.system.NetworkData
import dev.menonng.infracraft.system.RailSystem
import dev.menonng.infracraft.system.RoadSystem
import dev.menonng.infracraft.system.Tools
import dev.menonng.infracraft.system.Water
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.dimension.DimensionType
import org.slf4j.LoggerFactory

object InfraCraft : ModInitializer {
    val LOGGER = LoggerFactory.getLogger("infracraft")!!

    override fun onInitialize() {
        Config.load()
        ModComponents.init()
        ModAttachments.init()
        ModBlocks.init()
        ModItems.init()
        ModStats.init()
        ModAttributes.init()
        FabricDefaultAttributeRegistry.register(Compat.playerType, Player.createAttributes().add(ModAttributes.ROAD_AFFINITY))

        CreateCompat.init()
        dev.menonng.infracraft.system.Loot.init()

        EntityElytraEvents.ALLOW.register { !Config.c.banElytra }

        Compat.onUseItem { player, _, hand -> if (player.isSpectator) null else Interactions.useItem(player, hand) }
        UseBlockCallback.EVENT.register { player, _, hand, hit ->
            if (player.isSpectator) InteractionResult.PASS else Interactions.useBlock(player, hand, hit) ?: InteractionResult.PASS
        }
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (player.isSpectator) InteractionResult.PASS else Interactions.useEntity(player, hand, entity) ?: InteractionResult.PASS
        }
        PlayerBlockBreakEvents.AFTER.register { level, _, pos, state, _ ->
            if (level is ServerLevel) Interactions.afterBreak(level, pos, BaseRailBlock.isRail(state), state.`is`(ModBlocks.STABLE))
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerList.players) {
                RoadSystem.tick(player)
                Tools.tick(player)
                Network.rememberPosition(player)
                Network.tickMilestone(player)
                dev.menonng.infracraft.system.Astra.tickHeld(player)
                dev.menonng.infracraft.system.Bridges.tickHeld(player)
                if (server.tickCount % 40 == 0) ElytraBan.sweep(player)
            }
            CreateCompat.tick(server)
            dev.menonng.infracraft.system.Astra.tick(server)
        }
        Compat.onLevelTick { level -> tickLevel(level) }
        Compat.onPlayerChangedLevel { player, origin, destination -> Network.onChangedLevel(player, origin, destination) }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val overworld = server.overworld()
            val nether = Compat.level(server, "minecraft:the_nether")
            if (nether != null) {
                val scale = DimensionType.getTeleportationScale(overworld.dimensionType(), nether.dimensionType())
                if (Config.c.netherOneToOne && scale != 1.0) LOGGER.warn("[InfraCraft] 네더 1:1 믹스인이 적용되지 않았습니다 (배율 {})", scale)
            }
            if (System.getProperty("infracraft.smoke") == "true") smokeTest(server)
        }
        LOGGER.info("[InfraCraft] 교통 인프라 모드 로드됨")
    }

    /** CI 에서 서버를 띄운 뒤 핵심 기능이 실제로 걸렸는지 확인하고 종료한다. */
    private fun smokeTest(server: net.minecraft.server.MinecraftServer) {
        val overworld = server.overworld()
        val failures = ArrayList<String>()
        fun check(name: String, ok: Boolean) {
            LOGGER.info("[InfraCraft] SMOKE {} {}", if (ok) "OK  " else "FAIL", name)
            if (!ok) failures.add(name)
        }
        for (key in listOf(ModEnchantments.ROADRUNNER, ModEnchantments.MOMENTUM, ModEnchantments.EQUESTRIAN, ModEnchantments.CONDUCTOR,
            ModEnchantments.WIDE_PAVING, ModEnchantments.BORE_RADIUS, ModEnchantments.THRIFT)) {
            check("enchantment ${key.toString()}", Enchants.holder(overworld, key) != null)
        }
        val soulSpeed = Enchants.holder(overworld, net.minecraft.world.item.enchantment.Enchantments.SOUL_SPEED)
        check("soul_speed max level 5 (mixin)", soulSpeed?.value()?.maxLevel == 5)
        val nether = Compat.level(server, "minecraft:the_nether")
        check("nether 1:1 (mixin)", nether == null || DimensionType.getTeleportationScale(overworld.dimensionType(), nether.dimensionType()) == 1.0)
        check("road tag", net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState().`is`(ModTags.ROAD_TIER_2))
        check("asphalt tier 3", ModBlocks.ASPHALT.defaultBlockState().`is`(ModTags.ROAD_TIER_3))
        check("rails tag", ModBlocks.EXPRESS_RAIL.defaultBlockState().`is`(net.minecraft.tags.BlockTags.RAILS))
        check("network attachment", Network.data(server).stations.isEmpty())
        check("screen door states", ModBlocks.SCREEN_DOOR.stateDefinition.possibleStates.size == 16)
        run {
            // 다리 키트 원재료 묶음: 이름 규칙으로 찾은 블록이 실제로 있는지
            val B = dev.menonng.infracraft.system.Bridges
            fun fam(kind: dev.menonng.infracraft.system.Bridges.Kind, item: net.minecraft.world.item.Item) =
                B.family(kind, net.minecraft.world.item.ItemStack(item))
            val timber = fam(B.Kind.TIMBER, net.minecraft.world.item.Items.SPRUCE_PLANKS)
            check("bridge timber family", timber?.rail == net.minecraft.world.level.block.Blocks.SPRUCE_FENCE && timber.post == net.minecraft.world.level.block.Blocks.SPRUCE_LOG)
            check("bridge crimson family", fam(B.Kind.TIMBER, net.minecraft.world.item.Items.CRIMSON_PLANKS)?.post == net.minecraft.world.level.block.Blocks.CRIMSON_STEM)
            val stone = fam(B.Kind.STONE, net.minecraft.world.item.Items.STONE_BRICKS)
            check("bridge stone family", stone?.rail == net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL && stone.stairs == net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS)
            check("bridge tiles family", fam(B.Kind.STONE, net.minecraft.world.item.Items.DEEPSLATE_TILES)?.rail == net.minecraft.world.level.block.Blocks.DEEPSLATE_TILE_WALL)
            check("bridge cobble family", fam(B.Kind.STONE, net.minecraft.world.item.Items.COBBLESTONE) != null && fam(B.Kind.STONE, net.minecraft.world.item.Items.STONE) == null)
            val copper = fam(B.Kind.COPPER, net.minecraft.world.item.Items.WEATHERED_CUT_COPPER)
            check("bridge copper family", copper?.slab == net.minecraft.world.level.block.Blocks.WEATHERED_CUT_COPPER_SLAB &&
                copper.accent == net.minecraft.world.level.block.Blocks.WEATHERED_COPPER_GRATE)
            check("bridge wrong kit refuses", fam(B.Kind.COPPER, net.minecraft.world.item.Items.OAK_PLANKS) == null)
            val feet = net.minecraft.core.BlockPos(0, 300, 64)
            overworld.getChunk(0, 4)
            val err = B.selfTest(overworld, feet)
            check("stone bridge built in world (${err ?: "ok"})", err == null)
        }
        for (s in listOf("end_crystal", "beacon", "netherite_block", "totem_of_undying", "dragon_head", "shulker_shell", "sculk_catalyst",
            "amethyst_cluster", "recovery_compass", "breeze_rod", "ominous_trial_key", "heavy_core", "prismarine_crystals",
            "enchanted_golden_apple", "ghast_tear", "crying_obsidian", "lodestone", "nether_star", "netherite_sword")) {
            check("astra ingredient minecraft:$s", net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Compat.id("minecraft", s)))
        }
        if (System.getenv("INFRACRAFT_SMOKE_CREATE") == "true") {
            check("create loaded", CreateCompat.loaded)
            check("create train API (reflection)", CreateCompat.selfCheck())
            val track = CreateCompat.trackBlock()
            check("create car blocks & glue: ${CreateCompat.carCheck()}", CreateCompat.carCheck().isEmpty())
            val carBase = net.minecraft.core.BlockPos(0, 290, 0)
            overworld.getChunk(0, 0)
            val carError = dev.menonng.infracraft.system.CreateCars.selfTest(overworld, carBase)
            check("create metro car built in world (${carError ?: "ok"})", carError == null)
            check("create track shape", track != null &&
                CreateCompat.trackState(track, net.minecraft.core.Direction.EAST, 0).toString().contains("shape=xo") &&
                CreateCompat.trackState(track, net.minecraft.core.Direction.NORTH, 1).toString().contains("shape=an"))
        }
        if (System.getenv("INFRACRAFT_SMOKE_RAILWAYS") == "true") {
            check("steam 'n' rails loaded", net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("railways"))
        }
        if (failures.isEmpty()) LOGGER.info("[InfraCraft] smoke test passed")
        else LOGGER.error("[InfraCraft] smoke test FAILED: {}", failures)
        Compat.halt(server)
    }

    private fun tickLevel(level: ServerLevel) {
        for (cart in level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Minecart::class.java)) { true }) {
            RailSystem.tick(level, cart)
        }
        for (boat in level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(BoatEntity::class.java)) { true }) {
            Water.tickBoat(level, boat)
        }
        val data = Network.data(level.server)
        val dim = Compat.dimensionId(level)
        for (kind in listOf(TickedBlock.SWITCH, TickedBlock.CURRENT, TickedBlock.LIFT, TickedBlock.ELEVATOR, TickedBlock.DOOR)) {
            if (kind == TickedBlock.SWITCH && level.gameTime % 2 != 0L) continue
            val keys = data.tickers[kind] ?: continue
            for (key in keys.toList()) {
                val (d, pos) = NetworkData.parse(key) ?: continue
                if (d != dim || !level.isLoaded(pos)) continue
                when (kind) {
                    TickedBlock.SWITCH -> RailSystem.tickSwitch(level, pos, key, data)
                    TickedBlock.CURRENT -> Water.tickCurrent(level, pos, key, data)
                    TickedBlock.LIFT -> Water.tickLift(level, pos, key, data)
                    TickedBlock.ELEVATOR -> dev.menonng.infracraft.system.Subway.tickElevator(level, pos, key, data)
                    TickedBlock.DOOR -> dev.menonng.infracraft.system.Subway.tickDoor(level, pos, key, data)
                }
            }
        }
    }
}
