package com.yapcore.bag.mixin;

import com.yapcore.bag.BagTitle;
import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected ContainerScreenMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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
        int pages = Math.min(9, state.get().pages());
        int current = state.get().page();
        int y = this.topPos - 22;
        for (int page = 1; page <= pages; page++) {
            int target = page;
            String label = page == current ? "[" + page + "]" : String.valueOf(page);
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> YapBagClient.requestOpen(target))
                    .bounds(this.leftPos + (page - 1) * 22, y, 20, 20)
                    .build());
        }
    }
}
