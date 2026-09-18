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
 * When HUD Hor+ uses the world VFOV, scale the viewmodel so held items stay
 * on screen (same NDC size as vanilla 70° HUD).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void yap$viewmodelScale(float tickDelta, PoseStack poseStack,
                                    SubmitNodeCollector submitNodeCollector,
                                    LocalPlayer player, int packedLight,
                                    CallbackInfo ci) {
        float scale = YapUltrawide.viewmodelScale();
        if (scale != 1.0f && poseStack != null) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
