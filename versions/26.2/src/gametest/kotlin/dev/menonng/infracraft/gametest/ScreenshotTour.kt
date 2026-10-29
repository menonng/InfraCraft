package dev.menonng.infracraft.gametest

import dev.menonng.infracraft.autoName
import dev.menonng.infracraft.client.AstraMapScreen
import dev.menonng.infracraft.content.ModItems
import dev.menonng.infracraft.lineName
import dev.menonng.infracraft.nm
import dev.menonng.infracraft.net.Payloads
import dev.menonng.infracraft.system.Astra
import dev.menonng.infracraft.system.Bridges
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

/**
 * 클라이언트 게임테스트: 실제 게임을 띄워 월드를 만들고, 손으로 확인해야 했던 화면을 스크린샷으로 남긴다.
 *  - 세 다리(낮·밤), 다리 키트 홀로그램, 독수리자리 발전과제 탭, 별빛 좌표 지도, 별빛 검격·낙하, 언어별 역 이름
 * 스크린샷은 run/clientGameTest/screenshots 에 쌓이고, CI 가 아티팩트로 올린다. 화면이 열리다 오류가 나면 테스트가 실패한다.
 */
object ScreenshotTour : FabricClientGameTest {
    override fun runTest(ctx: ClientGameTestContext) {
        language(ctx, "ko_kr")
        ctx.onClient { mc -> mc.options.renderDistance().set(10) }
        ctx.worldBuilder().adjustSettings { it.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE) }
            .create().use { sp -> tour(ctx, sp) }
    }

    private fun language(ctx: ClientGameTestContext, code: String) {
        val done = ctx.fromClient { mc ->
            mc.options.languageCode = code
            mc.languageManager.setSelected(code)
            mc.reloadResourcePacks()
        }
        ctx.waitFor({ done.isDone && it.gui.overlay() == null }, 2400)
        ctx.waitTicks(20)
    }

    // Failable* 의 예외 형식을 Kotlin 이 추론하지 못하므로 감싼다
    private fun ClientGameTestContext.onClient(f: (Minecraft) -> Unit) = runOnClient<RuntimeException> { f(it) }
    private fun <T> ClientGameTestContext.fromClient(f: (Minecraft) -> T): T = computeOnClient<T, RuntimeException> { f(it) }
    private fun TestServerContext.onServer(f: (MinecraftServer) -> Unit) = runOnServer<RuntimeException> { f(it) }
    private fun <T> TestServerContext.fromServer(f: (MinecraftServer) -> T): T = computeOnServer<T, RuntimeException> { f(it) }

    private fun player(server: MinecraftServer): ServerPlayer = server.playerList.players.first()

    private fun cmd(sp: TestSingleplayerContext, c: String) = sp.server.runCommand(c)

    /** 강둑 두 개(높이 10)와 그 사이 강. 반환: 왼쪽 강둑 위(다리를 놓을 때 설 자리) */
    private fun valley(server: MinecraftServer, z: Int): BlockPos {
        val level = server.overworld()
        for (cx in -1..2) for (cz in ((z - 24) shr 4)..((z + 24) shr 4)) level.getChunk(cx, cz)
        val g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 12, z)
        val top = g + 10
        val stone = Blocks.STONE.defaultBlockState()
        val grass = Blocks.GRASS_BLOCK.defaultBlockState()
        for (x in -10..34) for (dz in -10..10) {
            val bank = x <= 0 || x >= 24
            for (y in g..top) {
                val p = BlockPos(x, y, z + dz)
                level.setBlock(p, if (!bank) Blocks.AIR.defaultBlockState() else if (y == top) grass else stone, 2)
            }
            if (!bank) level.setBlock(BlockPos(x, g, z + dz), Blocks.WATER.defaultBlockState(), 2)
        }
        return BlockPos(0, top + 1, z)
    }

    private fun bridge(server: MinecraftServer, feet: BlockPos, kind: Bridges.Kind, raw: Block) {
        val level = server.overworld()
        val fam = Bridges.family(kind, ItemStack(raw)) ?: error("no family $raw")
        val plan = Bridges.plan(level, feet, Direction.EAST, kind, fam) ?: error("no plan $kind")
        Bridges.build(level, plan)
    }

    private fun shot(ctx: ClientGameTestContext, name: String, settle: Int = 60) {
        ctx.waitTicks(settle)
        ctx.takeScreenshot(name)
    }

    private fun hud(ctx: ClientGameTestContext, visible: Boolean) {
        val hidden = ctx.fromClient { mc -> mc.gui.hud.isHidden }
        if (hidden == visible) ctx.input.pressKey { o: net.minecraft.client.Options -> o.keyToggleGui }
    }

    private fun tour(ctx: ClientGameTestContext, sp: TestSingleplayerContext) {
        cmd(sp, "gamerule minecraft:advance_time false")
        cmd(sp, "gamerule minecraft:advance_weather false")
        cmd(sp, "gamerule minecraft:send_command_feedback false")
        cmd(sp, "weather clear")
        cmd(sp, "time set 1000")
        // 독수리자리 발전과제를 먼저 모두 달성(알림이 다리 촬영 동안 사라지도록)
        cmd(sp, "advancement grant @a from infracraft:astra/star_altair")

        // ── 세 다리 ──
        val kinds = listOf(
            Triple(Bridges.Kind.TIMBER, "spruce_planks", 0),
            Triple(Bridges.Kind.STONE, "stone_bricks", 120),
            Triple(Bridges.Kind.COPPER, "cut_copper", 240),
        )
        val feetByKind = HashMap<Bridges.Kind, BlockPos>()
        for ((kind, raw, z) in kinds) sp.server.onServer { s ->
            val feet = valley(s, z)
            bridge(s, feet, kind, BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(raw)))
            feetByKind[kind] = feet
        }
        hud(ctx, false)
        for ((kind, _, z) in kinds) {
            val f = feetByKind.getValue(kind)
            val name = kind.name.lowercase()
            // 옆에서 본 모습(남쪽을 바라봄)과 다리 위에서 본 모습
            cmd(sp, "tp @a 12 ${f.y + 3} ${z - 30} 0 6")
            shot(ctx, "bridge_${name}_side", 120)
            cmd(sp, "tp @a -4 ${f.y + 2} ${z} -90 8")
            shot(ctx, "bridge_${name}_deck")
            cmd(sp, "tp @a 18 ${f.y - 8} ${z - 12} 30 -12")
            shot(ctx, "bridge_${name}_below")
        }
        cmd(sp, "time set 18000")
        for ((kind, _, z) in kinds) {
            val f = feetByKind.getValue(kind)
            cmd(sp, "tp @a 12 ${f.y + 3} ${z - 30} 0 6")
            shot(ctx, "bridge_${kind.name.lowercase()}_night", 80)
        }
        cmd(sp, "time set 1000")

        // ── 다리 키트 홀로그램(손에 들면 놓일 자리가 반투명 블록으로) ──
        val holo = sp.server.fromServer { s -> valley(s, 360) }
        cmd(sp, "tp @a ${holo.x} ${holo.y} ${holo.z} -90 28")
        cmd(sp, "give @a stone_bricks 1024")
        cmd(sp, "item replace entity @a weapon.mainhand with infracraft:stone_bridge_kit")
        hud(ctx, true)
        shot(ctx, "hologram_stone", 80)
        cmd(sp, "item replace entity @a weapon.mainhand with infracraft:copper_bridge_kit")
        cmd(sp, "give @a cut_copper 1024")
        shot(ctx, "hologram_copper", 60)
        cmd(sp, "item replace entity @a weapon.mainhand with air")
        ctx.waitTicks(10)

        // ── 독수리자리 발전과제 탭 ──
        ctx.onClient { mc -> mc.gui.toastManager().clear() }
        ctx.setScreen { AdvancementsScreen(Minecraft.getInstance().connection!!.advancements) }
        ctx.onClient { mc ->
            val adv = mc.connection!!.advancements
            adv.setSelectedTab(adv.get(Identifier.fromNamespaceAndPath("infracraft", "astra/star_altair")), true)
        }
        shot(ctx, "astra_advancements", 30)
        ctx.setScreen { null }

        // ── 별빛 좌표 지도 ──
        cmd(sp, "item replace entity @a weapon.offhand with infracraft:astra_obliterator")
        ctx.setScreen { AstraMapScreen() }
        ctx.input.setCursorPos(0.62 * ctx.fromClient { it.window.screenWidth }, 0.45 * ctx.fromClient { it.window.screenHeight })
        shot(ctx, "astra_map", 30)
        ctx.setScreen { null }

        // ── 아스트라: 모델로 그린 검격·레일건·아홉 별(밤에 찍어 빛이 잘 보이게) ──
        val g0 = sp.server.fromServer { s ->
            val lv = s.overworld()
            for (cx in -4..5) for (cz in 34..40) lv.getChunk(cx, cz)
            lv.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 600)
        }
        cmd(sp, "time set 6000")
        cmd(sp, "tp @a 0 $g0 600 -90 0")
        cmd(sp, "item replace entity @a weapon.offhand with air")
        cmd(sp, "item replace entity @a weapon.mainhand with infracraft:astra_obliterator")
        hud(ctx, true)
        ctx.waitTicks(60)
        sp.server.onServer { s -> Astra.slash(player(s)) }
        shot(ctx, "astra_slash_a", 2)
        shot(ctx, "astra_slash_b", 3)
        ctx.waitTicks(20)
        sp.server.onServer { s -> Astra.slash(player(s)) }
        cmd(sp, "tp @a 7 ${g0 + 2} 591 facing 9 ${g0 + 1} 600")
        shot(ctx, "astra_slash_side", 4)
        ctx.waitTicks(20)
        cmd(sp, "time set 14000")

        // AS-01 별자리 레일건
        cmd(sp, "tp @a 0 $g0 600 -90 -4")
        ctx.waitTicks(10)
        sp.server.onServer { s -> Astra.cast(player(s), player(s).mainHandItem) }
        shot(ctx, "astra_rail_lock", 14)
        shot(ctx, "astra_rail_charge", 22)
        shot(ctx, "astra_rail_fire", 5)
        shot(ctx, "astra_rail_fade", 8)
        ctx.waitTicks(140)  // 쿨다운

        // AS-09 아홉 별: 시네마틱 → 하늘의 투영 → 차례로 떨어짐 → 알타이르 → 흔적
        cmd(sp, "tp @a 0 $g0 600 -90 0")
        cmd(sp, "item replace entity @a weapon.offhand with infracraft:astra_obliterator")
        ctx.waitTicks(5)
        sp.server.onServer { s -> Astra.strike(player(s), Payloads.AstraStrike(Payloads.AstraStrike.VIEW, 40.5, g0.toDouble(), 600.5)) }
        shot(ctx, "astra_nine_cinematic_a", 8)
        shot(ctx, "astra_nine_cinematic_b", 14)
        cmd(sp, "tp @a -12 ${g0 + 16} 552 facing 40 ${g0 + 22} 600")
        shot(ctx, "astra_nine_projection", 16)
        shot(ctx, "astra_nine_first", 12)
        shot(ctx, "astra_nine_falling", 26)
        shot(ctx, "astra_nine_altair_charge", 24)
        shot(ctx, "astra_nine_altair_impact", 6)
        shot(ctx, "astra_nine_altair_dome", 8)
        cmd(sp, "time set 1000")
        cmd(sp, "tp @a 18 ${g0 + 12} 578 facing 40 $g0 600")
        shot(ctx, "astra_nine_aftermath", 60)
        cmd(sp, "tp @a 40.5 ${g0 + 34} 600.5 0 90")
        shot(ctx, "astra_nine_scar_top", 40)

        // ── 역 이름이 플레이어 언어를 따른다 ──
        cmd(sp, "item replace entity @a weapon.mainhand with air")
        fun names(s: MinecraftServer) = player(s).sendSystemMessage(
            nm(autoName("station", 3)).append("  /  ").append(nm(lineName(autoName("station", 3), 1))).append("  /  ")
                .append(nm(autoName("harbor", 2))).append("  /  ").append(nm(lineName("Seoul", 2))))
        sp.server.onServer { s -> names(s) }
        shot(ctx, "names_ko_kr", 10)
        language(ctx, "en_us")
        sp.server.onServer { s -> names(s) }
        shot(ctx, "names_en_us", 10)
        // 아이템이 이 모드 것인지 확인(등록이 깨지면 위 명령이 조용히 실패하므로)
        check(BuiltInRegistries.ITEM.getKey(ModItems.ASTRA_OBLITERATOR).path == "astra_obliterator")
    }
}
