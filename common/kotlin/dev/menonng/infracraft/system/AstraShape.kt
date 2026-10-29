package dev.menonng.infracraft.system

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 아스트라 기술의 모양과 시간표. 서버(판정·지형)와 클라이언트(모델 렌더링)가 같은 식을 쓴다.
 *
 * 연출은 The Shooting Star [Demo](rimuru_dev, 모든 권리 보유)의 기술 연출 문법을 참고했다.
 * 코드·에셋은 가져오지 않았고, 우주 시네마틱 → 별자리 투영 → 별이 차례로 떨어지는 흐름만 아스트라(독수리자리 아홉 별)로 새로 짰다.
 */
object AstraShape {
    // ───────────── 암흑 별빛 검격: 공간을 가른 반달 모양의 틈 ─────────────

    const val SLASH_LIFE = 16
    /** 바깥 호의 반각(라디안). 클수록 반달에 가깝다 */
    const val SLASH_SPAN = 1.35
    /** 안쪽 가장자리가 현(두 끝을 잇는 직선)에서 바깥 호 쪽으로 다가간 비율. 0 이면 반달, 1 이면 선 */
    const val SLASH_FULL = 0.28

    /** 화면에 비스듬히 놓인 칼날의 두 축: a = 칼날을 따라, b = 화면에서 a 에 수직(위쪽) */
    fun slashAxes(right: Vec3, up: Vec3, roll: Double): Pair<Vec3, Vec3> =
        right.scale(cos(roll)).add(up.scale(sin(roll))) to right.scale(-sin(roll)).add(up.scale(cos(roll)))

    fun slashCenter(origin: Vec3, dir: Vec3, age: Double): Vec3 = origin.add(dir.scale(0.4 + age * 1.15))
    fun slashRadius(age: Double) = 1.9 + age * 0.11

    /** 틈이 벌어졌다 닫히는 정도(0..1): 처음 두 틱에 열리고 마지막 네 틱에 닫힌다 */
    fun slashOpen(age: Double): Double {
        val open = (age / 2.0).coerceIn(0.0, 1.0)
        val close = ((SLASH_LIFE - age) / 4.0).coerceIn(0.0, 1.0)
        return min(1 - (1 - open) * (1 - open), close)
    }

    /** 바깥 호 위의 점. s ∈ [-1, 1] 은 한쪽 끝에서 다른 끝. 무지개처럼 위로 굽고 가운데가 앞으로 조금 나온다 */
    fun slashOuter(c: Vec3, dir: Vec3, a: Vec3, b: Vec3, age: Double, s: Double): Vec3 {
        val r = slashRadius(age)
        val phi = s * SLASH_SPAN
        return c.add(a.scale(r * sin(phi))).add(b.scale(r * cos(phi) - r * 0.6)).add(dir.scale(0.7 * cos(phi)))
    }

    /** 안쪽 가장자리: 두 끝을 잇는 현에서 바깥 호 쪽으로 [SLASH_FULL] 만큼. open 이 작으면 바깥 호에 붙어 틈이 가늘다 */
    fun slashInner(c: Vec3, dir: Vec3, a: Vec3, b: Vec3, age: Double, s: Double, open: Double): Vec3 {
        val outer = slashOuter(c, dir, a, b, age, s)
        val left = slashOuter(c, dir, a, b, age, -1.0)
        val right = slashOuter(c, dir, a, b, age, 1.0)
        val chord = left.add(right.subtract(left).scale((s + 1) / 2))
        val inner = chord.add(outer.subtract(chord).scale(SLASH_FULL))
        return outer.add(inner.subtract(outer).scale(open))
    }

    /** 판정: 점이 틈(여유 포함) 안인가. 바깥 호 안쪽이면서 현보다 바깥 */
    fun slashHits(c: Vec3, dir: Vec3, a: Vec3, b: Vec3, age: Double, p: Vec3): Boolean {
        val rel = p.subtract(c)
        if (abs(rel.dot(dir)) > 1.9) return false
        val r = slashRadius(age)
        val x = rel.dot(a)
        val y = rel.dot(b) + r * 0.6
        if (abs(atan2(x, y)) > SLASH_SPAN + 0.2) return false
        if (sqrt(x * x + y * y) > r + 1.0) return false
        return y >= r * cos(SLASH_SPAN) - 1.0
    }

    // ───────────── AS-01 별자리 레일건 ─────────────

    const val RAIL_FIRE = 40
    const val RAIL_END = 72

    /** 레일건 앞에 펼치는 별자리의 중심과 축(오른쪽, 위) */
    fun railFrame(eye: Vec3, look: Vec3): Triple<Vec3, Vec3, Vec3> {
        val (r, u) = basis(look)
        return Triple(eye.add(look.scale(12.0)).add(u.scale(3.5)), r, u)
    }

    fun railStar(eye: Vec3, look: Vec3, i: Int): Vec3 {
        val (c, r, u) = railFrame(eye, look)
        val s = Astra.STARS[i]
        return c.add(r.scale(s.x * 1.9)).add(u.scale(s.y * 1.9))
    }

    // ───────────── AS-09 아홉 별 ─────────────

    const val SKY_HEIGHT = 46.0
    const val SKY_SPREAD = 4.2
    const val GROUND_SPREAD = 2.6
    const val CINEMATIC = 34
    const val FIRST_IMPACT = 46
    const val IMPACT_GAP = 6
    const val ALTAIR_IMPACT = 108
    const val NINE_END = 150

    /** 떨어지는 순서: 알타이르에서 먼 별부터, 알타이르는 맨 마지막 */
    val FALL_ORDER: List<Int> = Astra.STARS.indices.filter { it != 0 }
        .sortedByDescending { Astra.STARS[it].x * Astra.STARS[it].x + Astra.STARS[it].y * Astra.STARS[it].y } + 0

    fun impactTick(star: Int): Int {
        val j = FALL_ORDER.indexOf(star)
        return if (star == 0) ALTAIR_IMPACT else FIRST_IMPACT + j * IMPACT_GAP
    }

    /** 하늘에 투영된 별(목표 위 [SKY_HEIGHT]) */
    fun skyStar(ground: Vec3, i: Int): Vec3 {
        val s = Astra.STARS[i]
        return ground.add(s.x * SKY_SPREAD, SKY_HEIGHT, -s.y * SKY_SPREAD)
    }

    /** 땅에 떨어질 자리의 수평 위치(높이는 서버가 지면으로 정한다) */
    fun groundOffset(i: Int): Pair<Double, Double> {
        val s = Astra.STARS[i]
        return s.x * GROUND_SPREAD to -s.y * GROUND_SPREAD
    }

    fun basis(look: Vec3): Pair<Vec3, Vec3> {
        var right = look.cross(Vec3(0.0, 1.0, 0.0))
        right = if (right.lengthSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else right.normalize()
        return right to right.cross(look).normalize()
    }
}
