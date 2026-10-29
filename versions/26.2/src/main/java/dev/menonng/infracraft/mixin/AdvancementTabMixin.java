package dev.menonng.infracraft.mixin;

import dev.menonng.infracraft.system.AstraAdvancements;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 독수리자리 탭을 처음 열면 아퀼라 성좌핵이 창 가운데에 오게 한다.
 * 바닐라는 전체 범위의 가운데를 맞추는데, 위로 길게 뻗은 오브리터레이터 때문에 성좌핵이 아래로 밀려 아래쪽 별이 잘린다.
 */
@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin {
    @Shadow @Final private AdvancementNode rootNode;
    @Shadow private double scrollX;
    @Shadow private double scrollY;
    @Shadow private int minX;
    @Shadow private int maxX;
    @Shadow private boolean centered;

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void infracraft$centerOnCore(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        if (centered || !AstraAdvancements.INSTANCE.isAstra(rootNode)) return;
        // 창 안쪽 234x113, 노드 칸 28x27(아이콘 중심은 칸의 14, 13). 성좌핵은 (0, 0)
        scrollX = 117 - (maxX + minX) / 2.0;
        scrollY = 56 - 13;
        centered = true;
    }
}
