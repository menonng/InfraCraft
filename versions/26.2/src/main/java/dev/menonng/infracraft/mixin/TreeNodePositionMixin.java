package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.system.AstraAdvancements;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.TreeNodePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 바닐라의 나무 배치가 끝난 뒤, 독수리자리 탭만 원형으로 다시 놓는다. */
@Mixin(TreeNodePosition.class)
public class TreeNodePositionMixin {
    @Inject(method = "run", at = @At("TAIL"))
    private static void infracraft$astraLayout(AdvancementNode root, CallbackInfo ci) {
        AstraAdvancements.layout(root);
    }
}
