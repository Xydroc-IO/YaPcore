package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceSkinApplier;
import com.yapcore.presence.PresenceTextureCache;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scrollable library column for {@link WardrobeScreen}: wardrobe slots, local PNGs, URL import.
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
                    "None yet — Use a downloaded skin below, then Save."), host.uiFont()));
        } else {
            int cols = host.width >= 900 ? 2 : 1;
            List<PresenceUiMessages.SlotView> slots = w.slots();
            for (int i = 0; i < slots.size(); i += cols) {
                LinearLayout row = LinearLayout.horizontal().spacing(8);
                for (int c = 0; c < cols && i + c < slots.size(); c++) {
                    row.addChild(buildSlotCard(slots.get(i + c), Math.max(150, (lw - 8) / cols)));
                }
                body.addChild(row);
            }
        }

        body.addChild(new StringWidget(Component.literal(" "), host.uiFont()));
        body.addChild(new StringWidget(Component.literal(
                "Downloaded skins (" + host.localSkins.size() + ")"), host.uiFont()));
        body.addChild(new StringWidget(Component.literal(
                "3D previews · Use updates you · Save as… keeps it"), host.uiFont()));

        if (host.localSkins.isEmpty()) {
            body.addChild(new StringWidget(Component.literal(
                    "No skin-sized PNGs found — use Browse PNG…"), host.uiFont()));
        } else {
            int cols = host.width >= 780 ? 3 : (host.width >= 520 ? 2 : 1);
            int cardW = Math.max(100, (lw - (cols - 1) * 8) / cols);
            for (int i = 0; i < host.localSkins.size(); i += cols) {
                LinearLayout row = LinearLayout.horizontal().spacing(8);
                for (int c = 0; c < cols && i + c < host.localSkins.size(); c++) {
                    row.addChild(buildLocalCard(host.localSkins.get(i + c), cardW));
                }
                body.addChild(row);
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
            host.localSkins = LocalSkinLibrary.scan(24);
            host.rebuild();
        }).width(lw).build());
    }

    private LinearLayout buildLocalCard(LocalSkinLibrary.Entry entry, int width) {
        LinearLayout card = LinearLayout.vertical().spacing(2);
        PresenceTextureCache.ensureLocalFile(entry.path());
        Minecraft mc = host.client() != null ? host.client() : Minecraft.getInstance();
        if (mc != null && mc.getEntityModels() != null) {
            int thumb = Math.min(84, Math.max(56, width - 8));
            PlayerSkinWidget mini = new PlayerSkinWidget(
                    thumb, (int) (thumb * 1.65f), mc.getEntityModels(), () -> skins.resolveLocalSkin(entry));
            card.addChild(mini);
        }
        card.addChild(new StringWidget(Component.literal(WardrobeScreen.truncate(entry.name(), 16)), host.uiFont()));
        int btnW = Math.max(48, (width - 6) / 2);
        GridLayout actions = new GridLayout().columnSpacing(4);
        GridLayout.RowHelper rows = actions.createRowHelper(2);
        Button use = Button.builder(Component.literal("Use"), b -> {
            skins.focusLocal(entry);
            skins.applyLocalFile(entry, false);
        }).width(btnW).build();
        use.setTooltip(Tooltip.create(Component.literal(entry.path().toString())));
        rows.addChild(use);
        rows.addChild(Button.builder(Component.literal("Save"), b -> {
            skins.focusLocal(entry);
            skins.applyLocalFile(entry, false);
            if (host.saveNameBox != null) {
                host.saveNameBox.setValue(entry.name());
            }
            YapPresenceClient.sendRaw("WARDROBE|SAVE|" + PresenceUiMessages.b64(entry.name()));
            TailorPreviewStore.setStatus("Saving " + entry.name() + "…");
        }).width(btnW).build());
        card.addChild(actions);
        return card;
    }

    private LinearLayout buildSlotCard(PresenceUiMessages.SlotView slot, int width) {
        LinearLayout card = LinearLayout.vertical().spacing(2);
        long selected = TailorPreviewStore.selectedSlotId();
        long active = TailorPreviewStore.activeSlotId();
        boolean isSelected = selected == slot.id();
        boolean isActive = active == slot.id();

        String title = WardrobeScreen.truncate(slot.name(), 18)
                + (slot.slim() ? " · slim" : " · wide")
                + (isActive ? " · wearing" : "")
                + (isSelected ? " · preview" : "");
        card.addChild(new StringWidget(Component.literal(title), host.uiFont()));

        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Minecraft mc = host.client() != null ? host.client() : Minecraft.getInstance();
        if (mc != null && mc.getEntityModels() != null) {
            int thumb = Math.min(72, Math.max(48, width / 2));
            PlayerSkinWidget mini = new PlayerSkinWidget(
                    thumb, (int) (thumb * 1.6f), mc.getEntityModels(), () -> skins.resolveSlotSkin(slot));
            card.addChild(mini);
        }

        int btnW = Math.max(60, (width - 6) / 2);
        GridLayout actions = new GridLayout().columnSpacing(6).rowSpacing(2);
        GridLayout.RowHelper rows = actions.createRowHelper(2);
        rows.addChild(Button.builder(Component.literal(isSelected ? "Selected" : "Preview"), b -> {
            host.pendingDeleteId = null;
            skins.selectSlot(slot);
        }).width(btnW).build());
        rows.addChild(Button.builder(Component.literal("Wear"), b -> {
            host.pendingDeleteId = null;
            skins.selectSlot(slot);
            skins.wearSlotNow(slot);
        }).width(btnW).build());

        if (host.pendingDeleteId != null && host.pendingDeleteId == slot.id()) {
            rows.addChild(Button.builder(Component.literal("Confirm del"), b -> {
                YapPresenceClient.sendRaw("WARDROBE|DELETE|" + slot.id());
                host.pendingDeleteId = null;
                if (TailorPreviewStore.selectedSlotId() == slot.id()) {
                    TailorPreviewStore.setSelectedSlotId(-1L);
                }
            }).width(btnW).build());
            rows.addChild(Button.builder(Component.literal("Cancel"), b -> {
                host.pendingDeleteId = null;
                host.rebuild();
            }).width(btnW).build());
        } else {
            rows.addChild(Button.builder(Component.literal("Delete"), b -> {
                host.pendingDeleteId = slot.id();
                host.rebuild();
            }).width(btnW).build());
            rows.addChild(Button.builder(Component.literal("Rename"), b -> {
                skins.selectSlot(slot);
                String name = host.renameBox != null && !host.renameBox.getValue().isBlank()
                        ? host.renameBox.getValue().trim()
                        : slot.name();
                YapPresenceClient.sendRaw(
                        "WARDROBE|RENAME|" + slot.id() + "|" + PresenceUiMessages.b64(name));
            }).width(btnW).build());
        }
        card.addChild(actions);

        if (isSelected) {
            host.renameBox = new EditBox(host.uiFont(), width, 18, Component.literal("Rename"));
            host.renameBox.setMaxLength(48);
            host.renameBox.setValue(slot.name());
            card.addChild(host.renameBox);
        }
        return card;
    }
}
