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
        return YapUltrawide.apply(original);
    }

    @ModifyReturnValue(method = "calculateHudFov(F)F", at = @At("RETURN"))
    private float yap$horPlusHud(float original) {
        if (!YapUltrawide.config().affectHudFov) {
            return original;
        }
        return YapUltrawide.apply(original);
    }
}
