package com.yapcore.bag.mixin;

import com.yapcore.bag.YapBagClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yap$bagKey(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player == null || self.level == null) {
            return;
        }
        if (self.gui.screen() instanceof ChatScreen) {
            return;
        }
        if (YapBagClient.openKey().consumeClick()) {
            YapBagClient.requestOpen(0);
        }
    }
}
