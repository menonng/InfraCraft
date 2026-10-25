package dev.menonng.infracraft.content

import com.mojang.serialization.Codec
import dev.menonng.infracraft.system.NetworkData
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.nbt.CompoundTag

/** Fabric 데이터 첨부로 저장하는 상태. 버전마다 다른 BlockEntity/SavedData 직렬화를 피하려고 전부 여기에 둔다. */
object ModAttachments {
    /** 마인카트: 화물 운송장으로 정한 목적지 */
    val CART_DESTINATION: AttachmentType<String> = AttachmentRegistry.create(id("cart_destination")) { it.persistent(Codec.STRING) }

    /** 마인카트: 남은 블레이즈 연료(블록 수) */
    val BLAZE_FUEL: AttachmentType<Int> = AttachmentRegistry.create(id("blaze_fuel")) { it.persistent(Codec.INT) }

    /** 말: 편자 등급(1 = 철, 2 = 다이아몬드) */
    val HORSESHOE: AttachmentType<Int> = AttachmentRegistry.create(id("horseshoe")) { it.persistent(Codec.INT) }

    /** 오버월드: 역·노선·분기기 등 노선망 전체 */
    val NETWORK: AttachmentType<NetworkData> = AttachmentRegistry.create(id("network")) {
        it.persistent(Codec.STRING.xmap({ s -> NetworkData.fromJson(s) }, { d -> d.toJson() }))
    }

    /** 오버월드: 마구간 위치 키 → 보관 중인 말 NBT */
    val STABLES: AttachmentType<Map<String, CompoundTag>> = AttachmentRegistry.create(id("stables")) {
        it.persistent(Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC))
    }

    fun init() {}
}
