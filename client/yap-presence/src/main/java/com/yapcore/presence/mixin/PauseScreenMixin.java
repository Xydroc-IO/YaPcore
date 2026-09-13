package com.yapcore.presence.mixin;

import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yap$tailorButton(CallbackInfo ci) {
        if (!YapPresenceClient.config().enabled || !YapPresenceClient.config().pauseButton) {
            return;
        }
        int w = 204;
        int x = this.width / 2 - w / 2;
        // One row below yap-staff's pause button (staff uses +120+24).
        int y = this.height / 4 + 120 + 48;
        this.addRenderableWidget(Button.builder(Component.literal("Tailor menu"), b -> YapPresenceClient.openHub())
                .bounds(x, y, w, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Skins, wardrobe, and emotes")))
                .build());
    }
}
