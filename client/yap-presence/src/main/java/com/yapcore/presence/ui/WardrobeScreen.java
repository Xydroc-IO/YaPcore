package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceSkinApplier;
import com.yapcore.presence.PresenceTextureCache;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Minecraft-style skin chooser: large live preview + scrollable library of saved wardrobe
 * looks and local downloaded PNGs (Prism skins / Downloads).
 */
public final class WardrobeScreen extends Screen {

    private final Screen parent;
    private HeaderAndFooterLayout layout;
    private ScrollableLayout rightScroll;
    private LinearLayout rightBody;
    private EditBox urlBox;
    private EditBox capeBox;
    private EditBox saveNameBox;
    private EditBox renameBox;
    private Long pendingDeleteId;
    private List<LocalSkinLibrary.Entry> localSkins = List.of();
    /** Carousel index across wardrobe slots then local PNGs (−1 = none). */
    private int carouselIndex = -1;
    private final Runnable listener = this::rebuild;
    private boolean rebuilding;

    public WardrobeScreen(Screen parent) {
        super(Component.literal("YaP Tailor · Skins"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        PresenceUiStore.removeListener(listener);
        TailorPreviewStore.removeListener(listener);
        PresenceUiStore.addListener(listener);
        TailorPreviewStore.addListener(listener);

        PresenceUiMessages.WardrobeView w = PresenceUiStore.wardrobe();
        TailorPreviewStore.setSlim(w.slim());
        if (!w.activeUrl().isBlank() && this.minecraft != null && this.minecraft.player != null) {
            PresenceTextureCache.ensureDownloaded(this.minecraft.player.getUUID(), w.activeUrl());
        }
        for (PresenceUiMessages.SlotView slot : w.slots()) {
            PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        }
        localSkins = LocalSkinLibrary.scan(24);
        for (LocalSkinLibrary.Entry entry : localSkins) {
            PresenceTextureCache.ensureLocalFile(entry.path());
        }
        if (carouselIndex < 0 && carouselSize() > 0) {
            carouselIndex = wardrobeSlotCount(); // start on first local if no wardrobe
        }
        if (carouselIndex >= carouselSize() && carouselSize() > 0) {
            carouselIndex = carouselSize() - 1;
        }

        int chrome = this.height < 280 ? 24 : (this.height < 360 ? 28 : 33);
        layout = new HeaderAndFooterLayout(this, chrome, chrome);
        layout.addTitleHeader(this.title, this.font);

        LinearLayout columns = LinearLayout.horizontal().spacing(12);
        columns.addChild(buildPreviewPane());

        rightBody = LinearLayout.vertical().spacing(4);
        fillLibraryPane(rightBody);
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        rightScroll = new ScrollableLayout(mc, rightBody, Math.max(48, layout.getContentHeight()));
        rightScroll.setMinWidth(libraryWidth());
        columns.addChild(rightScroll);
        layout.addToContents(columns);

        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        int fw = Math.min(120, Math.max(72, this.width / 5));
        footer.addChild(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).width(fw).build());
        Button done = Button.builder(CommonComponents.GUI_DONE, b -> closeToGame()).width(fw).build();
        done.setTooltip(Tooltip.create(Component.literal("Close Tailor and return to the world.")));
        footer.addChild(done);
        layout.addToFooter(footer);
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    public void removed() {
        PresenceUiStore.removeListener(listener);
        TailorPreviewStore.removeListener(listener);
        super.removed();
    }

    @Override
    protected void repositionElements() {
        if (layout == null) {
            return;
        }
        if (rightScroll != null) {
            rightScroll.setMinWidth(libraryWidth());
            rightScroll.arrangeElements();
            rightScroll.setMaxHeight(Math.max(48, layout.getContentHeight()));
        }
        layout.arrangeElements();
    }

    /**
     * Left pane stays short so HeaderAndFooterLayout never clips the 3D preview
     * (import / slots live in the scrollable library).
     */
    private LinearLayout buildPreviewPane() {
        LinearLayout left = LinearLayout.vertical().spacing(6);
        left.addChild(new StringWidget(Component.literal("Your look"), this.font));
        left.addChild(buildPreviewWidget());

        // Minecraft-style rotate-through skins (◀ look ▶)
        int n = carouselSize();
        if (n > 0) {
            if (carouselIndex < 0) {
                carouselIndex = 0;
            }
            int side = 28;
            int mid = Math.max(80, previewWidth() - side * 2 - 8);
            String label = carouselLabel();
            GridLayout nav = new GridLayout().columnSpacing(4);
            GridLayout.RowHelper navRows = nav.createRowHelper(3);
            navRows.addChild(Button.builder(Component.literal("◀"), b -> stepCarousel(-1))
                    .width(side).build());
            Button center = Button.builder(Component.literal(truncate(
                    (carouselIndex + 1) + "/" + n + " " + label, 22)), b -> applyCarouselSelection(true))
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
        left.addChild(new StringWidget(Component.literal(truncate(status, 36)), this.font));

        GridLayout modelGrid = new GridLayout().columnSpacing(6);
        GridLayout.RowHelper modelRows = modelGrid.createRowHelper(2);
        int half = Math.max(70, (previewWidth() - 6) / 2);
        modelRows.addChild(Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "Wide" : "● Wide"), b -> {
            TailorPreviewStore.setSlim(false);
            if (this.minecraft != null && this.minecraft.player != null) {
                PresenceSkinApplier.applyModelOnly(this.minecraft.player.getUUID(), false);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|0");
        }).width(half).build());
        modelRows.addChild(Button.builder(Component.literal(
                TailorPreviewStore.slim() ? "● Slim" : "Slim"), b -> {
            TailorPreviewStore.setSlim(true);
            if (this.minecraft != null && this.minecraft.player != null) {
                PresenceSkinApplier.applyModelOnly(this.minecraft.player.getUUID(), true);
            }
            YapPresenceClient.sendRaw("SKIN|MODEL|1");
        }).width(half).build());
        left.addChild(modelGrid);

        Button browse = Button.builder(Component.literal("Browse PNG…"), b -> pickAndUpload(false))
                .width(previewWidth()).build();
        browse.setTooltip(Tooltip.create(Component.literal(
                "Open a skin from Downloads, Prism skins, or anywhere")));
        left.addChild(browse);
        left.addChild(Button.builder(Component.literal("Clear custom"), b -> {
            TailorPreviewStore.clear();
            TailorPreviewStore.setSelectedSlotId(-1L);
            if (this.minecraft != null && this.minecraft.player != null) {
                PresenceSkinApplier.clear(this.minecraft.player.getUUID());
            }
            YapPresenceClient.sendRaw("SKIN|CLEAR");
        }).width(previewWidth()).build());
        return left;
    }

    private void fillLibraryPane(LinearLayout body) {
        PresenceUiMessages.WardrobeView w = PresenceUiStore.wardrobe();
        int lw = libraryWidth();

        body.addChild(new StringWidget(Component.literal(
                "Saved looks (" + w.slots().size() + ")"), this.font));
        if (w.slots().isEmpty()) {
            body.addChild(new StringWidget(Component.literal(
                    "None yet — Use a downloaded skin below, then Save."), this.font));
        } else {
            int cols = this.width >= 900 ? 2 : 1;
            List<PresenceUiMessages.SlotView> slots = w.slots();
            for (int i = 0; i < slots.size(); i += cols) {
                LinearLayout row = LinearLayout.horizontal().spacing(8);
                for (int c = 0; c < cols && i + c < slots.size(); c++) {
                    row.addChild(buildSlotCard(slots.get(i + c), Math.max(150, (lw - 8) / cols)));
                }
                body.addChild(row);
            }
        }

        body.addChild(new StringWidget(Component.literal(" "), this.font));
        body.addChild(new StringWidget(Component.literal(
                "Downloaded skins (" + localSkins.size() + ")"), this.font));
        body.addChild(new StringWidget(Component.literal(
                "3D previews · Use updates you · Save as… keeps it"), this.font));

        if (localSkins.isEmpty()) {
            body.addChild(new StringWidget(Component.literal(
                    "No skin-sized PNGs found — use Browse PNG…"), this.font));
        } else {
            int cols = this.width >= 780 ? 3 : (this.width >= 520 ? 2 : 1);
            int cardW = Math.max(100, (lw - (cols - 1) * 8) / cols);
            for (int i = 0; i < localSkins.size(); i += cols) {
                LinearLayout row = LinearLayout.horizontal().spacing(8);
                for (int c = 0; c < cols && i + c < localSkins.size(); c++) {
                    row.addChild(buildLocalCard(localSkins.get(i + c), cardW));
                }
                body.addChild(row);
            }
        }

        body.addChild(new StringWidget(Component.literal(" "), this.font));
        body.addChild(new StringWidget(Component.literal("Import / URL"), this.font));

        body.addChild(Button.builder(Component.literal("Choose cape PNG…"), b -> pickAndUpload(true))
                .width(lw).build());

        urlBox = new EditBox(this.font, lw, 20, Component.literal("Skin URL"));
        urlBox.setMaxLength(512);
        urlBox.setHint(Component.literal("https://… skin URL"));
        urlBox.setValue(w.activeUrl() == null ? "" : w.activeUrl());
        body.addChild(urlBox);
        body.addChild(Button.builder(Component.literal("Apply skin URL"), b -> {
            String url = urlBox.getValue().trim();
            if (!url.isBlank() && this.minecraft != null && this.minecraft.player != null) {
                PresenceSkinApplier.applyLocal(
                        this.minecraft.player.getUUID(),
                        TailorPreviewStore.slim(),
                        url,
                        null);
                TailorPreviewStore.setSelectedSlotId(-1L);
            }
            YapPresenceClient.sendRaw("SKIN|URL|" + PresenceUiMessages.b64(url));
            TailorPreviewStore.setStatus(url.isBlank() ? "Cleared URL" : "Applying URL…");
        }).width(lw).build());

        capeBox = new EditBox(this.font, lw, 20, Component.literal("Cape URL"));
        capeBox.setMaxLength(512);
        capeBox.setHint(Component.literal("https://… cape URL"));
        capeBox.setValue(w.activeCape() == null ? "" : w.activeCape());
        body.addChild(capeBox);
        body.addChild(Button.builder(Component.literal("Apply cape URL"), b -> {
            YapPresenceClient.sendRaw("SKIN|CAPE|" + PresenceUiMessages.b64(capeBox.getValue().trim()));
            TailorPreviewStore.setStatus("Applying cape…");
        }).width(lw).build());

        saveNameBox = new EditBox(this.font, lw, 20, Component.literal("Slot name"));
        saveNameBox.setMaxLength(48);
        saveNameBox.setHint(Component.literal("Name for Save"));
        saveNameBox.setValue("My skin");
        body.addChild(saveNameBox);
        body.addChild(Button.builder(Component.literal("Save current look"), b -> {
            YapPresenceClient.sendRaw("WARDROBE|SAVE|" + PresenceUiMessages.b64(saveNameBox.getValue().trim()));
            TailorPreviewStore.setStatus("Saving…");
        }).width(lw)
                .tooltip(Tooltip.create(Component.literal(
                        "Stores the look you’re previewing into your wardrobe")))
                .build());

        body.addChild(Button.builder(Component.literal("Refresh local files"), b -> {
            localSkins = LocalSkinLibrary.scan(24);
            rebuild();
        }).width(lw).build());
    }

    private LinearLayout buildLocalCard(LocalSkinLibrary.Entry entry, int width) {
        LinearLayout card = LinearLayout.vertical().spacing(2);
        PresenceTextureCache.ensureLocalFile(entry.path());
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.getEntityModels() != null) {
            int thumb = Math.min(84, Math.max(56, width - 8));
            PlayerSkinWidget mini = new PlayerSkinWidget(
                    thumb, (int) (thumb * 1.65f), mc.getEntityModels(), () -> resolveLocalSkin(entry));
            card.addChild(mini);
        }
        card.addChild(new StringWidget(Component.literal(truncate(entry.name(), 16)), this.font));
        int btnW = Math.max(48, (width - 6) / 2);
        GridLayout actions = new GridLayout().columnSpacing(4);
        GridLayout.RowHelper rows = actions.createRowHelper(2);
        Button use = Button.builder(Component.literal("Use"), b -> {
            focusLocal(entry);
            applyLocalFile(entry, false);
        }).width(btnW).build();
        use.setTooltip(Tooltip.create(Component.literal(entry.path().toString())));
        rows.addChild(use);
        rows.addChild(Button.builder(Component.literal("Save"), b -> {
            focusLocal(entry);
            applyLocalFile(entry, false);
            if (saveNameBox != null) {
                saveNameBox.setValue(entry.name());
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

        String title = truncate(slot.name(), 18)
                + (slot.slim() ? " · slim" : " · wide")
                + (isActive ? " · wearing" : "")
                + (isSelected ? " · preview" : "");
        card.addChild(new StringWidget(Component.literal(title), this.font));

        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.getEntityModels() != null) {
            int thumb = Math.min(72, Math.max(48, width / 2));
            PlayerSkinWidget mini = new PlayerSkinWidget(
                    thumb, (int) (thumb * 1.6f), mc.getEntityModels(), () -> resolveSlotSkin(slot));
            card.addChild(mini);
        }

        int btnW = Math.max(60, (width - 6) / 2);
        GridLayout actions = new GridLayout().columnSpacing(6).rowSpacing(2);
        GridLayout.RowHelper rows = actions.createRowHelper(2);
        rows.addChild(Button.builder(Component.literal(isSelected ? "Selected" : "Preview"), b -> {
            pendingDeleteId = null;
            selectSlot(slot);
        }).width(btnW).build());
        rows.addChild(Button.builder(Component.literal("Wear"), b -> {
            pendingDeleteId = null;
            selectSlot(slot);
            wearSlotNow(slot);
        }).width(btnW).build());

        if (pendingDeleteId != null && pendingDeleteId == slot.id()) {
            rows.addChild(Button.builder(Component.literal("Confirm del"), b -> {
                YapPresenceClient.sendRaw("WARDROBE|DELETE|" + slot.id());
                pendingDeleteId = null;
                if (TailorPreviewStore.selectedSlotId() == slot.id()) {
                    TailorPreviewStore.setSelectedSlotId(-1L);
                }
            }).width(btnW).build());
            rows.addChild(Button.builder(Component.literal("Cancel"), b -> {
                pendingDeleteId = null;
                rebuild();
            }).width(btnW).build());
        } else {
            rows.addChild(Button.builder(Component.literal("Delete"), b -> {
                pendingDeleteId = slot.id();
                rebuild();
            }).width(btnW).build());
            rows.addChild(Button.builder(Component.literal("Rename"), b -> {
                selectSlot(slot);
                String name = renameBox != null && !renameBox.getValue().isBlank()
                        ? renameBox.getValue().trim()
                        : slot.name();
                YapPresenceClient.sendRaw(
                        "WARDROBE|RENAME|" + slot.id() + "|" + PresenceUiMessages.b64(name));
            }).width(btnW).build());
        }
        card.addChild(actions);

        if (isSelected) {
            renameBox = new EditBox(this.font, width, 18, Component.literal("Rename"));
            renameBox.setMaxLength(48);
            renameBox.setValue(slot.name());
            card.addChild(renameBox);
        }
        return card;
    }

    private void selectSlot(PresenceUiMessages.SlotView slot) {
        TailorPreviewStore.setSelectedSlotId(slot.id());
        TailorPreviewStore.setSlim(slot.slim());
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier skin = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        if (skin != null) {
            TailorPreviewStore.setSkinTexture(skin);
        }
        Identifier cape = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        TailorPreviewStore.setCapeTexture(cape);
        TailorPreviewStore.setStatus("Previewing " + slot.name());
        rebuild();
    }

    private void applyLocalFile(LocalSkinLibrary.Entry entry, boolean cape) {
        if (entry == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(entry.path());
            if (bytes.length > 1_048_576) {
                TailorPreviewStore.setStatus("File too large (max 1 MB)");
                return;
            }
            if (!cape) {
                LocalSkinLibrary.Entry check = LocalSkinLibrary.tryRead(entry.path());
                if (check == null) {
                    TailorPreviewStore.setStatus("Not a Minecraft skin PNG");
                    return;
                }
            }
            loadPngBytes(cape, bytes, entry.name());
        } catch (Exception e) {
            TailorPreviewStore.setStatus("Failed: " + e.getMessage());
        }
    }

    private void loadPngBytes(boolean cape, byte[] bytes, String label) {
        UUID uuid = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getUUID()
                : UUID.randomUUID();
        Identifier id = cape
                ? TailorPreviewStore.previewCapeId(uuid)
                : TailorPreviewStore.previewSkinId(uuid);
        PresenceTextureCache.registerBytes(id, bytes);
        TailorPreviewStore.setSelectedSlotId(-1L);
        if (cape) {
            TailorPreviewStore.setCapeTexture(id);
            TailorPreviewStore.setStatus("Cape: " + label + " — uploading…");
        } else {
            TailorPreviewStore.setSkinTexture(id);
            TailorPreviewStore.setStatus("Skin: " + label + " — uploading…");
            if (this.minecraft != null && this.minecraft.player != null) {
                PresenceSkinApplier.applyLocal(
                        this.minecraft.player.getUUID(),
                        TailorPreviewStore.slim(),
                        "",
                        id);
            }
            if (saveNameBox != null && (saveNameBox.getValue().isBlank()
                    || "My skin".equals(saveNameBox.getValue()))) {
                saveNameBox.setValue(label);
            }
        }
        YapPresenceClient.uploadPngFile(cape, bytes);
    }

    /** Preview + optimistic in-world apply, then ask Tailor to persist. */
    private void wearSlotNow(PresenceUiMessages.SlotView slot) {
        if (slot == null) {
            return;
        }
        TailorPreviewStore.setSlim(slot.slim());
        if (this.minecraft != null && this.minecraft.player != null) {
            PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
            Identifier ready = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
            PresenceSkinApplier.applyLocal(
                    this.minecraft.player.getUUID(),
                    slot.slim(),
                    slot.skinUrl(),
                    ready);
        }
        YapPresenceClient.sendRaw("WARDROBE|APPLY|" + slot.id());
        TailorPreviewStore.setStatus("Wearing " + slot.name() + "…");
        TailorPreviewStore.setActiveSlotId(slot.id());
    }

    private PlayerSkinWidget buildPreviewWidget() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int w = Math.min(160, Math.max(110, previewWidth() - 8));
        int h = (int) (w * 1.7f);
        return new PlayerSkinWidget(w, h, mc.getEntityModels(), this::resolvePreviewSkin);
    }

    private PlayerSkin resolvePreviewSkin() {
        long selected = TailorPreviewStore.selectedSlotId();
        if (selected > 0) {
            for (PresenceUiMessages.SlotView slot : PresenceUiStore.wardrobe().slots()) {
                if (slot.id() == selected) {
                    return resolveSlotSkin(slot);
                }
            }
        }
        return resolveActiveOrImportSkin();
    }

    private PlayerSkin resolveSlotSkin(PresenceUiMessages.SlotView slot) {
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier bodyId = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        Identifier capeId = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        PlayerModelType model = slot.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        if (bodyId != null) {
            ClientAsset.Texture body = new ClientAsset.ResourceTexture(bodyId);
            ClientAsset.Texture cape = capeId == null ? null : new ClientAsset.ResourceTexture(capeId);
            return PlayerSkin.insecure(body, cape, null, model);
        }
        return resolveActiveOrImportSkin();
    }

    private PlayerSkin resolveActiveOrImportSkin() {
        Minecraft mc = Minecraft.getInstance();
        Identifier bodyId = TailorPreviewStore.skinTexture();
        if (bodyId == null && mc.player != null) {
            Identifier ready = PresenceTextureCache.getIfReady(mc.player.getUUID());
            if (ready != null) {
                bodyId = ready;
            }
        }
        if (bodyId != null) {
            ClientAsset.Texture body = new ClientAsset.ResourceTexture(bodyId);
            Identifier capeId = TailorPreviewStore.capeTexture();
            ClientAsset.Texture cape = capeId == null ? null : new ClientAsset.ResourceTexture(capeId);
            return PlayerSkin.insecure(body, cape, null, TailorPreviewStore.modelType());
        }
        if (mc.player != null) {
            return mc.player.getSkin();
        }
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
    }

    private void pickAndUpload(boolean cape) {
        String title = cape ? "Choose cape PNG" : "Choose skin PNG";
        Path start = LocalSkinLibrary.preferredBrowseDir();
        TailorPreviewStore.setStatus("Opening " + start.getFileName() + "…");
        SkinFileDialogs.openPng(title, start).thenAccept(opt -> {
            Minecraft.getInstance().execute(() -> {
                if (opt.isEmpty()) {
                    TailorPreviewStore.setStatus("File dialog cancelled");
                    return;
                }
                try {
                    Path path = opt.get();
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length > 1_048_576) {
                        TailorPreviewStore.setStatus("File too large (max 1 MB)");
                        return;
                    }
                    if (!cape && LocalSkinLibrary.tryRead(path) == null) {
                        TailorPreviewStore.setStatus("Not a Minecraft skin PNG (need 64×64 etc.)");
                        return;
                    }
                    String name = path.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        name = name.substring(0, dot);
                    }
                    loadPngBytes(cape, bytes, name);
                } catch (Exception e) {
                    TailorPreviewStore.setStatus("Failed to read file: " + e.getMessage());
                }
            });
        });
    }

    private PlayerSkin resolveLocalSkin(LocalSkinLibrary.Entry entry) {
        Identifier id = PresenceTextureCache.ensureLocalFile(entry.path());
        Identifier ready = PresenceTextureCache.getIfReady(PresenceTextureCache.localFileCacheKey(entry.path()));
        if (ready == null) {
            ready = id != null && PresenceTextureCache.getIfReady(id.toString()) != null ? id : null;
        }
        // ensureLocalFile puts KEY_READY under localFileCacheKey
        ready = PresenceTextureCache.getIfReady(PresenceTextureCache.localFileCacheKey(entry.path()));
        if (ready != null) {
            ClientAsset.Texture body = new ClientAsset.ResourceTexture(ready);
            return PlayerSkin.insecure(body, null, null, TailorPreviewStore.modelType());
        }
        return resolveActiveOrImportSkin();
    }

    private int wardrobeSlotCount() {
        return PresenceUiStore.wardrobe().slots().size();
    }

    private int carouselSize() {
        return wardrobeSlotCount() + localSkins.size();
    }

    private String carouselLabel() {
        int slots = wardrobeSlotCount();
        if (carouselIndex < 0 || carouselIndex >= carouselSize()) {
            return "";
        }
        if (carouselIndex < slots) {
            return PresenceUiStore.wardrobe().slots().get(carouselIndex).name();
        }
        return localSkins.get(carouselIndex - slots).name();
    }

    private void stepCarousel(int delta) {
        int n = carouselSize();
        if (n <= 0) {
            return;
        }
        carouselIndex = Math.floorMod(carouselIndex + delta, n);
        applyCarouselSelection(false);
        rebuild();
    }

    /** Preview only (false) or wear/upload (true). */
    private void applyCarouselSelection(boolean wear) {
        int slots = wardrobeSlotCount();
        if (carouselIndex < 0 || carouselIndex >= carouselSize()) {
            return;
        }
        if (carouselIndex < slots) {
            PresenceUiMessages.SlotView slot = PresenceUiStore.wardrobe().slots().get(carouselIndex);
            if (wear) {
                selectSlot(slot);
                wearSlotNow(slot);
            } else {
                previewSlotOnly(slot);
            }
            return;
        }
        LocalSkinLibrary.Entry entry = localSkins.get(carouselIndex - slots);
        if (wear) {
            applyLocalFile(entry, false);
        } else {
            previewLocalOnly(entry);
        }
    }

    private void focusLocal(LocalSkinLibrary.Entry entry) {
        int slots = wardrobeSlotCount();
        for (int i = 0; i < localSkins.size(); i++) {
            if (localSkins.get(i).path().equals(entry.path())) {
                carouselIndex = slots + i;
                break;
            }
        }
    }

    private void previewSlotOnly(PresenceUiMessages.SlotView slot) {
        TailorPreviewStore.setSelectedSlotId(slot.id());
        TailorPreviewStore.setSlim(slot.slim());
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier skin = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        if (skin != null) {
            TailorPreviewStore.setSkinTexture(skin);
        }
        Identifier cape = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        TailorPreviewStore.setCapeTexture(cape);
        TailorPreviewStore.setStatus("Preview · " + slot.name());
    }

    private void previewLocalOnly(LocalSkinLibrary.Entry entry) {
        Identifier id = PresenceTextureCache.ensureLocalFile(entry.path());
        Identifier ready = PresenceTextureCache.getIfReady(PresenceTextureCache.localFileCacheKey(entry.path()));
        if (ready == null) {
            ready = id;
        }
        TailorPreviewStore.setSelectedSlotId(-1L);
        if (ready != null) {
            TailorPreviewStore.setSkinTexture(ready);
        }
        TailorPreviewStore.setStatus("Preview · " + entry.name());
    }

    private void rebuild() {
        if (rebuilding) {
            return;
        }
        rebuilding = true;
        try {
            double scroll = 0.0;
            for (GuiEventListener child : this.children()) {
                if (child instanceof AbstractScrollArea area) {
                    scroll = area.scrollAmount();
                    break;
                }
            }
            this.clearWidgets();
            this.init();
            if (scroll > 0.0) {
                for (GuiEventListener child : this.children()) {
                    if (child instanceof AbstractScrollArea area) {
                        area.setScrollAmount(scroll);
                        break;
                    }
                }
            }
        } finally {
            rebuilding = false;
        }
    }

    private int previewWidth() {
        return Math.min(200, Math.max(140, this.width / 4));
    }

    private int libraryWidth() {
        int margin = this.width < 360 ? 16 : 32;
        return Math.max(180, this.width - margin - previewWidth() - 24);
    }

    @Override
    public void onClose() {
        if (parent instanceof net.minecraft.client.gui.screens.PauseScreen) {
            closeToGame();
            return;
        }
        open(parent);
    }

    private void closeToGame() {
        open(null);
    }

    private void open(Screen screen) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null) {
            mc.gui.setScreen(screen);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
