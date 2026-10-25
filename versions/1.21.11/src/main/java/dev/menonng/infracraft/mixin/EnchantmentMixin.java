package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.system.Hooks;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 설정에 적힌 바닐라 인챈트의 최대 레벨을 늘린다(인챈트 테이블·모루에 그대로 반영된다). */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true, require = 0)
	private void infracraft$extendMaxLevel(CallbackInfoReturnable<Integer> cir) {
		if (((Enchantment) (Object) this).description().getContents() instanceof TranslatableContents contents) {
			cir.setReturnValue(Hooks.extendedMaxLevel(contents.getKey(), cir.getReturnValue()));
		}
	}
}
