package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Classpath Bedrock network-NBT dumps for login packets (biome defs / actor identifiers).
 */
public final class BedrockNbtDumps {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockNbt");
    private static final String BIOME = "protocol/bedrock/1.21.50/biome_definitions.nbt";
    private static final String ACTORS = "protocol/bedrock/1.21.50/available_entity_identifiers.nbt";

    private static volatile byte[] biomeDump;
    private static volatile byte[] actorDump;
    private static volatile NbtMap actorNbt;

    private BedrockNbtDumps() {
    }

    public static ByteBuf availableEntityIdentifiers() {
        byte[] dump = actors();
        if (dump.length == 0) {
            return BedrockPacketCodec.availableEntityIdentifiersEmpty();
        }
        ByteBuf out = io.netty.buffer.Unpooled.buffer(dump.length + 8);
        BedrockPacketCodec.writeUnsignedVarInt(out, BedrockPacketIds.AVAILABLE_ACTOR_IDENTIFIERS.id);
        out.writeBytes(dump);
        return out;
    }

    /**
     * Parsed actor-identifier compound for Cloudburst {@code AvailableEntityIdentifiersPacket}.
     * Prefer this over empty NBT — Geyser always sends a full idlist.
     */
    public static NbtMap availableEntityIdentifiersNbt() {
        NbtMap local = actorNbt;
        if (local != null) {
            return local;
        }
        synchronized (BedrockNbtDumps.class) {
            if (actorNbt == null) {
                byte[] dump = actors();
                if (dump.length == 0) {
                    actorNbt = NbtMap.EMPTY;
                } else {
                    try (var nbt = NbtUtils.createNetworkReader(new ByteArrayInputStream(dump))) {
                        Object root = nbt.readTag();
                        actorNbt = root instanceof NbtMap map ? map : NbtMap.EMPTY;
                        LOG.info("Parsed actor identifiers NBT keys=" + actorNbt.keySet());
                    } catch (Exception e) {
                        LOG.warning("Actor identifiers NBT parse failed: " + e.getMessage());
                        actorNbt = NbtMap.EMPTY;
                    }
                }
            }
            return actorNbt;
        }
    }

    public static ByteBuf biomeDefinitionList() {
        byte[] dump = biomes();
        if (dump.length == 0) {
            return BedrockPacketCodec.biomeDefinitionListEmpty();
        }
        ByteBuf out = io.netty.buffer.Unpooled.buffer(dump.length + 8);
        BedrockPacketCodec.writeUnsignedVarInt(out, BedrockPacketIds.BIOME_DEFINITION_LIST.id);
        out.writeBytes(dump);
        return out;
    }

    private static byte[] biomes() {
        byte[] local = biomeDump;
        if (local != null) {
            return local;
        }
        synchronized (BedrockNbtDumps.class) {
            if (biomeDump == null) {
                biomeDump = load(BIOME);
            }
            return biomeDump;
        }
    }

    private static byte[] actors() {
        byte[] local = actorDump;
        if (local != null) {
            return local;
        }
        synchronized (BedrockNbtDumps.class) {
            if (actorDump == null) {
                actorDump = load(ACTORS);
            }
            return actorDump;
        }
    }

    private static byte[] load(String resource) {
        try (InputStream in = BedrockNbtDumps.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warning("Missing NBT dump " + resource);
                return new byte[0];
            }
            byte[] all = in.readAllBytes();
            LOG.info("Loaded Bedrock NBT dump " + resource + " bytes=" + all.length);
            return all;
        } catch (Exception e) {
            LOG.warning("NBT dump load failed " + resource + ": " + e.getMessage());
            return new byte[0];
        }
    }
}
