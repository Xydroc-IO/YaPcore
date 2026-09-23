package com.yapcore.presence;

import com.yapcore.presence.ui.TailorPreviewStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async PNG download → {@link DynamicTexture}. Supports per-player active skins and
 * arbitrary URL keys (wardrobe slot thumbnails).
 */
public final class PresenceTextureCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("yap-presence");
    private static final ExecutorService DOWNLOAD =
            Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "yap-presence-skin-dl");
                t.setDaemon(true);
                return t;
            });

    private static final Map<UUID, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<UUID, String> IN_FLIGHT_URL = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> KEY_READY = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_IN_FLIGHT = new ConcurrentHashMap<>();
    /** Ids we successfully bound as {@link DynamicTexture} — never probe TextureManager.getTexture. */
    private static final ConcurrentHashMap.KeySetView<Identifier, Boolean> DYNAMIC_BOUND =
            ConcurrentHashMap.newKeySet();

    private PresenceTextureCache() {
    }

    public static Identifier textureId(UUID uuid) {
        return Identifier.fromNamespaceAndPath("yap-presence", "skins/" + uuid.toString().toLowerCase(Locale.ROOT));
    }

    public static Identifier wardrobeTextureId(long slotId) {
        return Identifier.fromNamespaceAndPath("yap-presence", "wardrobe/" + slotId);
    }

    public static Identifier wardrobeCapeTextureId(long slotId) {
        return Identifier.fromNamespaceAndPath("yap-presence", "wardrobe/" + slotId + "_cape");
    }

    /** Returns a registered texture id when download finished; otherwise empty. */
    public static Identifier getIfReady(UUID uuid) {
        return READY.get(uuid);
    }

    public static Identifier getIfReady(String cacheKey) {
        return cacheKey == null ? null : KEY_READY.get(cacheKey);
    }

    /**
     * True when we registered {@code id} as a {@link DynamicTexture}.
     * Never call {@code TextureManager#getTexture} here — on MC 26.2 that auto-loads a
     * SimpleTexture from the resource pack and logs {@code Missing resource yap-presence:…}.
     */
    public static boolean isRegistered(Identifier id) {
        return id != null && DYNAMIC_BOUND.contains(id);
    }

    public static void ensureDownloaded(UUID uuid, String url) {
        if (uuid == null || url == null || url.isBlank()) {
            return;
        }
        Identifier existing = READY.get(uuid);
        String inflight = IN_FLIGHT_URL.get(uuid);
        if (existing != null && url.equals(inflight)) {
            return;
        }
        if (url.equals(inflight)) {
            return;
        }
        IN_FLIGHT_URL.put(uuid, url);
        DOWNLOAD.execute(() -> downloadAndRegisterPlayer(uuid, url));
    }

    /**
     * Download {@code url} into a stable texture keyed by {@code cacheKey}
     * (e.g. {@code wardrobe/7} or {@code wardrobe/7_cape}).
     */
    public static void ensureDownloaded(String cacheKey, Identifier textureId, String url) {
        if (cacheKey == null || cacheKey.isBlank() || textureId == null || url == null || url.isBlank()) {
            return;
        }
        Identifier existing = KEY_READY.get(cacheKey);
        String inflight = KEY_IN_FLIGHT.get(cacheKey);
        if (existing != null && url.equals(inflight)) {
            return;
        }
        if (url.equals(inflight)) {
            return;
        }
        KEY_IN_FLIGHT.put(cacheKey, url);
        DOWNLOAD.execute(() -> downloadAndRegisterKey(cacheKey, textureId, url));
    }

    public static void ensureWardrobeSlot(long slotId, String skinUrl, String capeUrl) {
        if (slotId <= 0) {
            return;
        }
        if (skinUrl != null && !skinUrl.isBlank()) {
            ensureDownloaded("wardrobe/" + slotId, wardrobeTextureId(slotId), skinUrl);
        }
        if (capeUrl != null && !capeUrl.isBlank()) {
            ensureDownloaded("wardrobe/" + slotId + "_cape", wardrobeCapeTextureId(slotId), capeUrl);
        }
    }

    /** Stable texture id for a local PNG path (chooser thumbnails). */
    public static Identifier localFileTextureId(Path path) {
        String key = localFileCacheKey(path);
        return Identifier.fromNamespaceAndPath("yap-presence", key);
    }

    public static String localFileCacheKey(Path path) {
        String abs = path == null ? "unknown"
                : path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        // Identifier path: [a-z0-9/._-] only
        String hex = Integer.toHexString(abs.hashCode());
        return "local/" + hex;
    }

    /**
     * Load a local skin PNG into the texture atlas (async). Returns the texture id immediately;
     * call {@link #getIfReady(String)} / notify listeners when decode finishes.
     */
    public static Identifier ensureLocalFile(Path path) {
        if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            return null;
        }
        String key = localFileCacheKey(path);
        Identifier id = Identifier.fromNamespaceAndPath("yap-presence", key);
        if (KEY_READY.containsKey(key)) {
            return id;
        }
        if (KEY_IN_FLIGHT.containsKey(key)) {
            return id;
        }
        KEY_IN_FLIGHT.put(key, path.toAbsolutePath().toString());
        DOWNLOAD.execute(() -> {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(path);
                if (bytes.length == 0 || bytes.length > 1_048_576) {
                    KEY_IN_FLIGHT.remove(key);
                    return;
                }
                NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                Minecraft.getInstance().execute(() -> {
                    try {
                        registerIdentifier(id, image);
                        KEY_READY.put(key, id);
                        // PlayerSkinWidget suppliers poll getIfReady — no UI rebuild.
                    } catch (Exception e) {
                        LOGGER.warn("Failed to register local skin {}: {}", path, e.toString());
                        try {
                            image.close();
                        } catch (Exception ignored) {
                        }
                    } finally {
                        KEY_IN_FLIGHT.remove(key);
                    }
                });
            } catch (Exception e) {
                KEY_IN_FLIGHT.remove(key);
                LOGGER.warn("Failed to load local skin {}: {}", path, e.toString());
            }
        });
        return id;
    }

    /** Register raw PNG bytes immediately (file picker preview / local apply). */
    public static void registerBytes(Identifier id, byte[] pngBytes) {
        if (id == null || pngBytes == null || pngBytes.length == 0) {
            return;
        }
        DOWNLOAD.execute(() -> {
            try {
                NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(pngBytes));
                Minecraft.getInstance().execute(() -> {
                    registerIdentifier(id, image);
                    KEY_READY.put(id.toString(), id);
                    TailorPreviewStore.notifyTexturesChanged();
                });
            } catch (Exception e) {
                LOGGER.warn("Failed to decode PNG for {}: {}", id, e.toString());
            }
        });
    }

    /** Decode + register on the calling thread (must be render thread). */
    public static void registerBytesNow(Identifier id, byte[] pngBytes) {
        if (id == null || pngBytes == null || pngBytes.length == 0) {
            return;
        }
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(pngBytes));
            registerIdentifier(id, image);
            KEY_READY.put(id.toString(), id);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode PNG for {}: {}", id, e.toString());
        }
    }

    private static void registerIdentifier(Identifier id, NativeImage image) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getTextureManager() == null) {
                image.close();
                return;
            }
            // Do NOT call getTexture(id) first — that fabricates a SimpleTexture + Missing resource warn.
            DynamicTexture dynamic = new DynamicTexture(() -> id.toString(), image);
            mc.getTextureManager().register(id, dynamic);
            DYNAMIC_BOUND.add(id);
        } catch (Exception e) {
            LOGGER.warn("Failed to register texture {}: {}", id, e.toString());
            DYNAMIC_BOUND.remove(id);
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void downloadAndRegisterPlayer(UUID uuid, String urlString) {
        try {
            NativeImage image = fetchPng(urlString);
            if (image == null) {
                return;
            }
            Minecraft.getInstance().execute(() -> registerOnClient(uuid, urlString, image));
        } catch (Exception e) {
            LOGGER.warn("Failed to download presence skin for {}: {}", uuid, e.toString());
        }
    }

    private static void downloadAndRegisterKey(String cacheKey, Identifier textureId, String urlString) {
        try {
            NativeImage image = fetchPng(urlString);
            if (image == null) {
                return;
            }
            Minecraft.getInstance().execute(() -> {
                try {
                    if (!urlString.equals(KEY_IN_FLIGHT.get(cacheKey))) {
                        image.close();
                        return;
                    }
                    registerIdentifier(textureId, image);
                    KEY_READY.put(cacheKey, textureId);
                    TailorPreviewStore.notifyTexturesChanged();
                } catch (Exception e) {
                    LOGGER.warn("Failed to register key texture {}: {}", cacheKey, e.toString());
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to download key skin {}: {}", cacheKey, e.toString());
        }
    }

    private static NativeImage fetchPng(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(12_000);
        conn.setRequestProperty("User-Agent", "YaP-Presence/1.0");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            LOGGER.warn("Skin download HTTP {} for {}", code, urlString);
            return null;
        }
        try (InputStream in = conn.getInputStream()) {
            return NativeImage.read(in);
        } finally {
            conn.disconnect();
        }
    }

    private static void registerOnClient(UUID uuid, String urlString, NativeImage image) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getTextureManager() == null) {
                image.close();
                return;
            }
            if (!urlString.equals(IN_FLIGHT_URL.get(uuid))) {
                image.close();
                return;
            }
            Identifier id = textureId(uuid);
            // Do NOT probe getTexture(id) — that loads a missing SimpleTexture first.
            DynamicTexture dynamic = new DynamicTexture(() -> "yap-presence/" + uuid, image);
            mc.getTextureManager().register(id, dynamic);
            DYNAMIC_BOUND.add(id);
            READY.put(uuid, id);
            TailorPreviewStore.setSkinTexture(id);
            TailorPreviewStore.notifyTexturesChanged();
            PresenceWorldRefresh.afterSkinReady();
        } catch (Exception e) {
            LOGGER.warn("Failed to register presence texture for {}: {}", uuid, e.toString());
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Point the player's live skin at an already-registered texture (wardrobe slot / browse)
     * so the in-world avatar updates immediately.
     */
    public static void bindReady(UUID uuid, Identifier texture) {
        if (uuid == null || texture == null || !isRegistered(texture)) {
            return;
        }
        READY.put(uuid, texture);
        IN_FLIGHT_URL.put(uuid, texture.toString());
        TailorPreviewStore.setSkinTexture(texture);
        TailorPreviewStore.notifyTexturesChanged();
        PresenceWorldRefresh.afterSkinReady();
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Identifier old = READY.remove(uuid);
        IN_FLIGHT_URL.remove(uuid);
        if (old != null) {
            // Keep DYNAMIC_BOUND — texture may still be used by preview widgets.
        }
    }

    public static void clear() {
        READY.clear();
        IN_FLIGHT_URL.clear();
        KEY_READY.clear();
        KEY_IN_FLIGHT.clear();
        DYNAMIC_BOUND.clear();
    }
}
