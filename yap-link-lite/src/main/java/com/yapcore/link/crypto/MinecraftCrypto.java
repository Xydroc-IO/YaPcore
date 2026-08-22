package com.yapcore.link.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.List;

/** Minecraft login encryption helpers (RSA + AES/CFB8). */
public final class MinecraftCrypto {

    private MinecraftCrypto() {
    }

    public static KeyPair generateRsa() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(1024);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] decryptRsa(KeyPair pair, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, pair.getPrivate());
        return cipher.doFinal(data);
    }

    public static String serverId(byte[] sharedSecret, PublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(sharedSecret);
            digest.update(publicKey.getEncoded());
            return new BigInteger(digest.digest()).toString(16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Cipher newCipher(int mode, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
        return cipher;
    }

    /** Bidirectional CFB8 codec for the raw TCP stream (before framing). */
    public static final class CipherCodec extends MessageToMessageCodec<ByteBuf, ByteBuf> {
        private final Cipher inbound;
        private final Cipher outbound;

        public CipherCodec(Cipher inboundDecrypt, Cipher outboundEncrypt) {
            this.inbound = inboundDecrypt;
            this.outbound = outboundEncrypt;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
            byte[] plain = new byte[msg.readableBytes()];
            msg.readBytes(plain);
            out.add(ctx.alloc().buffer(plain.length).writeBytes(outbound.update(plain)));
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
            byte[] enc = new byte[msg.readableBytes()];
            msg.readBytes(enc);
            out.add(ctx.alloc().buffer(enc.length).writeBytes(inbound.update(enc)));
        }
    }
}
