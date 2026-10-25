package dev.menonng.infracraft

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** config/infracraft.json 에 저장되는 서버 설정. 파일이 없으면 기본값으로 만든다. */
class InfraConfig {
    // ── 제한 규칙 ──
    var banElytra = true
    var netherOneToOne = true

    // ── 도로 ── (이동 속도에 곱해지는 보너스, 등급 1/2/3)
    var roadTierBonus = listOf(0.08, 0.18, 0.32)
    var roadRampPerTick = 0.012
    var roadDecayPerTick = 0.03

    // ── 철도 ── (블록/틱, 바닐라 마인카트 최고 속도는 0.4)
    var highSpeedRailMax = 0.6
    var expressRailMax = 0.8
    var blazeRailMax = 1.1
    var railBoostPerTick = 0.06
    var curveSpeedCap = 0.4
    var blazeFuelBlocks = 128
    var cartChunkLoading = true

    // ── 노선망과 급행 ──
    var expressCompression = 3.0
    var expressMinSeconds = 3
    var stationScanRadius = 16

    // ── 건설 장비 ──
    var paverBaseWidth = 3
    var borerBaseRadius = 1
    var borerMaxHardness = 30.0
    var bridgeMaxLength = 96

    // ── 지하철 ──
    var elevatorSpeed = 0.35
    var elevatorMaxHeight = 200

    // ── 말 ──
    var whistleRange = 96.0

    // ── 수운 ──
    var currentStrength = 0.05
    var currentMaxSpeed = 0.7
    var liftSpeed = 0.28
    var liftMaxHeight = 24
    var iceBoatSpeedCap = 0.0

    /** 바닐라 인챈트 최대 레벨 확장. 값이 바닐라보다 낮으면 무시된다. */
    var extendedEnchantLevels = mapOf(
        "minecraft:soul_speed" to 5,
        "minecraft:frost_walker" to 3,
        "minecraft:feather_falling" to 5,
    )
}

object Config {
    var c = InfraConfig()
        private set

    /** 번역 키(enchantment.minecraft.soul_speed) → 확장된 최대 레벨 */
    var enchantLevelByKey: Map<String, Int> = emptyMap()
        private set

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        val path = FabricLoader.getInstance().configDir.resolve("infracraft.json")
        c = try {
            if (Files.exists(path)) gson.fromJson(Files.readString(path), InfraConfig::class.java) ?: InfraConfig()
            else InfraConfig()
        } catch (e: Exception) {
            InfraCraft.LOGGER.error("[InfraCraft] config/infracraft.json 을 읽지 못해 기본값을 씁니다", e)
            InfraConfig()
        }
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, gson.toJson(c))
        } catch (e: Exception) {
            InfraCraft.LOGGER.warn("[InfraCraft] 설정 파일을 쓰지 못했습니다", e)
        }
        enchantLevelByKey = c.extendedEnchantLevels.mapKeys { (id, _) ->
            val (ns, path2) = if (':' in id) id.split(':', limit = 2) else listOf("minecraft", id)
            "enchantment.$ns.$path2"
        }
    }
}
