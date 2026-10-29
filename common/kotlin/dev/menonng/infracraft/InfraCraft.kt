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
import dev.menonng.infracraft.system.Bridges
import dev.menonng.infracraft.system.Astra
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
        dev.menonng.infracraft.net.Payloads.register()
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.menonng.infracraft.net.Payloads.AstraSlash.TYPE) { _, ctx ->
            dev.menonng.infracraft.system.Astra.slash(ctx.player())
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.menonng.infracraft.net.Payloads.AstraStrike.TYPE) { p, ctx ->
            dev.menonng.infracraft.system.Astra.strike(ctx.player(), p)
        }
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
                dev.menonng.infracraft.system.Explored.tick(player)
                if (server.tickCount % 40 == 0) ElytraBan.sweep(player)
            }
            CreateCompat.tick(server)
            Network.tickChimes(server)
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
            fun blk(path: String) = Compat.blockById("minecraft", path)
            fun fam(kind: Bridges.Kind, path: String) = blk(path)?.let { Bridges.family(kind, net.minecraft.world.item.ItemStack(it)) }
            val timber = fam(Bridges.Kind.TIMBER, "spruce_planks")
            check("bridge timber family", timber?.rail == blk("spruce_fence") && timber?.post == blk("spruce_log"))
            check("bridge crimson family", fam(Bridges.Kind.TIMBER, "crimson_planks")?.post == blk("crimson_stem"))
            val stone = fam(Bridges.Kind.STONE, "stone_bricks")
            check("bridge stone family", stone?.rail == blk("stone_brick_wall") && stone?.stairs == blk("stone_brick_stairs"))
            check("bridge tiles family", fam(Bridges.Kind.STONE, "deepslate_tiles")?.rail == blk("deepslate_tile_wall"))
            check("bridge cobble family", fam(Bridges.Kind.STONE, "cobblestone") != null && fam(Bridges.Kind.STONE, "stone") == null)
            val copper = fam(Bridges.Kind.COPPER, "weathered_cut_copper")
            check("bridge copper family", copper != null && copper.slab == blk("weathered_cut_copper_slab") && copper.accent == blk("weathered_copper_grate"))
            check("bridge copper bars (${copper?.rail})", copper?.rail != null)
            check("bridge wrong kit refuses", fam(Bridges.Kind.COPPER, "oak_planks") == null)
            val feet = net.minecraft.core.BlockPos(0, 300, 64)
            overworld.getChunk(0, 4)
            val err = Bridges.selfTest(overworld, feet)
            check("stone bridge built in world (${err ?: "ok"})", err == null)
            for (adv in listOf("root", "first_bridge", "first_station", "road_1km", "surveyor", "tunneler", "rail_layer", "rider"))
                check("advancement $adv", server.advancements.get(Compat.id("infracraft", "journey/$adv")) != null)
            for (adv in Astra.STARS.map { "star_${it.key}" } + listOf("aquila_core", "astra_obliterator"))
                check("advancement astra/$adv", server.advancements.get(Compat.id("infracraft", "astra/$adv")) != null)
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
