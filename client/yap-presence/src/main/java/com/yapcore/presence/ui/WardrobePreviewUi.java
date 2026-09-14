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
 * Rotator steps update preview state + labels only — never full-screen rebuild.
 */
final class WardrobePreviewUi {

    private final WardrobeScreen host;
    private final WardrobeSkinSupport skins;

    WardrobePreviewUi(WardrobeScreen host, WardrobeSkinSupport skins) {
        this.host = host;
        this.skins = skins;
    }

    LinearLayout build() {
        LinearLayout left = LinearLayout.vertical().spacing(6);
        left.addChild(new StringWidget(Component.literal("Your look · rotator"), host.uiFont()));
        left.addChild(skins.buildPreviewWidget());

        int n = host.carouselSize();
        if (n > 0) {
            if (host.carouselIndex < 0) {
                host.carouselIndex = 0;
            }
            int side = 32;
            int mid = Math.max(90, host.previewWidth() - side * 2 - 8);
            GridLayout nav = new GridLayout().columnSpacing(4);
            GridLayout.RowHelper navRows = nav.createRowHelper(3);
            navRows.addChild(Button.builder(Component.literal("◀"), b -> stepCarousel(-1))
                    .width(side).build());
            host.carouselCenterBtn = Button.builder(Component.literal(WardrobeScreen.truncate(
                    (host.carouselIndex + 1) + "/" + n + "  " + host.carouselLabel(), 24)),
                    b -> applyCarouselSelection(true))
                    .width(mid).build();
            host.carouselCenterBtn.setTooltip(Tooltip.create(Component.literal(
                    "Click to wear · ◀ ▶ rotate through saved + downloaded skins")));
            navRows.addChild(host.carouselCenterBtn);
            navRows.addChild(Button.builder(Component.literal("▶"), b -> stepCarousel(1))
                    .width(side).build());
            left.addChild(nav);
        }

        String status = !TailorPreviewStore.status().isBlank()
                ? TailorPreviewStore.status()
                : "◀ ▶ browse · click name to wear";
        host.statusWidget = new StringWidget(Component.literal(WardrobeScreen.truncate(status, 36)), host.uiFont());
        left.addChild(host.statusWidget);

        GridLayout modelGrid = new GridLayout().columnSpacing(6);
        GridLayout.RowHelper modelRows = modelGrid.createRowHelper(2);
        int half = Math.max(70, (host.previewWidth() - 6) / 2);
        host.wideBtn = Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "Wide" : "● Wide"), b -> {
            TailorPreviewStore.setSlim(false);
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyModelOnly(host.client().player.getUUID(), false);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|0");
            host.refreshLiveLabels();
        }).width(half).build();
        host.slimBtn = Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "● Slim" : "Slim"), b -> {
            TailorPreviewStore.setSlim(true);
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyModelOnly(host.client().player.getUUID(), true);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|1");
            host.refreshLiveLabels();
        }).width(half).build();
        modelRows.addChild(host.wideBtn);
        modelRows.addChild(host.slimBtn);
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
            host.refreshLiveLabels();
        }).width(host.previewWidth()).build());
        return left;
    }

    private void stepCarousel(int delta) {
        int n = host.carouselSize();
        if (n <= 0) {
            return;
        }
        host.carouselIndex = Math.floorMod(host.carouselIndex + delta, n);
        host.prefetchCarouselTexture();
        applyCarouselSelection(false);
        host.refreshLiveLabels();
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
            host.refreshLiveLabels();
            return;
        }
        LocalSkinLibrary.Entry entry = host.localSkins.get(host.carouselIndex - slots);
        if (wear) {
            skins.applyLocalFile(entry, false);
        } else {
            skins.previewLocalOnly(entry);
        }
        host.refreshLiveLabels();
    }
}
