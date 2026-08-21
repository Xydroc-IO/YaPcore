package com.yapcore.crossplay.skin;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock / Java skin registry (Geyser parity 4.G4 UX).
 */
public final class SkinService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Skin");

    public record SkinData(
            UUID uuid,
            String skinId,
            String geometryName,
            String capeData,
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
            byPlayer.put(username.toLowerCase(), new SkinData(uuid, skinId, geometry, cape, raw));
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
        return BedrockPacketCodec.playerSkin(skin.uuid(), skin.skinId(), "", skin.capeData(), skin.geometryName());
    }

    public Map<String, SkinData> snapshot() {
        return Map.copyOf(byPlayer);
    }
}
