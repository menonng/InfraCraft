package dev.menonng.infracraft.system

import dev.menonng.infracraft.InfraCraft
import dev.menonng.infracraft.compat.Compat
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.UUID
import kotlin.math.abs

/**
 * Create(26.x Fabric 은 Create Fly) 연동. 컴파일 의존성 없이 리플렉션으로 읽기만 하므로 Create 가 없어도 아무 일도 하지 않는다.
 *
 *  - Create 기차역(GlobalStation)을 노선망의 역으로 등록하고 이름을 맞춘다.
 *  - Create 기차가 역 A 를 떠나 역 B 에 도착하면 그 구간을 "create_train" 노선으로 기록한다.
 *    Steam 'n' Rails 같은 애드온의 트랙도 같은 Create 선로망을 쓰므로 그대로 잡힌다.
 *  - 지하철 키트가 쓸 Create 트랙 블록(create:track)을 찾아 준다.
 */
object CreateCompat {
    val loaded: Boolean = FabricLoader.getInstance().isModLoaded("create")

    /** 리플렉션 대상이 모두 확인되었는지 */
    var ready = false
        private set

    private lateinit var railways: Field
    private lateinit var trainsField: Field
    private lateinit var networksField: Field
    private lateinit var trainSpeed: Field
    private lateinit var currentStation: Method
    private lateinit var stationName: Field
    private lateinit var stationPos: Method
    private lateinit var stationDim: Method
    private lateinit var getPoints: Method
    private var stationType: Any? = null

    fun init() {
        if (!loaded) return
        for (pkg in listOf("com.zurrtum.create", "com.simibubi.create")) {
            try {
                val create = Class.forName("$pkg.Create")
                val manager = Class.forName("$pkg.content.trains.GlobalRailwayManager")
                val train = Class.forName("$pkg.content.trains.entity.Train")
                val station = Class.forName("$pkg.content.trains.station.GlobalStation")
                val graph = Class.forName("$pkg.content.trains.graph.TrackGraph")
                val pointType = Class.forName("$pkg.content.trains.graph.EdgePointType")
                railways = create.getField("RAILWAYS")
                trainsField = manager.getField("trains")
                networksField = manager.getField("trackNetworks")
                trainSpeed = train.getField("speed")
                currentStation = train.getMethod("getCurrentStation")
                stationName = station.getField("name")
                stationPos = station.getMethod("getBlockEntityPos")
                stationDim = station.getMethod("getBlockEntityDimension")
                getPoints = graph.getMethod("getPoints", pointType)
                stationType = pointType.getField("STATION").get(null)
                ready = true
                try {
                    glueCtor = Class.forName("$pkg.content.contraptions.glue.SuperGlueEntity").getConstructor(Level::class.java, AABB::class.java)
                } catch (e: Throwable) {
                    InfraCraft.LOGGER.warn("[InfraCraft] Create 초강력 접착제를 찾지 못해 차량 도면이 접착제를 붙이지 않습니다", e)
                }
                InfraCraft.LOGGER.info("[InfraCraft] Create 연동 활성화 ({})", pkg)
                return
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                InfraCraft.LOGGER.warn("[InfraCraft] Create 를 찾았지만 기차 API 가 예상과 달라 연동을 끕니다 ({})", pkg, e)
                return
            }
        }
        InfraCraft.LOGGER.warn("[InfraCraft] Create 모드는 있지만 기차 클래스를 찾지 못했습니다")
    }

    // ───────────── 초강력 접착제 ─────────────

    private var glueCtor: Constructor<*>? = null

    /** 상자 범위 전체에 Create 초강력 접착제를 붙인다(차체를 한 덩어리로 조립하게). */
    fun spawnGlue(level: Level, box: AABB): Boolean {
        val ctor = glueCtor ?: return false
        return try {
            level.addFreshEntity(ctor.newInstance(level, box) as Entity)
        } catch (e: Throwable) {
            InfraCraft.LOGGER.warn("[InfraCraft] 초강력 접착제를 붙이지 못했습니다", e)
            false
        }
    }

    // ───────────── 트랙 블록 ─────────────

    private val track: Block? by lazy { if (loaded) Compat.blockById("create", "track") else null }

    fun trackBlock(): Block? = track

    /** Create 트랙의 모양: 평지는 xo/zo, 경사는 오르는 쪽 방향(an/as/ae/aw) */
    fun trackState(block: Block, dir: Direction, slope: Int): BlockState {
        val shape = when (slope) {
            0 -> if (dir.axis == Direction.Axis.X) "xo" else "zo"
            1 -> "a" + dir.name.lowercase()[0]
            else -> "a" + dir.opposite.name.lowercase()[0]
        }
        return withProperty(block.defaultBlockState(), "shape", shape)
    }

    fun withProperty(state: BlockState, name: String, value: String): BlockState {
        val prop = state.block.stateDefinition.getProperty(name) ?: return state
        @Suppress("UNCHECKED_CAST")
        return setValue(state, prop as Property<Comparable<Any>>, value)
    }

    private fun <T : Comparable<T>> setValue(state: BlockState, prop: Property<T>, value: String): BlockState {
        val v = prop.getValue(value).orElse(null) ?: return state
        return state.setValue(prop, v)
    }

    private val stationBlock: Block? by lazy { if (loaded) Compat.blockById("create", "track_station") else null }

    /** Create 기차역 블록인가(클라이언트에서도 같은 판단을 하도록 블록으로만 본다) */
    fun isStationBlock(state: BlockState): Boolean = stationBlock?.let { state.block == it } ?: false

    // ───────────── 기차와 역 ─────────────

    private class TrainState {
        var at: String? = null
        var departed: String? = null
        var departTick = 0L
        var distance = 0.0
    }

    private val trains = HashMap<UUID, TrainState>()

    private fun manager(): Any? = try { railways.get(null) } catch (_: Throwable) { null }

    /** Create 역을 노선망 역으로 등록(또는 이름을 맞춤)하고 돌려준다. */
    private fun upsert(data: NetworkData, station: Any): StationRec? {
        val pos = stationPos.invoke(station) as? BlockPos ?: return null
        val dimKey = stationDim.invoke(station) as? ResourceKey<*> ?: return null
        val dim = Compat.keyId(dimKey)
        val key = NetworkData.key(dim, pos)
        val name = (stationName.get(station) as? String)?.takeIf { it.isNotBlank() } ?: "Create ${pos.x},${pos.z}"
        val rec = data.station(key) ?: StationRec().apply {
            this.dim = dim; x = pos.x; y = pos.y; z = pos.z; source = StationRec.CREATE
            data.stations[key] = this
        }
        if (rec.name != name) {
            val clash = data.stationByName(name)
            rec.name = if (clash != null && clash !== rec) "$name (Create)" else name
        }
        return rec
    }

    fun tick(server: MinecraftServer) {
        if (!ready) return
        val manager = manager() ?: return
        val map = trainsField.get(manager) as? Map<*, *> ?: return
        val now = server.overworld().gameTime
        val data = Network.data(server)
        try {
            for ((id, train) in map) {
                if (id !is UUID || train == null) continue
                val st = trains.getOrPut(id) { TrainState() }
                st.distance += abs(trainSpeed.getDouble(train))
                val station = currentStation.invoke(train)
                if (station != null) {
                    val rec = upsert(data, station) ?: continue
                    if (st.at == rec.key) continue
                    st.at = rec.key
                    val from = st.departed
                    val seconds = (now - st.departTick) / 20.0
                    if (from != null && from != rec.key && seconds > 0.5 && st.distance > 4.0 && data.station(from) != null) {
                        data.putEdge(EdgeRec().apply {
                            this.from = from; to = rec.key; mode = "create_train"
                            distance = st.distance; this.seconds = seconds; recordedAt = now
                        })
                    }
                } else if (st.at != null) {
                    st.departed = st.at
                    st.at = null
                    st.departTick = now
                    st.distance = 0.0
                }
            }
            trains.keys.retainAll(map.keys)
            if (now % 200 == 0L) syncStations(data, manager)
        } catch (e: Throwable) {
            ready = false
            InfraCraft.LOGGER.warn("[InfraCraft] Create 기차 정보를 읽다가 오류가 나서 연동을 멈춥니다", e)
        }
    }

    /** 선로망의 모든 Create 역을 등록하고, 사라진 역은 노선망에서 뺀다. */
    private fun syncStations(data: NetworkData, manager: Any) {
        val networks = networksField.get(manager) as? Map<*, *> ?: return
        val alive = HashSet<String>()
        for (graph in networks.values) {
            val points = getPoints.invoke(graph, stationType) as? Collection<*> ?: continue
            for (p in points) if (p != null) upsert(data, p)?.let { alive.add(it.key) }
        }
        val gone = data.stations.values.filter { it.source == StationRec.CREATE && it.key !in alive }.map { it.key }
        gone.forEach { data.removeAt(it) }
    }

    /** CI 스모크 테스트용 */
    fun selfCheck(): Boolean = ready && manager() != null && trackBlock() != null

    /** CI 스모크 테스트용: 차량 도면이 쓰는 블록과 접착제 */
    fun carCheck(): List<String> {
        val missing = CreateCars.requiredBlocks().filter { Compat.blockById("create", it) == null }.toMutableList()
        if (glueCtor == null) missing.add("SuperGlueEntity(Level, AABB)")
        return missing
    }
}
