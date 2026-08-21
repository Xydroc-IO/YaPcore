package com.yapcore.crossplay.floodgate;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class XboxChainValidatorTest {

    @Test
    void mojangRootParses() {
        assertNotNull(XboxChainValidator.parseSpkiEc(XboxChainValidator.MOJANG_ROOT_SPKI_B64));
    }

    @Test
    void emptyChainFails() {
        XboxChainValidator v = new XboxChainValidator(false);
        var r = v.validateChainJson("");
        assertFalse(r.valid());
    }

    @Test
    void selfSignedOfflineAcceptedWhenAllowed() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair kp = kpg.generateKeyPair();
        String spki = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        String header = b64Url("{\"alg\":\"ES384\",\"x5u\":\"" + spki + "\"}");
        String payload = b64Url("{\"extraData\":{\"displayName\":\"TestBedrock\",\"XUID\":\"\"},"
                + "\"identityPublicKey\":\"" + spki + "\",\"nbf\":0,\"exp\":9999999999,\"iat\":1}");
        byte[] joseSig = signEs384((ECPrivateKey) kp.getPrivate(), header + "." + payload);
        String jwt = header + "." + payload + "." + b64UrlRaw(joseSig);
        String chain = "{\"chain\":[\"" + jwt + "\"]}";

        XboxChainValidator v = new XboxChainValidator(true);
        var r = v.validateChainJson(chain);
        assertTrue(r.valid(), r.failReason());
        assertEquals("TestBedrock", r.username());
        assertFalse(r.mojangAuthenticated());
    }

    private static String b64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64UrlRaw(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] signEs384(ECPrivateKey key, String signingInput) throws Exception {
        Signature sig = Signature.getInstance("SHA384withECDSA");
        sig.initSign(key);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] der = sig.sign();
        return derToJose(der, 48);
    }

    /** DER ECDSA → JOSE R||S */
    private static byte[] derToJose(byte[] der, int len) {
        // minimal DER parse: 30 len 02 rlen r 02 slen s
        int i = 2;
        if (der[0] != 0x30) {
            throw new IllegalArgumentException("not DER");
        }
        i = der[1] == (byte) 0x81 ? 3 : 2;
        if (der[i++] != 0x02) {
            throw new IllegalArgumentException("no R");
        }
        int rLen = der[i++] & 0xff;
        byte[] r = new byte[rLen];
        System.arraycopy(der, i, r, 0, rLen);
        i += rLen;
        if (der[i++] != 0x02) {
            throw new IllegalArgumentException("no S");
        }
        int sLen = der[i++] & 0xff;
        byte[] s = new byte[sLen];
        System.arraycopy(der, i, s, 0, sLen);
        byte[] out = new byte[len * 2];
        copyRight(r, out, 0, len);
        copyRight(s, out, len, len);
        return out;
    }

    private static void copyRight(byte[] src, byte[] dst, int dstOff, int len) {
        int copy = Math.min(src.length, len);
        // skip leading 0x00 sign pad
        int srcOff = src.length > len ? src.length - len : 0;
        if (src.length > 0 && src[0] == 0 && src.length > len) {
            srcOff = src.length - len;
        } else if (src[0] == 0 && src.length - 1 <= len) {
            srcOff = 1;
            copy = src.length - 1;
        }
        System.arraycopy(src, srcOff, dst, dstOff + (len - copy), copy);
    }
}
