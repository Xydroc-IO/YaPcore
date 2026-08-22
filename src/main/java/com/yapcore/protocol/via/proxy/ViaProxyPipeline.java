package com.yapcore.protocol.via.proxy;

import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.java.codec.McCompressionCodec;
import com.yapcore.protocol.via.ViaSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.logging.Logger;

/**
 * Manual length framing and zlib compression for the Via proxy path.
 */
public final class ViaProxyPipeline {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaProxy");
    /** Reuse Deflater per Netty worker — new Deflater() per packet stalls keepalives. */
    private static final ThreadLocal<java.util.zip.Deflater> DEFLATER =
            ThreadLocal.withInitial(java.util.zip.Deflater::new);

    private ViaProxyPipeline() {
    }

    public static String hexPrefix(ByteBuf buf, int max) {
        int n = Math.min(max, buf.readableBytes());
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", buf.getByte(buf.readerIndex() + i) & 0xff));
        }
        return sb.toString();
    }

    /** Set Compression — raw length frame, never zlib-wrapped. */
    public static io.netty.channel.ChannelFuture writeUncompressedFramed(Channel ch, ByteBuf packet) {
        ByteBuf framed = ch.alloc().buffer(packet.readableBytes() + 5);
        McCodec.writeVarInt(framed, packet.readableBytes());
        framed.writeBytes(packet, packet.readerIndex(), packet.readableBytes());
        packet.release();
        return ch.writeAndFlush(framed);
    }

    /**
     * S2C/C2S to a peer: length frame, and after Set Compression add Minecraft zlib header.
     * Done manually — pipeline MessageToMessageEncoder+frame-enc was corrupting frames.
     */
    public static io.netty.channel.ChannelFuture writeFramed(
            Channel ch, ByteBuf packet, ViaSession session, boolean compressionInstalled) {
        int threshold = session != null ? session.compressionThreshold() : -1;
        ByteBuf payload = packet;
        if (compressionInstalled && threshold >= 0) {
            payload = zlibWrap(ch, packet, threshold);
        }
        ByteBuf framed = ch.alloc().buffer(payload.readableBytes() + 5);
        McCodec.writeVarInt(framed, payload.readableBytes());
        framed.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        payload.release();
        return ch.writeAndFlush(framed);
    }

    /** Minecraft post-compression body: VarInt(dataLength) + data (0 = uncompressed). */
    private static ByteBuf zlibWrap(Channel ch, ByteBuf packet, int threshold) {
        int readable = packet.readableBytes();
        if (readable < threshold) {
            ByteBuf out = ch.alloc().buffer(readable + 5);
            McCodec.writeVarInt(out, 0);
            out.writeBytes(packet, packet.readerIndex(), readable);
            packet.release();
            return out;
        }
        byte[] input = new byte[readable];
        packet.getBytes(packet.readerIndex(), input);
        packet.release();
        java.util.zip.Deflater deflater = DEFLATER.get();
        deflater.reset();
        try {
            deflater.setInput(input);
            deflater.finish();
            byte[] buf = new byte[Math.max(64, readable / 2)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(readable);
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                } else {
                    break;
                }
            }
            byte[] compressed = baos.toByteArray();
            ByteBuf out = ch.alloc().buffer(compressed.length + 5);
            McCodec.writeVarInt(out, readable);
            out.writeBytes(compressed);
            return out;
        } finally {
            deflater.reset();
        }
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Channel> matchingClientChannel(Channel inbound) {
        String name = inbound.getClass().getName();
        try {
            if (name.contains("epoll")) {
                return (Class<? extends Channel>) Class.forName(
                        "io.netty.channel.epoll.EpollSocketChannel");
            }
            if (name.contains("kqueue")) {
                return (Class<? extends Channel>) Class.forName(
                        "io.netty.channel.kqueue.KQueueSocketChannel");
            }
        } catch (ClassNotFoundException e) {
            LOG.warning("Native channel missing, NIO fallback: " + e.getMessage());
        }
        return NioSocketChannel.class;
    }

    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            // Do not write EMPTY_BUFFER — McFrameCodec would emit a 0-length frame
            // and Paper throws CorruptedFrameException: Frame length cannot be zero.
            ch.close();
        }
    }

    public static void installBackendDecompress(ChannelPipeline pipeline, int threshold) {
        if (pipeline.get("mc-decompress") != null) {
            ((McCompressionCodec.Decoder) pipeline.get("mc-decompress")).setThreshold(threshold);
            return;
        }
        McCompressionCodec.Decoder decoder = new McCompressionCodec.Decoder();
        decoder.setThreshold(threshold);
        pipeline.addAfter("frame-dec", "mc-decompress", decoder);
    }

    public static void installBackendCompress(ChannelPipeline pipeline, int threshold) {
        // Outbound C2S uses writeFramed() manual zlib — do NOT also install a pipeline
        // Encoder (double-wrap → Paper "unknown packet id" / client partial packet 256).
        if (pipeline.get("mc-compress") != null) {
            pipeline.remove("mc-compress");
        }
        if (pipeline.get("frame-enc") != null) {
            pipeline.remove("frame-enc");
        }
    }

    public static void installClientCompression(ChannelPipeline pipeline, int threshold) {
        // Inbound only: decompress client→Via. S2C zlib is applied in writeFramed().
        if (pipeline.get("mc-decompress") == null) {
            McCompressionCodec.Decoder decoder = new McCompressionCodec.Decoder();
            decoder.setThreshold(threshold);
            pipeline.addAfter("frame-dec", "mc-decompress", decoder);
        } else {
            ((McCompressionCodec.Decoder) pipeline.get("mc-decompress")).setThreshold(threshold);
        }
        // Remove any leftover outbound compress/frame-enc from older builds
        if (pipeline.get("mc-compress") != null) {
            pipeline.remove("mc-compress");
        }
        if (pipeline.get("frame-enc") != null) {
            pipeline.remove("frame-enc");
        }
    }
}
