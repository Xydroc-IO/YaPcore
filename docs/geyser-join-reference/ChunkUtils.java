/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.Unpooled
 *  lombok.Generated
 */
package org.geysermc.geyser.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.chunk.BlockStorage;
import org.geysermc.geyser.level.chunk.GeyserChunkSection;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.IntLists;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.MathUtils;

public final class ChunkUtils {
    private static final boolean SHOW_CHUNK_HEIGHT_WARNING_LOGS = Boolean.parseBoolean(System.getProperty("Geyser.ShowChunkHeightWarningLogs", "true"));
    public static final byte[] EMPTY_BIOME_DATA;
    public static final BlockStorage[] EMPTY_BLOCK_STORAGE;
    public static final int EMPTY_CHUNK_SECTION_SIZE;
    private static final ConcurrentHashMap<Integer, byte[]> EMPTY_CHUNK_PAYLOAD_CACHE;

    public static int indexYZXtoXZY(int yzx) {
        return yzx >> 8 | yzx & 0xF0 | (yzx & 0xF) << 8;
    }

    public static void updateChunkPosition(GeyserSession session, Vector3i position) {
        Vector2i chunkPos = session.getLastChunkPosition();
        Vector2i newChunkPos = Vector2i.from(position.getX() >> 4, position.getZ() >> 4);
        if (chunkPos == null || !chunkPos.equals(newChunkPos)) {
            NetworkChunkPublisherUpdatePacket chunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
            chunkPublisherUpdatePacket.setPosition(position);
            chunkPublisherUpdatePacket.setRadius(ChunkUtils.squareToCircle(session.getServerRenderDistance()) << 4);
            session.sendUpstreamPacket(chunkPublisherUpdatePacket);
            session.setLastChunkPosition(newChunkPos);
        }
    }

    public static int squareToCircle(int renderDistance) {
        return GenericMath.ceil((double)(renderDistance + 1) * MathUtils.SQRT_OF_TWO);
    }

    public static void updateBlock(GeyserSession session, int blockState, Vector3i position) {
        ChunkUtils.updateBlockClientSide(session, BlockState.of(blockState), position);
        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockState);
    }

    public static void updateBlock(GeyserSession session, BlockState blockState, Vector3i position) {
        ChunkUtils.updateBlockClientSide(session, blockState, position);
        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockState.javaId());
    }

    public static void updateBlockClientSide(GeyserSession session, BlockState blockState, Vector3i position) {
        ItemFrameEntity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, position);
        if (itemFrameEntity != null && blockState.is(Blocks.AIR)) {
            itemFrameEntity.updateBlock(true);
            return;
        }
        blockState.block().updateBlock(session, blockState, position);
    }

    public static void sendEmptyChunk(GeyserSession session, int chunkX, int chunkZ, boolean forceUpdate) {
        BedrockDimension bedrockDimension = session.getBedrockDimension();
        int bedrockSubChunkCount = bedrockDimension.height() >> 4;
        byte[] payload = EMPTY_CHUNK_PAYLOAD_CACHE.computeIfAbsent(bedrockSubChunkCount, subChunkCount -> {
            int biomeLength = EMPTY_BIOME_DATA.length;
            int totalLength = biomeLength + subChunkCount;
            byte[] data = new byte[totalLength];
            System.arraycopy(EMPTY_BIOME_DATA, 0, data, 0, biomeLength);
            int marker = -1;
            for (int i = 0; i < subChunkCount - 1; ++i) {
                data[biomeLength + i] = marker;
            }
            data[totalLength - 1] = 0;
            return data;
        });
        LevelChunkPacket data = new LevelChunkPacket();
        data.setDimension(bedrockDimension.bedrockId());
        data.setChunkX(chunkX);
        data.setChunkZ(chunkZ);
        data.setSubChunksLength(0);
        data.setData(Unpooled.wrappedBuffer((byte[])payload));
        data.setCachingEnabled(false);
        session.sendUpstreamPacket(data);
        if (forceUpdate) {
            Vector3i pos = Vector3i.from(chunkX << 4, 80, chunkZ << 4);
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(pos);
            blockPacket.setDataLayer(0);
            blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(1));
            session.sendUpstreamPacket(blockPacket);
        }
    }

    public static void sendEmptyChunks(GeyserSession session, Vector3i position, int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; ++x) {
            for (int z = -radius; z <= radius; ++z) {
                ChunkUtils.sendEmptyChunk(session, chunkX + x, chunkZ + z, forceUpdate);
            }
        }
    }

    public static void loadDimension(GeyserSession session) {
        JavaDimension dimension = session.getDimensionType();
        int minY = dimension.minY();
        int height = dimension.height();
        int maxY = minY + height;
        BedrockDimension bedrockDimension = session.getBedrockDimension();
        if (SHOW_CHUNK_HEIGHT_WARNING_LOGS && (minY < bedrockDimension.minY() || bedrockDimension.doUpperHeightWarn() && maxY > bedrockDimension.maxY())) {
            session.getGeyser().getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.translator.chunk.out_of_bounds", String.valueOf(bedrockDimension.minY()), String.valueOf(bedrockDimension.maxY()), session.getRegistryCache().registry(JavaRegistries.DIMENSION_TYPE).byValue(session.getDimensionType())));
        }
        session.getChunkCache().setMinY(minY);
        session.getChunkCache().setHeightY(height);
    }

    @Generated
    private ChunkUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static {
        EMPTY_CHUNK_PAYLOAD_CACHE = new ConcurrentHashMap(3);
        EMPTY_BLOCK_STORAGE = new BlockStorage[0];
        ByteBuf byteBuf = Unpooled.buffer();
        try {
            new GeyserChunkSection(EMPTY_BLOCK_STORAGE, 0).writeToNetwork(byteBuf);
            byte[] emptyChunkData = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(emptyChunkData);
            EMPTY_CHUNK_SECTION_SIZE = emptyChunkData.length;
            emptyChunkData = null;
        }
        finally {
            byteBuf.release();
        }
        byteBuf = Unpooled.buffer();
        try {
            BlockStorage blockStorage = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(0));
            blockStorage.writeToNetwork(byteBuf);
            EMPTY_BIOME_DATA = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(EMPTY_BIOME_DATA);
        }
        finally {
            byteBuf.release();
        }
    }
}
