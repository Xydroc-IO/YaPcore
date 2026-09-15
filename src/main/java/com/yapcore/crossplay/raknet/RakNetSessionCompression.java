package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.logging.Logger;

/**
 * Deflate / inflate / batch-peek helpers for {@link RakNetSessionManager}
 * (split for the ≤500-line domain gate).
 */
final class RakNetSessionCompression {

    private static final Logger LOG = Logger.getLogger("YaPcore.RakNet");

    private RakNetSessionCompression() {
    }

    static ByteBuf deflateRaw(ByteBuf plain) {
        byte[] in = new byte[plain.readableBytes()];
        plain.getBytes(plain.readerIndex(), in);
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(in);
            deflater.finish();
            byte[] buf = new byte[Math.max(64, in.length)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(in.length / 2);
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                } else {
                    break;
                }
            }
            return Unpooled.wrappedBuffer(baos.toByteArray());
        } catch (Exception e) {
            return null;
        } finally {
            deflater.end();
        }
    }

    /** Peek up to {@code max} packet ids from a Bedrock batch (len-varint ‖ id-varint ‖ body). */
    static String peekBatchPacketIds(ByteBuf batch, int max) {
        if (batch == null || !batch.isReadable() || max <= 0) {
            return "";
        }
        int reader = batch.readerIndex();
        StringBuilder sb = new StringBuilder();
        try {
            for (int n = 0; n < max && batch.isReadable(); n++) {
                int len = readUnsignedVarIntSafe(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                int end = batch.readerIndex() + len;
                int id = readUnsignedVarIntSafe(batch);
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append("0x").append(Integer.toHexString(id));
                if (id == 0x71) {
                    sb.append("=SetLocalPlayerAsInitialized");
                }
                batch.readerIndex(end);
            }
        } catch (Exception ignored) {
            // best-effort peek
        } finally {
            batch.readerIndex(reader);
        }
        return sb.toString();
    }

    static int readUnsignedVarIntSafe(ByteBuf buf) {
        int value = 0;
        int size = 0;
        while (buf.isReadable()) {
            int b = buf.readUnsignedByte();
            value |= (b & 0x7f) << (size++ * 7);
            if (size > 5) {
                throw new IllegalArgumentException("VarInt too long");
            }
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("VarInt truncated");
    }

    /** Minecraft Bedrock batch compression method 0 = raw deflate (no zlib wrapper). */
    static ByteBuf inflateRaw(ByteBuf compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.getBytes(compressed.readerIndex(), in);
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
        try {
            inflater.setInput(in);
            byte[] buf = new byte[Math.max(8192, in.length * 4)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(buf.length);
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                    if (baos.size() > 16 * 1024 * 1024) {
                        return null;
                    }
                    continue;
                }
                if (inflater.needsInput()) {
                    break;
                }
                if (n == 0) {
                    break;
                }
            }
            if (baos.size() == 0) {
                return null;
            }
            return Unpooled.wrappedBuffer(baos.toByteArray());
        } catch (Exception e) {
            LOG.warning("inflateRaw: " + e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }

    /** Bedrock game-batch compression method 1 (SNAPPY). */
    static ByteBuf inflateSnappy(ByteBuf compressed) {
        try {
            byte[] in = new byte[compressed.readableBytes()];
            compressed.getBytes(compressed.readerIndex(), in);
            byte[] out = org.xerial.snappy.Snappy.uncompress(in);
            if (out == null || out.length == 0 || out.length > 16 * 1024 * 1024) {
                return null;
            }
            return Unpooled.wrappedBuffer(out);
        } catch (Exception e) {
            LOG.warning("inflateSnappy: " + e.getMessage());
            return null;
        }
    }
}
