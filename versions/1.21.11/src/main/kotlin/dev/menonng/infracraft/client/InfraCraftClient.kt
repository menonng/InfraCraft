package dev.menonng.infracraft.client

import dev.menonng.infracraft.content.ModBlocks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.chunk.ChunkSectionLayer

object InfraCraftClient : ClientModInitializer {
    override fun onInitializeClient() {
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, ModBlocks.HIGH_SPEED_RAIL, ModBlocks.EXPRESS_RAIL, ModBlocks.BLAZE_RAIL)
    }
}
