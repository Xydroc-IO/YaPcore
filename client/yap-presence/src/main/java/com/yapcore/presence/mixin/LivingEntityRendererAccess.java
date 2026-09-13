package com.yapcore.presence.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * addLayer is declared on LivingEntityRenderer; AvatarRendererMixin cannot @Shadow it
 * (Mixin does not resolve inherited shadows here without a refmap).
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {

    @Invoker("addLayer")
    boolean yap$addLayer(RenderLayer<?, ?> layer);
}
