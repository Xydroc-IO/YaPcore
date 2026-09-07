package com.yapcore.staff.mixin;

import com.yapcore.staff.YapStaffClient;
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
    private void yap$staffButton(CallbackInfo ci) {
        if (!YapStaffClient.config().enabled || !YapStaffClient.config().pauseButton) {
            return;
        }
        int w = 204;
        int x = this.width / 2 - w / 2;
        int y = this.height / 4 + 120 + 24;
        this.addRenderableWidget(Button.builder(Component.literal("Staff menu"), b -> YapStaffClient.openHub())
                .bounds(x, y, w, 20)
                .build());
    }
}
