package com.yapcore.ultrawide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.yapcore.ultrawide.PaniniPass;
import com.yapcore.ultrawide.YapUltrawide;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Widens the world projection, then compresses the edges back onto the screen
 * before the hand is drawn. The weapon stays on the vanilla HUD camera.
 * View-bob is scaled with the FOV change so the world does not slide under
 * the crosshair.
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

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void yap$widenForPanini(DeltaTracker deltaTracker, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        PaniniPass.widen(renderer.gameRenderState().levelRenderState.cameraRenderState.projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void yap$paniniWorld(DeltaTracker deltaTracker, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        PaniniPass.apply(renderer.mainRenderTarget());
    }
}
