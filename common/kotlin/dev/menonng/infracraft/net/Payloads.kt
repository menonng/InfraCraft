package dev.menonng.infracraft.net

import dev.menonng.infracraft.content.id
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * 서버 ↔ 클라이언트 패킷.
 *  - 다리 미리보기(서버 → 클라이언트): 놓일 블록과 상태, 재료가 충분한지. 블록이 비어 있으면 지운다.
 *  - 별빛 검격(클라이언트 → 서버): 아스트라 오브리터레이터로 공격 키를 눌렀다.
 *  - 별빛 낙하(클라이언트 → 서버): 조준한 지점(시야) 또는 지도에서 고른 지점에 발동.
 *  - 아스트라 연출(서버 → 클라이언트): 검격·레일건·아홉 별이 시작됐다. 모양은 클라이언트가 [dev.menonng.infracraft.system.AstraShape] 로 그린다.
 */
object Payloads {
    private fun <T : CustomPacketPayload> type(name: String): CustomPacketPayload.Type<T> = CustomPacketPayload.Type(id(name))

    /** 미리보기 블록 하나: 위치, 블록 상태 ID, 역할(0 상판·난간 등, 1 기둥) */
    class Ghost(val pos: BlockPos, val state: Int, val role: Int)

    class BridgePreview(val ghosts: List<Ghost>, val enough: Boolean) : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            val TYPE: CustomPacketPayload.Type<BridgePreview> = type("bridge_preview")
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, BridgePreview> = CustomPacketPayload.codec(
                { p, buf ->
                    buf.writeBoolean(p.enough)
                    buf.writeVarInt(p.ghosts.size)
                    for (g in p.ghosts) {
                        buf.writeBlockPos(g.pos); buf.writeVarInt(g.state); buf.writeByte(g.role)
                    }
                },
                { buf ->
                    val enough = buf.readBoolean()
                    val n = buf.readVarInt()
                    BridgePreview(List(n) { Ghost(buf.readBlockPos(), buf.readVarInt(), buf.readByte().toInt()) }, enough)
                },
            )
        }
    }

    class AstraSlash : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            val TYPE: CustomPacketPayload.Type<AstraSlash> = type("astra_slash")
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, AstraSlash> = CustomPacketPayload.codec({ _, _ -> }, { AstraSlash() })
        }
    }

    /** mode 0 = 시야로 조준한 지점(x, y, z), mode 1 = 지도에서 고른 지점(x, z; 높이는 서버가 지면으로 정한다) */
    class AstraStrike(val mode: Int, val x: Double, val y: Double, val z: Double) : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            const val VIEW = 0
            const val MAP = 1
            val TYPE: CustomPacketPayload.Type<AstraStrike> = type("astra_strike")
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, AstraStrike> = CustomPacketPayload.codec(
                { p, buf -> buf.writeByte(p.mode); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z) },
                { buf -> AstraStrike(buf.readByte().toInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()) },
            )
        }
    }

    /** kind: 0 검격(origin, dir, roll), 1 레일건(eye, look, end), 2 아홉 별(목표, 별 아홉의 착탄점). caster 는 쓴 사람의 엔티티 ID */
    class AstraFx(val kind: Int, val caster: Int, val start: Long, val data: DoubleArray) : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            const val SLASH = 0
            const val RAILGUN = 1
            const val NINE_STARS = 2
            val TYPE: CustomPacketPayload.Type<AstraFx> = type("astra_fx")
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, AstraFx> = CustomPacketPayload.codec(
                { p, buf ->
                    buf.writeByte(p.kind); buf.writeVarInt(p.caster); buf.writeLong(p.start)
                    buf.writeVarInt(p.data.size); for (d in p.data) buf.writeDouble(d)
                },
                { buf ->
                    val kind = buf.readByte().toInt()
                    val caster = buf.readVarInt()
                    val start = buf.readLong()
                    val n = buf.readVarInt().coerceAtMost(256)
                    AstraFx(kind, caster, start, DoubleArray(n) { buf.readDouble() })
                },
            )
        }
    }

    fun register() {
        PayloadTypeRegistry.clientboundPlay().register(AstraFx.TYPE, AstraFx.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(BridgePreview.TYPE, BridgePreview.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AstraSlash.TYPE, AstraSlash.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AstraStrike.TYPE, AstraStrike.CODEC)
    }
}
