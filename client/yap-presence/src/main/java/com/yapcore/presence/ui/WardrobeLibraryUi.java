package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceSkinApplier;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

/**
 * Scrollable library column: wardrobe + local skin <em>names</em> (no per-card 3D widgets).
 * The left rotator is the only live {@code PlayerSkinWidget}.
 */
final class WardrobeLibraryUi {

    private final WardrobeScreen host;
    private final WardrobeSkinSupport skins;

    WardrobeLibraryUi(WardrobeScreen host, WardrobeSkinSupport skins) {
        this.host = host;
        this.skins = skins;
    }

    void fill(LinearLayout body) {
        PresenceUiMessages.WardrobeView w = PresenceUiStore.wardrobe();
        int lw = host.libraryWidth();

        body.addChild(new StringWidget(Component.literal(
                "Saved looks (" + w.slots().size() + ")"), host.uiFont()));
        if (w.slots().isEmpty()) {
            body.addChild(new StringWidget(Component.literal(
                    "None yet — Use a downloaded skin, then Save."), host.uiFont()));
        } else {
            for (PresenceUiMessages.SlotView slot : w.slots()) {
                body.addChild(buildSlotRow(slot, lw));
            }
        }

        body.addChild(new StringWidget(Component.literal(" "), host.uiFont()));
        body.addChild(new StringWidget(Component.literal(
                "Downloaded (" + host.localSkins.size() + ") · use ◀ ▶ rotator"), host.uiFont()));

        if (host.localSkins.isEmpty()) {
            body.addChild(new StringWidget(Component.literal(
                    "No skin-sized PNGs found — Browse PNG…"), host.uiFont()));
        } else {
            for (LocalSkinLibrary.Entry entry : host.localSkins) {
                body.addChild(buildLocalRow(entry, lw));
            }
        }

        body.addChild(new StringWidget(Component.literal(" "), host.uiFont()));
        body.addChild(new StringWidget(Component.literal("Import / URL"), host.uiFont()));

        body.addChild(Button.builder(Component.literal("Choose cape PNG…"), b -> skins.pickAndUpload(true))
                .width(lw).build());

        host.urlBox = new EditBox(host.uiFont(), lw, 20, Component.literal("Skin URL"));
        host.urlBox.setMaxLength(512);
        host.urlBox.setHint(Component.literal("https://… skin URL"));
        host.urlBox.setValue(w.activeUrl() == null ? "" : w.activeUrl());
        body.addChild(host.urlBox);
        body.addChild(Button.builder(Component.literal("Apply skin URL"), b -> {
            String url = host.urlBox.getValue().trim();
            if (!url.isBlank() && host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyLocal(
                        host.client().player.getUUID(),
                        TailorPreviewStore.slim(),
                        url,
                        null);
                TailorPreviewStore.setSelectedSlotId(-1L);
            }
            YapPresenceClient.sendRaw("SKIN|URL|" + PresenceUiMessages.b64(url));
            TailorPreviewStore.setStatus(url.isBlank() ? "Cleared URL" : "Applying URL…");
        }).width(lw).build());

        host.capeBox = new EditBox(host.uiFont(), lw, 20, Component.literal("Cape URL"));
        host.capeBox.setMaxLength(512);
        host.capeBox.setHint(Component.literal("https://… cape URL"));
        host.capeBox.setValue(w.activeCape() == null ? "" : w.activeCape());
        body.addChild(host.capeBox);
        body.addChild(Button.builder(Component.literal("Apply cape URL"), b -> {
            YapPresenceClient.sendRaw("SKIN|CAPE|" + PresenceUiMessages.b64(host.capeBox.getValue().trim()));
            TailorPreviewStore.setStatus("Applying cape…");
        }).width(lw).build());

        host.saveNameBox = new EditBox(host.uiFont(), lw, 20, Component.literal("Slot name"));
        host.saveNameBox.setMaxLength(48);
        host.saveNameBox.setHint(Component.literal("Name for Save"));
        host.saveNameBox.setValue("My skin");
        body.addChild(host.saveNameBox);
        body.addChild(Button.builder(Component.literal("Save current look"), b -> {
            YapPresenceClient.sendRaw("WARDROBE|SAVE|" + PresenceUiMessages.b64(host.saveNameBox.getValue().trim()));
            TailorPreviewStore.setStatus("Saving…");
        }).width(lw)
                .tooltip(Tooltip.create(Component.literal(
                        "Stores the look you’re previewing into your wardrobe")))
                .build());

        body.addChild(Button.builder(Component.literal("Refresh local files"), b -> {
            host.localSkins = LocalSkinLibrary.scan(32);
            host.rebuild();
        }).width(lw).build());
    }

    private LinearLayout buildLocalRow(LocalSkinLibrary.Entry entry, int width) {
        LinearLayout row = LinearLayout.horizontal().spacing(4);
        int nameW = Math.max(80, width - 100);
        int btnW = 48;
        Button name = Button.builder(Component.literal(WardrobeScreen.truncate(entry.name(), 22)), b -> {
            skins.focusLocal(entry);
            skins.previewLocalOnly(entry);
            host.refreshLiveLabels();
        }).width(nameW).build();
        name.setTooltip(Tooltip.create(Component.literal(entry.path().toString())));
        row.addChild(name);
        row.addChild(Button.builder(Component.literal("Use"), b -> {
            skins.focusLocal(entry);
            skins.applyLocalFile(entry, false);
            host.refreshLiveLabels();
        }).width(btnW).build());
        row.addChild(Button.builder(Component.literal("Save"), b -> {
            skins.focusLocal(entry);
            skins.applyLocalFile(entry, false);
            if (host.saveNameBox != null) {
                host.saveNameBox.setValue(entry.name());
            }
            YapPresenceClient.sendRaw("WARDROBE|SAVE|" + PresenceUiMessages.b64(entry.name()));
            TailorPreviewStore.setStatus("Saving " + entry.name() + "…");
            host.refreshLiveLabels();
        }).width(btnW).build());
        return row;
    }

    private LinearLayout buildSlotRow(PresenceUiMessages.SlotView slot, int width) {
        long selected = TailorPreviewStore.selectedSlotId();
        long active = TailorPreviewStore.activeSlotId();
        boolean isSelected = selected == slot.id();
        boolean isActive = active == slot.id();

        String title = WardrobeScreen.truncate(slot.name(), 16)
                + (slot.slim() ? " · slim" : "")
                + (isActive ? " · on" : "")
                + (isSelected ? " · ·" : "");

        LinearLayout row = LinearLayout.horizontal().spacing(4);
        int nameW = Math.max(70, width - 148);
        int btnW = 46;
        row.addChild(Button.builder(Component.literal(title), b -> {
            host.pendingDeleteId = null;
            skins.selectSlot(slot);
            for (int i = 0; i < PresenceUiStore.wardrobe().slots().size(); i++) {
                if (PresenceUiStore.wardrobe().slots().get(i).id() == slot.id()) {
                    host.carouselIndex = i;
                    break;
                }
            }
            host.refreshLiveLabels();
        }).width(nameW).build());
        row.addChild(Button.builder(Component.literal("Wear"), b -> {
            host.pendingDeleteId = null;
            skins.selectSlot(slot);
            skins.wearSlotNow(slot);
            host.refreshLiveLabels();
        }).width(btnW).build());

        if (host.pendingDeleteId != null && host.pendingDeleteId == slot.id()) {
            row.addChild(Button.builder(Component.literal("OK del"), b -> {
                YapPresenceClient.sendRaw("WARDROBE|DELETE|" + slot.id());
                host.pendingDeleteId = null;
                if (TailorPreviewStore.selectedSlotId() == slot.id()) {
                    TailorPreviewStore.setSelectedSlotId(-1L);
                }
            }).width(btnW).build());
            row.addChild(Button.builder(Component.literal("No"), b -> {
                host.pendingDeleteId = null;
                host.rebuild();
            }).width(btnW).build());
        } else {
            row.addChild(Button.builder(Component.literal("Del"), b -> {
                host.pendingDeleteId = slot.id();
                host.rebuild();
            }).width(btnW).build());
        }
        return row;
    }
}
