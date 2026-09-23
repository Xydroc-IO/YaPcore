package com.yapcore.ultrawide.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yapcore.ultrawide.YapUltrawide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hor+ lowers the hand-camera VFOV, which drops held items onto the hotbar.
 * Scale Y so hands, blocks, and items keep the vanilla 70° gap above it.
 * A small X translate keeps the viewmodel on-screen on 32:9.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void yap$viewmodelAdjust(float tickDelta, PoseStack poseStack,
                                     SubmitNodeCollector submitNodeCollector,
                                     LocalPlayer player, int packedLight,
                                     CallbackInfo ci) {
        if (poseStack == null) {
            return;
        }
        float ox = YapUltrawide.viewmodelOffsetX();
        float oy = YapUltrawide.viewmodelOffsetY();
        if (ox != 0.0f || oy != 0.0f) {
            poseStack.translate(ox, oy, 0.0f);
        }
        float vertical = YapUltrawide.viewmodelVerticalScale();
        if (vertical != 1.0f) {
            poseStack.scale(1.0f, vertical, 1.0f);
        }
        float scale = YapUltrawide.viewmodelScale();
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
