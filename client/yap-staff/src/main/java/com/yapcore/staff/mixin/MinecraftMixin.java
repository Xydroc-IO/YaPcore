package com.yapcore.staff.mixin;

import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yap$staffKey(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player == null || self.level == null) {
            return;
        }
        if (!YapStaffClient.config().enabled || !YapStaffClient.config().keybind) {
            return;
        }
        if (self.gui.screen() instanceof ChatScreen) {
            return;
        }
        if (self.gui.screen() != null) {
            return;
        }
        if (YapStaffClient.openKey().consumeClick()) {
            YapStaffClient.openHub();
        }
    }
}
