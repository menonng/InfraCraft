package dev.menonng.infracraft.system

import dev.menonng.infracraft.content.id
import net.minecraft.advancements.AdvancementNode
import kotlin.math.cos
import kotlin.math.sin

/**
 * 독수리자리 발전과제 탭의 배치. 바닐라는 나무 모양으로 자동 배치하므로, 그 계산(TreeNodePosition.run)이 끝난 뒤
 * 이 탭의 노드만 원형으로 다시 놓는다(믹스인). 서버가 정한 좌표는 그대로 클라이언트로 전해진다.
 *  - 가운데: 아퀼라 성좌핵
 *  - 둘레(반지름 R): 별조각 아홉, 반시계로 30°부터 40°씩(알타이르는 맨 아래), 위쪽 90°는 비워 둔다
 *  - 성좌핵에서 위로 2R: 아스트라 오브리터레이터
 * 좌표 단위는 바닐라 그대로(가로 28px, 세로 27px)이므로 세로는 28/27 을 곱해 원이 찌그러지지 않게 한다.
 */
object AstraAdvancements {
    const val R = 3.0
    private const val ASPECT = 28.0 / 27.0
    val ROOT = id("astra/star_altair")

    /** 노드 이름(경로 끝) → 좌표 */
    fun position(item: String): Pair<Float, Float>? {
        if (item == "aquila_core") return 0f to 0f
        if (item == "astra_obliterator") return 0f to (-2 * R * ASPECT).toFloat()
        val i = Astra.RING.indexOf(item.removePrefix("star_"))
        if (i < 0) return null
        val a = Math.toRadians(30.0 + 40.0 * i)
        return (R * cos(a)).toFloat() to (-R * sin(a) * ASPECT).toFloat()
    }

    fun isAstra(root: AdvancementNode) = root.holder().id() == ROOT

    @JvmStatic
    fun layout(root: AdvancementNode) {
        if (!isAstra(root)) return
        fun visit(n: AdvancementNode) {
            val path = n.holder().id().path.removePrefix("astra/")
            position(path)?.let { (x, y) -> dev.menonng.infracraft.compat.Compat.setAdvancementLocation(n, x, y) }
            for (c in n.children()) visit(c)
        }
        visit(root)
    }
}
