package com.yapcore.bag.mixin;

import com.yapcore.bag.YapBagClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    private InventoryScreenMixin(InventoryMenu menu, RecipeBookComponent<?> book,
                                 Inventory inventory, Component title) {
        super(menu, book, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yap$bagTab(CallbackInfo ci) {
        if (!YapBagClient.config().enabled || !YapBagClient.config().inventoryTab) {
            return;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Bag"), button -> YapBagClient.requestOpen(0))
                .bounds(this.leftPos + this.imageWidth + 4, this.topPos + 8, 40, 20)
                .build());
    }
}
