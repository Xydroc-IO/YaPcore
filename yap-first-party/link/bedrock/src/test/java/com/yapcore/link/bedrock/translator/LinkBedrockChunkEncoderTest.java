package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Golden wire checks for Bedrock BlockStorage network encoding — prevents regressing to
 * {@code bits|0x10} headers, JE 64-bit words, or unsigned-vs-zigzag palette mistakes that
 * crash Bedrock clients.
 */
class LinkBedrockChunkEncoderTest {

    @Test
    void uniformSubChunk_headerIsNetworkSingleton() {
        ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        LinkBedrockChunkEncoder.writeUniformSubChunk(buf, 0);
        assertEquals(8, buf.readUnsignedByte());
        assertEquals(1, buf.readUnsignedByte());
        assertEquals(0x01, buf.readUnsignedByte()); // (0<<1)|1
        assertEquals(0, buf.readUnsignedByte()); // zigzag(0)
        assertEquals(0, buf.readableBytes());
        buf.release();
    }

    @Test
    void paletteSubChunk_headerBitsShiftOr1_int32Words_zigzagPalette() {
        // Two distinct states in YZX — forces bits=1 network header 0x03.
        int[] section = new int[4096];
        Arrays.fill(section, 0);
        section[0] = 1; // air vs stone
        section[1] = 1;

        ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        LinkBedrockChunkEncoder.writeSectionSubChunk(buf, section);

        assertEquals(8, buf.readUnsignedByte());
        assertEquals(1, buf.readUnsignedByte());
        int header = buf.readUnsignedByte();
        assertEquals((1 << 1) | 1, header, "network header must be (bits<<1)|1");

        int bits = header >> 1;
        int blocksPerWord = 32 / bits;
        int words = (4096 + blocksPerWord - 1) / blocksPerWord;
        // Consume LE int32 words
        for (int w = 0; w < words; w++) {
            buf.readIntLE();
        }
        // Palette size zigzag(2) = 4
        assertEquals(4, buf.readUnsignedByte());
        // Palette order is first-seen: state 1 then 0 → zigzag 0x02, 0x00
        assertEquals(2, buf.readUnsignedByte());
        assertEquals(0, buf.readUnsignedByte());
        assertEquals(0, buf.readableBytes());
        buf.release();
    }

    @Test
    void columnPayload_validatesAndMatchesSectionCountBiomes() {
        int[][] col = new int[24][];
        for (int i = 0; i < 24; i++) {
            col[i] = new int[4096];
            Arrays.fill(col[i], i == 8 ? 1 : 0); // one stone section mid-world
        }
        ByteBuf payload = LinkBedrockChunkEncoder.encodeColumnPayload(col, 24);
        assertNotNull(payload);
        assertNull(LinkBedrockChunkEncoder.validateColumnPayload(payload, 24));
        // Border + biome reuse tail
        assertEquals(0, payload.getByte(payload.writerIndex() - 1));
        String hex = LinkBedrockChunkEncoder.hexPrefix(payload, 3);
        assertTrue(hex.startsWith("0801"), "first subchunk v8+1storage: " + hex);
        payload.release();
    }

    @Test
    void indexYzxToXzy_matchesGeyser() {
        // y=1,z=2,x=3 → yzx = 1<<8 | 2<<4 | 3 = 291; xzy = 3<<8 | 2<<4 | 1 = 801
        int yzx = (1 << 8) | (2 << 4) | 3;
        assertEquals((3 << 8) | (2 << 4) | 1, LinkBedrockChunkEncoder.indexYzxToXzy(yzx));
    }

    @Test
    void countNonAirNearFeet_detectsFloorUnderSpawn() {
        int[][] col = new int[24][];
        for (int i = 0; i < 24; i++) {
            col[i] = new int[4096];
        }
        // feet Y=87 → block under feet y=86 is section (86+64)>>4 = 9, local y=6
        // place stone at local (3,6,4) world (-13,86,-12) in chunk -1,-1
        // localX = -13 & 15 = 3; localZ = -12 & 15 = 4
        int section = (86 - (-64)) >> 4;
        int ly = 86 & 15;
        int lx = 3;
        int lz = 4;
        col[section][(ly << 8) | (lz << 4) | lx] = 1;
        // also neighbors
        col[section][(ly << 8) | (lz << 4) | (lx + 1)] = 1;
        col[section][(((ly - 1) & 15) << 8) | (lz << 4) | lx] = 1;
        col[section][(((ly - 2) & 15) << 8) | (lz << 4) | lx] = 1;
        int near = LinkBedrockChunkEncoder.countNonAirNearFeet(col, -64, -13, 87.0, -12);
        assertTrue(near >= 4, "near=" + near);
        assertTrue(LinkBedrockChunkEncoder.countNonAirColumn(col) >= 4);
    }

    @Test
    void resolveStandOnFeetY_liftsWhenFeetInsideSolid() {
        int[][] col = new int[24][];
        for (int i = 0; i < 24; i++) {
            col[i] = new int[4096];
        }
        // Solid at Y=86 and below; air at 87+ — Folia feet wrongly at 86.0 (inside stone).
        int lx = 3;
        int lz = 4;
        for (int y = 80; y <= 86; y++) {
            int section = (y - (-64)) >> 4;
            int ly = y & 15;
            col[section][(ly << 8) | (lz << 4) | lx] = 1;
        }
        double stand = LinkBedrockChunkEncoder.resolveStandOnFeetY(col, -64, -13, 86.0, -12);
        assertEquals(87.0, stand, 0.001, "must stand on top of block 86");
        // Already clear at 87 → unchanged
        assertEquals(87.0,
                LinkBedrockChunkEncoder.resolveStandOnFeetY(col, -64, -13, 87.0, -12), 0.001);
    }

    @Test
    void resolveStandOnFeetY_climbsOutOfTwoBlockPocket() {
        int[][] col = new int[24][];
        for (int i = 0; i < 24; i++) {
            col[i] = new int[4096];
        }
        int lx = 3;
        int lz = 4;
        // Solid mass with 2-high air pocket at 86–87; solid ceiling at 88+ → black/buried feel.
        for (int y = 80; y <= 95; y++) {
            if (y == 86 || y == 87) {
                continue;
            }
            int section = (y - (-64)) >> 4;
            int ly = y & 15;
            col[section][(ly << 8) | (lz << 4) | lx] = 1;
        }
        double stand = LinkBedrockChunkEncoder.resolveStandOnFeetY(col, -64, -13, 86.0, -12);
        assertEquals(96.0, stand, 0.001, "must climb to surface above pocket");
    }

    @Test
    void countNonAirColumn_highEvenWhenFeetCellIsAir() {
        int[][] col = new int[24][];
        for (int i = 0; i < 24; i++) {
            col[i] = new int[4096];
        }
        // Dense stone in section Y=64..79 except feet cell Y=71 — columnNonAir gate case.
        int lx = 2;
        int lz = 5;
        for (int y = 64; y <= 79; y++) {
            if (y == 71) {
                continue;
            }
            int section = (y - (-64)) >> 4;
            int ly = y & 15;
            col[section][(ly << 8) | (lz << 4) | lx] = 1;
        }
        assertTrue(LinkBedrockChunkEncoder.countNonAirColumn(col) >= 14);
        assertTrue(LinkBedrockChunkEncoder.countNonAirNearFeet(col, -64, 66, 71.0, -11) >= 0);
    }
}
