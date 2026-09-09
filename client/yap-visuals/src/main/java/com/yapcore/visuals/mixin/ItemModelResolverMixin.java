package com.yapcore.visuals.mixin;

import com.yapcore.visuals.rainbow.RainbowItemNbt;
import com.yapcore.visuals.rainbow.RainbowRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void yap$markRainbow(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo ci) {
        ((RainbowRenderState) (Object) state).yap$setRainbow(RainbowItemNbt.isRainbow(stack));
    }
}
