package com.yapcore.ultrawide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.yapcore.ultrawide.YapUltrawide;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyReturnValue(method = "calculateFov(F)F", at = @At("RETURN"))
    private float yap$horPlusWorld(float original) {
        return YapUltrawide.applyWorld(original);
    }

    /**
     * Hands use {@code calculateHudFov}. Matching world Hor+ VFOV keeps the
     * held item in the same frustum as block picking; viewmodel scale (see
     * {@code ItemInHandRendererMixin}) keeps weapons on screen.
     */
    @ModifyReturnValue(method = "calculateHudFov(F)F", at = @At("RETURN"))
    private float yap$horPlusHud(float original) {
        return YapUltrawide.applyHud(original);
    }
}
