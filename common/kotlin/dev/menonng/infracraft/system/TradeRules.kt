package dev.menonng.infracraft.system

import kotlin.math.roundToInt

/**
 * 항구 교역의 규칙(마인크래프트 객체에 의존하지 않는 순수 계산).
 * 항구의 특산품은 생물 군계로 정해지고, 다른 지역 항구까지 배로 실어 나르면 거리만큼 대가를 받는다.
 */
object TradeRules {
    enum class Region(val id: String, val keywords: List<String>, val specialties: List<String>) {
        MUSHROOM("mushroom", listOf("mushroom"), listOf("red_mushroom", "brown_mushroom", "mycelium")),
        SWAMP("swamp", listOf("mangrove", "swamp"), listOf("mangrove_log", "mud", "lily_pad", "slime_ball")),
        SNOWY("snowy", listOf("frozen", "snowy", "ice", "grove", "peaks"), listOf("ice", "packed_ice", "snowball", "rabbit_hide")),
        JUNGLE("jungle", listOf("jungle", "bamboo"), listOf("cocoa_beans", "bamboo", "melon_slice", "jungle_log")),
        DESERT("desert", listOf("desert"), listOf("cactus", "sand", "sandstone", "dead_bush")),
        BADLANDS("badlands", listOf("badlands"), listOf("terracotta", "red_sand", "gold_nugget")),
        SAVANNA("savanna", listOf("savanna"), listOf("acacia_log", "leather", "pumpkin")),
        CHERRY("cherry", listOf("cherry"), listOf("cherry_log", "pink_petals", "honeycomb")),
        TAIGA("taiga", listOf("taiga"), listOf("spruce_log", "sweet_berries", "podzol")),
        COAST("coast", listOf("ocean", "beach", "river", "shore"), listOf("cod", "salmon", "kelp", "sea_pickle", "ink_sac")),
        TEMPERATE("temperate", emptyList(), listOf("wheat", "apple", "oak_log", "birch_log", "carrot", "potato"));

        companion object {
            fun byId(id: String): Region? = entries.firstOrNull { it.id == id }
        }
    }

    /** 이보다 가까운 항구끼리는 교역하지 않는다 */
    const val MIN_DISTANCE = 200
    /** 대가가 더 늘지 않는 거리 */
    const val MAX_DISTANCE = 4000
    /** 진귀품이 나오기 시작하는 거리 */
    const val EXOTIC_DISTANCE = 800

    /** 생물 군계 ID 의 경로(예: "snowy_taiga")로 지역을 정한다. 앞 항목이 우선이다. */
    fun region(biomePath: String): Region =
        Region.entries.firstOrNull { r -> r.keywords.any { biomePath.contains(it) } } ?: Region.TEMPERATE

    /** 대가 배율: 최소 거리에서 0.6 배, 최대 거리에서 2.5 배 */
    fun multiplier(distance: Double): Double {
        val t = ((distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE)).coerceIn(0.0, 1.0)
        return 0.6 + 1.9 * t
    }

    fun rewardCount(count: Int, distance: Double): Int = (count * multiplier(distance)).roundToInt().coerceAtLeast(1)

    /** 화물 한 상자가 진귀품 하나를 얹어 줄 확률(가득 찬 상자 기준, 수량에 비례) */
    fun exoticChance(count: Int, distance: Double): Double {
        if (distance < EXOTIC_DISTANCE) return 0.0
        return ((0.15 + distance / 8000.0).coerceAtMost(0.6)) * (count.coerceIn(0, 64) / 64.0)
    }

    /** 거리별 진귀품 후보(아이템 경로, 가중치) */
    fun exoticPool(distance: Double): List<Pair<String, Int>> {
        val pool = mutableListOf("nautilus_shell" to 4, "name_tag" to 3, "tropical_fish_bucket" to 2)
        if (distance >= 2000) pool += listOf("sniffer_egg" to 2, "saddle" to 2)
        if (distance >= 3500) pool += "heart_of_the_sea" to 1
        return pool
    }

    /** 왜 팔 수 없는지(가능하면 null) */
    fun refusal(origin: Region, destination: Region, distance: Double, sameDimension: Boolean, sameHarbor: Boolean): String? = when {
        sameHarbor -> "same_harbor"
        !sameDimension -> "other_dimension"
        origin == destination -> "same_region"
        distance < MIN_DISTANCE -> "too_close"
        else -> null
    }

    /** 대가 수량을 도착 항구 특산품들에 고르게 나눈다(무작위 값 r 을 받아 결정적으로). */
    fun split(total: Int, specialties: List<String>, r: (Int) -> Int): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        repeat(total) { val s = specialties[r(specialties.size)]; out[s] = (out[s] ?: 0) + 1 }
        return out
    }
}
