package com.yapcore.bag.mixin;

import com.yapcore.bag.BagTitle;
import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chest page tabs for YaP /bag.
 * Targets {@link AbstractContainerScreen#init()} — {@link ContainerScreen} does not
 * declare {@code init} in 26.2, so a direct mixin on ContainerScreen fails to apply.
 * <p>
 * Tab Y must use the real panel height ({@code 114 + rows*18}). Hardcoding the
 * 3-row chest offset places tabs on the first slot row of a 6-row bag.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_BASE_HEIGHT = 114;
    private static final int ROW_HEIGHT = 18;
    private static final int TAB_SIZE = 20;
    private static final int TAB_GAP = 2;
    private static final int TAB_STRIDE = 22;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yap$pageTabs(CallbackInfo ci) {
        if (!((Object) this instanceof ContainerScreen)) {
            return;
        }
        if (!YapBagClient.config().enabled || !YapBagClient.config().chestTabs) {
            return;
        }
        var state = BagTitle.parse(this.title.getString());
        if (state.isEmpty()) {
            return;
        }

        ContainerScreen chest = (ContainerScreen) (Object) this;
        ChestMenu menu = chest.getMenu();
        int rows = Math.max(1, menu.getRowCount());
        int panelHeight = PANEL_BASE_HEIGHT + rows * ROW_HEIGHT;
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - panelHeight) / 2;
        // Entirely above the chest panel (clears title + first slot row).
        int y = top - TAB_SIZE - TAB_GAP;

        int pages = Math.min(9, state.get().pages());
        int current = state.get().page();
        for (int page = 1; page <= pages; page++) {
            int target = page;
            String label = page == current ? "[" + page + "]" : String.valueOf(page);
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> YapBagClient.requestOpen(target))
                    .bounds(left + (page - 1) * TAB_STRIDE, y, TAB_SIZE, TAB_SIZE)
                    .build());
        }
    }
}
