package com.yapcore.visuals.mixin;

import com.yapcore.visuals.rainbow.RainbowTint;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {

    @Inject(method = "prepareMainSubmit", at = @At("HEAD"))
    private void yap$beginRainbowSubmit(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        RainbowTint.beginSubmit(submit.quads());
    }

    @Inject(method = "prepareMainSubmit", at = @At("RETURN"))
    private void yap$endRainbowSubmit(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        RainbowTint.endSubmit();
    }

    @ModifyArg(
            method = "prepareMainSubmit",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V"
            ),
            index = 0
    )
    private int yap$rainbowMeshColor(int original) {
        if (RainbowTint.isCurrentSubmitTagged()) {
            return RainbowTint.applyToTint(original);
        }
        return original;
    }

    @Inject(method = "buildGroup", at = @At("RETURN"))
    private void yap$clearRainbowTags(CallbackInfo ci) {
        RainbowTint.clearTags();
    }
}
