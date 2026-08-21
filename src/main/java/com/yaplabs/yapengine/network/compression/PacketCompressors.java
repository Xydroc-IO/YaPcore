package com.yaplabs.yapengine.network.compression;

/**
 * Factory — always prefers native Zstd; never returns a Deflater-based compressor.
 */
public final class PacketCompressors {

    private static volatile PacketCompressor SHARED;

    private PacketCompressors() {
    }

    public static PacketCompressor shared() {
        PacketCompressor local = SHARED;
        if (local == null) {
            synchronized (PacketCompressors.class) {
                local = SHARED;
                if (local == null) {
                    SHARED = local = new ZstdPacketCompressor(3);
                }
            }
        }
        return local;
    }

    public static PacketCompressor zstd(int level) {
        return new ZstdPacketCompressor(level);
    }
}
