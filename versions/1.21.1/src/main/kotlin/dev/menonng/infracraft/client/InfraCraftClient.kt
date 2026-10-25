package dev.menonng.infracraft.client

import dev.menonng.infracraft.content.ModBlocks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.RenderType

object InfraCraftClient : ClientModInitializer {
    override fun onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.HIGH_SPEED_RAIL, ModBlocks.EXPRESS_RAIL, ModBlocks.BLAZE_RAIL)
    }
}
