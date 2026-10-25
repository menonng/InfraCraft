package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.system.Hooks;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 네더 포탈 좌표 배율(8배)을 없애 1:1 로 이동하게 한다. */
@Mixin(DimensionType.class)
public abstract class DimensionTypeMixin {
	@Inject(method = "getTeleportationScale", at = @At("HEAD"), cancellable = true, require = 0)
	private static void infracraft$oneToOne(CallbackInfoReturnable<Double> cir) {
		if (Hooks.netherOneToOne()) cir.setReturnValue(1.0);
	}
}
