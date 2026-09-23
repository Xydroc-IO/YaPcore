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
     * Hands use {@code calculateHudFov}. Default leaves that value alone so the
     * weapon stays in the corner. {@code affectHudFov} opts into world Hor+.
     */
    @ModifyReturnValue(method = "calculateHudFov(F)F", at = @At("RETURN"))
    private float yap$horPlusHud(float original) {
        return YapUltrawide.applyHud(original);
    }
}
