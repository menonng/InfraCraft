package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.system.Hooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 등급 선로 위에서 마인카트 최고 속도를 올린다(커브 앞에서는 다시 낮춘다). */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {
	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true, require = 0)
	private void infracraft$tierSpeed(CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(Hooks.minecartMaxSpeed((Entity) (Object) this, cir.getReturnValue()));
	}
}
