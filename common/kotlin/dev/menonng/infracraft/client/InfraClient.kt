package dev.menonng.infracraft.client

import dev.menonng.infracraft.system.Gradient
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.resources.language.I18n

/** 버전별 ClientModInitializer 가 부르는 공통 클라이언트 초기화 */
object InfraClient {
    fun init() {
        Gradient.resolver = { key -> I18n.get(key) }
        BridgeHologram.register()
        ExploredMap.register()
        AstraAim.register()
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> AstraMapScreen.Tiles.clear(); AstraAim.stop() }
    }
}
