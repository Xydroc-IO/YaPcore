package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Minecraft-style skin chooser: large live preview + scrollable library of saved wardrobe
 * looks and local downloaded PNGs (Prism skins / Downloads).
 */
public final class WardrobeScreen extends Screen {

    final Screen parent;
    HeaderAndFooterLayout layout;
    ScrollableLayout rightScroll;
    LinearLayout rightBody;
    EditBox urlBox;
    EditBox capeBox;
    EditBox saveNameBox;
    EditBox renameBox;
    Long pendingDeleteId;
    List<LocalSkinLibrary.Entry> localSkins = List.of();
    /** Carousel index across wardrobe slots then local PNGs (−1 = none). */
    int carouselIndex = -1;
    private final Runnable listener = this::rebuild;
    private boolean rebuilding;

    private final WardrobeSkinSupport skins = new WardrobeSkinSupport(this);
    private final WardrobePreviewUi previewUi = new WardrobePreviewUi(this, skins);
    private final WardrobeLibraryUi libraryUi = new WardrobeLibraryUi(this, skins);

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
        columns.addChild(previewUi.build());

        rightBody = LinearLayout.vertical().spacing(4);
        libraryUi.fill(rightBody);
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

    void rebuild() {
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

    /** Package helpers cannot read protected {@link Screen} fields directly. */
    Minecraft client() {
        return this.minecraft != null ? this.minecraft : Minecraft.getInstance();
    }

    Font uiFont() {
        return this.font;
    }

    int wardrobeSlotCount() {
        return PresenceUiStore.wardrobe().slots().size();
    }

    int carouselSize() {
        return wardrobeSlotCount() + localSkins.size();
    }

    int previewWidth() {
        return Math.min(200, Math.max(140, this.width / 4));
    }

    int libraryWidth() {
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

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
