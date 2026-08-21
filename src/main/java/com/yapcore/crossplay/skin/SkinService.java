package com.yapcore.crossplay.skin;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock / Java skin registry (Geyser parity 4.G4 / P4.8).
 * Exposes a JE {@code textures} property so Paper player-list / JE clients can see BE skins.
 */
public final class SkinService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Skin");

    public record SkinData(
            UUID uuid,
            String skinId,
            String geometryName,
            String capeData,
            String skinDataBase64,
            byte[] rawClientPacket
    ) {
    }

    private final ConcurrentHashMap<String, SkinData> byPlayer = new ConcurrentHashMap<>();

    public void registerDefault(String username, UUID uuid) {
        byPlayer.put(username.toLowerCase(), new SkinData(
                uuid,
                "Standard_Custom",
                "geometry.humanoid.custom",
                "",
                "",
                null
        ));
        LOG.fine("Default skin for " + username);
    }

    public void ingestClientSkin(String username, ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long msb = body.readLongLE();
            long lsb = body.readLongLE();
            UUID uuid = new UUID(msb, lsb);
            String skinId = BedrockPacketCodec.readString(body);
            String skinData = BedrockPacketCodec.readString(body);
            String cape = body.isReadable() ? BedrockPacketCodec.readString(body) : "";
            String geometry = body.isReadable() ? BedrockPacketCodec.readString(body) : "geometry.humanoid.custom";
            byte[] raw = new byte[body.writerIndex() - mark];
            body.getBytes(mark, raw);
            byPlayer.put(username.toLowerCase(), new SkinData(uuid, skinId, geometry, cape, skinData, raw));
            LOG.info("Skin update " + username + " id=" + skinId + " bytes=" + skinData.length());
        } catch (Exception e) {
            body.readerIndex(mark);
            LOG.fine("Skin ingest failed: " + e.getMessage());
        }
    }

    public SkinData get(String username) {
        return byPlayer.get(username.toLowerCase());
    }

    public ByteBuf clientboundSkinPacket(String username) {
        SkinData skin = get(username);
        if (skin == null) {
            return null;
        }
        return BedrockPacketCodec.playerSkin(skin.uuid(), skin.skinId(),
                skin.skinDataBase64(), skin.capeData(), skin.geometryName());
    }

    /**
     * JE GameProfile textures property value (base64 JSON). Uses textures.minecraft.net
     * URL when we only have an id; when Bedrock skin bytes exist, embeds a data URL marker
     * for Geyser-style converters (consumers may still rewrite to a hosted PNG).
     */
    public String javaTexturesPropertyValue(String username) {
        SkinData skin = get(username);
        if (skin == null) {
            return null;
        }
        StringBuilder json = new StringBuilder(256);
        json.append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"").append(skin.uuid().toString().replace("-", ""))
                .append("\",\"profileName\":\"").append(escape(username))
                .append("\",\"textures\":{\"SKIN\":{\"url\":\"");
        if (skin.skinDataBase64() != null && !skin.skinDataBase64().isBlank()) {
            // Marker for downstream JE remappers — not a live CDN URL
            json.append("https://yapcore.local/skin/").append(skin.uuid());
        } else {
            json.append("http://textures.minecraft.net/texture/yapcore-default");
        }
        json.append("\"}}}");
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Apply textures property onto an injected CraftPlayer via reflection (best-effort). */
    public boolean applyToPaperPlayer(String username, Object craftPlayer) {
        if (craftPlayer == null) {
            return false;
        }
        String value = javaTexturesPropertyValue(username);
        if (value == null) {
            return false;
        }
        try {
            Object profile = craftPlayer.getClass().getMethod("getPlayerProfile").invoke(craftPlayer);
            if (profile == null) {
                return false;
            }
            // Paper PlayerProfile#setProperty(String, String) or similar
            try {
                profile.getClass().getMethod("setProperty", String.class, String.class)
                        .invoke(profile, "textures", value);
            } catch (NoSuchMethodException e) {
                ClassLoader cl = craftPlayer.getClass().getClassLoader();
                Class<?> propCl = Class.forName("com.destroystokyo.paper.profile.ProfileProperty", true, cl);
                Object prop = propCl.getConstructor(String.class, String.class)
                        .newInstance("textures", value);
                profile.getClass().getMethod("setProperty", propCl).invoke(profile, prop);
            }
            craftPlayer.getClass().getMethod("setPlayerProfile", profile.getClass())
                    .invoke(craftPlayer, profile);
            LOG.info("JE textures property applied for BE skin " + username);
            return true;
        } catch (Exception e) {
            LOG.fine("applyToPaperPlayer skin: " + e.getMessage());
            return false;
        }
    }

    public Map<String, SkinData> snapshot() {
        return Map.copyOf(byPlayer);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
