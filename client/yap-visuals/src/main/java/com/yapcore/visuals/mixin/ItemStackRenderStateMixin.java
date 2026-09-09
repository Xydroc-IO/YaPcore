package com.yapcore.visuals.mixin;

import com.yapcore.visuals.rainbow.RainbowRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements RainbowRenderState {

    @Unique
    private boolean yap$rainbow;

    @Override
    public void yap$setRainbow(boolean rainbow) {
        this.yap$rainbow = rainbow;
    }

    @Override
    public boolean yap$isRainbow() {
        return this.yap$rainbow;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void yap$clearRainbow(CallbackInfo ci) {
        this.yap$rainbow = false;
    }
}
