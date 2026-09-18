package com.yapcore.ultrawide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.yapcore.ultrawide.YapUltrawide;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hor+ lowers vertical FOV (zoom). Vanilla view-bob is a fixed camera
 * translation, so the world slides under a stable HUD crosshair while
 * block picking still uses the un-bobbed look vector. Scale bob with the
 * FOV ratio so placement matches where you are aiming.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyExpressionValue(
            method = "bobView",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F"
            )
    )
    private float yap$stabilizeBob(float bob) {
        return bob * YapUltrawide.aimStabilizeScale();
    }
}
