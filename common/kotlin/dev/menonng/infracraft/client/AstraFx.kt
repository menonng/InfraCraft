package dev.menonng.infracraft.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.menonng.infracraft.content.id
import dev.menonng.infracraft.net.Payloads
import dev.menonng.infracraft.system.Astra
import dev.menonng.infracraft.system.AstraShape
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 아스트라 기술 연출을 파티클이 아니라 모델(면)로 그린다. 서버가 보낸 [Payloads.AstraFx] 를 받아
 * 시간(게임 틱 + 부분 틱)에 따라 [AstraShape] 의 같은 식으로 모양을 계산하고, 렌더 단계마다 다음 묶음으로 제출한다.
 *  - 빛(가산 혼합): 빛무리·별빛 반짝임·띠(선·고리·칼날 가장자리)·흐르는 빔
 *  - 어둠(반투명): 암흑 별빛 칼날의 몸
 *  - 면(가산, 텍스처 없음): 충격파 돔
 */
object AstraFx {
    private val GLOW = id("textures/effect/astra_glow.png")
    private val GLINT = id("textures/effect/astra_glint.png")
    private val LINE = id("textures/effect/astra_line.png")
    private val BEAM = id("textures/effect/astra_beam.png")
    private val VOID = id("textures/effect/astra_void.png")

    private const val VIOLET = 0x8A4DFF
    private const val DEEP = 0x3A0F7A
    private const val STARLIGHT = 0xF2F7FF

    abstract class Fx(val caster: Int, val start: Long) {
        abstract val life: Int
        fun time(level: Long, partial: Float) = (level - start) + partial.toDouble()
    }

    class Slash(caster: Int, start: Long, val origin: Vec3, val dir: Vec3, val a: Vec3, val b: Vec3) : Fx(caster, start) {
        override val life = AstraShape.SLASH_LIFE + 3
        /** 벽에 닿은 나이(그 뒤로는 빠르게 사라진다) */
        var stopped = -1.0
    }

    class Rail(caster: Int, start: Long, val eye: Vec3, val look: Vec3, val end: Vec3) : Fx(caster, start) {
        override val life = AstraShape.RAIL_END
    }

    class Nine(caster: Int, start: Long, val ground: Vec3, val points: List<Vec3>) : Fx(caster, start) {
        override val life = AstraShape.NINE_END
        /** 시네마틱을 건너뛰었는가(쓴 사람만) */
        var skipped = false
    }

    val active = ArrayList<Fx>()

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.AstraFx.TYPE) { p, _ -> receive(p) }
        LevelRenderEvents.COLLECT_SUBMITS.register { ctx -> render(ctx) }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, mc -> mc.execute { active.clear() } }
    }

    private fun v3(d: DoubleArray, i: Int) = Vec3(d[i], d[i + 1], d[i + 2])

    fun receive(p: Payloads.AstraFx) {
        val d = p.data
        when (p.kind) {
            Payloads.AstraFx.SLASH -> if (d.size >= 7) {
                val dir = v3(d, 3).normalize()
                val (right, up) = AstraShape.basis(dir)
                val (a, b) = AstraShape.slashAxes(right, up, d[6])
                active.add(Slash(p.caster, p.start, v3(d, 0), dir, a, b))
            }
            Payloads.AstraFx.RAILGUN -> if (d.size >= 9) active.add(Rail(p.caster, p.start, v3(d, 0), v3(d, 3).normalize(), v3(d, 6)))
            Payloads.AstraFx.NINE_STARS -> if (d.size >= 30) active.add(Nine(p.caster, p.start, v3(d, 0), List(9) { v3(d, 3 + it * 3) }))
        }
    }

    private fun tick(mc: Minecraft) {
        val level = mc.level ?: run { active.clear(); return }
        val now = level.gameTime
        active.removeAll { now - it.start > it.life + 2 || now < it.start - 200 }
        // 쓴 사람은 점프 키로 우주 시네마틱을 건너뛴다
        val me = mc.player?.id ?: return
        if (mc.options.keyJump.isDown) for (fx in active) if (fx is Nine && fx.caster == me && now - fx.start < AstraShape.CINEMATIC) fx.skipped = true
    }

    // ───────────── 묶음(제출 단위) ─────────────

    private class Buf(val type: RenderType, val textured: Boolean) {
        var f = FloatArray(6 * 1024)
        var c = IntArray(1024)
        var n = 0

        fun add(x: Float, y: Float, z: Float, color: Int, u: Float, v: Float) {
            if (n == c.size) {
                f = f.copyOf(f.size * 2); c = c.copyOf(c.size * 2)
            }
            val i = n * 5
            f[i] = x; f[i + 1] = y; f[i + 2] = z; f[i + 3] = u; f[i + 4] = v
            c[n] = color
            n++
        }

        fun emit(pose: PoseStack.Pose, vc: VertexConsumer) {
            for (k in 0 until n) {
                val i = k * 5
                vc.addVertex(pose, f[i], f[i + 1], f[i + 2]).setColor(c[k])
                if (textured) vc.setUv(f[i + 3], f[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0f, 1f, 0f)
            }
        }
    }

    private class Frame(val cam: Vec3, val left: Vec3, val up: Vec3, val time: Double) {
        val glow = Buf(RenderTypes.energySwirl(GLOW, 0f, 0f), true)
        val glint = Buf(RenderTypes.energySwirl(GLINT, 0f, 0f), true)
        val line = Buf(RenderTypes.energySwirl(LINE, 0f, 0f), true)
        val beam = Buf(RenderTypes.energySwirl(BEAM, 0f, (-time * 0.08 % 1.0).toFloat()), true)
        val dark = Buf(RenderTypes.eyes(VOID), true)
        val flat = Buf(RenderTypes.lightning(), false)
        val all get() = listOf(dark, flat, beam, line, glow, glint)

        fun quad(b: Buf, p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, c0: Int, c1: Int, c2: Int, c3: Int,
                 u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f) {
            fun put(p: Vec3, c: Int, u: Float, v: Float) = b.add((p.x - cam.x).toFloat(), (p.y - cam.y).toFloat(), (p.z - cam.z).toFloat(), c, u, v)
            put(p0, c0, u0, v0); put(p1, c1, u0, v1); put(p2, c2, u1, v1); put(p3, c3, u1, v0)
        }
    }

    /** 가산 혼합용 색: 밝기(0..1)를 RGB 에 곱한다 */
    private fun light(rgb: Int, k: Double): Int {
        val q = k.coerceIn(0.0, 1.0)
        val r = ((rgb shr 16 and 0xFF) * q).toInt()
        val g = ((rgb shr 8 and 0xFF) * q).toInt()
        val b = ((rgb and 0xFF) * q).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun alpha(rgb: Int, a: Double): Int = ((a.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

    private fun mixRgb(a: Int, b: Int, t: Double): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * t.coerceIn(0.0, 1.0)).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ───────────── 기본 도형 ─────────────

    /** 카메라를 보는 사각형(빛무리·반짝임). angle 로 돌린다 */
    private fun Frame.sprite(b: Buf, c: Vec3, size: Double, color: Int, angle: Double = 0.0) {
        val ca = cos(angle) * size / 2
        val sa = sin(angle) * size / 2
        val l = left.scale(ca).add(up.scale(sa))
        val u = left.scale(-sa).add(up.scale(ca))
        quad(b, c.add(l).add(u), c.add(l).subtract(u), c.subtract(l).subtract(u), c.subtract(l).add(u), color, color, color, color)
    }

    /** 카메라를 보는 띠(선·빔). across: 텍스처에서 단면이 u 축이면 true(빔), v 축이면 false(선) */
    private fun Frame.ribbon(b: Buf, a: Vec3, z: Vec3, width: Double, ca: Int, cz: Int, across: Boolean = false, repeat: Double = 1.0) {
        val d = z.subtract(a)
        if (d.lengthSqr() < 1e-6) return
        val view = a.add(z).scale(0.5).subtract(cam)
        var side = d.cross(view)
        if (side.lengthSqr() < 1e-8) side = d.cross(up)
        side = side.normalize().scale(width / 2)
        val len = (d.length() / max(width, 0.2) * 0.25 * repeat).toFloat()
        // across: u = 단면(0..1), v = 길이 방향 / 아니면 u = 길이 방향, v = 단면
        if (across) quad(b, a.subtract(side), z.subtract(side), z.add(side), a.add(side), ca, cz, cz, ca, 0f, 0f, 1f, len)
        else quad(b, a.subtract(side), a.add(side), z.add(side), z.subtract(side), ca, ca, cz, cz, 0f, 0f, len, 1f)
    }

    /** 평면 고리(충격파·조준 고리). normal 에 수직인 면, 반지름 r, 폭 w. dash > 0 이면 끊긴 고리 */
    private fun Frame.ring(b: Buf, center: Vec3, normal: Vec3, r: Double, w: Double, color: Int, phase: Double = 0.0,
                           dash: Int = 0, segments: Int = 64) {
        if (r <= 0.01) return
        val n = normal.normalize()
        val e1 = (if (abs(n.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)).cross(n).normalize()
        val e2 = n.cross(e1)
        for (i in 0 until segments) {
            if (dash > 0 && (i / dash) % 2 == 1) continue
            val t0 = i * 2 * PI / segments + phase
            val t1 = (i + 1) * 2 * PI / segments + phase
            fun at(t: Double, rr: Double) = center.add(e1.scale(cos(t) * rr)).add(e2.scale(sin(t) * rr))
            quad(b, at(t0, r - w / 2), at(t0, r + w / 2), at(t1, r + w / 2), at(t1, r - w / 2), color, color, color, color, 0f, 0f, 1f, 1f)
        }
    }

    /** 가장자리가 밝은 빛의 돔(반구) */
    private fun Frame.dome(center: Vec3, r: Double, rgb: Int, a: Double, hemi: Boolean = true) {
        if (r <= 0.05 || a <= 0.01) return
        val lat = 10
        val lon = 24
        val top = PI / 2
        val bottom = if (hemi) 0.0 else -PI / 2
        fun p(i: Int, j: Int): Pair<Vec3, Int> {
            val th = bottom + (top - bottom) * i / lat
            val ph = 2 * PI * j / lon
            val nrm = Vec3(cos(th) * cos(ph), sin(th), cos(th) * sin(ph))
            val pos = center.add(nrm.scale(r))
            val view = pos.subtract(cam).normalize()
            val rim = (1 - abs(nrm.dot(view))).pow(2.0)
            return pos to alpha(rgb, a * (0.12 + 0.88 * rim))
        }
        for (i in 0 until lat) for (j in 0 until lon) {
            val (p0, c0) = p(i, j); val (p1, c1) = p(i + 1, j); val (p2, c2) = p(i + 1, j + 1); val (p3, c3) = p(i, j + 1)
            quad(flat, p0, p1, p2, p3, c0, c1, c2, c3)
        }
    }

    /** 별 하나: 색 빛무리 + 흰 반짝임(돌며 깜빡인다) */
    private fun Frame.star(c: Vec3, size: Double, rgb: Int, k: Double, spin: Double) {
        sprite(glow, c, size * 2.2, light(rgb, 0.85 * k))
        sprite(glint, c, size * 2.6, light(mixRgb(rgb, 0xFFFFFF, 0.6), k), spin)
        sprite(glow, c, size * 0.7, light(0xFFFFFF, k))
    }

    // ───────────── 렌더 ─────────────

    private fun render(ctx: LevelRenderContext) {
        if (active.isEmpty()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camera = mc.gameRenderer.mainCamera()
        val cam = camera.position()
        val l = camera.leftVector()
        val u = camera.upVector()
        val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val now = level.gameTime + partial.toDouble()
        val fr = Frame(cam, Vec3(l.x().toDouble(), l.y().toDouble(), l.z().toDouble()), Vec3(u.x().toDouble(), u.y().toDouble(), u.z().toDouble()), now)
        for (fx in active) {
            val t = fx.time(level.gameTime, partial)
            if (t < 0 || t > fx.life) continue
            when (fx) {
                is Slash -> slash(fr, fx, t)
                is Rail -> rail(fr, fx, t)
                is Nine -> nine(fr, fx, t)
            }
        }
        val ps = ctx.poseStack()
        val collector = ctx.submitNodeCollector()
        for (b in fr.all) if (b.n > 0) collector.submitCustomGeometry(ps, b.type) { pose, vc -> b.emit(pose, vc) }
    }

    // ── 1) 암흑 별빛 검격: 초승달 칼날 ──

    private fun slash(f: Frame, s: Slash, t: Double) {
        val level = Minecraft.getInstance().level ?: return
        if (s.stopped < 0) {
            val c = AstraShape.slashCenter(s.origin, s.dir, t)
            val bp = BlockPos.containing(c)
            if (level.getBlockState(bp).isSuffocating(level, bp)) s.stopped = t
        }
        // 본 칼날과 두 잔상
        for ((lag, k) in listOf(0.0 to 1.0, 1.1 to 0.42, 2.2 to 0.18)) {
            val age = t - lag
            if (age < 0) continue
            val end = if (s.stopped >= 0) s.stopped else AstraShape.SLASH_LIFE.toDouble()
            if (age > end + 1.5) continue
            val fadeIn = min(1.0, (age + 0.5) / 1.3)
            val fadeOut = if (s.stopped >= 0 && age > s.stopped) max(0.0, 1 - (age - s.stopped) / 1.5) else 1 - (age / AstraShape.SLASH_LIFE).pow(3.0)
            blade(f, s, min(age, end), k * fadeIn * max(0.0, fadeOut), lag == 0.0)
        }
    }

    private fun blade(f: Frame, s: Slash, age: Double, k: Double, main: Boolean) {
        if (k <= 0.01) return
        val c = AstraShape.slashCenter(s.origin, s.dir, age)
        val n = 30
        val w = AstraShape.SLASH_WIDTH
        fun pt(i: Int, inset: Double) = AstraShape.slashPoint(c, s.dir, s.a, s.b, age, i / n.toDouble() * 2 - 1, inset)
        fun th(i: Int) = AstraShape.slashThickness(i / n.toDouble() * 2 - 1)
        for (i in 0 until n) {
            val u0 = i / n.toFloat()
            val u1 = (i + 1) / n.toFloat()
            // 몸: 바깥 날(v=1)에서 안쪽(v=0)까지 암흑 별빛
            val body = alpha(0xFFFFFF, k)
            f.quad(f.dark, pt(i, w * th(i)), pt(i, 0.0), pt(i + 1, 0.0), pt(i + 1, w * th(i + 1)), body, body, body, body, u0, 0f, u1, 1f)
            if (!main) continue
            // 바깥 날의 별빛(흰 심 + 보랏빛 번짐)
            val e0 = 0.35 + 0.65 * th(i)
            val e1 = 0.35 + 0.65 * th(i + 1)
            val hot0 = light(STARLIGHT, k * e0)
            val hot1 = light(STARLIGHT, k * e1)
            f.quad(f.line, pt(i, 0.12 * th(i)), pt(i, -0.12 * th(i)), pt(i + 1, -0.12 * th(i + 1)), pt(i + 1, 0.12 * th(i + 1)), hot0, hot0, hot1, hot1)
            val haze0 = light(VIOLET, k * 0.8 * e0)
            val haze1 = light(VIOLET, k * 0.8 * e1)
            f.quad(f.line, pt(i, 0.55 * th(i)), pt(i, -0.5 * th(i)), pt(i + 1, -0.5 * th(i + 1)), pt(i + 1, 0.55 * th(i + 1)), haze0, haze0, haze1, haze1)
        }
        if (!main) return
        // 칼날에 박힌 아홉 별
        for ((j, st) in Astra.STARS.withIndex()) {
            val sp = -0.8 + j * 1.6 / 8
            val i = ((sp + 1) / 2 * n).toInt().coerceIn(0, n)
            val tw = 0.6 + 0.4 * sin(f.time * 0.9 + j * 1.7)
            val p = AstraShape.slashPoint(c, s.dir, s.a, s.b, age, sp, w * th(i) * (0.3 + 0.4 * ((j * 37) % 10) / 10.0))
            f.sprite(f.glint, p, 0.35 + 0.25 * tw, light(st.color, k * tw), f.time * 0.15 + j)
        }
    }

    // ── 2) AS-01 별자리 레일건 ──

    private fun rail(f: Frame, r: Rail, t: Double) {
        val fire = AstraShape.RAIL_FIRE.toDouble()
        val altair = AstraShape.railStar(r.eye, r.look, 0)
        fun pos(i: Int): Vec3 {
            val p = AstraShape.railStar(r.eye, r.look, i)
            if (t < 34) return p
            val k = ((t - 34) / 6).coerceIn(0.0, 1.0)
            return p.add(altair.subtract(p).scale(k * k))
        }
        if (t < fire + 2) {
            // 별이 하나씩 켜진다
            for ((i, st) in Astra.STARS.withIndex()) {
                val on = ((t - i * 2) / 3).coerceIn(0.0, 1.0)
                if (on <= 0) continue
                val tw = 0.75 + 0.25 * sin(t * 0.8 + i)
                f.star(pos(i), 0.45 + 0.2 * tw, st.color, on * tw, t * 0.05 + i)
            }
            // 별자리 선
            if (t >= 18) {
                val g = ((t - 18) / 12).coerceIn(0.0, 1.0)
                val fade = if (t > 34) max(0.0, 1 - (t - 34) / 6) else 1.0
                for ((a, c) in Astra.LINES) {
                    val pa = pos(a); val pc = pos(c)
                    f.ribbon(f.line, pa, pa.add(pc.subtract(pa).scale(g)), 0.16, light(Astra.STARS[a].color, 0.9 * fade), light(Astra.STARS[c].color, 0.9 * fade))
                }
            }
            // 조준선: 알타이르에서 목표까지 가는 보랏빛 선, 목표의 조준 고리
            if (t >= 10) {
                val pulse = 0.55 + 0.45 * sin(t * 1.3)
                f.ribbon(f.line, altair, r.end, 0.07 + 0.04 * pulse, light(VIOLET, 0.9), light(0xFF4E9A, 0.7 * pulse))
                val dist = r.end.distanceTo(altair)
                var d = (t * 2.5) % 6.0
                val dir = r.end.subtract(altair).normalize()
                while (d < dist) {
                    f.sprite(f.glow, altair.add(dir.scale(d)), 0.35, light(0xFFB4CC, 0.6))
                    d += 6.0
                }
                val lock = ((t - 10) / 30).coerceIn(0.0, 1.0)
                val rr = 2.6 - 1.5 * lock
                f.ring(f.line, r.end, r.look, rr, 0.14, light(0xFF4E9A, 0.9), t * 0.12, 4, 48)
                f.ring(f.line, r.end, r.look, rr * 0.55, 0.1, light(STARLIGHT, 0.8), -t * 0.2, 6, 48)
            }
            // 충전 구슬
            if (t >= 30) {
                val k = ((t - 30) / 10).coerceIn(0.0, 1.0)
                f.sprite(f.glow, altair, 1.0 + 5.0 * k, light(VIOLET, 0.9))
                f.sprite(f.glow, altair, 0.6 + 2.6 * k, light(STARLIGHT, 1.0))
                f.ring(f.line, altair, r.look, 4.5 - 3.0 * k, 0.2, light(0xFFB4CC, k), t * 0.3, 3, 48)
            }
        }
        if (t >= fire) {
            val k = ((t - fire) / (AstraShape.RAIL_END - fire)).coerceIn(0.0, 1.0)
            val dir = r.end.subtract(altair).normalize()
            val len = r.end.distanceTo(altair)
            val width = 3.0 * (1 - k).pow(1.4) + 0.05
            val reach = min(len, (t - fire) * 40)
            val tip = altair.add(dir.scale(reach))
            f.ribbon(f.beam, altair, tip, width * 2.6, light(DEEP, 0.9 * (1 - k)), light(DEEP, 0.9 * (1 - k)), true, 0.5)
            f.ribbon(f.beam, altair, tip, width, light(VIOLET, 1 - k * 0.6), light(VIOLET, 1 - k * 0.6), true, 0.5)
            f.ribbon(f.beam, altair, tip, width * 0.38, light(STARLIGHT, 1 - k * 0.4), light(STARLIGHT, 1 - k * 0.4), true, 0.5)
            // 가속 고리: 아홉 빛깔이 광선을 감고 돈다
            var d = 4.0
            var j = 0
            while (d < reach) {
                val st = Astra.STARS[j % 9]
                f.ring(f.line, altair.add(dir.scale(d)), dir, width * 0.9 + 0.6 + 0.2 * sin(t + j), 0.3, light(st.color, 1 - k), t * 0.4 + j, 3, 36)
                d += 7.0; j++
            }
            // 총구 고리
            val m = ((t - fire) / 8).coerceIn(0.0, 1.0)
            f.ring(f.line, altair, dir, 1 + 7 * m, 0.5, light(STARLIGHT, 1 - m))
            // 착탄
            if (reach >= len - 0.01) {
                val h = ((t - fire) / 14).coerceIn(0.0, 1.0)
                f.sprite(f.glow, r.end, 10 * (1 - h) + 1, light(STARLIGHT, 1 - h))
                f.dome(r.end, 6 * h.pow(0.5), VIOLET, 0.8 * (1 - h), hemi = false)
                f.ring(f.line, r.end, Vec3(0.0, 1.0, 0.0), 16 * h, 0.8, light(0xFFB4CC, 1 - h))
            }
        }
    }

    // ── 3) AS-09 아홉 별 ──

    private fun nine(f: Frame, n: Nine, t: Double) {
        val up = Vec3(0.0, 1.0, 0.0)
        val g = n.ground.add(0.0, 0.08, 0.0)
        val altairT = AstraShape.ALTAIR_IMPACT.toDouble()
        fun landed(i: Int) = t >= AstraShape.impactTick(i)
        // 땅의 조준 표식: 끊긴 바깥 고리(시계), 안쪽 고리(반시계), 아홉 착탄점의 작은 고리
        if (t < altairT + 6) {
            val on = min(1.0, t / 10)
            val off = if (t > altairT) max(0.0, 1 - (t - altairT) / 6) else 1.0
            val k = on * off
            f.ring(f.line, g, up, 12.0, 0.35, light(0x39C5BB, 0.8 * k), t * 0.03, 2, 96)
            f.ring(f.line, g, up, 7.5, 0.22, light(0xFFB4CC, 0.7 * k), -t * 0.05, 3, 72)
            for (q in 0 until 12) {
                val a = q * PI / 6 + t * 0.03
                f.ribbon(f.line, g.add(cos(a) * 12.6, 0.0, sin(a) * 12.6), g.add(cos(a) * 13.8, 0.0, sin(a) * 13.8), 0.3, light(0x39C5BB, k), light(0x39C5BB, 0.2 * k))
            }
            for ((i, p) in n.points.withIndex()) {
                if (landed(i) || t < i * 2) continue
                val pulse = 0.6 + 0.4 * sin(t * 0.5 + i)
                f.ring(f.line, p.add(0.0, 0.1, 0.0), up, 1.3 + 0.2 * pulse, 0.18, light(Astra.STARS[i].color, 0.9 * k), t * 0.1, 0, 32)
            }
        }
        // 하늘의 독수리자리 투영: 별, 선, 바깥의 원격측정 고리
        val sky0 = AstraShape.skyStar(n.ground, 0)
        val skyCenter = sky0
        if (t < altairT + 4) {
            val on = min(1.0, t / 8)
            f.ring(f.line, skyCenter, up, 22.0, 0.5, light(0x660099, 0.9 * on), t * 0.01, 3, 96)
            f.ring(f.line, skyCenter, up, 18.5, 0.3, light(0xFFB4CC, 0.6 * on), -t * 0.02, 1, 96)
        }
        for ((i, st) in Astra.STARS.withIndex()) {
            val impact = AstraShape.impactTick(i).toDouble()
            val sky = AstraShape.skyStar(n.ground, i)
            val on = ((t - i * 2) / 3).coerceIn(0.0, 1.0)
            if (on <= 0) continue
            val tw = 0.75 + 0.25 * sin(t * 0.7 + i * 1.3)
            if (t < impact - 6) {
                // 알타이르는 마지막에 커지며 기운을 모은다
                val charge = if (i == 0 && t > altairT - 18) ((t - (altairT - 18)) / 12).coerceIn(0.0, 1.0) else 0.0
                f.star(sky, (1.3 + 0.5 * tw) * (1 + 3.5 * charge), st.color, on * tw, t * 0.04 + i)
                if (charge > 0) {
                    f.ring(f.line, sky, up, 16 - 13 * charge, 0.6, light(STARLIGHT, charge), t * 0.2, 4, 64)
                    f.ring(f.line, sky, up, 10 - 8 * charge, 0.4, light(VIOLET, charge), -t * 0.3, 2, 48)
                }
            }
            // 떨어지는 별: 머리와 꼬리 빔
            val p = n.points[i]
            val fallLen = if (i == 0) 10.0 else 6.0
            if (t >= impact - fallLen && t < impact) {
                val k = ((t - (impact - fallLen)) / fallLen).coerceIn(0.0, 1.0)
                val head = sky.add(p.subtract(sky).scale(k * k))
                val w = if (i == 0) 4.5 else 2.2
                f.ribbon(f.beam, sky, head, w, light(st.color, 0.8), light(STARLIGHT, 1.0), true, 0.6)
                f.ribbon(f.beam, sky, head, w * 0.35, light(STARLIGHT, 0.9), light(STARLIGHT, 1.0), true, 0.6)
                f.star(head, if (i == 0) 3.2 else 1.6, st.color, 1.0, t * 0.3)
            }
            // 착탄: 빛기둥이 남았다 사라지고, 땅을 따라 충격파와 돔
            if (t >= impact) {
                val big = i == 0
                val span = if (big) 30.0 else 14.0
                val k = ((t - impact) / span).coerceIn(0.0, 1.0)
                if (k < 1) {
                    val top = if (big) p.add(0.0, 180.0, 0.0) else sky
                    val w = (if (big) 10.0 else 3.4) * (1 - k).pow(1.3)
                    f.ribbon(f.beam, top, p, w * 2.2, light(DEEP, 0.9 * (1 - k)), light(VIOLET, 1 - k), true, 0.3)
                    f.ribbon(f.beam, top, p, w, light(st.color, 1 - k), light(STARLIGHT, 1 - k * 0.5), true, 0.3)
                    f.ribbon(f.beam, top, p, w * 0.32, light(STARLIGHT, 1 - k * 0.3), light(STARLIGHT, 1 - k * 0.3), true, 0.3)
                    val flash = if (big) 30.0 else 7.0
                    f.sprite(f.glow, p.add(0.0, 1.0, 0.0), flash * (1 - k).pow(0.7) + 0.5, light(STARLIGHT, (1 - k) * 0.9))
                    f.sprite(f.glow, p.add(0.0, 1.0, 0.0), flash * 1.6 * (1 - k).pow(0.5), light(st.color, (1 - k) * 0.7))
                    val rr = (if (big) 30.0 else 8.0) * k.pow(0.6)
                    f.ring(f.line, p.add(0.0, 0.3, 0.0), up, rr, if (big) 2.4 else 0.9, light(STARLIGHT, (1 - k) * 0.9))
                    f.ring(f.line, p.add(0.0, 0.3, 0.0), up, rr * 0.7, if (big) 1.6 else 0.6, light(st.color, (1 - k) * 0.8))
                    f.dome(p, (if (big) 14.0 else 3.5) * k.pow(0.45), if (big) VIOLET else st.color, (1 - k) * (if (big) 0.9 else 0.7))
                    if (big) {
                        val k2 = ((t - impact - 6) / span).coerceIn(0.0, 1.0)
                        if (k2 > 0) f.ring(f.line, p.add(0.0, 0.5, 0.0), up, 44 * k2.pow(0.6), 1.4, light(0xFFB4CC, (1 - k2) * 0.8))
                    }
                }
            }
        }
        // 땅에 새겨지는 별자리 선(양 끝이 모두 떨어진 뒤 빛나다 식는다)
        for ((a, c) in Astra.LINES) {
            val at = max(AstraShape.impactTick(a), AstraShape.impactTick(c)).toDouble()
            if (t < at) continue
            val k = ((t - at) / 40).coerceIn(0.0, 1.0)
            val glow = (1 - k) * (0.7 + 0.3 * sin(t * 0.6 + a))
            f.ribbon(f.line, n.points[a].add(0.0, 0.25, 0.0), n.points[c].add(0.0, 0.25, 0.0), 0.9, light(VIOLET, glow), light(VIOLET, glow))
            f.ribbon(f.line, n.points[a].add(0.0, 0.3, 0.0), n.points[c].add(0.0, 0.3, 0.0), 0.3, light(STARLIGHT, glow), light(STARLIGHT, glow))
        }
        // 하늘의 투영 선(별이 떨어지면 그 선도 꺼진다)
        if (t >= 16) for ((a, c) in Astra.LINES) {
            val gone = min(AstraShape.impactTick(a), AstraShape.impactTick(c)) - 6.0
            if (t >= gone) continue
            val grow = ((t - 16) / 12).coerceIn(0.0, 1.0)
            val pa = AstraShape.skyStar(n.ground, a)
            val pc = AstraShape.skyStar(n.ground, c)
            f.ribbon(f.line, pa, pa.add(pc.subtract(pa).scale(grow)), 0.5, light(Astra.STARS[a].color, 0.8), light(Astra.STARS[c].color, 0.8))
        }
    }

    /** 착탄 섬광의 세기(HUD 가 화면을 하얗게 덮는 데 쓴다): 가까울수록, 막 떨어졌을수록 크다 */
    fun flash(player: Vec3, partial: Float): Double {
        val level = Minecraft.getInstance().level ?: return 0.0
        var best = 0.0
        for (fx in active) {
            if (fx !is Nine) continue
            val t = fx.time(level.gameTime, partial)
            val k = (t - AstraShape.ALTAIR_IMPACT) / 12
            if (k < 0 || k > 1) continue
            val d = sqrt(player.distanceToSqr(fx.points[0]))
            best = max(best, (1 - k) * (1 - d / 160).coerceIn(0.0, 1.0) * 0.5)
        }
        return best
    }
}
