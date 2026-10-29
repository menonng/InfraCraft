package dev.menonng.infracraft.client

import com.mojang.blaze3d.platform.NativeImage
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.system.Astra
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * 별빛 좌표 지도: 플레이어가 지금까지 불러온 땅(ExploredMap)을 지도 색으로 그린다. 가 본 적 없는 곳은 우주 배경.
 * 좌클릭으로 그곳에 별빛 낙하, 드래그로 이동, 휠로 확대·축소, ESC 로 닫는다.
 * 지도는 512x512 블록 타일(DynamicTexture)로 나누어 올리고, 새로 불러온 청크가 있는 타일만 다시 올린다.
 */
class AstraMapScreen : Screen(Component.translatable("infracraft.map.title")) {
    private val mc = Minecraft.getInstance()
    private val dim = mc.level?.let { ExploredMap.dimension(it) } ?: "minecraft:overworld"
    private var cx = mc.player?.x ?: 0.0
    private var cz = mc.player?.z ?: 0.0
    /** 화면 픽셀 하나에 해당하는 블록 수의 역수(= 블록 하나의 픽셀 수) */
    private var scale = 1.0
    private var pressX = 0.0
    private var pressY = 0.0
    private var dragged = false
    private var opened = System.currentTimeMillis()

    override fun isPauseScreen() = false

    private fun toWorldX(sx: Double) = cx + (sx - width / 2.0) / scale
    private fun toWorldZ(sy: Double) = cz + (sy - height / 2.0) / scale
    private fun toScreenX(wx: Double) = (width / 2.0 + (wx - cx) * scale)
    private fun toScreenY(wz: Double) = (height / 2.0 + (wz - cz) * scale)

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        // 우주 배경(천천히 흐른다)
        val drift = ((System.currentTimeMillis() - opened) / 80L % 512).toInt()
        for (ty in -1..(height / 512 + 1)) for (tx in -1..(width / 512 + 1)) {
            g.blit(RenderPipelines.GUI_TEXTURED, SPACE, tx * 512 - drift / 2, ty * 512, 0f, 0f, 512, 512, 512, 512)
        }
        // 지도 타일
        val x0 = floor(toWorldX(0.0) / TILE).toInt()
        val x1 = floor(toWorldX(width.toDouble()) / TILE).toInt()
        val z0 = floor(toWorldZ(0.0) / TILE).toInt()
        val z1 = floor(toWorldZ(height.toDouble()) / TILE).toInt()
        for (tz in z0..z1) for (tx in x0..x1) {
            val id = Tiles.get(dim, tx, tz) ?: continue
            val sx = toScreenX(tx * TILE.toDouble()).toInt()
            val sy = toScreenY(tz * TILE.toDouble()).toInt()
            val size = max(1, (TILE * scale).toInt())
            g.blit(RenderPipelines.GUI_TEXTURED, id, sx, sy, 0f, 0f, size, size, TILE, TILE, TILE, TILE)
        }
        // 나(노란 점과 바라보는 방향)
        mc.player?.let { p ->
            val px = toScreenX(p.x).toInt()
            val py = toScreenY(p.z).toInt()
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000.toInt())
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000.toInt() or Palette.YELLOW)
            val yaw = Math.toRadians(p.yRot.toDouble() + 90.0)
            for (i in 3..7) g.fill(px + (cos(yaw) * i).toInt(), py + (sin(yaw) * i).toInt(), px + (cos(yaw) * i).toInt() + 1, py + (sin(yaw) * i).toInt() + 1, 0xFF000000.toInt() or Palette.YELLOW)
            g.text(font, Component.translatable("infracraft.map.you"), px + 5, py - 4, 0xFF000000.toInt() or Palette.YELLOW, true)
        }
        // 커서: 낙하 범위 고리와 아홉 별, 좌표
        val wx = floor(toWorldX(mouseX.toDouble())).toInt()
        val wz = floor(toWorldZ(mouseY.toDouble())).toInt()
        val known = ExploredMap.explored(dim, wx, wz)
        val ringColor = if (known) Palette.SKY else Palette.RED
        val r = max(6.0, 12.0 * scale)
        val t = System.currentTimeMillis() / 600.0
        for (k in 0 until 72) {
            val a = k / 72.0 * Math.PI * 2 + t
            val x = mouseX + (cos(a) * r).toInt()
            val y = mouseY + (sin(a) * r).toInt()
            g.fill(x, y, x + 1, y + 1, (if (k % 2 == 0) 0xFF000000.toInt() else 0x99000000.toInt()) or ringColor)
        }
        for ((i, s) in Astra.STARS.withIndex()) {
            val a = i / 9.0 * Math.PI * 2 - t * 0.7
            val x = mouseX + (cos(a) * r * 0.55).toInt()
            val y = mouseY + (sin(a) * r * 0.55).toInt()
            g.fill(x - 1, y, x + 2, y + 1, 0xFF000000.toInt() or s.color)
            g.fill(x, y - 1, x + 1, y + 2, 0xFF000000.toInt() or s.color)
        }
        g.fill(mouseX - 4, mouseY, mouseX + 5, mouseY + 1, 0xFFFFFFFF.toInt())
        g.fill(mouseX, mouseY - 4, mouseX + 1, mouseY + 5, 0xFFFFFFFF.toInt())
        val label = if (known) Component.literal("$wx, $wz") else Component.translatable("infracraft.map.unexplored")
        g.text(font, label, mouseX + 10, mouseY + 8, 0xFF000000.toInt() or ringColor, true)
        // 위·아래 띠
        g.fill(0, 0, width, 22, 0xB0080314.toInt())
        g.fill(0, height - 20, width, height, 0xB0080314.toInt())
        g.centeredText(font, title, width / 2, 7, 0xFF000000.toInt() or Palette.PINK)
        g.centeredText(font, Component.translatable("infracraft.map.hint"), width / 2, height - 14, 0xFFC8B6FF.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        pressX = event.x(); pressY = event.y(); dragged = false
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (abs(event.x() - pressX) + abs(event.y() - pressY) > 3) dragged = true
        cx -= dx / scale
        cz -= dy / scale
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!dragged && event.button() == 0) {
            val wx = floor(toWorldX(event.x())).toInt()
            val wz = floor(toWorldZ(event.y())).toInt()
            if (ExploredMap.explored(dim, wx, wz)) {
                AstraAim.strikeMap(wx, wz)
                onClose()
            }
        }
        return true
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        val before = toWorldX(mx) to toWorldZ(my)
        scale = (scale * if (sy > 0) 1.25 else 0.8).coerceIn(1.0 / 16, 8.0)
        // 커서 아래 지점이 그대로 있도록
        cx += before.first - toWorldX(mx)
        cz += before.second - toWorldZ(my)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (AstraAim.MAP_KEY.matches(event)) {
            onClose(); return true
        }
        return super.keyPressed(event)
    }

    /** 지도 타일: 512x512 블록 = 32x32 청크. 한 번 만들고, 새 청크가 들어오면 다시 그린다 */
    object Tiles {
        private class Tile(val id: net.minecraft.resources.Identifier, val tex: DynamicTexture)
        private val tiles = HashMap<String, Tile?>()

        fun get(dim: String, tx: Int, tz: Int): net.minecraft.resources.Identifier? {
            val key = "$dim/$tx/$tz"
            val dirtyKey = ExploredMap.key(tx, tz)
            val existing = tiles[key]
            if (existing != null && dirtyKey !in ExploredMap.dirty) return existing.id
            if (existing == null && tiles.containsKey(key) && dirtyKey !in ExploredMap.dirty) return null
            ExploredMap.dirty.remove(dirtyKey)
            val chunks = ExploredMap.chunks[dim] ?: return null
            var any = false
            val img = existing?.tex?.pixels ?: NativeImage(TILE, TILE, true)
            for (cz in 0 until 32) for (cxi in 0 until 32) {
                val b = chunks[ExploredMap.key(tx * 32 + cxi, tz * 32 + cz)] ?: continue
                any = true
                for (z in 0 until 16) for (x in 0 until 16) {
                    val idb = b[z * 16 + x].toInt() and 0xFF
                    img.setPixel(cxi * 16 + x, cz * 16 + z, if (idb == 0) 0 else net.minecraft.world.level.material.MapColor.getColorFromPackedId(idb))
                }
            }
            if (!any) {
                if (existing == null) { img.close(); tiles[key] = null }
                return existing?.id
            }
            if (existing != null) {
                existing.tex.upload(); return existing.id
            }
            val id = id("astra_map/${dim.replace(':', '_')}/$tx/$tz".lowercase())
            val tex = DynamicTexture({ "infracraft astra map $key" }, img)
            Minecraft.getInstance().textureManager.register(id, tex)
            tiles[key] = Tile(id, tex)
            return id
        }

        fun clear() {
            for (t in tiles.values) t?.let { Minecraft.getInstance().textureManager.release(it.id) }
            tiles.clear()
        }
    }

    companion object {
        const val TILE = 512
        val SPACE = id("textures/gui/advancements/backgrounds/astra_space.png")
    }
}
