package dev.menonng.infracraft.client

import dev.menonng.infracraft.InfraCraft
import dev.menonng.infracraft.compat.Compat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.MapColor
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 플레이어가 실제로 불러온 청크의 지도 색(바닐라 지도와 같은 MapColor 와 명암)을 모은다.
 * 아스트라 오브리터레이터 지도 화면이 이것으로 그려진다. 세계(싱글플레이 저장 이름 또는 서버 주소)·차원마다 디스크에 저장한다.
 */
object ExploredMap {
    /** 차원 → 청크 키 → 256칸(16x16)의 MapColor 묶음 ID(0 = 빈 칸) */
    val chunks = ConcurrentHashMap<String, ConcurrentHashMap<Long, ByteArray>>()
    /** 바뀐 청크(지도 화면의 타일을 다시 올려야 한다) */
    val dirty: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private var worldKey: String? = null
    private var ticks = 0

    fun key(cx: Int, cz: Int): Long = (cx.toLong() and 0xFFFFFFFFL) or (cz.toLong() shl 32)
    fun keyX(k: Long): Int = k.toInt()
    fun keyZ(k: Long): Int = (k shr 32).toInt()

    fun register() {
        ClientChunkEvents.CHUNK_LOAD.register { level, chunk -> record(level, chunk) }
        ClientPlayConnectionEvents.JOIN.register { _, _, mc -> load(mc) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, mc -> mc.execute { save(); chunks.clear(); worldKey = null } }
        ClientTickEvents.END_CLIENT_TICK.register { if (++ticks % (20 * 300) == 0) save() }
    }

    fun dimension(level: ClientLevel): String = Compat.dimensionId(level)

    private fun record(level: ClientLevel, chunk: LevelChunk) {
        val cx = chunk.pos.x
        val cz = chunk.pos.z
        val out = ByteArray(256)
        val ceiling = level.dimensionType().hasCeiling()
        val pos = BlockPos.MutableBlockPos()
        val heights = IntArray(256)
        for (z in 0 until 16) for (x in 0 until 16) {
            val wx = (cx shl 4) + x
            val wz = (cz shl 4) + z
            var y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
            if (ceiling) {
                // 천장이 있는 차원(네더): 천장 아래에서 처음 만나는 바닥
                y = 100
                while (y > level.minY) {
                    val ground = !chunk.getBlockState(pos.set(wx, y, wz)).isAir
                    val open = chunk.getBlockState(pos.set(wx, y + 1, wz)).isAir
                    if (ground && open) break
                    y--
                }
            }
            var state = chunk.getBlockState(pos.set(wx, y, wz))
            var depth = 0
            while (!state.fluidState.isEmpty && y > level.minY && depth < 12) {
                y--; depth++
                state = chunk.getBlockState(pos.set(wx, y, wz))
            }
            heights[z * 16 + x] = y
            val color = if (depth > 0) MapColor.WATER else state.getMapColor(level, pos.set(wx, y, wz))
            if (color == MapColor.NONE) continue
            // 명암: 북쪽 칸보다 높으면 밝게, 낮으면 어둡게(물은 깊이로)
            val brightness = if (depth > 0) when {
                depth > 6 -> MapColor.Brightness.LOW
                depth > 2 -> MapColor.Brightness.NORMAL
                else -> MapColor.Brightness.HIGH
            } else {
                val north = if (z > 0) heights[(z - 1) * 16 + x] else y
                when {
                    y > north -> MapColor.Brightness.HIGH
                    y < north -> MapColor.Brightness.LOW
                    else -> MapColor.Brightness.NORMAL
                }
            }
            out[z * 16 + x] = color.getPackedId(brightness)
        }
        chunks.getOrPut(dimension(level)) { ConcurrentHashMap() }[key(cx, cz)] = out
        dirty.add(key(cx shr 5, cz shr 5))
    }

    private fun dir(mc: Minecraft): File? {
        val name = worldKey ?: return null
        return File(mc.gameDirectory, "infracraft/maps/" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    private fun load(mc: Minecraft) {
        chunks.clear()
        worldKey = mc.singleplayerServer?.worldData?.levelName?.let { "sp_$it" } ?: mc.currentServer?.ip?.let { "mp_$it" } ?: "unknown"
        val d = dir(mc) ?: return
        val files = d.listFiles { f -> f.name.endsWith(".bin") } ?: return
        for (f in files) try {
            DataInputStream(GZIPInputStream(f.inputStream().buffered())).use { input ->
                val dim = input.readUTF()
                val n = input.readInt()
                val m = ConcurrentHashMap<Long, ByteArray>(n * 2)
                repeat(n) {
                    val k = input.readLong()
                    val b = ByteArray(256)
                    input.readFully(b)
                    m[k] = b
                }
                chunks[dim] = m
            }
        } catch (e: Exception) {
            InfraCraft.LOGGER.warn("[InfraCraft] 지도 파일을 읽지 못했습니다: {}", f, e)
        }
    }

    fun save() {
        val mc = Minecraft.getInstance()
        val d = dir(mc) ?: return
        d.mkdirs()
        for ((dim, m) in chunks) try {
            val f = File(d, dim.replace(':', '_') + ".bin")
            DataOutputStream(GZIPOutputStream(f.outputStream().buffered())).use { out ->
                out.writeUTF(dim)
                val entries = m.entries.toList()
                out.writeInt(entries.size)
                for ((k, b) in entries) {
                    out.writeLong(k); out.write(b)
                }
            }
        } catch (e: Exception) {
            InfraCraft.LOGGER.warn("[InfraCraft] 지도를 저장하지 못했습니다: {}", dim, e)
        }
    }

    /** 그 블록 좌표의 지도 색(ARGB). 기록이 없으면 0 */
    fun colorAt(dim: String, x: Int, z: Int): Int {
        val b = chunks[dim]?.get(key(x shr 4, z shr 4)) ?: return 0
        val id = b[(z and 15) * 16 + (x and 15)].toInt() and 0xFF
        return if (id == 0) 0 else MapColor.getColorFromPackedId(id)
    }

    fun explored(dim: String, x: Int, z: Int): Boolean = chunks[dim]?.containsKey(key(x shr 4, z shr 4)) == true
}
