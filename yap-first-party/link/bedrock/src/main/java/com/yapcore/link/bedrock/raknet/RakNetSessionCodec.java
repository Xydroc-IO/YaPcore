package com.yapcore.link.bedrock.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.logging.Logger;

/**
 * Game-batch encapsulate + zlib helpers (split from {@link RakNetSessionManager}).
 */
final class RakNetSessionCodec {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private RakNetSessionCodec() {}

    static List<ByteBuf> encapsulateGameDatagrams(RakNetSessionManager.RakNetPeer peer, ByteBuf gameBatch) {
        boolean header = peer != null && peer.gameCompressionHeader();
        ByteBuf body = gameBatch;
        int method = 0xff;
        // Match NetworkSettings threshold (Geyser zlib threshold=512).
        if (header && gameBatch.readableBytes() >= 512) {
            ByteBuf deflated = deflateRaw(gameBatch);
            if (deflated != null && deflated.readableBytes() < gameBatch.readableBytes()) {
                body = deflated;
                method = 0;
            } else if (deflated != null) {
                deflated.release();
            }
        }
        ByteBuf payload;
        if (header) {
            payload = Unpooled.buffer(body.readableBytes() + 1);
            payload.writeByte(method);
            payload.writeBytes(body);
            if (body != gameBatch) {
                body.release();
            }
        } else {
            payload = body.retainedDuplicate();
            if (body != gameBatch) {
                body.release();
            }
        }
        // Cloudburst: compress → encrypt → frame 0xfe (NetworkSettings compression header first)
        if (peer != null && peer.encryptionEnabled()) {
            try {
                int plainLen = payload.readableBytes();
                ByteBuf encrypted = peer.encryption().encrypt(payload);
                payload.release();
                payload = encrypted;
                // Prove S2C game packets are encrypted on the wire after enableEncryption.
                if (peer.encryptAppliedLogs.getAndIncrement() < 8) {
                    LOG.info("BE encrypt applied outbound " + peer.address()
                            + " plain=" + plainLen
                            + " cipher=" + payload.readableBytes()
                            + " method=" + (header ? method : -1)
                            + " (compress→encrypt→0xfe)");
                }
            } catch (Exception e) {
                payload.release();
                LOG.warning("BE encrypt failed: " + e.getMessage());
                return List.of();
            }
        }
        ByteBuf wrapped = Unpooled.buffer(payload.readableBytes() + 1);
        wrapped.writeByte(0xfe);
        wrapped.writeBytes(payload);
        payload.release();
        return RakNetReliability.wrapReliableOrderedPossiblySplit(peer.state(), wrapped);
    }

    static ByteBuf encapsulateGame(RakNetSessionManager.RakNetPeer peer, ByteBuf gameBatch) {
        List<ByteBuf> dgs = encapsulateGameDatagrams(peer, gameBatch);
        if (dgs.size() == 1) {
            return dgs.get(0);
        }
        // Concatenate only for callers that ignore splits (tests); production uses Datagrams API.
        ByteBuf first = dgs.get(0);
        for (int i = 1; i < dgs.size(); i++) {
            dgs.get(i).release();
        }
        return first;
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

    static ByteBuf inflateRaw(ByteBuf compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.getBytes(compressed.readerIndex(), in);
        ByteBuf zlib = inflateWith(in, false);
        if (zlib != null) {
            return zlib;
        }
        LOG.fine("try zlib failed; using raw deflate");
        return inflateWith(in, true);
    }

    static ByteBuf inflateWith(byte[] in, boolean nowrap) {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(nowrap);
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
                if (inflater.needsInput() || n == 0) {
                    break;
                }
            }
            if (baos.size() == 0) {
                return null;
            }
            return Unpooled.wrappedBuffer(baos.toByteArray());
        } catch (Exception e) {
            return null;
        } finally {
            inflater.end();
        }
    }

}
