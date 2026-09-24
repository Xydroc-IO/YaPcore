package com.yapcore.tailor;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Outbound SKIN plugin messages for yap:presence clients. */
final class PresenceSkinBroadcast {

    private static final String FIXTURE_GEO =
            "protocol/bedrock/parity/band_26_50/fixtures/skin/geometry.humanoid.custom.json";

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;
    private final Set<UUID> presenceClients;

    PresenceSkinBroadcast(TailorPlugin plugin, TailorServiceImpl service, Set<UUID> presenceClients) {
        this.plugin = plugin;
        this.service = service;
        this.presenceClients = presenceClients;
    }

    void sendSkin(Player subject) {
        if (subject == null) {
            return;
        }
        UUID subjectUuid = subject.getUniqueId();
        YapSched.async(plugin, () -> {
            for (UUID client : presenceClients) {
                sendSkinAsync(client, subjectUuid);
            }
            sendSkinAsync(subjectUuid, subjectUuid);
        });
    }

    void broadcastExternalSkin(UUID subjectUuid, boolean slim, String skinUrl, String geometryJson) {
        if (subjectUuid == null || skinUrl == null || skinUrl.isBlank()) {
            return;
        }
        String geo = geometryJson == null ? "" : geometryJson;
        String payload = "SKIN|"
                + subjectUuid
                + "|"
                + (slim ? "1" : "0")
                + "|"
                + skinUrl
                + "|"
                + (geo.isEmpty()
                        ? ""
                        : Base64.getEncoder().encodeToString(geo.getBytes(StandardCharsets.UTF_8)));
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        YapSched.global(plugin, () -> {
            for (UUID client : presenceClients) {
                Player viewer = Bukkit.getPlayer(client);
                if (viewer != null && viewer.isOnline()) {
                    viewer.sendPluginMessage(plugin, PresenceChannel.CHANNEL, bytes);
                }
            }
            Player subject = Bukkit.getPlayer(subjectUuid);
            if (subject != null && subject.isOnline() && !presenceClients.contains(subjectUuid)) {
                subject.sendPluginMessage(plugin, PresenceChannel.CHANNEL, bytes);
            }
        });
    }

    void sendSkinAsync(UUID viewerUuid, UUID subjectUuid) {
        try {
            Optional<ActiveSkin> activeOpt = service.getActiveSkin(subjectUuid);
            if (activeOpt.isEmpty()) {
                byte[] clear = ("SKIN|" + subjectUuid + "|CLEAR").getBytes(StandardCharsets.UTF_8);
                YapSched.global(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerUuid);
                    if (viewer == null || !viewer.isOnline()) {
                        return;
                    }
                    viewer.sendPluginMessage(plugin, PresenceChannel.CHANNEL, clear);
                });
                return;
            }
            ActiveSkin active = activeOpt.get();
            String skinUrl = resolveSkinUrl(active);
            boolean slim = active.model() == SkinModel.SLIM;
            String geometryJson = PresenceGeometryParse.extractGeometryData(active.bedrockCanonicalJson());
            if (geometryJson == null) {
                geometryJson = "";
            }
            String geoB64 = geometryJson.isEmpty()
                    ? ""
                    : Base64.getEncoder().encodeToString(geometryJson.getBytes(StandardCharsets.UTF_8));

            byte[] png = readSkinPng(active);
            if (png != null && png.length > 0 && png.length <= 96_000) {
                String inline = "SKIN|INLINE|"
                        + subjectUuid
                        + "|"
                        + (slim ? "1" : "0")
                        + "|"
                        + Base64.getEncoder().encodeToString(png)
                        + "|"
                        + geoB64;
                byte[] inlineBytes = inline.getBytes(StandardCharsets.UTF_8);
                YapSched.global(plugin, () -> {
                    Player viewer = Bukkit.getPlayer(viewerUuid);
                    if (viewer == null || !viewer.isOnline()) {
                        return;
                    }
                    viewer.sendPluginMessage(plugin, PresenceChannel.CHANNEL, inlineBytes);
                });
            }

            if (skinUrl == null || skinUrl.isBlank()) {
                if (png == null || png.length == 0) {
                    plugin.getLogger().warning(
                            "PresenceChannel: no public skin URL or PNG for " + subjectUuid
                                    + " — set skin-host-public-base-url so other clients can download");
                }
                return;
            }
            String payload = "SKIN|"
                    + subjectUuid
                    + "|"
                    + (slim ? "1" : "0")
                    + "|"
                    + skinUrl
                    + "|"
                    + geoB64;
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            YapSched.global(plugin, () -> {
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer == null || !viewer.isOnline()) {
                    return;
                }
                viewer.sendPluginMessage(plugin, PresenceChannel.CHANNEL, bytes);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "PresenceChannel sendSkin failed: " + e.getMessage());
        }
    }

    private byte[] readSkinPng(ActiveSkin active) {
        if (active == null || active.playerUuid() == null) {
            return null;
        }
        byte[] local = service.images().readStoredSkin(active.playerUuid());
        if (local != null && local.length > 0) {
            return local;
        }
        String url = resolveSkinUrl(active);
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return service.images().downloadAndValidate(url);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveSkinUrl(ActiveSkin active) {
        if (active.sourceUrl() != null && !active.sourceUrl().isBlank()) {
            return service.config().publicSkinUrl(active.playerUuid(), active.sourceUrl());
        }
        return service.config().publicSkinUrl(active.playerUuid(), null);
    }

    String resolveGeometryJson(ActiveSkin active) {
        String fromCanonical = PresenceGeometryParse.extractGeometryData(active.bedrockCanonicalJson());
        if (fromCanonical != null && !fromCanonical.isBlank()) {
            return fromCanonical;
        }
        return loadFixtureGeometry();
    }

    private String loadFixtureGeometry() {
        try (InputStream in = PresenceSkinBroadcast.class.getClassLoader().getResourceAsStream(FIXTURE_GEO)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load geometry fixture", e);
            return "";
        }
    }
}
