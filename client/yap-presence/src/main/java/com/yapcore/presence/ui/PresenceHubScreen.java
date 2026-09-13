package com.yapcore.presence.ui;

import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level Tailor hub — skins, wardrobe, and emotes (staff-menu style entry from Esc / P).
 */
public final class PresenceHubScreen extends PresencePanelScreen {

    private final Runnable listener = this::rebuildWidgets;

    public PresenceHubScreen(Screen parent) {
        super(Component.literal("YaP Tailor"), parent);
    }

    @Override
    protected void init() {
        PresenceUiStore.addListener(listener);
        super.init();
    }

    @Override
    public void removed() {
        PresenceUiStore.removeListener(listener);
        super.removed();
    }

    @Override
    protected void addContents() {
        addSubtitle("Skins, wardrobe, and emotes · same records as /wardrobe and Bedrock forms");
        String status = PresenceUiStore.lastStatus();
        if (!status.isBlank()) {
            addSubtitle(status);
        }

        PresenceUiMessages.WardrobeView w = PresenceUiStore.wardrobe();
        addSection("Active look");
        addSubtitle("Model: " + (w.slim() ? "slim" : "wide")
                + " · wardrobe slots: " + w.slots().size());
        if (w.activeUrl().isBlank()) {
            addSubtitle("No custom skin — Mojang / default look");
        } else {
            addSubtitle(truncate(w.activeUrl(), 72));
        }
        if (!w.activeCape().isBlank()) {
            addSubtitle("Cape: " + truncate(w.activeCape(), 64));
        }

        addSection("Appearance");
        addButtonGrid(
                action("Wardrobe", "Skin chooser — preview saved looks, wear, import PNG", () ->
                        open(new WardrobeScreen(this))),
                action("Emotes", "Play free Bedrock catalog emotes", () ->
                        open(new EmotePickerScreen(this)))
        );

        List<PresenceUiMessages.EmoteView> emotes = PresenceUiStore.emotes();
        if (!emotes.isEmpty()) {
            addSection("Quick emotes");
            addSubtitle("Plays immediately · use Emotes → Play & watch to see yourself in F5");
            List<Button> row = new ArrayList<>();
            for (PresenceUiMessages.EmoteView e : emotes) {
                row.add(action(e.name(), "Play " + e.name() + " and watch", () -> playAndWatch(e.id())));
                if (row.size() >= gridColumns()) {
                    addButtonGrid(row.toArray(Button[]::new));
                    row.clear();
                }
            }
            if (!row.isEmpty()) {
                addButtonGrid(row.toArray(Button[]::new));
            }
        }

        addSection("Sync");
        addButtonGrid(
                actionWide("Refresh from server", "Reload wardrobe + emote catalog (UI|SYNC)", () -> {
                    YapPresenceClient.sendRaw("UI|SYNC");
                    rebuildWidgets();
                })
        );
    }

    private void playAndWatch(String emoteId) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            try {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } catch (Exception ignored) {
            }
        }
        YapPresenceClient.sendEmote(emoteId);
        closeToGame();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
