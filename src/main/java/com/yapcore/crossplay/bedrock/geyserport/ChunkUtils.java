package com.yapcore.crossplay.bedrock.geyserport;

import io.netty.buffer.Unpooled;
import java.util.concurrent.ConcurrentHashMap;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

/**
 * Port of Geyser {@code org.geysermc.geyser.util.ChunkUtils} (join-path subset).
 *
 * <p>Source: {@code vendor/geyser-ref/.../ChunkUtils.java}. Empty LevelChunks use the same
 * {@code EMPTY_CHUNK_PAYLOAD} construction as Geyser — this is Geyser's API for radius fills
 * when Java columns are not yet available, not a YaP flat-world hack.
 */
public final class ChunkUtils {

    /** Geyser {@code MathUtils.SQRT_OF_TWO}. */
    private static final double SQRT_OF_TWO = Math.sqrt(2.0);

    /**
     * Geyser {@code EMPTY_BIOME_DATA}: SingletonBitArray V0 runtime header + zigzag palette 0.
     * Verified vs Geyser-Spigot / {@code BlockStorage(SingletonBitArray, [0])}.
     */
    public static final byte[] EMPTY_BIOME_DATA = new byte[] {0x01, 0x00};

    private static final ConcurrentHashMap<Integer, byte[]> EMPTY_CHUNK_PAYLOAD_CACHE =
            new ConcurrentHashMap<>(3);

    private ChunkUtils() {}

    /**
     * Literal Geyser {@code squareToCircle}:
     * {@code ceil((renderDistance + 1) * √2)}.
     */
    public static int squareToCircle(int renderDistance) {
        return (int) Math.ceil((renderDistance + 1) * SQRT_OF_TWO);
    }

    /**
     * Geyser {@code updateChunkPosition}: NetworkChunkPublisherUpdate when column changes.
     */
    public static void updateChunkPosition(YapGeyserSession session, Vector3i position) {
        Vector2i chunkPos = session.getLastChunkPosition();
        Vector2i newChunkPos = Vector2i.from(position.getX() >> 4, position.getZ() >> 4);
        if (chunkPos == null || !chunkPos.equals(newChunkPos)) {
            NetworkChunkPublisherUpdatePacket packet = new NetworkChunkPublisherUpdatePacket();
            packet.setPosition(position);
            packet.setRadius(squareToCircle(session.getServerRenderDistance()) << 4);
            session.sendUpstreamPacket(packet);
            session.setLastChunkPosition(newChunkPos);
        }
    }

    /**
     * Geyser {@code sendEmptyChunk}: {@code subChunksLength=0} + cached EMPTY_CHUNK payload.
     *
     * <p>Payload = EMPTY_BIOME_DATA + (subChunkCount−1)×marker + border 0.
     * Marker = {@code (127 << 1) | 1} (= 0xFF), same as Geyser.
     */
    public static void sendEmptyChunk(YapGeyserSession session, int chunkX, int chunkZ,
                                      boolean forceUpdate) {
        int bedrockSubChunkCount = session.bedrockDimensionHeight() >> 4;
        byte[] payload = EMPTY_CHUNK_PAYLOAD_CACHE.computeIfAbsent(bedrockSubChunkCount, subChunkCount -> {
            int biomeLength = EMPTY_BIOME_DATA.length;
            int totalLength = biomeLength + subChunkCount;
            byte[] data = new byte[totalLength];
            System.arraycopy(EMPTY_BIOME_DATA, 0, data, 0, biomeLength);
            // Geyser: byte marker = (byte) ((127 << 1) | 1);
            byte marker = (byte) ((127 << 1) | 1);
            for (int i = 0; i < subChunkCount - 1; i++) {
                data[biomeLength + i] = marker;
            }
            data[totalLength - 1] = 0;
            return data;
        });

        LevelChunkPacket data = new LevelChunkPacket();
        data.setDimension(session.bedrockDimensionId());
        data.setChunkX(chunkX);
        data.setChunkZ(chunkZ);
        data.setSubChunksLength(0);
        // Copy — do not wrap the shared EMPTY_CHUNK_PAYLOAD_CACHE array. Encode+release of
        // ReferenceCounted LevelChunk must not alias the same bytes across in-flight packets.
        data.setData(Unpooled.copiedBuffer(payload));
        data.setCachingEnabled(false);
        session.sendUpstreamPacket(data);

        if (forceUpdate) {
            Vector3i pos = Vector3i.from(chunkX << 4, 80, chunkZ << 4);
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(pos);
            blockPacket.setDataLayer(0);
            BlockDefinition stone = session.blockDefinitionOrAir(1);
            if (stone != null) {
                blockPacket.setDefinition(stone);
                session.sendUpstreamPacket(blockPacket);
            }
        }
    }

    /**
     * Geyser {@code sendEmptyChunks}: square {@code [-radius..radius]²} of empty LevelChunks.
     * Used in {@code connect()} with radius 0, and after {@code setServerRenderDistance} when
     * Paper columns are unavailable — same Geyser API either way.
     */
    public static void sendEmptyChunks(YapGeyserSession session, Vector3i position,
                                       int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                sendEmptyChunk(session, chunkX + x, chunkZ + z, forceUpdate);
            }
        }
    }

    /** Bytes of EMPTY_CHUNK payload for a given sub-chunk count (test / encode parity). */
    public static byte[] emptyChunkPayload(int subChunkCount) {
        return EMPTY_CHUNK_PAYLOAD_CACHE.computeIfAbsent(subChunkCount, sc -> {
            int biomeLength = EMPTY_BIOME_DATA.length;
            int totalLength = biomeLength + sc;
            byte[] data = new byte[totalLength];
            System.arraycopy(EMPTY_BIOME_DATA, 0, data, 0, biomeLength);
            byte marker = (byte) ((127 << 1) | 1);
            for (int i = 0; i < sc - 1; i++) {
                data[biomeLength + i] = marker;
            }
            data[totalLength - 1] = 0;
            return data;
        });
    }
}
