package dev.menonng.infracraft

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor

/** 메시지 색상 팔레트 */
object Palette {
    const val RED = 0xFF0045
    const val ORANGE = 0xFF7E00
    const val YELLOW = 0xFFCC11
    const val GREEN = 0x55BB44
    const val SKY = 0x39C5BB
    const val BLUE = 0x3355BB
    const val PURPLE = 0x660099
    const val PINK = 0xFFB4CC
}

fun MutableComponent.color(rgb: Int): MutableComponent = withStyle { it.withColor(TextColor.fromRgb(rgb)) }

/** infracraft.msg.<key> 번역 메시지. 인자는 문자열로 넘긴다. */
fun msg(key: String, color: Int, vararg args: Any): MutableComponent =
    Component.translatable("infracraft.msg.$key", *args.map { if (it is Component) it else it.toString() }.toTypedArray()).color(color)

fun lit(s: String, color: Int): MutableComponent = Component.literal(s).color(color)

fun fmt1(d: Double): String = String.format("%.1f", d)

/**
 * 역 이름 표시. 자동으로 붙은 이름은 언어와 무관한 표식(@station:3, @station:3:1, @harbor:2, @line:1:<이름>, 겹치면 끝에 " 2")으로 저장하고,
 * 보여 줄 때 번역 키로 바꿔 각 플레이어의 언어로 읽히게 한다. 플레이어가 붙인 이름은 그대로.
 */
private val AUTO_NAME = Regex("""^@(station|harbor):(\d+)(?::(\d+))?(?: (\d+))?$""")

fun autoName(kind: String, number: Int, line: Int? = null): String = "@$kind:$number" + (line?.let { ":$it" } ?: "")

private val LINE_NAME = Regex("""^@line:(\d+):(.*)$""")

/** 복선 역의 선로별 이름: 자동 이름이면 "@station:3:1", 플레이어가 붙인 이름이면 "@line:1:<이름>" */
fun lineName(base: String, line: Int): String {
    val m = AUTO_NAME.matchEntire(base)
    if (m != null && m.groupValues[3].isEmpty()) {
        val dup = m.groupValues[4]
        return autoName(m.groupValues[1], m.groupValues[2].toInt(), line) + (if (dup.isEmpty()) "" else " $dup")
    }
    return "@line:$line:$base"
}

fun nm(name: String): MutableComponent {
    LINE_NAME.matchEntire(name)?.let { return Component.translatable("infracraft.name.line", nm(it.groupValues[2]), it.groupValues[1]) }
    val m = AUTO_NAME.matchEntire(name) ?: return Component.literal(name)
    val (kind, n, line, dup) = m.destructured
    val c = if (line.isEmpty()) Component.translatable("infracraft.name.$kind", n)
    else Component.translatable("infracraft.name.${kind}_line", n, line)
    return if (dup.isEmpty()) c else c.append(" $dup")
}

fun nm(name: String, color: Int): MutableComponent = nm(name).color(color)
