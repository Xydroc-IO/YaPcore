package com.yapcore.visuals.mixin;

import com.yapcore.visuals.rainbow.RainbowRenderState;
import com.yapcore.visuals.rainbow.RainbowTint;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin {

    @Shadow
    @Final
    ItemStackRenderState this$0;

    @Shadow
    @Final
    private java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads;

    @Inject(method = "submit", at = @At("HEAD"))
    private void yap$tagRainbowQuads(CallbackInfo ci) {
        if (((RainbowRenderState) (Object) this$0).yap$isRainbow()) {
            RainbowTint.setActive(true);
            RainbowTint.tagQuads(this.quads);
            RainbowTint.clear();
        }
    }
}
