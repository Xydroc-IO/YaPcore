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
 * When HUD Hor+ uses the world VFOV, scale + nudge the viewmodel so held items
 * stay on screen on extreme ultrawide (same NDC size as vanilla 70° HUD, pulled
 * inward from the bottom-right clip).
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
        float scale = YapUltrawide.viewmodelScale();
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
