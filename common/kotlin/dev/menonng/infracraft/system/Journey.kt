package dev.menonng.infracraft.system

import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModStats
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats

/**
 * 여정 발전과제: 모드 통계가 기준을 넘으면 수여한다(조건식 JSON 형식이 버전마다 달라 코드로 판정).
 * 기준은 tools/gen_data.py 의 발전과제 목록과 같다.
 */
object Journey {
    private val GOALS = listOf(
        Triple("root", ModStats.ROAD_ONE_CM, 100),
        Triple("road_1km", ModStats.ROAD_ONE_CM, 100_000),
        Triple("first_bridge", ModStats.BRIDGE_BLOCKS, 1),
        Triple("first_station", ModStats.STATIONS_ARRIVED, 1),
        Triple("surveyor", ModStats.ROUTES_SURVEYED, 5),
        Triple("rail_layer", ModStats.TRACK_PLACED, 256),
        Triple("tunneler", ModStats.BLOCKS_BORED, 2000),
        Triple("rider", ModStats.HORSES_CALLED, 1),
    )

    fun tick(player: ServerPlayer) {
        if (player.tickCount % 40 != 7) return
        val server = player.level().server
        for ((name, stat, min) in GOALS) {
            if (player.stats.getValue(Stats.CUSTOM.get(stat)) < min) continue
            val holder = server.advancements.get(Compat.id("infracraft", "journey/$name")) ?: continue
            if (player.advancements.getOrStartProgress(holder).isDone) continue
            player.advancements.award(holder, "done")
        }
    }
}
