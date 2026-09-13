package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.Unpooled;
import java.util.concurrent.ConcurrentHashMap;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

/** Port of Geyser {@code ChunkUtils} join-path subset for Link. */
public final class ChunkUtils {

    private static final double SQRT_OF_TWO = Math.sqrt(2.0);
    public static final byte[] EMPTY_BIOME_DATA = new byte[] {0x01, 0x00};
    private static final ConcurrentHashMap<Integer, byte[]> EMPTY_CHUNK_PAYLOAD_CACHE =
            new ConcurrentHashMap<>(3);

    private ChunkUtils() {
    }

    public static int squareToCircle(int renderDistance) {
        return (int) Math.ceil((renderDistance + 1) * SQRT_OF_TWO);
    }

    public static void updateChunkPosition(LinkBedrockSession session, Vector3i position) {
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

    public static void sendEmptyChunk(LinkBedrockSession session, int chunkX, int chunkZ) {
        sendEmptyChunk(session, chunkX, chunkZ, false);
    }

    /**
     * Send Cloudburst EMPTY_CHUNK for column. When {@code forceUpdate}, also send UpdateBlock
     * air→stone at (cx<<4, 80, cz<<4) — Geyser dim-switch path so the client refreshes the column.
     */
    public static void sendEmptyChunk(LinkBedrockSession session, int chunkX, int chunkZ,
                                      boolean forceUpdate) {
        int bedrockSubChunkCount = session.bedrockDimensionHeight() >> 4;
        byte[] payload = EMPTY_CHUNK_PAYLOAD_CACHE.computeIfAbsent(bedrockSubChunkCount, subChunkCount -> {
            int biomeLength = EMPTY_BIOME_DATA.length;
            int totalLength = biomeLength + subChunkCount;
            byte[] data = new byte[totalLength];
            System.arraycopy(EMPTY_BIOME_DATA, 0, data, 0, biomeLength);
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
        data.setData(Unpooled.copiedBuffer(payload));
        data.setCachingEnabled(false);
        session.sendUpstreamPacket(data);

        if (forceUpdate) {
            Vector3i pos = Vector3i.from(chunkX << 4, 80, chunkZ << 4);
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(pos);
            blockPacket.setDataLayer(0);
            blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL);
            BlockDefinition stone = session.stoneBlockDefinition();
            if (stone != null) {
                blockPacket.setDefinition(stone);
                session.sendUpstreamPacket(blockPacket);
            }
        }
    }

    public static void sendEmptyChunks(LinkBedrockSession session, Vector3i position,
                                       int radius) {
        sendEmptyChunks(session, position, radius, false);
    }

    public static void sendEmptyChunks(LinkBedrockSession session, Vector3i position,
                                       int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                sendEmptyChunk(session, chunkX + x, chunkZ + z, forceUpdate);
            }
        }
    }
}
