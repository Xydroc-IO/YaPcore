package com.yapcore.protocol.java;

import com.yapcore.config.ServerConfig;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.util.UUID;

/**
 * Version-aware clientbound packet builders.
 * Prefer {@link #forProtocol(int)} over the legacy no-arg helpers.
 */
public final class PacketFactory {

    private PacketFactory() {
    }

    public static ProtocolBand band(int protocolVersion) {
        return ProtocolBand.of(protocolVersion);
    }

    public static void send(Channel ch, ByteBuf packet) {
        ch.writeAndFlush(packet);
    }

    public static ByteBuf statusResponse(String json) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x00);
        McCodec.writeString(buf, json);
        return buf;
    }

    public static ByteBuf statusPong(long payload) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x01);
        buf.writeLong(payload);
        return buf;
    }

    public static ByteBuf loginDisconnect(String plain) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x00);
        McCodec.writeString(buf, "{\"text\":\"" + escape(plain) + "\"}");
        return buf;
    }

    public static ByteBuf loginSuccess(int protocolVersion, UUID uuid, String name) {
        ProtocolBand b = ProtocolBand.of(protocolVersion);
        return loginSuccess(uuid, name, UUID.randomUUID(), b.loginIncludesSessionId());
    }

    public static ByteBuf loginSuccess(UUID uuid, String name, UUID sessionId, boolean includeSessionId) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x02);
        McCodec.writeUuid(buf, uuid);
        McCodec.writeString(buf, name);
        McCodec.writeVarInt(buf, 0); // properties empty
        if (includeSessionId) {
            McCodec.writeUuid(buf, sessionId != null ? sessionId : uuid);
        }
        return buf;
    }

    public static ByteBuf loginPluginRequest(int messageId, String channel, byte[] data) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x04);
        McCodec.writeVarInt(buf, messageId);
        McCodec.writeString(buf, channel);
        if (data != null && data.length > 0) {
            buf.writeBytes(data);
        }
        return buf;
    }

    public static ByteBuf knownPacksClientbound(String version) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x0E);
        McCodec.writeVarInt(buf, 1);
        McCodec.writeString(buf, "minecraft");
        McCodec.writeString(buf, "core");
        McCodec.writeString(buf, version);
        return buf;
    }

    public static ByteBuf updateEnabledFeatures() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x0C);
        McCodec.writeVarInt(buf, 1);
        McCodec.writeString(buf, "minecraft:vanilla");
        return buf;
    }

    public static ByteBuf updateTagsEmpty() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x0D);
        McCodec.writeVarInt(buf, 0);
        return buf;
    }

    /**
     * Configuration Update Tags (0x0D).
     * {@code tagsByRegistry} maps registry id → (tag id → entry numeric ids).
     */
    public static ByteBuf updateTags(java.util.Map<String, java.util.Map<String, int[]>> tagsByRegistry) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x0D);
        McCodec.writeVarInt(buf, tagsByRegistry.size());
        for (var regEntry : tagsByRegistry.entrySet()) {
            McCodec.writeString(buf, regEntry.getKey());
            var tags = regEntry.getValue();
            McCodec.writeVarInt(buf, tags.size());
            for (var tagEntry : tags.entrySet()) {
                McCodec.writeString(buf, tagEntry.getKey());
                int[] ids = tagEntry.getValue();
                McCodec.writeVarInt(buf, ids.length);
                for (int id : ids) {
                    McCodec.writeVarInt(buf, id);
                }
            }
        }
        return buf;
    }

    public static ByteBuf finishConfiguration() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x03);
        return buf;
    }

    public static ByteBuf registryData(String registryId, String entryId, ByteBuf nbtOrNull) {
        return registryData(registryId,
                new String[]{entryId},
                nbtOrNull == null ? null : new ByteBuf[]{nbtOrNull});
    }

    public static ByteBuf registryData(String registryId, String[] entryIds, ByteBuf[] nbtPerEntry) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 0x07);
        McCodec.writeString(buf, registryId);
        McCodec.writeVarInt(buf, entryIds.length);
        for (int i = 0; i < entryIds.length; i++) {
            McCodec.writeString(buf, entryIds[i]);
            ByteBuf nbt = nbtPerEntry != null && i < nbtPerEntry.length ? nbtPerEntry[i] : null;
            if (nbt != null) {
                buf.writeBoolean(true);
                buf.writeBytes(nbt);
                nbt.release();
            } else {
                buf.writeBoolean(false);
            }
        }
        return buf;
    }

    public static ByteBuf playLogin(int protocolVersion, ServerConfig config, int entityId) {
        ProtocolBand band = ProtocolBand.of(protocolVersion);
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, band.playLoginId());
        buf.writeInt(entityId);
        buf.writeBoolean(false); // hardcore
        if (band.hasConfigurationPhase() || protocolVersion >= 735) {
            // Modern join-game style (dimension registry list + world name)
            McCodec.writeVarInt(buf, 1);
            McCodec.writeString(buf, "minecraft:overworld");
            McCodec.writeVarInt(buf, config.getMaxPlayers());
            McCodec.writeVarInt(buf, Math.max(2, Math.min(32, config.getViewDistance())));
            McCodec.writeVarInt(buf, Math.max(2, Math.min(32, config.getViewDistance())));
            buf.writeBoolean(false); // reduced debug
            buf.writeBoolean(true); // respawn screen
            if (protocolVersion >= 764) {
                buf.writeBoolean(false); // limited crafting
            }
            McCodec.writeVarInt(buf, 0); // dimension type
            McCodec.writeString(buf, "minecraft:overworld");
            buf.writeLong(0L);
            buf.writeByte(1); // creative
            buf.writeByte(-1);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            if (protocolVersion >= 759) {
                McCodec.writeVarInt(buf, 0); // portal cooldown
            }
            if (protocolVersion >= 766) {
                McCodec.writeVarInt(buf, 63); // sea level
            }
            if (protocolVersion >= 767) {
                buf.writeBoolean(config.isOnlineMode());
                buf.writeBoolean(false); // secure chat
            }
        } else {
            // Legacy-ish: gamemode / dimension / difficulty / maxPlayers / levelType
            buf.writeByte(1); // creative
            buf.writeInt(0); // overworld
            buf.writeByte(2); // difficulty
            buf.writeByte(Math.min(255, config.getMaxPlayers()));
            McCodec.writeString(buf, "default");
            buf.writeBoolean(false); // reduced debug
        }
        return buf;
    }

    public static ByteBuf gameEvent(int protocolVersion, int event, float value) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, ProtocolBand.of(protocolVersion).gameEventId());
        buf.writeByte(event);
        buf.writeFloat(value);
        return buf;
    }

    /**
     * Synchronize Player Position — ID + layout depend on {@link ProtocolBand}.
     * Y is placed below world min so the client clears “Loading terrain…” without chunks.
     */
    public static ByteBuf playerPosition(int protocolVersion, double x, double y, double z) {
        ProtocolBand band = ProtocolBand.of(protocolVersion);
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, band.playerPositionId());
        if (band.modernPlayerPosition()) {
            McCodec.writeVarInt(buf, 1);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeDouble(0d);
            buf.writeDouble(0d);
            buf.writeDouble(0d);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeInt(0);
        } else {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(0f);
            buf.writeFloat(0f);
            buf.writeByte(0);
            McCodec.writeVarInt(buf, 1);
        }
        return buf;
    }

    public static ByteBuf keepAlive(int protocolVersion, long id) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, ProtocolBand.of(protocolVersion).keepAliveCbId());
        buf.writeLong(id);
        return buf;
    }

    public static ByteBuf pluginMessage(int protocolVersion, String channel, byte[] data) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, ProtocolBand.of(protocolVersion).playCustomPayloadId());
        McCodec.writeString(buf, channel);
        if (data != null) {
            buf.writeBytes(data);
        }
        return buf;
    }

    public static ByteBuf setCenterChunk(int protocolVersion, int chunkX, int chunkZ) {
        int id = ProtocolBand.of(protocolVersion).setCenterChunkId();
        if (id < 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, id);
        McCodec.writeVarInt(buf, chunkX);
        McCodec.writeVarInt(buf, chunkZ);
        return buf;
    }

    /** Player Abilities — creative + flying for early native world (26.2 id 64). */
    public static ByteBuf playerAbilities(int protocolVersion, boolean creative, boolean flying) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, ProtocolBand.of(protocolVersion).playerAbilitiesId());
        byte flags = 0;
        if (creative) {
            flags |= 0x08; // creative mode
            flags |= 0x04; // allow flying
            flags |= 0x01; // invulnerable
        }
        if (flying) {
            flags |= 0x02;
        }
        buf.writeByte(flags);
        buf.writeFloat(0.05f); // fly speed
        buf.writeFloat(0.1f);  // walk speed
        return buf;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
