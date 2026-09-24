package com.yapcore.tailor;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Fabric client channel {@code yap:presence}.
 * <ul>
 *   <li>{@code HELLO} — client ready; server pushes MOVEMENT + EMOTE_CATALOG + WARDROBE</li>
 *   <li>{@code UI|SYNC} — refresh wardrobe + emote catalog</li>
 *   <li>{@code WARDROBE|APPLY|&lt;id&gt;} / {@code DELETE|&lt;id&gt;} / {@code SAVE|&lt;nameB64&gt;}</li>
 *   <li>{@code SKIN|URL|&lt;urlB64&gt;} / {@code SKIN|MODEL|0|1} / {@code SKIN|CAPE|&lt;urlB64&gt;}</li>
 *   <li>{@code SKIN|PNG|&lt;b64&gt;} / {@code SKIN|CAPEPNG|&lt;b64&gt;} — single-packet file upload</li>
 *   <li>{@code SKIN|PNGPART|i|n|b64} then {@code SKIN|PNGEND} (or CAPEPNGPART / CAPEPNGEND) — chunked</li>
 *   <li>{@code EMOTE|&lt;bedrockEmoteUuid&gt;} — client requests play</li>
 *   <li>Server→client {@code SKIN|…}, {@code EMOTE|…}, {@code MOVEMENT|…}, {@code WARDROBE|v1|…},
 *       {@code EMOTE_CATALOG|v1|…}, {@code UI|OK|…} / {@code UI|ERR|…}</li>
 * </ul>
 */
public final class PresenceChannel implements PluginMessageListener {

    public static final String CHANNEL = "yap:presence";

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;
    private final TailorEmoteCatalog emotes = TailorEmoteCatalog.get();
    private final Set<UUID> presenceClients = ConcurrentHashMap.newKeySet();
    /** In-flight chunked PNG uploads: player → assembled base64 parts. */
    private final ConcurrentHashMap<UUID, PresenceInboundCommands.PngUpload> pngUploads =
            new ConcurrentHashMap<>();
    private final PresenceInboundCommands inbound;
    private final PresenceSkinBroadcast skinBroadcast;

    public PresenceChannel(TailorPlugin plugin, TailorServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
        this.inbound = new PresenceInboundCommands(this, service, pngUploads);
        this.skinBroadcast = new PresenceSkinBroadcast(plugin, service, presenceClients);
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        try {
            messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
        presenceClients.clear();
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            presenceClients.remove(uuid);
            pngUploads.remove(uuid);
        }
    }

    public boolean hasPresenceHello(UUID uuid) {
        return uuid != null && presenceClients.contains(uuid);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null) {
            return;
        }
        String text = decode(message);
        if (text.isEmpty()) {
            return;
        }
        if (text.regionMatches(true, 0, "HELLO", 0, 5)) {
            presenceClients.add(player.getUniqueId());
            UUID viewer = player.getUniqueId();
            sendMovementProfile(player);
            sendEmoteCatalog(player);
            sendWardrobe(player);
            YapSched.async(plugin, () -> {
                skinBroadcast.sendSkinAsync(viewer, player.getUniqueId());
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(viewer)) {
                        skinBroadcast.sendSkinAsync(viewer, other.getUniqueId());
                    }
                }
            });
            return;
        }
        if (text.equalsIgnoreCase("UI|SYNC") || text.regionMatches(true, 0, "UI|SYNC|", 0, 8)) {
            presenceClients.add(player.getUniqueId());
            sendEmoteCatalog(player);
            sendWardrobe(player);
            return;
        }
        if (text.regionMatches(true, 0, "WARDROBE|", 0, 9)) {
            inbound.handleWardrobeCommand(player, text);
            return;
        }
        if (text.regionMatches(true, 0, "SKIN|", 0, 5)) {
            // URL / MODEL / CAPE / PNG / CAPEPNG / PNGPART / PNGEND / CLEAR
            inbound.handleSkinCommand(player, text);
            return;
        }
        if (text.regionMatches(true, 0, "EMOTE|", 0, 6)) {
            String emoteId = text.substring(6).trim();
            // Ignore server-style EMOTE|playerUuid|id if a confused client echoes it
            if (emoteId.indexOf('|') >= 0) {
                return;
            }
            playEmote(player, emoteId);
        }
    }

    public void sendEmoteCatalog(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        List<PresenceUiCodec.EmoteView> views = new java.util.ArrayList<>();
        for (TailorEmoteCatalog.Entry e : emotes.entries()) {
            views.add(new PresenceUiCodec.EmoteView(e.id(), e.name()));
        }
        byte[] bytes = PresenceUiCodec.encodeEmoteCatalog(views).getBytes(StandardCharsets.UTF_8);
        YapSched.global(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.sendPluginMessage(plugin, CHANNEL, bytes);
            }
        });
    }

    public void sendWardrobe(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        UUID uuid = viewer.getUniqueId();
        YapSched.async(plugin, () -> {
            try {
                boolean slim = false;
                String activeUrl = "";
                String activeCape = "";
                long activeSlotId = -1L;
                Optional<ActiveSkin> active = service.getActiveSkin(uuid);
                if (active.isPresent()) {
                    slim = active.get().model() == SkinModel.SLIM;
                    activeUrl = active.get().sourceUrl() == null ? "" : active.get().sourceUrl();
                    activeCape = active.get().capeUrl() == null ? "" : active.get().capeUrl();
                    if (active.get().activeSlotId() != null && active.get().activeSlotId() > 0) {
                        activeSlotId = active.get().activeSlotId();
                    }
                }
                List<PresenceUiCodec.SlotView> slots = new java.util.ArrayList<>();
                for (WardrobeSlot s : service.listWardrobe(uuid)) {
                    slots.add(new PresenceUiCodec.SlotView(
                            s.id(),
                            s.name(),
                            s.model() == SkinModel.SLIM,
                            s.skinUrl() == null ? "" : s.skinUrl(),
                            s.capeUrl() == null ? "" : s.capeUrl()));
                }
                String payload = PresenceUiCodec.encodeWardrobe(
                        new PresenceUiCodec.WardrobeView(slim, activeUrl, activeCape, activeSlotId, slots));
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                YapSched.global(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.sendPluginMessage(plugin, CHANNEL, bytes);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "sendWardrobe failed: " + e.getMessage());
            }
        });
    }

    void sendUi(Player viewer, String payload) {
        if (viewer == null || payload == null) {
            return;
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        YapSched.global(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.sendPluginMessage(plugin, CHANNEL, bytes);
            }
        });
    }

    /**
     * Push Phase 3 movement constants to a presence client.
     */
    public void sendMovementProfile(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        byte[] bytes = ParityMovementApplier.presencePayload().getBytes(StandardCharsets.UTF_8);
        YapSched.global(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.sendPluginMessage(plugin, CHANNEL, bytes);
            }
        });
    }

    /**
     * Validate catalog id (or name), broadcast to JE presence clients, relay to chassis/BE.
     */
    public void playEmote(Player actor, String emoteIdOrName) {
        if (actor == null || emoteIdOrName == null || emoteIdOrName.isBlank()) {
            return;
        }
        Optional<TailorEmoteCatalog.Entry> entry = emotes.resolve(emoteIdOrName.trim());
        if (entry.isEmpty()) {
            sendUi(actor, PresenceUiCodec.uiErr("Unknown catalog emote"));
            actor.sendMessage("Unknown catalog emote.");
            return;
        }
        String emoteId = entry.get().id();
        String emoteName = entry.get().name();
        presenceClients.add(actor.getUniqueId());
        broadcastEmote(actor.getUniqueId(), emoteId);
        sendUi(actor, PresenceUiCodec.uiOk("Playing " + emoteName));
        YapSched.async(plugin, () -> {
            boolean ok = ChassisEmotePush.push(
                    plugin, plugin.tailorConfig(), actor.getName(), actor.getUniqueId(), emoteId);
            if (!ok) {
                plugin.getLogger().fine("Chassis emote push failed for " + actor.getName()
                        + " (JE broadcast still sent)");
            }
        });
    }

    /**
     * Push EMOTE for {@code subject} to all HELLO clients. Called from chassis JE relay
     * (Bedrock-originated emotes) and local play.
     */
    public void broadcastEmote(UUID subjectUuid, String emoteId) {
        if (subjectUuid == null || emoteId == null || emoteId.isBlank()) {
            return;
        }
        if (emotes.byId(emoteId).isEmpty()) {
            return;
        }
        String payload = "EMOTE|" + subjectUuid + "|" + emoteId;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        YapSched.global(plugin, () -> {
            for (UUID client : presenceClients) {
                Player viewer = Bukkit.getPlayer(client);
                if (viewer != null && viewer.isOnline()) {
                    viewer.sendPluginMessage(plugin, CHANNEL, bytes);
                }
            }
            // Ensure subject receives even if HELLO raced
            Player subject = Bukkit.getPlayer(subjectUuid);
            if (subject != null && subject.isOnline() && !presenceClients.contains(subjectUuid)) {
                subject.sendPluginMessage(plugin, CHANNEL, bytes);
            }
        });
    }

    /**
     * Push SKIN for {@code subject} to all clients that sent HELLO (and to the subject if online).
     * Called after a successful ChassisSkinPush.
     */
    public void sendSkin(Player subject) {
        skinBroadcast.sendSkin(subject);
    }

    /**
     * Chassis → JE: broadcast a Bedrock-ingested skin (URL + geometry) without requiring a
     * Tailor ActiveSkin row. Used so persona / custom geo from {@code SkinService} reaches
     * {@code yap-presence} clients.
     */
    public void broadcastExternalSkin(UUID subjectUuid, boolean slim, String skinUrl, String geometryJson) {
        skinBroadcast.broadcastExternalSkin(subjectUuid, slim, skinUrl, geometryJson);
    }

    private static String decode(byte[] message) {
        String text = new String(message == null ? new byte[0] : message, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return "";
        }
        int start = 0;
        while (start < text.length() && text.charAt(start) < 32) {
            start++;
        }
        return text.substring(start).trim();
    }
}
