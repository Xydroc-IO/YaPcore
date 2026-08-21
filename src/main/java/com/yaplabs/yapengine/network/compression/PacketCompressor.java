package com.yaplabs.yapengine.network.compression;

/**
 * Packet compression off the game tick — never use synchronized
 * {@link java.util.zip.Deflater} on Thread 2 or spatial loops.
 */
public interface PacketCompressor {

    byte[] compress(byte[] input, int offset, int length);

    byte[] decompress(byte[] input, int offset, int length, int maxOutputBytes);

    String name();

    default byte[] compress(byte[] input) {
        return compress(input, 0, input.length);
    }

    default byte[] decompress(byte[] input, int maxOutputBytes) {
        return decompress(input, 0, input.length, maxOutputBytes);
    }
}
