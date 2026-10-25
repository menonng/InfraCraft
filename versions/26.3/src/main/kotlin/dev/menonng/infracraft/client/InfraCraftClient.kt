package dev.menonng.infracraft.client

import net.fabricmc.api.ClientModInitializer

/** 26.x 는 텍스처의 투명도로 렌더 레이어를 정하므로 따로 등록할 것이 없다. */
object InfraCraftClient : ClientModInitializer {
    override fun onInitializeClient() {}
}
