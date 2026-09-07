package com.yapcore.bag.mixin;

import com.yapcore.bag.BagTitle;
import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chest page tabs for YaP /bag.
 * Targets {@link AbstractContainerScreen#init()} — {@link ContainerScreen} does not
 * declare {@code init} in 26.2, so a direct mixin on ContainerScreen fails to apply.
 * <p>
 * Tab Y must use the real panel height ({@code 114 + rows*18}). Hardcoding the
 * 3-row chest offset places tabs on the first slot row of a 6-row bag.
 * <p>
 * B (open key) while this bag is open closes it — KeyMapping.consumeClick() does not
 * fire with a screen up.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_BASE_HEIGHT = 114;
    private static final int ROW_HEIGHT = 18;
    private static final int TAB_GAP = 1;
    private static final int TAB_MIN = 14;
    private static final int TAB_MAX = 20;

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
        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - panelHeight) / 2;

        int pages = Math.min(9, state.get().pages());
        int current = state.get().page();
        // Fit the whole tab strip to the chest panel width (9×20+gaps was overflowing).
        int tabW = Math.min(TAB_MAX, Math.max(TAB_MIN,
                (PANEL_WIDTH - TAB_GAP * Math.max(0, pages - 1)) / Math.max(1, pages)));
        int tabH = tabW;
        int rowW = tabW * pages + TAB_GAP * Math.max(0, pages - 1);
        int left = panelLeft + Math.max(0, (PANEL_WIDTH - rowW) / 2);
        int y = top - tabH - 2;

        for (int page = 1; page <= pages; page++) {
            int target = page;
            boolean selected = page == current;
            // Narrow tabs: no "[n]" brackets (they clip). Bold+underline marks current.
            Component label = selected
                    ? Component.literal(String.valueOf(page)).withStyle(s -> s.withBold(true).withUnderlined(true))
                    : Component.literal(String.valueOf(page));
            this.addRenderableWidget(Button.builder(label, button -> YapBagClient.requestOpen(target))
                    .bounds(left + (page - 1) * (tabW + TAB_GAP), y, tabW, tabH)
                    .build());
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yap$bagToggleClose(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!YapBagClient.config().enabled) {
            return;
        }
        if (!YapBagClient.isBagScreen((Screen) (Object) this)) {
            return;
        }
        if (!YapBagClient.openKey().matches(event)) {
            return;
        }
        YapBagClient.closeBag();
        cir.setReturnValue(true);
    }
}
