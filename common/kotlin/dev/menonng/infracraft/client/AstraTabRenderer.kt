package dev.menonng.infracraft.client

import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.system.Astra
import dev.menonng.infracraft.system.AstraAdvancements
import net.minecraft.advancements.AdvancementNode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * 독수리자리 발전과제 탭: 바닐라의 꺾인 연결선 대신 직접 그린다(믹스인이 AdvancementWidget.extractConnectivity 를 가로챈다).
 *  - 배경: 우주(512px, 스크롤의 절반 속도로 움직이는 시차) + 깜박이는 별
 *  - 성좌핵 → 별조각: 희미한 바퀴살,  별조각끼리: 독수리자리 선(양 끝 별빛으로 물드는 그라데이션)
 *  - 성좌핵 → 아스트라 오브리터레이터: 굵은 별빛 기둥
 * 아직 보이지 않는(서버가 보내지 않은) 발전과제로는 선을 긋지 않는다.
 */
object AstraTabRenderer {
    private val SPACE = id("textures/gui/advancements/backgrounds/astra_space.png")
    private val TWINKLE = List(48) { i ->
        val h = (i * 2654435761L).toInt()
        Triple(((h ushr 3) and 511), ((h ushr 13) and 511), Astra.STARS[i % Astra.STARS.size].color)
    }

    @JvmStatic
    fun handles(node: AdvancementNode): Boolean = node.root().holder().id() == AstraAdvancements.ROOT

    private fun visible(item: String): Boolean =
        Minecraft.getInstance().connection?.advancements?.get(id("astra/$item")) != null

    /** 노드 아이콘 가운데(탭 안 좌표) */
    private fun center(item: String, x: Int, y: Int): Pair<Int, Int>? {
        val (px, py) = AstraAdvancements.position(item) ?: return null
        return (x + floor(px * 28.0).toInt() + 16) to (y + floor(py * 27.0).toInt() + 13)
    }

    @JvmStatic
    fun connectivity(g: GuiGraphicsExtractor, x: Int, y: Int, shadow: Boolean, node: AdvancementNode) {
        if (node.parent() != null) return  // 뿌리(알타이르) 위젯이 한 번에 그린다
        if (shadow) background(g, x, y)
        val core = center("aquila_core", x, y)
        val stars = Astra.STARS.associate { s -> s.key to center("star_${s.key}", x, y) }
        val coreVisible = visible("aquila_core")
        // 바퀴살
        if (coreVisible && core != null) for (s in Astra.STARS) {
            if (!visible("star_${s.key}")) continue
            val p = stars[s.key] ?: continue
            line(g, core, p, if (shadow) 0x80000000.toInt() else 0x558A4DFF, 0x55000000 or (s.color and 0xFFFFFF), if (shadow) 3 else 1)
        }
        // 독수리자리 선
        for ((a, b) in Astra.LINES) {
            val sa = Astra.STARS[a]; val sb = Astra.STARS[b]
            if (!visible("star_${sa.key}") || !visible("star_${sb.key}")) continue
            val pa = stars[sa.key] ?: continue
            val pb = stars[sb.key] ?: continue
            if (shadow) line(g, pa, pb, 0xC0000000.toInt(), 0xC0000000.toInt(), 4)
            else line(g, pa, pb, 0xFF000000.toInt() or sa.color, 0xFF000000.toInt() or sb.color, 2)
        }
        // 성좌핵 → 아스트라 오브리터레이터
        val top = center("astra_obliterator", x, y)
        if (core != null && top != null && visible("astra_obliterator")) {
            if (shadow) line(g, core, top, 0xC0000000.toInt(), 0xC0000000.toInt(), 6)
            else {
                line(g, core, top, 0xFF8A4DFF.toInt(), 0xFFF2F7FF.toInt(), 3)
                val pulse = (0.5 + 0.5 * sin(System.currentTimeMillis() / 300.0))
                val alpha = (80 + 100 * pulse).toInt() shl 24
                line(g, core, top, alpha or 0xFFFFFF, alpha or 0xFFE27A, 1)
            }
        }
    }

    private fun background(g: GuiGraphicsExtractor, x: Int, y: Int) {
        // 시차: 스크롤의 절반만 움직인다. 512 타일을 2x2 로 깔아 창 안을 덮는다
        val ox = Math.floorMod(x / 2, 512) - 512
        val oy = Math.floorMod(y / 2, 512) - 512
        for (ty in 0..1) for (tx in 0..1) {
            g.blit(RenderPipelines.GUI_TEXTURED, SPACE, ox + tx * 512, oy + ty * 512, 0f, 0f, 512, 512, 512, 512)
        }
        val t = System.currentTimeMillis() / 1000.0
        for ((i, s) in TWINKLE.withIndex()) {
            val a = 0.5 + 0.5 * sin(t * (0.8 + (i % 5) * 0.35) + i)
            if (a < 0.55) continue
            val sx = Math.floorMod(s.first + (x * 3) / 4, 512) - 256 + 117
            val sy = Math.floorMod(s.second + (y * 3) / 4, 512) - 256 + 56
            val col = ((a * 255).toInt() shl 24) or (s.third and 0xFFFFFF)
            g.fill(sx, sy, sx + 1, sy + 1, col)
            if (a > 0.9) {
                g.fill(sx - 1, sy, sx + 2, sy + 1, col)
                g.fill(sx, sy - 1, sx + 1, sy + 2, col)
            }
        }
    }

    /** 두께 w 의 직선, 양 끝 색 사이로 물든다 */
    private fun line(g: GuiGraphicsExtractor, a: Pair<Int, Int>, b: Pair<Int, Int>, ca: Int, cb: Int, w: Int) {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val n = max(abs(dx), abs(dy))
        if (n == 0) return
        val h = w / 2
        for (i in 0..n) {
            val t = i / n.toDouble()
            val px = a.first + (dx * t).toInt()
            val py = a.second + (dy * t).toInt()
            g.fill(px - h, py - h, px - h + w, py - h + w, lerp(ca, cb, t))
        }
    }

    private fun lerp(a: Int, b: Int, t: Double): Int {
        fun ch(s: Int) = (((a ushr s) and 0xFF) + (((b ushr s) and 0xFF) - ((a ushr s) and 0xFF)) * t).toInt() and 0xFF
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
