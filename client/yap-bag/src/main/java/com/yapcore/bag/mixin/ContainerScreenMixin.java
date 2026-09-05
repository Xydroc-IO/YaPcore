package com.yapcore.bag.mixin;

import com.yapcore.bag.BagTitle;
import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chest page tabs for YaP /bag.
 * Extends {@link Screen} and positions via screen size — no parent @Shadow fields.
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {

    protected ContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yap$pageTabs(CallbackInfo ci) {
        if (!YapBagClient.config().enabled || !YapBagClient.config().chestTabs) {
            return;
        }
        var state = BagTitle.parse(this.title.getString());
        if (state.isEmpty()) {
            return;
        }
        int pages = Math.min(9, state.get().pages());
        int current = state.get().page();
        // Centered above a standard 176-wide chest panel
        int baseX = this.width / 2 - 88;
        int y = this.height / 2 - 83 - 22;
        for (int page = 1; page <= pages; page++) {
            int target = page;
            String label = page == current ? "[" + page + "]" : String.valueOf(page);
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> YapBagClient.requestOpen(target))
                    .bounds(baseX + (page - 1) * 22, y, 20, 20)
                    .build());
        }
    }
}
