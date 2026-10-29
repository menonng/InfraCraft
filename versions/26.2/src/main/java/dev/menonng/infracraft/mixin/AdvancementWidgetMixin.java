package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.client.AstraTabRenderer;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 독수리자리 탭의 연결선과 배경은 직접 그린다(바닐라의 꺾인 선 대신 별자리 선). */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {
    @Shadow @Final private AdvancementNode advancementNode;

    @Inject(method = "extractConnectivity", at = @At("HEAD"), cancellable = true)
    private void infracraft$astraConnectivity(GuiGraphicsExtractor graphics, int x, int y, boolean shadow, CallbackInfo ci) {
        if (AstraTabRenderer.handles(advancementNode)) {
            AstraTabRenderer.connectivity(graphics, x, y, shadow, advancementNode);
            ci.cancel();
        }
    }
}
