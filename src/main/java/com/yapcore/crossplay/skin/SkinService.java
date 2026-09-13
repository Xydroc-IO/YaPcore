package com.yapcore.crossplay.skin;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock / Java skin registry (Geyser parity 4.G4 / P4.8).
 * Exposes a JE {@code textures} property so Paper player-list / JE clients can see BE skins.
 * Hosts PNG bytes under {@code /skin/{uuid}.png} when a public base URL + skins dir are set.
 * Phase 1: {@link BedrockCanonicalSkin} is the source of truth when present.
 * Texture helpers live in {@link SkinServiceTextures}.
 */
public final class SkinService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Skin");

    public record SkinData(
            UUID uuid,
            String skinId,
            String geometryName,
            String capeData,
            String skinDataBase64,
            byte[] rawClientPacket,
            boolean slim,
            byte[] capeBytes,
            byte[] skinPng,
            BedrockCanonicalSkin canonical
    ) {
        public SkinData(UUID uuid, String skinId, String geometryName, String capeData,
                        String skinDataBase64, byte[] rawClientPacket) {
            this(uuid, skinId, geometryName, capeData, skinDataBase64, rawClientPacket,
                    false, null, null, null);
        }

        public SkinData(UUID uuid, String skinId, String geometryName, String capeData,
                        String skinDataBase64, byte[] rawClientPacket, boolean slim,
                        byte[] capeBytes, byte[] skinPng) {
            this(uuid, skinId, geometryName, capeData, skinDataBase64, rawClientPacket,
                    slim, capeBytes, skinPng, null);
        }
    }

    private final ConcurrentHashMap<String, SkinData> byPlayer = new ConcurrentHashMap<>();
    private volatile String publicSkinBaseUrl;
    private volatile Path skinsDir;
    private volatile java.util.function.Consumer<String> onSkinChanged;

    /** Called after putCanonical / ingest so Bedrock sessions can receive PlayerSkin refresh. */
    public void setOnSkinChanged(java.util.function.Consumer<String> hook) {
        this.onSkinChanged = hook;
    }

    private void notifySkinChanged(String username) {
        java.util.function.Consumer<String> hook = onSkinChanged;
        if (hook != null && username != null && !username.isBlank()) {
            try {
                hook.accept(username);
            } catch (Exception e) {
                LOG.fine("onSkinChanged: " + e.getMessage());
            }
        }
    }

    public void setPublicSkinBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.publicSkinBaseUrl = null;
            return;
        }
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        this.publicSkinBaseUrl = u;
        LOG.info("Public skin base URL → " + u);
    }

    public String publicSkinBaseUrl() {
        return publicSkinBaseUrl;
    }

    public void setSkinsDir(Path dir) {
        this.skinsDir = dir;
        if (dir != null) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LOG.warning("Could not create skins dir: " + e.getMessage());
            }
        }
    }

    public Path skinsDir() {
        return skinsDir;
    }

    public void registerDefault(String username, UUID uuid) {
        BedrockCanonicalSkin canonical = BedrockCanonicalSkin.classicPng(uuid, null, null, false)
                .ensureGeometryData();
        byPlayer.put(username.toLowerCase(), new SkinData(
                uuid,
                canonical.skinId(),
                canonical.geometryName(),
                "",
                "",
                null,
                false,
                null,
                null,
                canonical
        ));
        LOG.fine("Default skin for " + username);
    }

    /**
     * Store a Bedrock-canonical skin (Tailor / HTTP apply). Persists PNG and ensures geometry.
     */
    public void putCanonical(String username, BedrockCanonicalSkin skin) {
        if (username == null || username.isBlank() || skin == null) {
            return;
        }
        BedrockCanonicalSkin ensured = skin.ensureGeometryData();
        byte[] png = ensured.skinPng();
        byte[] cape = ensured.capePng();
        String b64 = png != null && png.length > 0 ? Base64.getEncoder().encodeToString(png) : "";
        String capeB64 = cape != null && cape.length > 0 ? Base64.getEncoder().encodeToString(cape) : "";
        byPlayer.put(username.toLowerCase(), new SkinData(
                ensured.uuid(),
                ensured.skinId(),
                ensured.geometryName(),
                capeB64,
                b64,
                null,
                ensured.slim(),
                cape,
                png,
                ensured
        ));
        persistPng(ensured.uuid(), png);
        LOG.info("Canonical skin stored for " + username
                + " slim=" + ensured.slim()
                + " png=" + (png == null ? 0 : png.length)
                + " geo=" + ensured.geometryData().length());
        notifySkinChanged(username);
    }

    /**
     * Register a Java / URL-sourced skin with optional cape and arm model.
     * Writes {@code {uuid}.png} into the skins HTTP directory when configured.
     */
    public void putJavaSkin(String username, UUID uuid, byte[] pngBytes, byte[] capeBytes, boolean slim) {
        if (username == null || username.isBlank() || uuid == null) {
            return;
        }
        byte[] png = pngBytes == null ? null : pngBytes.clone();
        byte[] cape = capeBytes == null ? null : capeBytes.clone();
        BedrockCanonicalSkin canonical = BedrockCanonicalSkin.classicPng(uuid, png, cape, slim)
                .ensureGeometryData();
        putCanonical(username, canonical);
        LOG.info("Java skin stored for " + username + " slim=" + slim
                + " png=" + (png == null ? 0 : png.length));
    }

    public byte[] pngBytes(String username) {
        SkinData skin = get(username);
        if (skin == null) {
            return null;
        }
        if (skin.canonical() != null && skin.canonical().skinPng() != null
                && skin.canonical().skinPng().length > 0) {
            return skin.canonical().skinPng();
        }
        if (skin.skinPng() != null && skin.skinPng().length > 0) {
            return skin.skinPng();
        }
        if (skin.skinDataBase64() != null && !skin.skinDataBase64().isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(skin.skinDataBase64().trim());
                if (looksLikePng(decoded)) {
                    return decoded;
                }
                // Raw RGBA → encode a PNG for HTTP hosting
                byte[] png = rgbaToPng(decoded);
                if (png != null) {
                    return png;
                }
            } catch (IllegalArgumentException ignored) {
                // not base64
            }
        }
        return null;
    }

    public void ingestClientSkin(String username, ByteBuf body) {
        ingestClientSkin(username, body, 2207);
    }

    /**
     * Ingest a PlayerSkin packet body (UUID + Skin structure). Prefer protocol ≥2168 so
     * Cloudburst {@code readSkin} is used; stores via {@link #putCanonical} (fires notify).
     */
    public void ingestClientSkin(String username, ByteBuf body, int protocol) {
        if (username == null || username.isBlank() || body == null) {
            return;
        }
        int mark = body.readerIndex();
        try {
            BedrockCanonicalSkin canonical =
                    com.yapcore.crossplay.bedrock.codec.BedrockLoginCodec.readPlayerSkinBody(body, protocol);
            putCanonical(username, canonical);
            LOG.info("Skin ingest " + username
                    + " id=" + canonical.skinId()
                    + " geoData=" + canonical.geometryData().length()
                    + " pieces=" + canonical.personaPieces().size()
                    + " sha=" + canonical.contentSha256());
        } catch (Exception e) {
            body.readerIndex(mark);
            LOG.fine("Skin ingest failed: " + e.getMessage());
        }
    }

    /**
     * Preferred Cloudburst path: convert {@link org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin}
     * directly and {@link #putCanonical}.
     */
    public void ingestClientSkin(String username, UUID uuid,
                                 org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin skin) {
        if (username == null || username.isBlank() || uuid == null || skin == null) {
            return;
        }
        try {
            BedrockCanonicalSkin canonical = BedrockCanonicalSkin.fromSerializedSkin(uuid, skin);
            putCanonical(username, canonical);
            LOG.info("Skin ingest (Cloudburst) " + username
                    + " id=" + canonical.skinId()
                    + " geoData=" + canonical.geometryData().length()
                    + " pieces=" + canonical.personaPieces().size()
                    + " sha=" + canonical.contentSha256());
        } catch (Exception e) {
            LOG.fine("Skin ingest (Cloudburst) failed: " + e.getMessage());
        }
    }

    public SkinData get(String username) {
        return byPlayer.get(username.toLowerCase());
    }

    public BedrockCanonicalSkin getCanonical(String username) {
        SkinData skin = get(username);
        return skin == null ? null : skin.canonical();
    }

    public ByteBuf clientboundSkinPacket(String username) {
        return clientboundSkinPacket(username, 2207);
    }

    public ByteBuf clientboundSkinPacket(String username, int protocol) {
        SkinData skin = get(username);
        if (skin == null) {
            return null;
        }
        if (skin.canonical() != null) {
            return BedrockPacketCodec.playerSkin(skin.canonical().ensureGeometryData(), protocol);
        }
        byte[] image = skin.skinPng();
        if (image == null && skin.skinDataBase64() != null && !skin.skinDataBase64().isBlank()) {
            image = SkinServiceTextures.tryDecodeBase64(skin.skinDataBase64());
        }
        byte[] cape = skin.capeBytes();
        if (cape == null && skin.capeData() != null && !skin.capeData().isBlank()) {
            cape = SkinServiceTextures.tryDecodeBase64(skin.capeData());
        }
        return BedrockPacketCodec.playerSkin(
                skin.uuid(),
                skin.skinId(),
                image,
                cape,
                skin.geometryName(),
                skin.slim(),
                protocol);
    }

    /** Build a clientbound PlayerSkin packet for refresh broadcasts. */
    public ByteBuf broadcastRefresh(String username) {
        return broadcastRefresh(username, 2207);
    }

    public ByteBuf broadcastRefresh(String username, int protocol) {
        return clientboundSkinPacket(username, protocol);
    }

    /**
     * JE GameProfile textures property value (base64 JSON). Prefers a real hosted
     * {@code https://…/skin/{uuid}.png} (or configured base) when available.
     */
    public String javaTexturesPropertyValue(String username) {
        return SkinServiceTextures.javaTexturesPropertyValue(get(username), username, publicSkinBaseUrl);
    }

    /** Apply textures property onto an injected CraftPlayer via reflection (best-effort). */
    public boolean applyToPaperPlayer(String username, Object craftPlayer) {
        if (craftPlayer == null) {
            return false;
        }
        String value = javaTexturesPropertyValue(username);
        return SkinServiceTextures.applyToPaperPlayer(username, craftPlayer, value);
    }

    public Map<String, SkinData> snapshot() {
        return Map.copyOf(byPlayer);
    }

    private void persistPng(UUID uuid, byte[] png) {
        Path dir = skinsDir;
        if (dir == null || png == null || png.length == 0 || uuid == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(uuid + ".png"), png);
        } catch (IOException e) {
            LOG.fine("persist skin png: " + e.getMessage());
        }
    }

    public static boolean looksLikePng(byte[] data) {
        return SkinServiceTextures.looksLikePng(data);
    }

    public static boolean isLikelyRgba(int len) {
        return SkinServiceTextures.isLikelyRgba(len);
    }

    public static byte[] rgbaToPng(byte[] rgba) {
        return SkinServiceTextures.rgbaToPng(rgba);
    }
}
