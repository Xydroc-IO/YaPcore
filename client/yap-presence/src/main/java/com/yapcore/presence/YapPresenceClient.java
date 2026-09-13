package com.yapcore.presence;

import com.mojang.blaze3d.platform.InputConstants;
import com.yapcore.presence.emote.EmoteClipLoader;
import com.yapcore.presence.emote.PresenceEmoteStore;
import com.yapcore.presence.movement.MovementProfile;
import com.yapcore.presence.movement.MovementProfileStore;
import com.yapcore.presence.ui.PresenceHubScreen;
import com.yapcore.presence.ui.PresenceUiMessages;
import com.yapcore.presence.ui.PresenceUiStore;
import com.yapcore.presence.ui.TailorPreviewStore;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class YapPresenceClient implements ClientModInitializer {

    public static final String MOD_ID = "yap-presence";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openKey;
    private static PresenceConfig config;

    @Override
    public void onInitializeClient() {
        config = PresenceConfig.load();
        MovementProfileStore.set(MovementProfile.catalogDefaults());
        openKey();
        LOGGER.info("YaP Tailor ready — Esc menu + keybind; HELLO/SKIN/EMOTE/MOVEMENT/UI; {} emote clips; band={}",
                EmoteClipLoader.size(), MovementProfileStore.get().band);
    }

    public static PresenceConfig config() {
        if (config == null) {
            config = PresenceConfig.load();
        }
        return config;
    }

    public static KeyMapping openKey() {
        if (openKey == null) {
            openKey = new KeyMapping(
                    "key.yap-presence.open",
                    InputConstants.KEY_P,
                    KeyMapping.Category.MISC);
        }
        return openKey;
    }

    /** Opens the YaP Tailor hub (wardrobe + emotes) — same records as /wardrobe and Bedrock forms. */
    public static void openHub() {
        if (!config().enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (minecraft.gui.screen() instanceof ChatScreen) {
            return;
        }
        sendRaw("UI|SYNC");
        // Never nest under PauseScreen — opening Tailor from Esc must not leave you in the pause menu.
        Screen current = minecraft.gui.screen();
        Screen parent = current instanceof PauseScreen ? null : current;
        if (parent instanceof PresenceHubScreen) {
            parent = null;
        }
        minecraft.gui.setScreen(new PresenceHubScreen(parent));
    }

    public static void sendHello() {
        sendRaw("HELLO");
    }

    public static void sendEmote(String bedrockEmoteId) {
        if (bedrockEmoteId == null || bedrockEmoteId.isBlank()) {
            return;
        }
        String id = bedrockEmoteId.trim();
        // Optimistic local playback — first-person can't see arms wait for the round-trip.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            PresenceEmoteStore.play(minecraft.player.getUUID(), id);
            String name = EmoteClipLoader.byId(id).map(EmoteClipLoader.Clip::name).orElse("emote");
            PresenceUiStore.setStatus("Playing " + name + " · switch to F5 to watch yourself");
        }
        sendRaw("EMOTE|" + id);
    }

    public static void sendRaw(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null
                || message == null || message.isBlank()) {
            return;
        }
        try {
            minecraft.getConnection().send(new ServerboundCustomPayloadPacket(
                    new PresenceChannelPayload(message)));
        } catch (Exception e) {
            LOGGER.warn("Failed to send yap:presence {}", message.split("\\|", 2)[0], e);
        }
    }

    /**
     * Upload a skin or cape PNG (chunked when base64 exceeds ~12KB).
     *
     * @param cape true for cape file, false for body skin
     */
    public static void uploadPngFile(boolean cape, byte[] png) {
        if (png == null || png.length == 0) {
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(png);
        final int chunk = 12_000;
        String prefix = cape ? "CAPEPNG" : "PNG";
        if (b64.length() <= chunk) {
            sendRaw("SKIN|" + prefix + "|" + b64);
            return;
        }
        int total = (b64.length() + chunk - 1) / chunk;
        for (int i = 0; i < total; i++) {
            int start = i * chunk;
            int end = Math.min(b64.length(), start + chunk);
            sendRaw("SKIN|" + prefix + "PART|" + i + "|" + total + "|" + b64.substring(start, end));
        }
        sendRaw("SKIN|" + prefix + "END");
    }

    public static void handleServerMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String text = message.trim();
        int start = 0;
        while (start < text.length() && text.charAt(start) < 32) {
            start++;
        }
        text = text.substring(start).trim();
        if (text.regionMatches(true, 0, "MOVEMENT|", 0, 9)) {
            MovementProfile profile = MovementProfile.parse(text);
            MovementProfileStore.set(profile);
            LOGGER.info("Presence MOVEMENT band={} speed={} sprint×={} fly={}",
                    profile.band, profile.speed, profile.sprintMultiplier, profile.flySpeed);
            return;
        }
        if (text.regionMatches(true, 0, "WARDROBE|", 0, 9)) {
            PresenceUiMessages.decodeWardrobe(text).ifPresent(PresenceUiStore::setWardrobe);
            return;
        }
        if (text.regionMatches(true, 0, "EMOTE_CATALOG|", 0, 14)) {
            PresenceUiMessages.decodeEmoteCatalog(text).ifPresent(PresenceUiStore::setEmotes);
            return;
        }
        if (text.regionMatches(true, 0, "UI|OK|", 0, 6)) {
            String ok = text.substring(6);
            PresenceUiStore.setStatus(ok);
            TailorPreviewStore.setStatus(ok);
            return;
        }
        if (text.regionMatches(true, 0, "UI|ERR|", 0, 7)) {
            String err = "Error: " + text.substring(7);
            PresenceUiStore.setStatus(err);
            TailorPreviewStore.setStatus(err);
            return;
        }
        if (text.regionMatches(true, 0, "EMOTE|", 0, 6)) {
            handleEmote(text);
            return;
        }
        if (!text.regionMatches(true, 0, "SKIN|", 0, 5)) {
            return;
        }
        // SKIN|<uuid>|CLEAR  OR  SKIN|<uuid>|<slim>|<url>|<geometryBase64>
        String[] parts = text.split("\\|", 5);
        if (parts.length < 3) {
            LOGGER.warn("Malformed SKIN payload");
            return;
        }
        try {
            UUID uuid = UUID.fromString(parts[1].trim());
            if ("CLEAR".equalsIgnoreCase(parts[2].trim())) {
                PresenceSkinApplier.clear(uuid);
                LOGGER.debug("Presence SKIN cleared for {}", uuid);
                return;
            }
            if (parts.length < 5) {
                LOGGER.warn("Malformed SKIN payload (need 5 fields)");
                return;
            }
            boolean slim = "1".equals(parts[2].trim());
            String url = parts[3].trim();
            if (url.isBlank()) {
                PresenceSkinApplier.clear(uuid);
                return;
            }
            String geoRaw = parts[4].trim();
            String geoJson = geoRaw.isEmpty()
                    ? ""
                    : new String(Base64.getDecoder().decode(geoRaw), StandardCharsets.UTF_8);
            PresenceSkin skin = new PresenceSkin(uuid, slim, url, geoJson);
            PresenceSkinStore.put(skin);
            PresenceTextureCache.ensureDownloaded(uuid, url);
            LOGGER.debug("Presence SKIN stored for {} slim={} cubes={}",
                    uuid, slim, skin.hasRenderableGeometry());
        } catch (Exception e) {
            LOGGER.warn("Failed to parse presence SKIN: {}", e.toString());
        }
    }

    private static void handleEmote(String text) {
        // EMOTE|<playerUuid>|<bedrockEmoteUuid>
        String[] parts = text.split("\\|", 3);
        if (parts.length < 3) {
            LOGGER.warn("Malformed EMOTE payload");
            return;
        }
        try {
            UUID uuid = UUID.fromString(parts[1].trim());
            String emoteId = parts[2].trim();
            PresenceEmoteStore.play(uuid, emoteId);
            LOGGER.debug("Presence EMOTE {} → {}", emoteId, uuid);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse presence EMOTE: {}", e.toString());
        }
    }
}
