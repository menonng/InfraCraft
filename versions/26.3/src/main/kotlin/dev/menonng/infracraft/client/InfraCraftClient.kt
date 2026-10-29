package dev.menonng.infracraft.client

import net.fabricmc.api.ClientModInitializer

/** 26.x 는 텍스처의 투명도로 렌더 레이어를 정하므로 블록 렌더 레이어는 따로 등록하지 않는다. */
object InfraCraftClient : ClientModInitializer {
    override fun onInitializeClient() = InfraClient.init()
}
