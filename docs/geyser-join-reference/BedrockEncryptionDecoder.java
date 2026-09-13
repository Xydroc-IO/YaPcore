/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.handler.codec.CorruptedFrameException
 *  io.netty.handler.codec.MessageToMessageDecoder
 *  org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper
 */
package org.cloudburstmc.protocol.bedrock.netty.codec.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;

public class BedrockEncryptionDecoder
extends MessageToMessageDecoder<BedrockBatchWrapper> {
    public static final String NAME = "bedrock-encryption-decoder";
    private static final boolean VALIDATE = Boolean.getBoolean("cloudburst.validateEncryption");
    private final AtomicLong packetCounter = new AtomicLong();
    private final SecretKey key;
    private final Cipher cipher;

    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        ByteBuffer inBuffer = msg.getCompressed().nioBuffer();
        ByteBuffer outBuffer = inBuffer.duplicate();
        this.cipher.update(inBuffer, outBuffer);
        ByteBuf output = msg.getCompressed().readSlice(msg.getCompressed().readableBytes() - 8);
        if (VALIDATE) {
            ByteBuf trailer = msg.getCompressed().readSlice(8);
            byte[] actual = new byte[8];
            trailer.readBytes(actual);
            byte[] expected = BedrockEncryptionEncoder.generateTrailer(output, this.key, this.packetCounter);
            if (!Arrays.equals(expected, actual)) {
                throw new CorruptedFrameException("Invalid encryption trailer");
            }
        }
        msg.setCompressed(output.retain());
        out.add(msg.retain());
    }

    public BedrockEncryptionDecoder(SecretKey key, Cipher cipher) {
        this.key = key;
        this.cipher = cipher;
    }
}
