/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.ByteBufAllocator
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.handler.codec.MessageToMessageEncoder
 *  io.netty.util.concurrent.FastThreadLocal
 *  org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper
 */
package org.cloudburstmc.protocol.bedrock.netty.codec.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.concurrent.FastThreadLocal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;

public class BedrockEncryptionEncoder
extends MessageToMessageEncoder<BedrockBatchWrapper> {
    public static final String NAME = "bedrock-encryption-encoder";
    private static final FastThreadLocal<MessageDigest> DIGEST = new /* Unavailable Anonymous Inner Class!! */;
    private final AtomicLong packetCounter = new AtomicLong();
    private final SecretKey key;
    private final Cipher cipher;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper in, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().ioBuffer(in.getCompressed().readableBytes() + 8);
        try {
            ByteBuffer trailer = ByteBuffer.wrap(BedrockEncryptionEncoder.generateTrailer(in.getCompressed(), this.key, this.packetCounter));
            ByteBuffer inBuffer = in.getCompressed().nioBuffer();
            ByteBuffer outBuffer = buf.nioBuffer(0, in.getCompressed().readableBytes() + 8);
            int index = this.cipher.update(inBuffer, outBuffer);
            buf.writerIndex(index += this.cipher.update(trailer, outBuffer));
            in.setCompressed(buf.retain());
            out.add(in.retain());
        }
        finally {
            buf.release();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static byte[] generateTrailer(ByteBuf buf, SecretKey key, AtomicLong counter) {
        MessageDigest digest = (MessageDigest)DIGEST.get();
        ByteBuf counterBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
        try {
            counterBuf.writeLongLE(counter.getAndIncrement());
            ByteBuffer keyBuffer = ByteBuffer.wrap(key.getEncoded());
            digest.update(counterBuf.nioBuffer(0, 8));
            digest.update(buf.nioBuffer(buf.readerIndex(), buf.readableBytes()));
            digest.update(keyBuffer);
            byte[] hash = digest.digest();
            byte[] byArray = Arrays.copyOf(hash, 8);
            return byArray;
        }
        finally {
            counterBuf.release();
            digest.reset();
        }
    }

    public BedrockEncryptionEncoder(SecretKey key, Cipher cipher) {
        this.key = key;
        this.cipher = cipher;
    }
}
