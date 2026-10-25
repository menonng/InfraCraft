package dev.menonng.infracraft.system

import com.google.gson.Gson
import net.minecraft.core.BlockPos

class StationRec {
    var name = ""
    var dim = ""
    var x = 0
    var y = 0
    var z = 0

    val pos: BlockPos get() = BlockPos(x, y, z)
    val key: String get() = NetworkData.key(dim, x, y, z)
}

/** 실제로 탈것이 달려서 증명된 구간. 무방향으로 취급한다. */
class EdgeRec {
    var from = ""
    var to = ""
    var mode = "rail"
    var distance = 0.0
    var seconds = 0.0
    var chunks: MutableList<Long> = mutableListOf()
    var recordedAt = 0L

    fun other(key: String): String = if (from == key) to else from
    fun touches(key: String): Boolean = from == key || to == key
}

/** 오버월드에 첨부되어 저장되는 노선망. Gson 으로 JSON 문자열이 되어 저장된다. */
class NetworkData {
    var stations: MutableMap<String, StationRec> = LinkedHashMap()
    var edges: MutableList<EdgeRec> = mutableListOf()
    /** 분기기 위치 키 → 분기할 목적지 이름들 */
    var switches: MutableMap<String, MutableSet<String>> = LinkedHashMap()
    /** 종류(switch/current/lift) → 위치 키들 */
    var tickers: MutableMap<String, MutableSet<String>> = LinkedHashMap()

    fun station(key: String): StationRec? = stations[key]
    fun stationByName(name: String): StationRec? = stations.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun uniqueName(base: String): String {
        if (stationByName(base) == null) return base
        var i = 2
        while (stationByName("$base $i") != null) i++
        return "$base $i"
    }

    fun addTicker(kind: String, dim: String, pos: BlockPos) {
        tickers.getOrPut(kind) { LinkedHashSet() }.add(key(dim, pos))
    }

    fun edgesOf(key: String): List<EdgeRec> = edges.filter { it.touches(key) }

    /** 같은 구간·수단의 기록이 있으면 더 빠른 쪽을 남긴다. */
    fun putEdge(edge: EdgeRec) {
        val old = edges.firstOrNull { it.mode == edge.mode && ((it.from == edge.from && it.to == edge.to) || (it.from == edge.to && it.to == edge.from)) }
        if (old == null) edges.add(edge)
        else if (edge.seconds < old.seconds || old.chunks.isEmpty()) {
            edges.remove(old)
            edges.add(edge)
        } else old.recordedAt = edge.recordedAt
    }

    fun removeAt(key: String) {
        stations.remove(key)
        switches.remove(key)
        tickers.values.forEach { it.remove(key) }
        edges.removeAll { it.touches(key) }
    }

    fun toJson(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        fun fromJson(json: String): NetworkData = try {
            GSON.fromJson(json, NetworkData::class.java) ?: NetworkData()
        } catch (e: Exception) {
            NetworkData()
        }

        fun key(dim: String, x: Int, y: Int, z: Int) = "$dim|$x|$y|$z"
        fun key(dim: String, pos: BlockPos) = key(dim, pos.x, pos.y, pos.z)

        /** 위치 키 → (차원, 좌표) */
        fun parse(key: String): Pair<String, BlockPos>? {
            val p = key.split('|')
            if (p.size != 4) return null
            return p[0] to BlockPos(p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null)
        }

        fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
    }
}
