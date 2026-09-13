package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceTextureCache;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;

/** Fabric emote picker — catalog play with live look preview and play-and-watch. */
public final class EmotePickerScreen extends PresencePanelScreen {

    private final Runnable listener = this::rebuildWidgets;

    public EmotePickerScreen(Screen parent) {
        super(Component.literal("YaP Tailor · Emotes"), parent);
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
        addSubtitle("Free Bedrock catalog · others with yap-presence see you play them");
        if (!PresenceUiStore.lastStatus().isBlank()) {
            addSubtitle(PresenceUiStore.lastStatus());
        }

        addSection("Preview");
        addPreviewWidget();
        addSubtitle("Emotes animate your in-world model (F5) — not this idle preview");

        List<PresenceUiMessages.EmoteView> emotes = PresenceUiStore.emotes();
        if (emotes.isEmpty()) {
            addSubtitle("No clips bundled — reinstall yap-presence or sync from the hub.");
            addButtonGrid(actionWide("Request catalog", "Ask Tailor for EMOTE_CATALOG", () ->
                    YapPresenceClient.sendRaw("UI|SYNC")));
            return;
        }

        addSection("Play (" + emotes.size() + ")");
        List<Button> row = new ArrayList<>();
        for (PresenceUiMessages.EmoteView e : emotes) {
            row.add(action(e.name(), "Play " + e.name() + " now (stay in menu)", () ->
                    YapPresenceClient.sendEmote(e.id())));
            if (row.size() >= gridColumns()) {
                addButtonGrid(row.toArray(Button[]::new));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            addButtonGrid(row.toArray(Button[]::new));
        }

        addSection("Play & watch");
        addSubtitle("Closes the menu and switches to third-person so you can see the animation");
        List<Button> watch = new ArrayList<>();
        for (PresenceUiMessages.EmoteView e : emotes) {
            watch.add(action(e.name() + " →", "Play " + e.name() + " and watch in F5", () ->
                    playAndWatch(e.id())));
            if (watch.size() >= gridColumns()) {
                addButtonGrid(watch.toArray(Button[]::new));
                watch.clear();
            }
        }
        if (!watch.isEmpty()) {
            addButtonGrid(watch.toArray(Button[]::new));
        }

        addButtonGrid(actionWide("Refresh catalog", "Reload EMOTE_CATALOG from Tailor", () ->
                YapPresenceClient.sendRaw("UI|SYNC")));
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

    private void addPreviewWidget() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null || mc.getEntityModels() == null) {
            addSubtitle("Preview unavailable");
            return;
        }
        int w = Math.min(120, Math.max(72, panelWidth() / 4));
        int h = (int) (w * 1.6f);
        PlayerSkinWidget preview = new PlayerSkinWidget(w, h, mc.getEntityModels(), this::resolvePreviewSkin);
        LinearLayout row = LinearLayout.horizontal().spacing(12);
        row.addChild(preview);
        addBody(row);
    }

    private PlayerSkin resolvePreviewSkin() {
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
}
