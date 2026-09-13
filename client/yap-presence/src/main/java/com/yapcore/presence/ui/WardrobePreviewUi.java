package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceSkinApplier;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

/**
 * Left preview column for {@link WardrobeScreen}: live skin, carousel, model toggles.
 */
final class WardrobePreviewUi {

    private final WardrobeScreen host;
    private final WardrobeSkinSupport skins;

    WardrobePreviewUi(WardrobeScreen host, WardrobeSkinSupport skins) {
        this.host = host;
        this.skins = skins;
    }

    /**
     * Left pane stays short so HeaderAndFooterLayout never clips the 3D preview
     * (import / slots live in the scrollable library).
     */
    LinearLayout build() {
        LinearLayout left = LinearLayout.vertical().spacing(6);
        left.addChild(new StringWidget(Component.literal("Your look"), host.uiFont()));
        left.addChild(skins.buildPreviewWidget());

        // Minecraft-style rotate-through skins (◀ look ▶)
        int n = host.carouselSize();
        if (n > 0) {
            if (host.carouselIndex < 0) {
                host.carouselIndex = 0;
            }
            int side = 28;
            int mid = Math.max(80, host.previewWidth() - side * 2 - 8);
            String label = carouselLabel();
            GridLayout nav = new GridLayout().columnSpacing(4);
            GridLayout.RowHelper navRows = nav.createRowHelper(3);
            navRows.addChild(Button.builder(Component.literal("◀"), b -> stepCarousel(-1))
                    .width(side).build());
            Button center = Button.builder(Component.literal(WardrobeScreen.truncate(
                    (host.carouselIndex + 1) + "/" + n + " " + label, 22)), b -> applyCarouselSelection(true))
                    .width(mid).build();
            center.setTooltip(Tooltip.create(Component.literal(
                    "Click to wear / upload · ◀ ▶ browse looks")));
            navRows.addChild(center);
            navRows.addChild(Button.builder(Component.literal("▶"), b -> stepCarousel(1))
                    .width(side).build());
            left.addChild(nav);
        }

        String status = !TailorPreviewStore.status().isBlank()
                ? TailorPreviewStore.status()
                : "◀ ▶ browse · click name to wear";
        left.addChild(new StringWidget(Component.literal(WardrobeScreen.truncate(status, 36)), host.uiFont()));

        GridLayout modelGrid = new GridLayout().columnSpacing(6);
        GridLayout.RowHelper modelRows = modelGrid.createRowHelper(2);
        int half = Math.max(70, (host.previewWidth() - 6) / 2);
        modelRows.addChild(Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "Wide" : "● Wide"), b -> {
            TailorPreviewStore.setSlim(false);
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyModelOnly(host.client().player.getUUID(), false);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|0");
        }).width(half).build());
        modelRows.addChild(Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "● Slim" : "Slim"), b -> {
            TailorPreviewStore.setSlim(true);
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyModelOnly(host.client().player.getUUID(), true);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|1");
        }).width(half).build());
        left.addChild(modelGrid);

        Button browse = Button.builder(Component.literal("Browse PNG…"), b -> skins.pickAndUpload(false))
                .width(host.previewWidth()).build();
        browse.setTooltip(Tooltip.create(Component.literal(
                "Open a skin from Downloads, Prism skins, or anywhere")));
        left.addChild(browse);
        left.addChild(Button.builder(Component.literal("Clear custom"), b -> {
            TailorPreviewStore.clear();
            TailorPreviewStore.setSelectedSlotId(-1L);
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.clear(host.client().player.getUUID());
            }
            YapPresenceClient.sendRaw("SKIN|CLEAR");
        }).width(host.previewWidth()).build());
        return left;
    }

    private String carouselLabel() {
        int slots = host.wardrobeSlotCount();
        if (host.carouselIndex < 0 || host.carouselIndex >= host.carouselSize()) {
            return "";
        }
        if (host.carouselIndex < slots) {
            return PresenceUiStore.wardrobe().slots().get(host.carouselIndex).name();
        }
        return host.localSkins.get(host.carouselIndex - slots).name();
    }

    private void stepCarousel(int delta) {
        int n = host.carouselSize();
        if (n <= 0) {
            return;
        }
        host.carouselIndex = Math.floorMod(host.carouselIndex + delta, n);
        applyCarouselSelection(false);
        host.rebuild();
    }

    /** Preview only (false) or wear/upload (true). */
    private void applyCarouselSelection(boolean wear) {
        int slots = host.wardrobeSlotCount();
        if (host.carouselIndex < 0 || host.carouselIndex >= host.carouselSize()) {
            return;
        }
        if (host.carouselIndex < slots) {
            PresenceUiMessages.SlotView slot = PresenceUiStore.wardrobe().slots().get(host.carouselIndex);
            if (wear) {
                skins.selectSlot(slot);
                skins.wearSlotNow(slot);
            } else {
                skins.previewSlotOnly(slot);
            }
            return;
        }
        LocalSkinLibrary.Entry entry = host.localSkins.get(host.carouselIndex - slots);
        if (wear) {
            skins.applyLocalFile(entry, false);
        } else {
            skins.previewLocalOnly(entry);
        }
    }
}
