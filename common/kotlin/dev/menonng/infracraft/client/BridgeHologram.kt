package dev.menonng.infracraft.client

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.net.Payloads
import com.mojang.blaze3d.vertex.QuadInstance
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * 다리 키트 미리보기: 리터매티카처럼 놓일 블록을 실제 블록 모양 그대로 반투명하게 그린다.
 * 색은 원래 미리보기와 같다: 재료가 충분하면 하늘색(#39C5BB), 모자라면 빨강(#FF0045), 기둥은 노랑(#FFCC11).
 * 블록 텍스처가 보이도록 흰색과 반쯤 섞은 색을 곱하고, 은은하게 숨쉬듯 투명도가 오르내린다.
 */
object BridgeHologram {
    private class Ghost(val pos: BlockPos, val state: BlockState, val pier: Boolean)

    @Volatile private var ghosts: List<Ghost> = emptyList()
    @Volatile private var enough = true
    private val quadCache = HashMap<BlockState, Map<Direction?, List<BakedQuad>>>()

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.BridgePreview.TYPE) { p, _ ->
            // 새 계획마다 모델을 다시 읽는다(리소스 팩을 다시 불러오면 아틀라스 좌표가 바뀌므로)
            quadCache.clear()
            ghosts = p.ghosts.map { Ghost(it.pos, Block.stateById(it.state), it.role == 1) }
            enough = p.enough
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ghosts = emptyList(); quadCache.clear() }
        LevelRenderEvents.COLLECT_SUBMITS.register { ctx -> render(ctx) }
    }

    private fun tint(base: Int, alpha: Int): Int {
        fun ch(s: Int) = (255 + ((base shr s) and 0xFF)) / 2
        return (alpha shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun quads(state: BlockState): Map<Direction?, List<BakedQuad>> = quadCache.getOrPut(state) {
        val model = Minecraft.getInstance().modelManager.blockStateModelSet.get(state)
        val parts = ArrayList<BlockStateModelPart>()
        model.collectParts(RandomSource.create(42L), parts)
        val out = HashMap<Direction?, List<BakedQuad>>()
        for (d in Direction.entries + listOf(null)) out[d] = parts.flatMap { it.getQuads(d) }
        out
    }

    private fun render(ctx: LevelRenderContext) {
        val list = ghosts
        if (list.isEmpty()) return
        val cam = ctx.levelState().cameraRenderState.pos ?: return
        // 서로 맞닿은 꽉 찬 블록 사이의 면은 그리지 않는다(속이 비쳐 보이며 어지럽지 않도록)
        val solid = HashSet<BlockPos>()
        for (g in list) if (g.state.canOcclude()) solid.add(g.pos)
        val breath = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0)
        val alpha = (110 + 50 * breath).toInt()
        val deck = QuadInstance().apply {
            setColor(tint(if (enough) Palette.SKY else Palette.RED, alpha)); setLightCoords(0xF000F0); setOverlayCoords(OverlayTexture.NO_OVERLAY)
        }
        val pier = QuadInstance().apply {
            setColor(tint(Palette.YELLOW, alpha)); setLightCoords(0xF000F0); setOverlayCoords(OverlayTexture.NO_OVERLAY)
        }
        val type = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
        val ps = ctx.poseStack()
        val collector: SubmitNodeCollector = ctx.submitNodeCollector()
        for (g in list) {
            val q = quads(g.state)
            val skip = if (g.state.canOcclude()) Direction.entries.filter { solid.contains(g.pos.relative(it)) }.toSet() else emptySet()
            val inst = if (g.pier) pier else deck
            ps.pushPose()
            ps.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z)
            collector.submitCustomGeometry(ps, type) { pose, vc ->
                for ((d, quads) in q) {
                    if (d != null && d in skip) continue
                    for (quad in quads) vc.putBakedQuad(pose, quad, inst)
                }
            }
            ps.popPose()
        }
    }
}
