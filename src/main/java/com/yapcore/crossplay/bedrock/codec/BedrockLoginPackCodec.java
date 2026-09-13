package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * Resource pack info/stack helpers (split from {@link BedrockLoginCodec} for the ≤500-line domain gate).
 */
final class BedrockLoginPackCodec {
    private BedrockLoginPackCodec() {}

    static ByteBuf resourcePacksInfoEmpty() {
        return resourcePacksInfoOffer(null, "0.0.0", 0L, "", false, 2207);
    }

    /** Bedrock resource pack offer mirrored from JE pack HTTP (G.34). Defaults to modern (≥2168) layout. */
    static ByteBuf resourcePacksInfoOffer(UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept) {
        return resourcePacksInfoOffer(packId, version, sizeBytes, cdnUrl, mustAccept, 2207);
    }

    /**
     * ResourcePacksInfo. Pack-list length encoding:
     * <ul>
     *   <li>≥2168 (Cloudburst {@code ResourcePacksInfoSerializer_v2168}): unsigned varint</li>
     *   <li>&lt;2168: unsigned short LE (legacy)</li>
     * </ul>
     * Writing shortLE to a 2207 client crashes immediately after LOGIN_SUCCESS+packs — confirmed die stage.
     */
    static ByteBuf resourcePacksInfoOffer(UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept, int protocol) {
        boolean v2168 = protocol >= 2168;
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACKS_INFO);
        out.writeBoolean(mustAccept);
        out.writeBoolean(false); // has_addons
        out.writeBoolean(false); // has_scripts
        out.writeBoolean(false); // force_disable_vibrant_visuals
        // World template id must be nil — never the resource-pack UUID (crashes / kicks some BE clients).
        // Geyser empty path uses worldTemplateVersion="" (not "0.0.0").
        UUID wt = new UUID(0L, 0L);
        out.writeLongLE(wt.getMostSignificantBits());
        out.writeLongLE(wt.getLeastSignificantBits());
        boolean hasPack = packId != null && cdnUrl != null && !cdnUrl.isBlank();
        writeString(out, hasPack ? "0.0.0" : "");
        if (v2168) {
            writeUnsignedVarInt(out, hasPack ? 1 : 0);
        } else {
            out.writeShortLE(hasPack ? 1 : 0);
        }
        if (!hasPack) {
            return out;
        }
        out.writeLongLE(packId.getMostSignificantBits());
        out.writeLongLE(packId.getLeastSignificantBits());
        writeString(out, version == null ? "0.0.0" : version);
        out.writeLongLE(Math.max(0L, sizeBytes));
        writeString(out, "");
        writeString(out, "");
        writeString(out, "");
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeString(out, cdnUrl);
        return out;
    }

    static ByteBuf resourcePackStackEmpty() {
        return resourcePackStackEmpty(2207);
    }

    /**
     * ResourcePackStack. Cloudburst {@code ResourcePackStackSerializer_v898}+ drops behavior_packs;
     * only resource packs remain before gameVersion.
     */
    static ByteBuf resourcePackStackEmpty(int protocol) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACK_STACK);
        out.writeBoolean(false); // must_accept
        if (protocol < 898) {
            writeUnsignedVarInt(out, 0); // behavior_packs (pre-898)
        }
        writeUnsignedVarInt(out, 0); // resource_packs
        writeString(out, "*");
        out.writeIntLE(0); // experiments count (li32)
        out.writeBoolean(false); // experiments_previously_used
        out.writeBoolean(false); // has_editor_packs
        return out;
    }

    static ByteBuf resourcePackStackOffer(UUID packId, String version, boolean mustAccept) {
        return resourcePackStackOffer(packId, version, mustAccept, 2207);
    }

    static ByteBuf resourcePackStackOffer(UUID packId, String version, boolean mustAccept, int protocol) {
        if (packId == null) {
            return resourcePackStackEmpty(protocol);
        }
        ByteBuf out = Unpooled.buffer(96);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACK_STACK);
        out.writeBoolean(mustAccept);
        if (protocol < 898) {
            writeUnsignedVarInt(out, 0); // behavior_packs
        }
        writeUnsignedVarInt(out, 1); // resource_packs
        // Stack entries use string UUID + version + subpack name (not binary UUID).
        writeString(out, packId.toString());
        writeString(out, version == null ? "0.0.0" : version);
        writeString(out, "");
        writeString(out, "*");
        out.writeIntLE(0);
        out.writeBoolean(false);
        out.writeBoolean(false);
        return out;
    }
}
