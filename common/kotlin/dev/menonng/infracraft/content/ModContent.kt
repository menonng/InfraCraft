package dev.menonng.infracraft.content

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.menonng.infracraft.block.CurrentBlock
import dev.menonng.infracraft.block.MilestoneBlock
import dev.menonng.infracraft.block.RailSwitchBlock
import dev.menonng.infracraft.block.RailTier
import dev.menonng.infracraft.block.ScreenDoorBlock
import dev.menonng.infracraft.block.StationBlock
import dev.menonng.infracraft.block.TickedBlock
import dev.menonng.infracraft.block.TierRailBlock
import dev.menonng.infracraft.compat.Compat
import dev.menonng.infracraft.compat.Id
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey
import net.minecraft.stats.StatFormatter
import net.minecraft.stats.Stats
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.RangedAttribute
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import java.util.UUID

const val MOD_ID = "infracraft"
fun id(path: String): Id = Compat.id(MOD_ID, path)

object ModBlocks {
    // 도로
    val ASPHALT = Compat.block("asphalt", Properties.ofFullCopy(Blocks.STONE), ::Block)
    val ROAD_LINE = Compat.block("road_line", Properties.ofFullCopy(Blocks.STONE), ::Block)
    val STREET_LAMP = Compat.block("street_lamp", Properties.ofFullCopy(Blocks.REDSTONE_LAMP).lightLevel { 15 }, ::Block)
    val SIGNPOST = Compat.block("signpost", Properties.ofFullCopy(Blocks.OAK_PLANKS), ::Block)
    // 옛 길: 아스팔트와 같은 3등급이지만 손으로 놓은 돌의 정취
    val SETTS = Compat.block("setts", Properties.ofFullCopy(Blocks.POLISHED_GRANITE), ::Block)
    val BRICK_PAVING = Compat.block("brick_paving", Properties.ofFullCopy(Blocks.BRICKS), ::Block)
    val FLAGSTONE = Compat.block("flagstone", Properties.ofFullCopy(Blocks.STONE_BRICKS), ::Block)
    val MILESTONE = Compat.block("milestone", Properties.ofFullCopy(Blocks.STONE_BRICKS).noOcclusion(), ::MilestoneBlock)

    // 철도
    val HIGH_SPEED_RAIL = Compat.block("high_speed_rail", Properties.ofFullCopy(Blocks.POWERED_RAIL)) { TierRailBlock(it, RailTier.HIGH_SPEED) }
    val EXPRESS_RAIL = Compat.block("express_rail", Properties.ofFullCopy(Blocks.POWERED_RAIL)) { TierRailBlock(it, RailTier.EXPRESS) }
    val BLAZE_RAIL = Compat.block("blaze_rail", Properties.ofFullCopy(Blocks.POWERED_RAIL).lightLevel { 7 }) { TierRailBlock(it, RailTier.BLAZE) }
    val STATION = Compat.block("station", Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE), ::StationBlock)
    val RAIL_SWITCH = Compat.block("rail_switch", Properties.ofFullCopy(Blocks.SMOOTH_STONE), ::RailSwitchBlock)

    // 수운
    val WATER_CURRENT = Compat.block("water_current", Properties.ofFullCopy(Blocks.PRISMARINE), ::CurrentBlock)
    val BOAT_LIFT = Compat.block("boat_lift", Properties.ofFullCopy(Blocks.PRISMARINE_BRICKS)) { TickedBlock(it, TickedBlock.LIFT) }

    // 지하철
    val TUNNEL_LINING = Compat.block("tunnel_lining", Properties.ofFullCopy(Blocks.SMOOTH_STONE), ::Block)
    val TUNNEL_LIGHT = Compat.block("tunnel_light", Properties.ofFullCopy(Blocks.SEA_LANTERN).lightLevel { 15 }, ::Block)
    val PLATFORM_EDGE = Compat.block("platform_edge", Properties.ofFullCopy(Blocks.SMOOTH_STONE), ::Block)
    val STATION_ELEVATOR = Compat.block("station_elevator", Properties.ofFullCopy(Blocks.IRON_BLOCK)) { TickedBlock(it, TickedBlock.ELEVATOR) }
    val SCREEN_DOOR = Compat.block("screen_door", Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion(), ::ScreenDoorBlock)

    // 항구
    val TRADING_POST = Compat.block("trading_post", Properties.ofFullCopy(Blocks.BARREL), ::Block)

    // 말
    val STABLE = Compat.block("stable", Properties.ofFullCopy(Blocks.HAY_BLOCK), ::Block)

    fun init() {}
}

object ModItems {
    val TICKET = Compat.item("ticket", Item.Properties().stacksTo(16))
    val ROAD_PAVER = Compat.item("road_paver", Compat.toolProperties(2048))
    val TRACK_LAYER = Compat.item("track_layer", Compat.toolProperties(2048))
    val TUNNEL_BORER = Compat.item("tunnel_borer", Compat.toolProperties(1561))
    val SURVEYOR = Compat.item("surveyor", Item.Properties().stacksTo(1))
    val WHISTLE = Compat.item("whistle", Item.Properties().stacksTo(1))
    val IRON_HORSESHOE = Compat.item("iron_horseshoe", Item.Properties().stacksTo(16))
    val DIAMOND_HORSESHOE = Compat.item("diamond_horseshoe", Item.Properties().stacksTo(16))
    val ENDER_RAIL_CORE = Compat.item("ender_rail_core", Item.Properties().stacksTo(64))
    val SUBWAY_SHIELD = Compat.item("subway_shield", Compat.toolProperties(4096))
    val STATION_BLUEPRINT = Compat.item("station_blueprint", Item.Properties().stacksTo(16))
    val GROUND_STATION_BLUEPRINT = Compat.item("ground_station_blueprint", Item.Properties().stacksTo(16))
    val HARBOR_BLUEPRINT = Compat.item("harbor_blueprint", Item.Properties().stacksTo(16))
    val METRO_CAR_BLUEPRINT = Compat.item("metro_car_blueprint", Item.Properties().stacksTo(16))
    val COACH_BLUEPRINT = Compat.item("coach_blueprint", Item.Properties().stacksTo(16))
    val CARGO_CRATE = Compat.item("cargo_crate", Item.Properties().stacksTo(1))

    fun init() {
        Compat.creativeTab("main", { ItemStack(ModBlocks.EXPRESS_RAIL) }) {
            listOf(
                ModBlocks.SETTS, ModBlocks.BRICK_PAVING, ModBlocks.FLAGSTONE, ModBlocks.MILESTONE,
                ModBlocks.ASPHALT, ModBlocks.ROAD_LINE, ModBlocks.STREET_LAMP, ModBlocks.SIGNPOST,
                ModBlocks.HIGH_SPEED_RAIL, ModBlocks.EXPRESS_RAIL, ModBlocks.BLAZE_RAIL,
                ModBlocks.STATION, ModBlocks.RAIL_SWITCH,
                ModBlocks.WATER_CURRENT, ModBlocks.BOAT_LIFT, ModBlocks.TRADING_POST, ModBlocks.STABLE,
                ModBlocks.TUNNEL_LINING, ModBlocks.TUNNEL_LIGHT, ModBlocks.PLATFORM_EDGE, ModBlocks.SCREEN_DOOR, ModBlocks.STATION_ELEVATOR,
                SUBWAY_SHIELD, STATION_BLUEPRINT, GROUND_STATION_BLUEPRINT, HARBOR_BLUEPRINT,
                METRO_CAR_BLUEPRINT, COACH_BLUEPRINT, CARGO_CRATE,
                TICKET, ROAD_PAVER, TRACK_LAYER, TUNNEL_BORER, SURVEYOR, WHISTLE,
                IRON_HORSESHOE, DIAMOND_HORSESHOE, ENDER_RAIL_CORE,
            )
        }
    }
}

/** 아이템에 붙는 데이터 컴포넌트 */
object ModComponents {
    /** 승차권·화물 운송장의 목적지 역 이름 */
    val DESTINATION: DataComponentType<String> = reg("destination", Codec.STRING, ByteBufCodecs.STRING_UTF8)
    /** 도로 포장기·선로 부설기 작동 여부 */
    val ACTIVE: DataComponentType<Boolean> = reg("active", Codec.BOOL, ByteBufCodecs.BOOL)
    /** 측량기의 첫 번째 지점 */
    val POINT_A: DataComponentType<BlockPos> = reg("point_a", BlockPos.CODEC, BlockPos.STREAM_CODEC)
    /** 호루라기에 묶인 말 */
    val BOUND_ENTITY: DataComponentType<UUID> = reg("bound_entity", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC)
    /** 화물 상자에 실린 짐(출발 항구와 물품) */
    val CARGO: DataComponentType<Cargo> = reg("cargo", Cargo.CODEC, ByteBufCodecs.fromCodec(Cargo.CODEC))

    private fun <T : Any> reg(name: String, codec: Codec<T>, stream: StreamCodec<in RegistryFriendlyByteBuf, T>): DataComponentType<T> =
        Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, id(name),
            DataComponentType.builder<T>().persistent(codec).networkSynchronized(stream).build(),
        )

    fun init() {}
}

/** 화물 상자 내용: 출발 항구(키·이름·차원·좌표·지역)와 물품 ID·수량 */
data class Cargo(
    val harbor: String, val harborName: String, val dim: String, val x: Int, val z: Int,
    val region: String, val item: String, val count: Int,
) {
    companion object {
        val CODEC: Codec<Cargo> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("harbor").forGetter(Cargo::harbor),
                Codec.STRING.fieldOf("harbor_name").forGetter(Cargo::harborName),
                Codec.STRING.fieldOf("dim").forGetter(Cargo::dim),
                Codec.INT.fieldOf("x").forGetter(Cargo::x),
                Codec.INT.fieldOf("z").forGetter(Cargo::z),
                Codec.STRING.fieldOf("region").forGetter(Cargo::region),
                Codec.STRING.fieldOf("item").forGetter(Cargo::item),
                Codec.INT.fieldOf("count").forGetter(Cargo::count),
            ).apply(i, ::Cargo)
        }
    }
}

/** 사용자 통계(통계 화면 > 일반 탭) */
object ModStats {
    val ROAD_ONE_CM = stat("road_one_cm", StatFormatter.DISTANCE)
    val STATIONS_ARRIVED = stat("stations_arrived", StatFormatter.DEFAULT)
    val ROUTES_SURVEYED = stat("routes_surveyed", StatFormatter.DEFAULT)
    val TRACK_PLACED = stat("track_placed", StatFormatter.DEFAULT)
    val ROAD_PAVED = stat("road_paved", StatFormatter.DEFAULT)
    val BLOCKS_BORED = stat("blocks_bored", StatFormatter.DEFAULT)
    val HORSES_CALLED = stat("horses_called", StatFormatter.DEFAULT)
    val CARGO_DELIVERED = stat("cargo_delivered", StatFormatter.DEFAULT)
    val TRADE_ONE_CM = stat("trade_one_cm", StatFormatter.DISTANCE)

    private fun stat(name: String, formatter: StatFormatter): Id {
        val key = id(name)
        Registry.register(BuiltInRegistries.CUSTOM_STAT, key, key)
        Stats.CUSTOM.get(key, formatter)
        return key
    }

    fun init() {}
}

/** 엔티티 속성. 로드러너 인챈트가 이 값을 올린다. */
object ModAttributes {
    val ROAD_AFFINITY: Holder<Attribute> = Registry.registerForHolder(
        BuiltInRegistries.ATTRIBUTE, id("road_affinity"),
        RangedAttribute("attribute.name.infracraft.road_affinity", 0.0, 0.0, 4.0).setSyncable(true),
    )

    fun init() {}
}

object ModEnchantments {
    val ROADRUNNER = key("roadrunner")
    val MOMENTUM = key("momentum")
    val EQUESTRIAN = key("equestrian")
    val CONDUCTOR = key("conductor")
    val WIDE_PAVING = key("wide_paving")
    val BORE_RADIUS = key("bore_radius")
    val THRIFT = key("thrift")

    private fun key(name: String): ResourceKey<Enchantment> = ResourceKey.create(Registries.ENCHANTMENT, id(name))
}

object ModTags {
    val ROAD_TIER_1 = block("road_tier_1")
    val ROAD_TIER_2 = block("road_tier_2")
    val ROAD_TIER_3 = block("road_tier_3")
    val PAVEABLE = block("paveable")
    val ICE = TagKey.create(Registries.BLOCK, Compat.id("minecraft", "ice"))
    val RAIL_ITEMS = TagKey.create(Registries.ITEM, Compat.id("minecraft", "rails"))

    private fun block(name: String): TagKey<Block> = TagKey.create(Registries.BLOCK, id(name))
}
