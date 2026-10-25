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
