package com.yapcore.map;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Compact binary chunk mesh ({@code .ymesh}) alongside JSON.
 *
 * <pre>
 * magic "YMSH" (4)
 * u16 version (=2)
 * u16 flags (bit0 = milliblock units)
 * i32 cx, i32 cz
 * u32 boxCount
 * u32 unit (typically 1000)
 * repeating boxCount × 7 × i32  (lx,y,lz,sx,sy,sz,rgb) milliblocks
 * </pre>
 */
public final class MeshBinaryCodec {

    public static final byte[] MAGIC = "YMSH".getBytes(StandardCharsets.US_ASCII);
    public static final int BINARY_VERSION = 2;
    public static final int FLAG_MILLI = 1;
    public static final String EXTENSION = ".ymesh";

    private MeshBinaryCodec() {
    }

    public static byte[] encode(ChunkMeshData data) {
        if (data == null) {
            data = ChunkMeshData.boxes(0, 0, new int[0]);
        }
        int[] p = data.packed();
        int n = data.blockCount();
        int stride = GreedyMesher.STRIDE;
        ByteBuffer buf = ByteBuffer.allocate(24 + n * stride * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(MAGIC);
        buf.putShort((short) BINARY_VERSION);
        buf.putShort((short) FLAG_MILLI);
        buf.putInt(data.chunkX());
        buf.putInt(data.chunkZ());
        buf.putInt(n);
        buf.putInt(MeshUnits.UNIT);
        for (int i = 0; i + stride - 1 < p.length; i += stride) {
            for (int k = 0; k < stride; k++) {
                buf.putInt(p[i + k]);
            }
        }
        return Arrays.copyOf(buf.array(), buf.position());
    }

    public static ChunkMeshData decode(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return ChunkMeshData.boxes(0, 0, new int[0]);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("Not a YMSH mesh");
        }
        int version = buf.getShort() & 0xffff;
        int flags = buf.getShort() & 0xffff;
        int cx = buf.getInt();
        int cz = buf.getInt();
        int n = buf.getInt();
        int unit = buf.getInt();
        if (n < 0 || n > 5_000_000) {
            throw new IllegalArgumentException("Invalid box count: " + n);
        }
        int stride = GreedyMesher.STRIDE;
        int[] packed = new int[n * stride];
        for (int i = 0; i < n * stride; i++) {
            if (buf.remaining() < 4) {
                throw new IllegalArgumentException("Truncated YMSH payload");
            }
            packed[i] = buf.getInt();
        }
        // Legacy / odd unit: rescale into milliblocks when flag missing and unit==1
        if ((flags & FLAG_MILLI) == 0 && unit == 1) {
            for (int i = 0; i < packed.length; i++) {
                if (i % stride != 6) {
                    packed[i] *= MeshUnits.UNIT;
                }
            }
        } else if (unit > 0 && unit != MeshUnits.UNIT) {
            for (int i = 0; i < packed.length; i++) {
                if (i % stride != 6) {
                    packed[i] = (int) ((long) packed[i] * MeshUnits.UNIT / unit);
                }
            }
        }
        if (version < 1) {
            // keep going — geometry is what matters
        }
        return ChunkMeshData.boxes(cx, cz, packed);
    }

    public static boolean looksLikeYmesh(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return bytes[0] == 'Y' && bytes[1] == 'M' && bytes[2] == 'S' && bytes[3] == 'H';
    }
}
