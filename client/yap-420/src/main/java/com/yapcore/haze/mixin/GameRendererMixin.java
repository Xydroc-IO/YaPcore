package com.yapcore.haze.mixin;

import com.yapcore.haze.Yap420Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks haze leaf particles while an active HAZE payload is in effect. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void yap$hazeTick(CallbackInfo ci) {
        Yap420Client.effect().tickParticles(Minecraft.getInstance());
    }
}
