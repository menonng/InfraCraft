package dev.menonng.infracraft.system

import dev.menonng.infracraft.Config
import dev.menonng.infracraft.Palette
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.content.ModAttachments
import dev.menonng.infracraft.content.ModBlocks
import dev.menonng.infracraft.content.ModStats
import dev.menonng.infracraft.fmt1
import dev.menonng.infracraft.lit
import dev.menonng.infracraft.msg
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.PriorityQueue
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

/** 역 등록, 실제 운행으로 구간 측량, 경로 탐색. 순간이동은 하지 않는다(타고 가야 도착한다). */
object Network {
    fun data(server: MinecraftServer): NetworkData =
        server.overworld().getAttachedOrCreate(ModAttachments.NETWORK) { NetworkData() }

    // ───────────── 역 ─────────────

    fun registerStation(level: ServerLevel, pos: BlockPos, requestedName: String?): StationRec {
        val data = data(level.server)
        val dim = Compat.dimensionId(level)
        val key = NetworkData.key(dim, pos)
        data.station(key)?.let { return it }
        val rec = StationRec().apply {
            name = data.uniqueName(requestedName?.takeIf { it.isNotBlank() } ?: "역 ${data.stations.size + 1}")
            this.dim = dim
            x = pos.x; y = pos.y; z = pos.z
        }
        data.stations[key] = rec
        return rec
    }

    fun stationAt(level: ServerLevel, pos: BlockPos): StationRec? {
        if (!level.getBlockState(pos).`is`(ModBlocks.STATION)) return null
        return data(level.server).station(NetworkData.key(Compat.dimensionId(level), pos))
            ?: registerStation(level, pos, null)
    }

    fun rename(level: ServerLevel, pos: BlockPos, newName: String, player: Player) {
        val st = stationAt(level, pos) ?: return
        val data = data(level.server)
        val clash = data.stationByName(newName)
        if (clash != null && clash !== st) {
            Compat.chat(player, msg("station.name_taken", Palette.RED, newName))
            return
        }
        // 이 역을 가리키는 분기기 설정도 새 이름으로 옮긴다
        val old = st.name
        data.switches.values.forEach { set -> if (set.remove(old)) set.add(newName) }
        st.name = newName
        Compat.chat(player, msg("station.renamed", Palette.GREEN, old, newName))
    }

    fun nearestStation(level: ServerLevel, pos: BlockPos, radius: Int): StationRec? {
        val dim = Compat.dimensionId(level)
        val r2 = radius.toDouble() * radius
        return data(level.server).stations.values
            .filter { it.dim == dim && it.pos.distSqr(pos) <= r2 }
            .minByOrNull { it.pos.distSqr(pos) }
    }

    fun showStationInfo(level: ServerLevel, pos: BlockPos, player: Player) {
        val st = stationAt(level, pos) ?: return
        val data = data(level.server)
        Compat.chat(player, msg("station.info", Palette.SKY, st.name, "${st.x}, ${st.y}, ${st.z}"))
        val edges = data.edgesOf(st.key)
        if (edges.isEmpty()) Compat.chat(player, msg("station.no_routes", Palette.ORANGE))
        for (e in edges) {
            val other = data.station(e.other(st.key)) ?: continue
            Compat.chat(player, lit("  → ${other.name}  ", Palette.YELLOW)
                .append(msg("mode.${e.mode}", Palette.PINK))
                .append(lit("  ${e.distance.toInt()}m · ${fmt1(e.seconds)}s", Palette.BLUE)))
        }
    }

    /** 이정표: 같은 차원에서 가장 가까운 역 다섯 곳의 방향과 거리 */
    fun showSignpost(level: ServerLevel, pos: BlockPos, player: Player) {
        val dim = Compat.dimensionId(level)
        val list = data(level.server).stations.values.filter { it.dim == dim }
            .sortedBy { it.pos.distSqr(pos) }.take(5)
        if (list.isEmpty()) {
            Compat.chat(player, msg("signpost.none", Palette.ORANGE))
            return
        }
        Compat.chat(player, msg("signpost.header", Palette.SKY))
        for (st in list) {
            val dx = (st.x - pos.x).toDouble()
            val dz = (st.z - pos.z).toDouble()
            val dist = sqrt(dx * dx + dz * dz).toInt()
            Compat.chat(player, lit("  ${arrow(dx, dz)} ${st.name}  ", Palette.YELLOW).append(lit("${dist}m", Palette.BLUE)))
        }
    }

    // ───────────── 이정석 ─────────────

    private val lastMilestone = WeakHashMap<ServerPlayer, BlockPos>()

    /** 길을 걷다 이정석 곁(2칸 안)을 지나면 가까운 역·항구 두 곳의 방향과 거리를 한 줄로 보여 준다. */
    fun tickMilestone(player: ServerPlayer) {
        if (player.tickCount % 10 != 0) return
        val level = player.level() as ServerLevel
        val c = player.blockPosition()
        var found: BlockPos? = null
        loop@ for (dx in -2..2) for (dy in -1..1) for (dz in -2..2) {
            val p = c.offset(dx, dy, dz)
            if (level.getBlockState(p).`is`(ModBlocks.MILESTONE)) { found = p; break@loop }
        }
        val here = found ?: run { lastMilestone.remove(player); return }
        if (lastMilestone[player] == here) return
        lastMilestone[player] = here
        val dim = Compat.dimensionId(level)
        val near = data(level.server).stations.values.filter { it.dim == dim }.sortedBy { it.pos.distSqr(here) }.take(2)
        if (near.isEmpty()) { Compat.overlay(player, msg("milestone.alone", Palette.SKY)); return }
        val line = msg("milestone.header", Palette.SKY)
        for (st in near) {
            val dx = (st.x - here.x).toDouble()
            val dz = (st.z - here.z).toDouble()
            val km = sqrt(dx * dx + dz * dz) / 1000.0
            line.append(lit("  ${arrow(dx, dz)} ${st.name} ", Palette.YELLOW)).append(lit(if (km < 1) "${(km * 1000).toInt()}m" else "${fmt1(km)}km", Palette.PINK))
        }
        Compat.overlay(player, line)
    }

    /** 이정표를 웅크리고 클릭: 노선망 요약과 모든 역(가까운 순) */
    fun showNetwork(level: ServerLevel, pos: BlockPos, player: Player) {
        val data = data(level.server)
        Compat.chat(player, msg("network.summary", Palette.SKY, data.stations.size.toString(), data.edges.size.toString(),
            fmt1(data.edges.sumOf { it.distance } / 1000.0)))
        for ((mode, list) in data.edges.groupBy { it.mode }) {
            Compat.chat(player, lit("  ", Palette.SKY).append(msg("mode.$mode", Palette.PINK)).append(lit(" × ${list.size}", Palette.YELLOW)))
        }
        val dim = Compat.dimensionId(level)
        for (st in data.stations.values.sortedWith(compareBy<StationRec>({ it.dim != dim }, { it.pos.distSqr(pos) })).take(20)) {
            val where = if (st.dim == dim) "${sqrt(st.pos.distSqr(pos)).toInt()}m" else st.dim.substringAfter(':')
            Compat.chat(player, lit("  ${st.name}  ", Palette.YELLOW).append(lit(where, Palette.BLUE))
                .append(lit("  ", Palette.BLUE)).append(msg("station.route_count", Palette.SKY, data.edgesOf(st.key).size.toString())))
        }
    }

    /** 역에서 목적지가 있는 승차권을 쓰면: 가장 빠른 경로 */
    fun showRoute(level: ServerLevel, player: Player, from: StationRec, destinationName: String) {
        val data = data(level.server)
        val to = data.stationByName(destinationName)
        if (to == null) {
            Compat.chat(player, msg("ticket.unknown", Palette.RED, destinationName)); return
        }
        val r = route(data, from.key, to.key)
        if (r == null) {
            Compat.chat(player, msg("route.none", Palette.RED, from.name, to.name)); return
        }
        Compat.chat(player, msg("route.header", Palette.SKY, from.name, to.name))
        Compat.chat(player, describeRoute(r))
        Compat.chat(player, msg("route.ride_hint", Palette.PINK))
    }

    private fun arrow(dx: Double, dz: Double): String {
        // 북쪽(-Z)을 위로 하는 8방위
        val deg = (Math.toDegrees(atan2(dx, -dz)) + 360.0) % 360.0
        return listOf("↑N", "↗NE", "→E", "↘SE", "↓S", "↙SW", "←W", "↖NW")[((deg + 22.5) / 45.0).toInt() % 8]
    }

    // ───────────── 운행 기록(측량) ─────────────

    private class Journey(var stationKey: String, var startTick: Long, var lastX: Double, var lastZ: Double) {
        var distance = 0.0
        val chunks = LinkedHashSet<Long>()
    }

    private val journeys = WeakHashMap<Entity, Journey>()

    /** 탈것이 움직인 거리와 지나간 청크를 쌓는다. 역에 있지 않으면 매 틱 호출된다. */
    fun trackMovement(vehicle: Entity) {
        val j = journeys[vehicle] ?: return
        val dx = vehicle.x - j.lastX
        val dz = vehicle.z - j.lastZ
        val d = sqrt(dx * dx + dz * dz)
        if (d < 8.0) j.distance += d // 순간이동은 무시
        j.lastX = vehicle.x
        j.lastZ = vehicle.z
        j.chunks.add(NetworkData.chunkKey(vehicle.blockX shr 4, vehicle.blockZ shr 4))
    }

    /**
     * 탈것이 역에 닿았을 때. 직전 역과 다른 역이면 그 구간을 노선으로 기록한다.
     * @return 새로 도착한 역(같은 역에 계속 머물면 null)
     */
    fun arriveAt(level: ServerLevel, vehicle: Entity, st: StationRec, mode: String): Boolean {
        val j = journeys[vehicle]
        if (j != null && j.stationKey == st.key) {
            j.startTick = level.gameTime
            j.distance = 0.0
            j.chunks.clear()
            j.lastX = vehicle.x
            j.lastZ = vehicle.z
            return false
        }
        if (j != null) {
            val seconds = (level.gameTime - j.startTick) / 20.0
            val data = data(level.server)
            val from = data.station(j.stationKey)
            if (from != null && seconds > 0.5 && j.distance > 4.0) {
                data.putEdge(EdgeRec().apply {
                    this.from = j.stationKey
                    to = st.key
                    this.mode = mode
                    distance = j.distance
                    this.seconds = seconds
                    chunks = j.chunks.toMutableList()
                    recordedAt = level.gameTime
                })
                for (p in vehicle.passengers) if (p is ServerPlayer) {
                    p.awardStat(ModStats.ROUTES_SURVEYED, 1)
                    Compat.overlay(p, msg("route.surveyed", Palette.GREEN, from.name, st.name, j.distance.toInt().toString(), fmt1(seconds)))
                }
            }
        }
        journeys[vehicle] = Journey(st.key, level.gameTime, vehicle.x, vehicle.z)
        for (p in vehicle.passengers) if (p is ServerPlayer) p.awardStat(ModStats.STATIONS_ARRIVED, 1)
        return true
    }

    /** 노선이 지나는 청크에서 선로가 부서지면 그 노선은 끊긴 것으로 본다. */
    fun invalidateChunk(server: MinecraftServer, dim: String, pos: BlockPos) {
        val ck = NetworkData.chunkKey(pos.x shr 4, pos.z shr 4)
        val data = data(server)
        data.edges.removeAll { e -> e.mode != "portal" && e.chunks.contains(ck) && data.station(e.from)?.dim == dim }
    }

    /** 포탈을 지나 차원을 옮겼을 때 양쪽 역을 포탈 노선으로 잇는다. */
    private val lastPositions = HashMap<UUID, Pair<String, BlockPos>>()

    fun rememberPosition(player: ServerPlayer) {
        lastPositions[player.uuid] = Compat.dimensionId(player.level()) to player.blockPosition()
    }

    fun onChangedLevel(player: ServerPlayer, origin: ServerLevel, destination: ServerLevel) {
        val (dim, pos) = lastPositions[player.uuid] ?: return
        if (dim != Compat.dimensionId(origin)) return
        val r = Config.c.stationScanRadius
        val a = nearestStation(origin, pos, r) ?: return
        val b = nearestStation(destination, player.blockPosition(), r) ?: return
        val data = data(origin.server)
        data.putEdge(EdgeRec().apply {
            from = a.key; to = b.key; mode = "portal"; distance = 0.0; seconds = 4.0; recordedAt = origin.gameTime
        })
        Compat.overlay(player, msg("route.portal", Palette.PURPLE, a.name, b.name))
    }

    // ───────────── 경로 탐색 ─────────────

    class Route(val stations: List<StationRec>, val edges: List<EdgeRec>) {
        val seconds get() = edges.sumOf { it.seconds }
        val distance get() = edges.sumOf { it.distance }
    }

    fun route(data: NetworkData, fromKey: String, toKey: String): Route? {
        if (fromKey == toKey) return null
        val dist = HashMap<String, Double>().apply { put(fromKey, 0.0) }
        val prev = HashMap<String, EdgeRec>()
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        queue.add(fromKey to 0.0)
        val adjacency = HashMap<String, MutableList<EdgeRec>>()
        for (e in data.edges) {
            adjacency.getOrPut(e.from) { mutableListOf() }.add(e)
            adjacency.getOrPut(e.to) { mutableListOf() }.add(e)
        }
        while (queue.isNotEmpty()) {
            val (k, d) = queue.poll()
            if (d > (dist[k] ?: Double.MAX_VALUE)) continue
            if (k == toKey) break
            for (e in adjacency[k].orEmpty()) {
                val o = e.other(k)
                val nd = d + e.seconds
                if (nd < (dist[o] ?: Double.MAX_VALUE)) {
                    dist[o] = nd
                    prev[o] = e
                    queue.add(o to nd)
                }
            }
        }
        if (toKey !in prev) return null
        val edges = ArrayList<EdgeRec>()
        val keys = ArrayList<String>().apply { add(toKey) }
        var cur = toKey
        while (cur != fromKey) {
            val e = prev[cur] ?: return null
            edges.add(e)
            cur = e.other(cur)
            keys.add(cur)
        }
        return Route(keys.reversed().mapNotNull { data.station(it) }, edges.reversed())
    }

    fun describeRoute(route: Route): Component {
        val c = lit(route.stations.joinToString(" → ") { it.name }, Palette.YELLOW)
        return c.append(lit("  (${route.distance.toInt()}m · ${fmt1(route.seconds)}s)", Palette.BLUE))
    }
}
