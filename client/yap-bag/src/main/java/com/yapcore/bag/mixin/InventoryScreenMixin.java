package com.yapcore.bag.mixin;

import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory "Bag" tab. Uses {@link Screen} size only — no @Shadow of
 * AbstractContainerScreen fields (those fail without a mixin refmap on 26.2).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends Screen {

    protected InventoryScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yap$bagTab(CallbackInfo ci) {
        if (!YapBagClient.config().enabled || !YapBagClient.config().inventoryTab) {
            return;
        }
        // Vanilla inventory panel is centered ~176x166; place tab to its right.
        int x = this.width / 2 + 90;
        int y = this.height / 2 - 76;
        this.addRenderableWidget(Button.builder(Component.literal("Bag"), button -> YapBagClient.requestOpen(0))
                .bounds(x, y, 40, 20)
                .build());
    }
}
