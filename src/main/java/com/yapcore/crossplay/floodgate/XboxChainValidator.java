package com.yapcore.crossplay.floodgate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Full Xbox / Mojang Bedrock login JWT chain validation (Floodgate-class, clean-room).
 * Walks the ES384 chain until Mojang's known root public key is reached.
 */
public final class XboxChainValidator {

    private static final Logger LOG = Logger.getLogger("YaPcore.XboxChain");

    /**
     * Mojang auth service public key (SPKI / SubjectPublicKeyInfo, base64) — public constant
     * documented for Bedrock server-side chain verification.
     */
    public static final String MOJANG_ROOT_SPKI_B64 =
            "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8ELkixyLcwlZryUQcu1TvPOmI2B7vX83ndnWRUaXm74w"
                    + "Ffa5f/lwQNTfrLVHa2PmenpGI6JhIMUJaWZrjmMj90NoKNFSNBuKdm8rYiXsfaz3K36x/1U26HpG0ZxK/V1V";

    public record ChainResult(
            boolean valid,
            boolean mojangAuthenticated,
            String username,
            String xuid,
            String identityPublicKey,
            String failReason
    ) {
        public static ChainResult fail(String reason) {
            return new ChainResult(false, false, null, null, null, reason);
        }
    }

    private final PublicKey mojangRoot;
    private final boolean allowSelfSignedOffline;

    public XboxChainValidator(boolean allowSelfSignedOffline) {
        this.allowSelfSignedOffline = allowSelfSignedOffline;
        this.mojangRoot = parseSpkiEc(MOJANG_ROOT_SPKI_B64);
    }

    /**
     * Test / soak constructor: inject a stand-in root (proves Mojang-rooted walk + XUID
     * without Mojang's private key). Production uses {@link #XboxChainValidator(boolean)}.
     */
    public XboxChainValidator(PublicKey rootPublicKey, boolean allowSelfSignedOffline) {
        this.allowSelfSignedOffline = allowSelfSignedOffline;
        this.mojangRoot = rootPublicKey != null ? rootPublicKey : parseSpkiEc(MOJANG_ROOT_SPKI_B64);
    }

    public XboxChainValidator() {
        this(true);
    }

    public ChainResult validateChainJson(String chainJson) {
        if (chainJson == null || chainJson.isBlank()) {
            return ChainResult.fail("empty chain");
        }
        chainJson = unwrapCertificateEnvelope(chainJson);
        List<String> tokens = extractJwtList(chainJson);
        if (tokens.isEmpty()) {
            // raw JWT blob
            if (chainJson.contains("eyJ")) {
                tokens = List.of(chainJson.trim().replace("\"", "").split(","));
            }
        }
        if (tokens.isEmpty()) {
            return ChainResult.fail("no JWTs in chain");
        }

        PublicKey currentKey = null;
        boolean sawMojang = false;
        String username = null;
        String xuid = null;
        String identityPublicKey = null;

        for (int i = 0; i < tokens.size(); i++) {
            String jwt = tokens.get(i).trim();
            if (jwt.isEmpty()) {
                continue;
            }
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                return ChainResult.fail("malformed JWT at " + i);
            }
            Map<String, Object> header = parseJsonObject(b64Url(parts[0]));
            Map<String, Object> payload = parseJsonObject(b64Url(parts[1]));
            byte[] sig = Base64.getUrlDecoder().decode(pad(parts[2]));

            String x5u = stringField(header, "x5u");
            PublicKey headerKey = x5u != null ? parseSpkiEc(x5u) : null;
            PublicKey verifyKey = currentKey != null ? currentKey : headerKey;
            if (verifyKey == null) {
                return ChainResult.fail("no verify key at token " + i);
            }

            if (!verifyEs384(verifyKey, (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII), sig)) {
                return ChainResult.fail("ES384 verify failed at token " + i);
            }

            if (keysEqual(verifyKey, mojangRoot) || (headerKey != null && keysEqual(headerKey, mojangRoot))) {
                sawMojang = true;
            }
            // Mojang issues CA token with certificateAuthority
            if (Boolean.TRUE.equals(payload.get("certificateAuthority")) && headerKey != null
                    && keysEqual(headerKey, mojangRoot)) {
                sawMojang = true;
            }

            identityPublicKey = stringField(payload, "identityPublicKey");
            if (identityPublicKey != null) {
                currentKey = parseSpkiEc(identityPublicKey);
            }

            Map<String, Object> extra = mapField(payload, "extraData");
            if (extra != null) {
                if (extra.get("displayName") != null) {
                    username = String.valueOf(extra.get("displayName"));
                }
                if (extra.get("XUID") != null) {
                    xuid = String.valueOf(extra.get("XUID"));
                } else if (extra.get("xuid") != null) {
                    xuid = String.valueOf(extra.get("xuid"));
                }
            }

            // nbf/exp soft check
            long now = System.currentTimeMillis() / 1000L;
            Number nbf = numberField(payload, "nbf");
            Number exp = numberField(payload, "exp");
            if (nbf != null && now + 60 < nbf.longValue()) {
                return ChainResult.fail("token not yet valid");
            }
            if (exp != null && now - 60 > exp.longValue()) {
                return ChainResult.fail("token expired");
            }
        }

        boolean authenticated = sawMojang && xuid != null && !xuid.isBlank();
        if (!authenticated && !allowSelfSignedOffline) {
            return ChainResult.fail("chain did not reach Mojang root / missing XUID");
        }
        if (!authenticated && tokens.size() == 1 && allowSelfSignedOffline) {
            LOG.info("Xbox chain self-signed offline identity username=" + username);
        } else if (authenticated) {
            LOG.info("Xbox chain OK username=" + username + " xuid=" + xuid);
        }
        return new ChainResult(true, authenticated, username, xuid, identityPublicKey, null);
    }

    private static boolean verifyEs384(PublicKey key, byte[] signingInput, byte[] joseSig) {
        try {
            // JOSE ES384 signature is R||S (96 bytes). Java ECDSA wants DER.
            byte[] der = joseEsToDer(joseSig, 48);
            Signature sig = Signature.getInstance("SHA384withECDSA");
            sig.initVerify(key);
            sig.update(signingInput);
            return sig.verify(der);
        } catch (Exception e) {
            LOG.fine("verifyEs384: " + e.getMessage());
            return false;
        }
    }

    /** Convert JOSE raw R||S to DER SEQUENCE. */
    static byte[] joseEsToDer(byte[] jose, int componentLen) {
        if (jose.length != componentLen * 2) {
            // try as already DER
            return jose;
        }
        byte[] r = trimLeadingZeros(jose, 0, componentLen);
        byte[] s = trimLeadingZeros(jose, componentLen, componentLen);
        int len = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + len];
        der[0] = 0x30;
        der[1] = (byte) len;
        int i = 2;
        der[i++] = 0x02;
        der[i++] = (byte) r.length;
        System.arraycopy(r, 0, der, i, r.length);
        i += r.length;
        der[i++] = 0x02;
        der[i++] = (byte) s.length;
        System.arraycopy(s, 0, der, i, s.length);
        return der;
    }

    private static byte[] trimLeadingZeros(byte[] src, int off, int len) {
        int start = off;
        int end = off + len;
        while (start < end - 1 && src[start] == 0) {
            start++;
        }
        // Ensure positive INTEGER
        boolean needPad = (src[start] & 0x80) != 0;
        byte[] out = new byte[(end - start) + (needPad ? 1 : 0)];
        if (needPad) {
            out[0] = 0;
            System.arraycopy(src, start, out, 1, end - start);
        } else {
            System.arraycopy(src, start, out, 0, end - start);
        }
        return out;
    }

    static PublicKey parseSpkiEc(String b64) {
        try {
            byte[] spki = Base64.getDecoder().decode(b64.replace("\n", "").replace("\r", ""));
            return KeyFactory.getInstance("EC").generatePublic(
                    new java.security.spec.X509EncodedKeySpec(spki));
        } catch (Exception e) {
            LOG.fine("parseSpkiEc fail: " + e.getMessage());
            return null;
        }
    }

    private static boolean keysEqual(PublicKey a, PublicKey b) {
        if (a == null || b == null) {
            return false;
        }
        return java.util.Arrays.equals(a.getEncoded(), b.getEncoded());
    }

    /**
     * Retail clients often wrap identity as {@code {"Certificate":"{\"chain\":[...]}"}}
     * or nest AuthenticationType; unwrap before JWT extraction.
     */
    static String unwrapCertificateEnvelope(String raw) {
        String s = raw.trim();
        if (!s.startsWith("{")) {
            return s;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new com.google.gson.Gson().fromJson(s, Map.class);
            if (map == null) {
                return s;
            }
            Object cert = map.get("Certificate");
            if (cert instanceof String cs && !cs.isBlank()) {
                return unwrapCertificateEnvelope(cs);
            }
            if (map.containsKey("chain")) {
                return s;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return s;
    }

    private static List<String> extractJwtList(String chainJson) {
        List<String> out = new ArrayList<>();
        // {"chain":["eyJ...","eyJ..."]}
        int idx = chainJson.indexOf("\"chain\"");
        if (idx < 0) {
            if (chainJson.trim().startsWith("eyJ")) {
                out.add(chainJson.trim());
            }
            return out;
        }
        int arr = chainJson.indexOf('[', idx);
        int end = chainJson.indexOf(']', arr);
        if (arr < 0 || end < 0) {
            return out;
        }
        String inner = chainJson.substring(arr + 1, end);
        for (String part : inner.split(",")) {
            String t = part.trim();
            if (t.startsWith("\"")) {
                t = t.substring(1);
            }
            if (t.endsWith("\"")) {
                t = t.substring(0, t.length() - 1);
            }
            t = t.replace("\\/", "/");
            if (t.startsWith("eyJ")) {
                out.add(t);
            }
        }
        return out;
    }

    private static String b64Url(String s) {
        return new String(Base64.getUrlDecoder().decode(pad(s)), StandardCharsets.UTF_8);
    }

    private static String pad(String s) {
        int m = s.length() % 4;
        if (m == 0) {
            return s;
        }
        return s + "====".substring(m);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String json) {
        // Minimal object parser for JWT payloads (flat + one-level nested extraData)
        Map<String, Object> map = new LinkedHashMap<>();
        String s = json.trim();
        if (!s.startsWith("{")) {
            return map;
        }
        // Use Gson if available
        try {
            return new com.google.gson.Gson().fromJson(s, Map.class);
        } catch (Exception e) {
            return map;
        }
    }

    private static String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static Number numberField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number n ? n : null;
    }
}
