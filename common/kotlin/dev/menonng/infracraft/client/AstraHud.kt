package dev.menonng.infracraft.client

import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.system.Astra
import dev.menonng.infracraft.system.AstraShape
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 아스트라 기술의 화면 연출(쓴 사람만).
 *  - AS-09 아홉 별: 짧은 우주 시네마틱(독수리자리가 그려지고 원격측정 글자가 뜬다, 점프 키로 건너뜀) → 오른쪽 위 착탄 현황
 *  - AS-01 별자리 레일건: 조준경과 충전률
 *  - 알타이르가 떨어지면 가까운 플레이어 모두의 화면이 잠깐 하얗게 번쩍인다
 * 글자 색은 팔레트(분홍·하늘·노랑·보라 계열)를 따른다.
 */
object AstraHud : HudElement {
    private val SPACE = id("textures/gui/advancements/backgrounds/astra_space.png")
    private val GLOW = id("textures/effect/astra_glow_a.png")
    private val GLINT = id("textures/effect/astra_glint_a.png")
    private val LINE = id("textures/effect/astra_line_a.png")

    /** 팔레트에서 뽑은 밝은 보라(어두운 바탕 위 글자용) */
    private const val LILAC = 0xB57BE6
    private const val DIM = 0x8C9AD9

    fun register() {
        HudElementRegistry.addLast(id("astra_hud"), this)
    }

    private fun argb(rgb: Int, a: Double) = ((a.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    override fun extractRenderState(g: GuiGraphicsExtractor, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val me = mc.player ?: return
        val partial = delta.getGameTimeDeltaPartialTick(false)
        for (fx in AstraFx.active) {
            if (fx.caster != me.id) continue
            val t = fx.time(level.gameTime, partial)
            when (fx) {
                is AstraFx.Nine -> if (t < AstraShape.CINEMATIC && !fx.skipped) cinematic(g, fx, t) else ninePanel(g, fx, t)
                is AstraFx.Rail -> railScope(g, t)
            }
        }
        val flash = AstraFx.flash(me.position(), partial)
        if (flash > 0.01) g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb(0xF2F7FF, flash))
    }

    private fun sprite(g: GuiGraphicsExtractor, tex: net.minecraft.resources.Identifier, cx: Float, cy: Float, size: Float, color: Int, angle: Float = 0f) {
        val p = g.pose()
        p.pushMatrix()
        p.translate(cx, cy)
        if (angle != 0f) p.rotate(angle)
        val s = size.toInt().coerceAtLeast(1)
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -s / 2, -s / 2, 0f, 0f, s, s, 64, 64, 64, 64, color)
        p.popMatrix()
    }

    private fun line(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float, width: Int, color: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy).toInt()
        if (len < 1) return
        val p = g.pose()
        p.pushMatrix()
        p.translate(x0, y0)
        p.rotate(atan2(dy, dx))
        g.blit(RenderPipelines.GUI_TEXTURED, LINE, 0, -width / 2, 0f, 0f, len, width, 4, 64, 4, 64, color)
        p.popMatrix()
    }

    private fun text(g: GuiGraphicsExtractor, c: Component, x: Int, y: Int, rgb: Int, a: Double, scale: Float = 1f, center: Boolean = false) {
        if (a < 0.04) return
        val font = Minecraft.getInstance().font
        val p = g.pose()
        p.pushMatrix()
        p.translate(x.toFloat(), y.toFloat())
        p.scale(scale, scale)
        val w = if (center) font.width(c) / 2 else 0
        g.text(font, c, -w, 0, argb(rgb, a), true)
        p.popMatrix()
    }

    private fun brackets(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, len: Int, color: Int) {
        g.fill(x0, y0, x0 + len, y0 + 1, color); g.fill(x0, y0, x0 + 1, y0 + len, color)
        g.fill(x1 - len, y0, x1, y0 + 1, color); g.fill(x1 - 1, y0, x1, y0 + len, color)
        g.fill(x0, y1 - 1, x0 + len, y1, color); g.fill(x0, y1 - len, x0 + 1, y1, color)
        g.fill(x1 - len, y1 - 1, x1, y1, color); g.fill(x1 - 1, y1 - len, x1, y1, color)
    }

    // ── AS-09 우주 시네마틱 ──

    private fun cinematic(g: GuiGraphicsExtractor, n: AstraFx.Nine, t: Double) {
        val end = AstraShape.CINEMATIC.toDouble()
        val a = min(1.0, t / 4) * min(1.0, (end - t) / 5)
        val w = g.guiWidth()
        val h = g.guiHeight()
        // 우주 배경(천천히 흐른다) + 위아래 어둠
        val drift = (t * 1.5).toInt() % 512
        for (ty in 0..(h / 512 + 1)) for (tx in -1..(w / 512 + 1)) {
            g.blit(RenderPipelines.GUI_TEXTURED, SPACE, tx * 512 - drift, ty * 512, 0f, 0f, 512, 512, 512, 512, argb(0xFFFFFF, a))
        }
        g.fillGradient(0, 0, w, h / 4, argb(0x000000, 0.8 * a), argb(0x000000, 0.0))
        g.fillGradient(0, h * 3 / 4, w, h, argb(0x000000, 0.0), argb(0x000000, 0.85 * a))
        // 독수리자리
        val cx = w / 2f
        val cy = h * 0.42f
        val k = h * 0.07f
        fun sx(i: Int) = cx + Astra.STARS[i].x.toFloat() * k
        fun sy(i: Int) = cy - Astra.STARS[i].y.toFloat() * k
        val grow = ((t - 12) / 12).coerceIn(0.0, 1.0)
        if (grow > 0) for ((p, q) in Astra.LINES) {
            line(g, sx(p), sy(p), sx(p) + (sx(q) - sx(p)) * grow.toFloat(), sy(p) + (sy(q) - sy(p)) * grow.toFloat(), 3, argb(0xC8B6FF, 0.75 * a))
        }
        for ((i, st) in Astra.STARS.withIndex()) {
            val on = ((t - i * 1.2) / 2).coerceIn(0.0, 1.0)
            if (on <= 0) continue
            val tw = 0.8 + 0.2 * sin(t * 0.9 + i * 1.7)
            val size = (if (i == 0) 26f else 18f) * tw.toFloat()
            sprite(g, GLOW, sx(i), sy(i), size * 1.5f, argb(st.color, 0.75 * a * on))
            sprite(g, GLINT, sx(i), sy(i), size * 1.8f, argb(0xFFFFFF, a * on), (t * 0.03 + i).toFloat())
            text(g, Component.translatable("item.infracraft.star_${st.key}.short"), (sx(i) + 9).toInt(), (sy(i) - 3).toInt(),
                if (i == 0) Palette.PINK else 0xA9E4DF, a * on * 0.9, 0.75f)
        }
        // 원 궤도 표식
        val ring = (h * 0.33f)
        for (q in 0 until 72) {
            if (q % 3 == 2) continue
            val ang = q * Math.PI * 2 / 72 + t * 0.01
            val x = cx + (cos(ang) * ring).toFloat()
            val y = cy + (sin(ang) * ring * 0.42).toFloat()
            g.fill(x.toInt(), y.toInt(), x.toInt() + 1, y.toInt() + 1, argb(0x39C5BB, 0.5 * a))
        }
        // 글자: 가운데 아래 제목, 네 귀퉁이 원격측정
        val type = ((t - 6) / 10).coerceIn(0.0, 1.0)
        text(g, Component.translatable("infracraft.astra.hud.nine.title"), w / 2, (h * 0.74).toInt(), Palette.PINK, a * type, 2f, true)
        text(g, Component.translatable("infracraft.astra.hud.nine.sub", n.ground.x.toInt(), n.ground.y.toInt(), n.ground.z.toInt()),
            w / 2, (h * 0.74).toInt() + 22, Palette.SKY, a * type, 1f, true)
        text(g, Component.translatable("infracraft.astra.hud.nine.distance"), w / 2, (h * 0.74).toInt() + 34, DIM, a * type * 0.9, 0.75f, true)
        val m = 10
        val frame = argb(Palette.SKY, 0.7 * a)
        brackets(g, m, m, w - m, h - m, 14, frame)
        text(g, Component.translatable("infracraft.astra.hud.uplink"), m + 6, m + 6, LILAC, a, 0.75f)
        val lit = Astra.STARS.indices.count { t >= it * 1.2 }
        text(g, Component.translatable("infracraft.astra.hud.array", lit), m + 6, m + 16, Palette.SKY, a, 0.75f)
        val left = (AstraShape.ALTAIR_IMPACT - t) / 20.0
        val tm = Component.translatable("infracraft.astra.hud.tminus", String.format("%.1f", left))
        text(g, tm, w - m - 6 - (Minecraft.getInstance().font.width(tm) * 0.75f).toInt(), m + 6, Palette.YELLOW, a, 0.75f)
        val skip = Component.translatable("infracraft.astra.hud.skip", Minecraft.getInstance().options.keyJump.translatedKeyMessage)
        text(g, skip, m + 6, h - m - 14, DIM, a * 0.9, 0.75f)
        // 훑고 지나가는 주사선
        val scan = ((t * 9) % h).toInt()
        g.fill(0, scan, w, scan + 1, argb(Palette.SKY, 0.12 * a))
    }

    /** 시네마틱이 끝난 뒤: 오른쪽 위에 별 아홉의 착탄 현황 */
    private fun ninePanel(g: GuiGraphicsExtractor, n: AstraFx.Nine, t: Double) {
        if (t > AstraShape.ALTAIR_IMPACT + 30) return
        val a = min(1.0, (AstraShape.ALTAIR_IMPACT + 30 - t) / 10)
        val w = g.guiWidth()
        val x0 = w - 128
        val y0 = 6
        g.fill(x0, y0, w - 6, y0 + 34, argb(0x080314, 0.7 * a))
        brackets(g, x0, y0, w - 6, y0 + 34, 5, argb(Palette.SKY, 0.8 * a))
        text(g, Component.translatable("infracraft.astra.hud.nine.title"), x0 + 5, y0 + 4, Palette.PINK, a, 0.75f)
        val landed = Astra.STARS.indices.count { t >= AstraShape.impactTick(it) }
        text(g, Component.translatable("infracraft.astra.hud.landed", landed), w - 50, y0 + 4, Palette.YELLOW, a, 0.75f)
        for ((j, i) in AstraShape.FALL_ORDER.withIndex()) {
            val x = x0 + 6 + j * 13
            val y = y0 + 17
            val c = Astra.STARS[i].color
            if (t >= AstraShape.impactTick(i)) g.fill(x, y, x + 10, y + 10, argb(c, a))
            else {
                g.fill(x, y, x + 10, y + 1, argb(c, a)); g.fill(x, y + 9, x + 10, y + 10, argb(c, a))
                g.fill(x, y, x + 1, y + 10, argb(c, a)); g.fill(x + 9, y, x + 10, y + 10, argb(c, a))
            }
        }
    }

    // ── AS-01 조준경 ──

    private fun railScope(g: GuiGraphicsExtractor, t: Double) {
        val fire = AstraShape.RAIL_FIRE.toDouble()
        if (t > fire + 14) return
        val w = g.guiWidth()
        val h = g.guiHeight()
        val cx = w / 2
        val cy = h / 2
        val charge = (t / fire).coerceIn(0.0, 1.0)
        val a = if (t > fire) max(0.0, 1 - (t - fire) / 14) else min(1.0, t / 4)
        val r = (34 - 22 * charge).toInt()
        val c = argb(if (charge < 1) LILAC else Palette.PINK, 0.9 * a)
        brackets(g, cx - r, cy - r, cx + r + 1, cy + r + 1, 6, c)
        for (q in 0 until 4) {
            val ang = q * Math.PI / 2 + t * 0.08
            val x = cx + (cos(ang) * (r + 8)).toInt()
            val y = cy + (sin(ang) * (r + 8)).toInt()
            g.fill(x - 1, y - 1, x + 2, y + 2, argb(Palette.SKY, 0.8 * a))
        }
        text(g, Component.translatable("infracraft.astra.hud.rail.title"), cx, cy + r + 10, Palette.PINK, a, 0.75f, true)
        val line = if (t < fire) Component.translatable("infracraft.astra.hud.rail.charge", (charge * 100).toInt())
        else Component.translatable("infracraft.astra.hud.rail.fire")
        text(g, line, cx, cy + r + 20, if (t < fire) Palette.SKY else Palette.RED, a, 1f, true)
        if (t >= fire) g.fill(0, 0, w, h, argb(0xE8DDFF, 0.35 * max(0.0, 1 - (t - fire) / 6)))
    }
}
