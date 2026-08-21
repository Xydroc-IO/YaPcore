package com.yapcore.crossplay.floodgate;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Floodgate-class Xbox / Bedrock identity linking — built into YaPcore (not Floodgate jar).
 * Derives stable Java UUIDs from XUID / username; parses login chain when present.
 */
public final class FloodgateAuth {

    private static final Logger LOG = Logger.getLogger("YaPcore.Floodgate");

    public record Identity(
            String username,
            String xuid,
            UUID javaUuid,
            int protocol,
            boolean linked,
            String identityPublicKey
    ) {
    }

    private final ConcurrentHashMap<String, Identity> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> linkJavaToBedrock = new ConcurrentHashMap<>();
    private final boolean offlineFallback;
    private final XboxChainValidator chainValidator;

    public FloodgateAuth(boolean offlineFallback) {
        this.offlineFallback = offlineFallback;
        this.chainValidator = new XboxChainValidator(offlineFallback);
    }

    public FloodgateAuth() {
        this(true);
    }

    public XboxChainValidator chainValidator() {
        return chainValidator;
    }

    /**
     * Authenticate from a Bedrock Login packet body (or empty → offline identity).
     */
    public Identity authenticate(ByteBuf loginBody, String address) {
        String username = "BedrockPlayer";
        String xuid = "";
        int protocol = 649;
        String pubKey = "";
        boolean linked = false;

        if (loginBody != null && loginBody.isReadable()) {
            try {
                // packet_login: i32 protocol + encapsulated(varint) LoginTokens
                // LoginTokens: LittleString identity + LittleString client
                if (loginBody.readableBytes() >= 4) {
                    protocol = loginBody.readInt();
                }
                if (loginBody.isReadable()) {
                    int encLen = readUnsignedVarInt(loginBody);
                    if (encLen < 0 || encLen > loginBody.readableBytes()) {
                        throw new IllegalArgumentException("bad login encapsulate len=" + encLen);
                    }
                }
                String identity = readLittleString(loginBody);
                String clientJwt = loginBody.isReadable() ? readLittleString(loginBody) : "";
                String chainJson = identity;
                maybeDumpChain(chainJson, clientJwt);
                XboxChainValidator.ChainResult result = chainValidator.validateChainJson(chainJson);
                if (!result.valid() && !offlineFallback) {
                    throw new IllegalStateException("Xbox chain invalid: " + result.failReason());
                }
                if (result.valid()) {
                    if (result.username() != null && !result.username().isBlank()) {
                        username = result.username();
                    }
                    if (result.xuid() != null) {
                        xuid = result.xuid();
                    }
                    linked = result.mojangAuthenticated();
                    pubKey = result.identityPublicKey() != null ? result.identityPublicKey() : "";
                } else {
                    ParsedChain scraped = scrapeChain(chainJson);
                    if (scraped.username == null && clientJwt != null && !clientJwt.isBlank()) {
                        scraped = mergeScrape(scraped, scrapeChain(clientJwt));
                    }
                    if (scraped.username != null) {
                        username = scraped.username;
                    }
                    if (scraped.xuid != null) {
                        xuid = scraped.xuid;
                    }
                    pubKey = scraped.identityPublicKey != null ? scraped.identityPublicKey : "";
                    LOG.info("Xbox chain offline/self-signed (" + result.failReason()
                            + ") identity=" + username);
                }
            } catch (Exception e) {
                LOG.warning("Login parse fallback: " + e.getMessage());
                if (!offlineFallback) {
                    throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
                }
            }
        }

        if (xuid.isBlank() || "0".equals(xuid)) {
            xuid = offlineXuid(username, address);
        }
        UUID javaUuid = uuidFromXuid(xuid);
        Identity id = new Identity(username, xuid, javaUuid, protocol, linked, pubKey);
        return register(id);
    }

    public Identity register(Identity id) {
        byName.put(id.username().toLowerCase(), id);
        LOG.info("Floodgate identity " + id.username() + " xuid=" + id.xuid()
                + " uuid=" + id.javaUuid() + " linked=" + id.linked());
        return id;
    }

    public Identity get(String username) {
        return byName.get(username.toLowerCase());
    }

    public UUID uuidFor(String username) {
        Identity id = get(username);
        return id != null ? id.javaUuid() : uuidFromXuid(offlineXuid(username, ""));
    }

    /** Operator link: Bedrock name ↔ existing Java account name. */
    public void linkAccounts(String javaName, String bedrockName) {
        linkJavaToBedrock.put(javaName.toLowerCase(), bedrockName.toLowerCase());
        LOG.info("Floodgate link java=" + javaName + " ↔ bedrock=" + bedrockName);
    }

    public String linkedBedrock(String javaName) {
        return linkJavaToBedrock.get(javaName.toLowerCase());
    }

    public Map<String, Identity> snapshot() {
        return Map.copyOf(byName);
    }

    /**
     * Real Floodgate UUID for an XUID: {@code new UUID(0, xuid)}.
     * Non-numeric tokens (legacy call sites) fall back to a deterministic MSB=0 id.
     */
    public static UUID uuidFromXuid(String xuid) {
        if (xuid == null || xuid.isBlank()) {
            return new UUID(0L, 0L);
        }
        try {
            long n = Long.parseUnsignedLong(xuid.trim());
            return new UUID(0L, n);
        } catch (NumberFormatException e) {
            // Synthetic / legacy: keep MSB=0 Floodgate shape
            long n = Math.abs((long) xuid.hashCode()) | 0x1L;
            return new UUID(0L, n);
        }
    }

    private static String offlineXuid(String username, String address) {
        return Long.toUnsignedString(Math.abs((username + "|" + address).hashCode() * 31L + 0xF100D6A7EL));
    }

    /**
     * When {@code -Dyap.floodgate.dumpChain=true}, write Login identity JSON to
     * {@code yap.floodgate.dumpChainPath} (default {@code build/xbox-chain-capture.json})
     * for retail soak. Contains live JWTs — do not commit.
     */
    private static void maybeDumpChain(String identity, String clientJwt) {
        if (!Boolean.getBoolean("yap.floodgate.dumpChain")) {
            return;
        }
        try {
            String path = System.getProperty("yap.floodgate.dumpChainPath", "build/xbox-chain-capture.json");
            java.nio.file.Path p = java.nio.file.Path.of(path);
            if (p.getParent() != null) {
                java.nio.file.Files.createDirectories(p.getParent());
            }
            String body = identity == null ? "" : identity.trim();
            if (!body.startsWith("{")) {
                body = "{\"chain\":[" + body + "]}";
            }
            java.nio.file.Files.writeString(p, body, StandardCharsets.UTF_8);
            LOG.warning("Wrote Xbox chain capture to " + p.toAbsolutePath()
                    + " (contains live JWTs — gitignore; use xbox-chain-soak.sh)");
            if (clientJwt != null && !clientJwt.isBlank()) {
                java.nio.file.Path skin = p.resolveSibling("xbox-client-jwt-capture.txt");
                java.nio.file.Files.writeString(skin, clientJwt, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.warning("dumpChain failed: " + e.getMessage());
        }
    }

    private static ParsedChain mergeScrape(ParsedChain a, ParsedChain b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a.username == null) {
            a.username = b.username;
        }
        if (a.xuid == null) {
            a.xuid = b.xuid;
        }
        if (a.identityPublicKey == null) {
            a.identityPublicKey = b.identityPublicKey;
        }
        return a;
    }

    private static ParsedChain scrapeChain(String chainJson) {
        ParsedChain p = new ParsedChain();
        if (chainJson == null || chainJson.isBlank()) {
            return p;
        }
        // Prefer JWT payload claims (offline: extraData.displayName / OIDC: xname)
        int jwt = chainJson.indexOf("eyJ");
        while (jwt >= 0) {
            int end = chainJson.indexOf('"', jwt);
            if (end < 0) {
                end = chainJson.indexOf(',', jwt);
            }
            if (end < 0) {
                end = chainJson.length();
            }
            String token = chainJson.substring(jwt, end).replace("]", "").replace("}", "").trim();
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] payload = java.util.Base64.getUrlDecoder().decode(padB64(parts[1]));
                    String json = new String(payload, StandardCharsets.UTF_8);
                    if (p.username == null) {
                        p.username = extractJsonString(json, "displayName");
                    }
                    if (p.username == null) {
                        p.username = extractJsonString(json, "xname");
                    }
                    if (p.username == null) {
                        p.username = extractJsonString(json, "ThirdPartyName");
                    }
                    if (p.xuid == null) {
                        p.xuid = extractJsonString(json, "XUID");
                    }
                    if (p.xuid == null) {
                        p.xuid = extractJsonString(json, "xid");
                    }
                    if (p.xuid == null || "0".equals(p.xuid)) {
                        String identity = extractJsonString(json, "identity");
                        if (identity != null && identity.length() > 8) {
                            // UUID-as-identity for offline — keep xuid synthetic later
                        }
                    }
                    if (p.identityPublicKey == null) {
                        p.identityPublicKey = extractJsonString(json, "identityPublicKey");
                    }
                    if (p.identityPublicKey == null) {
                        p.identityPublicKey = extractJsonString(json, "cpk");
                    }
                } catch (Exception ignored) {
                    // try next token
                }
            }
            jwt = chainJson.indexOf("eyJ", jwt + 3);
        }
        if (p.username == null) {
            p.username = extractJsonString(chainJson, "displayName");
        }
        if (p.username == null) {
            p.username = extractJsonString(chainJson, "xname");
        }
        if (p.xuid == null) {
            p.xuid = extractJsonString(chainJson, "XUID");
        }
        if (p.xuid == null) {
            p.xuid = extractJsonString(chainJson, "xuid");
        }
        if (p.identityPublicKey == null) {
            p.identityPublicKey = extractJsonString(chainJson, "identityPublicKey");
        }
        return p;
    }

    private static String padB64(String s) {
        int m = s.length() % 4;
        if (m == 0) {
            return s;
        }
        return s + "====".substring(m);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + pattern.length());
        if (colon < 0) {
            return null;
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return json.substring(q1 + 1, q2);
    }

    private static int readUnsignedVarInt(ByteBuf in) {
        int value = 0;
        int size = 0;
        int b;
        while (((b = in.readUnsignedByte()) & 0x80) == 0x80) {
            value |= (b & 0x7F) << (size++ * 7);
            if (size > 5) {
                throw new IllegalArgumentException("VarInt too big");
            }
        }
        return value | ((b & 0x7F) << (size * 7));
    }

    /** Bedrock LittleString: li32 length + UTF-8 bytes. */
    private static String readLittleString(ByteBuf in) {
        int len = in.readIntLE();
        if (len < 0 || len > in.readableBytes()) {
            throw new IllegalArgumentException("bad LittleString len=" + len);
        }
        byte[] bytes = new byte[len];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class ParsedChain {
        String username;
        String xuid;
        String identityPublicKey;
    }
}
