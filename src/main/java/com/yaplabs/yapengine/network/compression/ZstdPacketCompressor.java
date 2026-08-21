package com.yaplabs.yapengine.network.compression;

import com.github.luben.zstd.Zstd;

import java.util.logging.Logger;

/**
 * Native Zstd via JNI (zstd-jni) — silicon-level compression without
 * {@code Deflater} monitor locks that stall Thread 2 / game loops.
 */
public final class ZstdPacketCompressor implements PacketCompressor {

    private static final Logger LOG = Logger.getLogger("YapEngine.Zstd");
    private final int level;

    public ZstdPacketCompressor() {
        this(3);
    }

    public ZstdPacketCompressor(int level) {
        this.level = Math.max(1, Math.min(level, 22));
        // Touch native via compressBound — loads libzstd-jni without Deflater locks.
        long probe = Zstd.compressBound(64);
        LOG.info("Packet compressor: Zstd-JNI level=" + this.level
                + " bound64=" + probe);
    }

    @Override
    public byte[] compress(byte[] input, int offset, int length) {
        if (input == null || length == 0) {
            return new byte[0];
        }
        long bound = Zstd.compressBound(length);
        byte[] out = new byte[(int) bound];
        long written = Zstd.compressByteArray(out, 0, out.length, input, offset, length, level);
        if (Zstd.isError(written)) {
            throw new IllegalStateException("Zstd compress: " + Zstd.getErrorName(written));
        }
        if (written == out.length) {
            return out;
        }
        byte[] trimmed = new byte[(int) written];
        System.arraycopy(out, 0, trimmed, 0, (int) written);
        return trimmed;
    }

    @Override
    public byte[] decompress(byte[] input, int offset, int length, int maxOutputBytes) {
        if (input == null || length == 0) {
            return new byte[0];
        }
        byte[] out = new byte[Math.max(1, maxOutputBytes)];
        long written = Zstd.decompressByteArray(out, 0, out.length, input, offset, length);
        if (Zstd.isError(written)) {
            throw new IllegalStateException("Zstd decompress: " + Zstd.getErrorName(written));
        }
        if (written == out.length) {
            return out;
        }
        byte[] trimmed = new byte[(int) written];
        System.arraycopy(out, 0, trimmed, 0, (int) written);
        return trimmed;
    }

    @Override
    public String name() {
        return "zstd-jni#" + level;
    }
}
