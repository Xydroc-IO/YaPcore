package com.yapcore.presence.mixin;

import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yap$presenceKey(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player == null || self.level == null) {
            return;
        }
        if (!YapPresenceClient.config().enabled || !YapPresenceClient.config().keybind) {
            return;
        }
        if (self.gui.screen() instanceof ChatScreen) {
            return;
        }
        if (self.gui.screen() != null) {
            return;
        }
        if (YapPresenceClient.openKey().consumeClick()) {
            YapPresenceClient.openHub();
        }
    }
}
