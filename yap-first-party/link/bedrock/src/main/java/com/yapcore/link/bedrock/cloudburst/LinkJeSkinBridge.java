package com.yapcore.link.bedrock.cloudburst;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;

/**
 * JE Mojang {@code textures} property → Bedrock {@link SerializedSkin} (64×64 RGBA + geometry).
 * Falls back to {@link LinkTrustedSkin#steveRemote()} when fetch/decode fails.
 */
public final class LinkJeSkinBridge {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final Pattern URL_RE = Pattern.compile(
            "\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
    private static final Pattern SLIM_RE = Pattern.compile(
            "\"model\"\\s*:\\s*\"slim\"", Pattern.CASE_INSENSITIVE);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<UUID, SerializedSkin> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private LinkJeSkinBridge() {
    }

    public static SerializedSkin cachedOrSteve(UUID uuid) {
        if (uuid == null) {
            return LinkTrustedSkin.steveRemote();
        }
        SerializedSkin skin = CACHE.get(uuid);
        return skin != null ? skin : LinkTrustedSkin.steveRemote();
    }

    /**
     * Parse textures property and fetch asynchronously. {@code onReady} runs when a real
     * skin is cached (may be immediate on cache hit).
     */
    public static void resolveAsync(UUID uuid, String texturesProperty, Runnable onReady) {
        if (uuid == null || texturesProperty == null || texturesProperty.isBlank()) {
            return;
        }
        if (CACHE.containsKey(uuid)) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (IN_FLIGHT.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }
        Thread.ofVirtual().name("yap-link-je-skin-" + uuid).start(() -> {
            try {
                SerializedSkin skin = fetchSkin(uuid, texturesProperty);
                if (skin != null) {
                    CACHE.put(uuid, skin);
                    LOG.info("BE JE→skin cached uuid=" + uuid + " valid=" + skin.isValid());
                    if (onReady != null) {
                        onReady.run();
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "BE JE skin fetch failed uuid=" + uuid + ": " + e.getMessage());
            } finally {
                IN_FLIGHT.remove(uuid);
            }
        });
    }

    static SerializedSkin fetchSkin(UUID uuid, String texturesProperty) throws Exception {
        byte[] jsonBytes = Base64.getDecoder().decode(texturesProperty.trim());
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        Matcher um = URL_RE.matcher(json);
        if (!um.find()) {
            return null;
        }
        String url = um.group(1).replace("\\/", "/");
        boolean slim = SLIM_RE.matcher(json).find();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2 || resp.body() == null || resp.body().length < 64) {
            return null;
        }
        byte[] rgba = pngToRgba64(resp.body());
        if (rgba == null) {
            return null;
        }
        return buildRemote(uuid, rgba, slim);
    }

    static byte[] pngToRgba64(byte[] png) throws Exception {
        BufferedImage img;
        try (InputStream in = new ByteArrayInputStream(png)) {
            img = ImageIO.read(in);
        }
        if (img == null) {
            return null;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        // Accept classic 64×32 / 64×64; scale larger skins down to 64×64.
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            if (h == 32 && w == 64) {
                g.drawImage(img, 0, 0, 64, 32, 0, 0, 64, 32, null);
                // Duplicate top half into bottom for modern Bedrock layout (legs/arms).
                g.drawImage(img, 0, 32, 64, 64, 0, 16, 64, 32, null);
            } else {
                g.drawImage(img, 0, 0, 64, 64, 0, 0, w, h, null);
            }
        } finally {
            g.dispose();
        }
        byte[] rgba = new byte[64 * 64 * 4];
        int i = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = out.getRGB(x, y);
                rgba[i++] = (byte) ((argb >> 16) & 0xff);
                rgba[i++] = (byte) ((argb >> 8) & 0xff);
                rgba[i++] = (byte) (argb & 0xff);
                rgba[i++] = (byte) ((argb >> 24) & 0xff);
            }
        }
        return rgba;
    }

    static SerializedSkin buildRemote(UUID uuid, byte[] rgba, boolean slim) {
        String geo = LinkTrustedSkin.loadGeometryJsonPublic();
        if (geo == null || geo.isBlank() || rgba == null || rgba.length < ImageData.DOUBLE_SKIN_SIZE) {
            return null;
        }
        String geoName = slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom";
        String patch = "{\"geometry\" :{\"default\" :\"" + geoName + "\"}}";
        String skinId = "Standard_Custom_" + (uuid != null ? uuid.toString() : "remote");
        SerializedSkin built = SerializedSkin.builder()
                .skinId(skinId)
                .playFabId("")
                .geometryName("")
                .skinResourcePatch(patch)
                .skinData(ImageData.of(64, 64, rgba))
                .animations(Collections.emptyList())
                .capeData(ImageData.EMPTY)
                .geometryData(geo)
                .geometryDataEngineVersion("1.26.45")
                .animationData("")
                .premium(true)
                .persona(false)
                .capeOnClassic(false)
                .primaryUser(false)
                .capeId("")
                .fullSkinId(skinId)
                .armSize(slim ? "slim" : "wide")
                .skinColor("#0")
                .color(new Color(0, true))
                .personaPieces(Collections.emptyList())
                .tintColors(Collections.emptyList())
                .overridingPlayerAppearance(true)
                .trusted(true)
                .profileHash("")
                .build();
        return built.isValid() ? built : null;
    }
}
