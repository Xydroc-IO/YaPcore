package com.yapcore.tailor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Download, validate, and store skin / cape PNG bytes. */
public final class SkinImageService {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Set<Integer> ALLOWED_WIDTHS = Set.of(64, 128);
    private static final Set<Integer> ALLOWED_HEIGHTS = Set.of(32, 64, 128);

    private final TailorPlugin plugin;
    private final TailorConfig config;
    private final Path skinsDir;
    private final Path capesDir;
    private final Path wardrobeDir;

    public SkinImageService(TailorPlugin plugin, TailorConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.skinsDir = plugin.getDataFolder().toPath().resolve("skins");
        this.capesDir = plugin.getDataFolder().toPath().resolve("capes");
        this.wardrobeDir = plugin.getDataFolder().toPath().resolve("wardrobe");
    }

    public void ensureDirs() throws TailorException {
        try {
            Files.createDirectories(skinsDir);
            Files.createDirectories(capesDir);
            Files.createDirectories(wardrobeDir);
        } catch (IOException e) {
            throw new TailorException("Cannot create skin storage folders", e);
        }
    }

    public Path skinPath(UUID uuid) {
        return skinsDir.resolve(uuid + ".png");
    }

    public Path capePath(UUID uuid) {
        return capesDir.resolve(uuid + ".png");
    }

    /** Per-slot skin PNG: {@code wardrobe/{playerUuid}/{slotId}.png}. */
    public Path wardrobeSkinPath(UUID playerUuid, long slotId) {
        return wardrobeDir.resolve(playerUuid.toString()).resolve(slotId + ".png");
    }

    /** Per-slot cape PNG: {@code wardrobe/{playerUuid}/{slotId}_cape.png}. */
    public Path wardrobeCapePath(UUID playerUuid, long slotId) {
        return wardrobeDir.resolve(playerUuid.toString()).resolve(slotId + "_cape.png");
    }

    public byte[] downloadAndValidate(String urlString) throws TailorException {
        URL url = parseHttpsUrl(urlString);
        if (!config.isHostAllowed(url.getHost())) {
            throw new TailorException("Host not allowed: " + url.getHost());
        }
        byte[] bytes = download(url);
        validatePng(bytes);
        return bytes;
    }

    public String storeSkin(UUID uuid, String sourceUrl) throws TailorException {
        byte[] bytes = downloadAndValidate(sourceUrl);
        return storeSkinBytes(uuid, bytes, sourceUrl);
    }

    public String storeCape(UUID uuid, String sourceUrl) throws TailorException {
        byte[] bytes = downloadAndValidate(sourceUrl);
        return storeCapeBytes(uuid, bytes, sourceUrl);
    }

    /**
     * Validate + persist skin PNG bytes (file upload path).
     * Returns the public HTTPS URL used in the Mojang textures property.
     */
    public String storeSkinBytes(UUID uuid, byte[] bytes) throws TailorException {
        return storeSkinBytes(uuid, bytes, null);
    }

    public String storeSkinBytes(UUID uuid, byte[] bytes, String fallbackUrl) throws TailorException {
        if (bytes == null || bytes.length == 0) {
            throw new TailorException("Empty skin PNG");
        }
        if (bytes.length > config.maxPngBytes()) {
            throw new TailorException("Image exceeds max-png-bytes (" + config.maxPngBytes() + ")");
        }
        // Resolve public host before writing — otherwise local yap-presence looks correct
        // while ActiveSkin stays on Mojang URL and other players never see the upload.
        if (!config.hasPublicSkinHost()) {
            SkinHostResolver.resolvePublicBase(plugin.getLogger()).ifPresent(config::setSkinHostPublicBaseUrl);
        }
        String pub = config.publicSkinUrl(uuid, fallbackUrl);
        if (pub == null || pub.isBlank()) {
            throw new TailorException(
                    "File upload needs skin-host-public-base-url in YaPTailor config "
                            + "(HTTPS base that serves /skin/{uuid}.png). "
                            + "Without it only you see the skin; others keep Mojang/previous.");
        }
        validatePng(bytes);
        try {
            ensureDirs();
            Files.write(skinPath(uuid), bytes);
        } catch (IOException e) {
            throw new TailorException("Failed to store skin PNG", e);
        }
        ChassisSkinPush.writeSharedSkinPng(plugin, uuid, bytes);
        return pub;
    }

    public String storeCapeBytes(UUID uuid, byte[] bytes) throws TailorException {
        return storeCapeBytes(uuid, bytes, null);
    }

    public String storeCapeBytes(UUID uuid, byte[] bytes, String fallbackUrl) throws TailorException {
        if (bytes == null || bytes.length == 0) {
            throw new TailorException("Empty cape PNG");
        }
        if (bytes.length > config.maxPngBytes()) {
            throw new TailorException("Image exceeds max-png-bytes (" + config.maxPngBytes() + ")");
        }
        validatePng(bytes);
        try {
            ensureDirs();
            Files.write(capePath(uuid), bytes);
        } catch (IOException e) {
            throw new TailorException("Failed to store cape PNG", e);
        }
        ChassisSkinPush.writeSharedCapePng(plugin, uuid, bytes);
        String pub = config.publicCapeUrl(uuid, fallbackUrl);
        if (pub == null || pub.isBlank()) {
            throw new TailorException(
                    "Cape upload needs skin-host-public-base-url in YaPTailor config "
                            + "(HTTPS base that serves /skin/{uuid}_cape.png)");
        }
        return pub;
    }

    public void deleteStored(UUID uuid) {
        try {
            Files.deleteIfExists(skinPath(uuid));
            Files.deleteIfExists(capePath(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete stored skin files for " + uuid + ": " + e.getMessage());
        }
    }

    /** Read previously stored skin PNG bytes, or null if missing. */
    public byte[] readStoredSkin(UUID uuid) {
        return readIfPresent(skinPath(uuid));
    }

    /** Read previously stored cape PNG bytes, or null if missing. */
    public byte[] readStoredCape(UUID uuid) {
        return readIfPresent(capePath(uuid));
    }

    /**
     * Persist a wardrobe-slot skin PNG and mirror it into the chassis shared skins tree
     * so {@code GET /skin/wardrobe/{uuid}/{slotId}.png} works.
     *
     * @return public HTTPS URL for the slot texture
     */
    public String storeWardrobeSkin(UUID playerUuid, long slotId, byte[] bytes) throws TailorException {
        if (playerUuid == null || slotId <= 0) {
            throw new TailorException("Invalid wardrobe slot");
        }
        if (bytes == null || bytes.length == 0) {
            throw new TailorException("Empty skin PNG");
        }
        if (bytes.length > config.maxPngBytes()) {
            throw new TailorException("Image exceeds max-png-bytes (" + config.maxPngBytes() + ")");
        }
        validatePng(bytes);
        Path path = wardrobeSkinPath(playerUuid, slotId);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            ChassisSkinPush.writeSharedWardrobeSkinPng(plugin, playerUuid, slotId, bytes);
        } catch (IOException e) {
            throw new TailorException("Failed to store/mirror wardrobe skin PNG: " + e.getMessage(), e);
        }
        String pub = config.publicWardrobeSkinUrl(playerUuid, slotId);
        if (pub == null || pub.isBlank()) {
            throw new TailorException(
                    "Wardrobe needs skin-host-public-base-url in YaPTailor config "
                            + "(HTTPS base that serves /skin/wardrobe/{uuid}/{slotId}.png)");
        }
        return pub;
    }

    /** Persist wardrobe-slot cape PNG; returns public URL or null when {@code bytes} empty. */
    public String storeWardrobeCape(UUID playerUuid, long slotId, byte[] bytes) throws TailorException {
        if (playerUuid == null || slotId <= 0) {
            throw new TailorException("Invalid wardrobe slot");
        }
        if (bytes == null || bytes.length == 0) {
            deleteWardrobeCapeFile(playerUuid, slotId);
            try {
                ChassisSkinPush.writeSharedWardrobeCapePng(plugin, playerUuid, slotId, null);
            } catch (IOException e) {
                throw new TailorException("Failed to clear mirrored wardrobe cape: " + e.getMessage(), e);
            }
            return null;
        }
        if (bytes.length > config.maxPngBytes()) {
            throw new TailorException("Image exceeds max-png-bytes (" + config.maxPngBytes() + ")");
        }
        validatePng(bytes);
        Path path = wardrobeCapePath(playerUuid, slotId);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            ChassisSkinPush.writeSharedWardrobeCapePng(plugin, playerUuid, slotId, bytes);
        } catch (IOException e) {
            throw new TailorException("Failed to store/mirror wardrobe cape PNG: " + e.getMessage(), e);
        }
        String pub = config.publicWardrobeCapeUrl(playerUuid, slotId);
        if (pub == null || pub.isBlank()) {
            throw new TailorException(
                    "Wardrobe cape needs skin-host-public-base-url in YaPTailor config");
        }
        return pub;
    }

    public byte[] readWardrobeSkin(UUID playerUuid, long slotId) {
        return readIfPresent(wardrobeSkinPath(playerUuid, slotId));
    }

    public byte[] readWardrobeCape(UUID playerUuid, long slotId) {
        return readIfPresent(wardrobeCapePath(playerUuid, slotId));
    }

    public void deleteWardrobeFiles(UUID playerUuid, long slotId) throws TailorException {
        deleteWardrobeSkinFile(playerUuid, slotId);
        deleteWardrobeCapeFile(playerUuid, slotId);
        try {
            ChassisSkinPush.deleteSharedWardrobePng(plugin, playerUuid, slotId);
        } catch (IOException e) {
            throw new TailorException("Failed to delete mirrored wardrobe PNGs: " + e.getMessage(), e);
        }
    }

    private void deleteWardrobeSkinFile(UUID playerUuid, long slotId) {
        try {
            Files.deleteIfExists(wardrobeSkinPath(playerUuid, slotId));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete wardrobe skin " + playerUuid + "/" + slotId
                    + ": " + e.getMessage());
        }
    }

    private void deleteWardrobeCapeFile(UUID playerUuid, long slotId) {
        try {
            Files.deleteIfExists(wardrobeCapePath(playerUuid, slotId));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete wardrobe cape " + playerUuid + "/" + slotId
                    + ": " + e.getMessage());
        }
    }

    /**
     * Resolve PNG bytes for a wardrobe slot: local file first, then download from URL.
     * On successful download, persists into the wardrobe slot path.
     */
    public byte[] resolveWardrobeSkinBytes(UUID playerUuid, long slotId, String skinUrl)
            throws TailorException {
        byte[] local = readWardrobeSkin(playerUuid, slotId);
        if (local != null && local.length > 0) {
            return local;
        }
        if (skinUrl == null || skinUrl.isBlank()) {
            throw new TailorException("Slot has no skin data");
        }
        byte[] downloaded = downloadAndValidate(skinUrl);
        storeWardrobeSkin(playerUuid, slotId, downloaded);
        return downloaded;
    }

    private static byte[] readIfPresent(Path path) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
    }

    public URL parseHttpsUrl(String urlString) throws TailorException {
        if (urlString == null || urlString.isBlank()) {
            throw new TailorException("URL is required");
        }
        String trimmed = urlString.trim();
        try {
            URI uri = URI.create(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new TailorException("Only HTTP/HTTPS URLs are allowed");
            }
            URL url = uri.toURL();
            if (url.getHost() == null || url.getHost().isBlank()) {
                throw new TailorException("Invalid URL host");
            }
            return url;
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Invalid URL: " + trimmed, e);
        }
    }

    private byte[] download(URL url) throws TailorException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(12_000);
            conn.setRequestProperty("User-Agent", "YaPTailor/1.0");
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new TailorException("Download failed HTTP " + code + " for " + url);
            }
            int contentLength = conn.getContentLength();
            if (contentLength > config.maxPngBytes()) {
                throw new TailorException("Image too large (" + contentLength + " bytes)");
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] data = in.readNBytes(config.maxPngBytes() + 1);
                if (data.length > config.maxPngBytes()) {
                    throw new TailorException("Image exceeds max-png-bytes (" + config.maxPngBytes() + ")");
                }
                if (data.length < PNG_MAGIC.length) {
                    throw new TailorException("Downloaded file too small to be a PNG");
                }
                return data;
            }
        } catch (TailorException e) {
            throw e;
        } catch (IOException e) {
            throw new TailorException("Failed to download " + url + ": " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void validatePng(byte[] bytes) throws TailorException {
        if (bytes == null || bytes.length < PNG_MAGIC.length) {
            throw new TailorException("Not a PNG image");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                throw new TailorException("PNG magic header missing");
            }
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new TailorException("Failed to decode PNG", e);
        }
        if (image == null) {
            throw new TailorException("Failed to decode PNG");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean dimsOk = (w == 64 && (h == 32 || h == 64))
                || (w == 128 && h == 128)
                || (ALLOWED_WIDTHS.contains(w) && ALLOWED_HEIGHTS.contains(h) && (h == w || h == w / 2));
        if (!dimsOk) {
            throw new TailorException("Unsupported skin dimensions " + w + "x" + h
                    + " (allowed: 64x32, 64x64, 128x128)");
        }
    }

    /** Build unsigned Mojang textures property value (base64). */
    public String buildTextureValue(String skinUrl, String capeUrl, SkinModel model) throws TailorException {
        if (skinUrl == null || skinUrl.isBlank()) {
            throw new TailorException("Skin URL required for texture value");
        }
        parseHttpsUrl(skinUrl);
        if (capeUrl != null && !capeUrl.isBlank()) {
            parseHttpsUrl(capeUrl);
        }
        StringBuilder json = new StringBuilder(256);
        json.append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"00000000000000000000000000000000\",\"profileName\":\"YaPTailor\",\"textures\":{");
        json.append("\"SKIN\":{\"url\":\"").append(escapeJson(skinUrl)).append("\"");
        if (model == SkinModel.SLIM) {
            json.append(",\"metadata\":{\"model\":\"slim\"}");
        }
        json.append('}');
        if (capeUrl != null && !capeUrl.isBlank()) {
            json.append(",\"CAPE\":{\"url\":\"").append(escapeJson(capeUrl)).append("\"}");
        }
        json.append("}}");
        return java.util.Base64.getEncoder().encodeToString(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String normalizeUrl(String url) {
        return url == null ? null : url.trim();
    }

    public static boolean looksLikeUrl(String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
