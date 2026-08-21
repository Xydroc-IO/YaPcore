package com.yapcore.crossplay.floodgate;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
                protocol = readUnsignedVarInt(loginBody);
                int chainLen = readUnsignedVarInt(loginBody);
                byte[] chainBytes = new byte[Math.min(chainLen, loginBody.readableBytes())];
                loginBody.readBytes(chainBytes);
                String chainJson = new String(chainBytes, StandardCharsets.UTF_8);
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
                    // Soft scrape fallback for corrupt/test chains when offline allowed
                    ParsedChain scraped = scrapeChain(chainJson);
                    if (scraped.username != null) {
                        username = scraped.username;
                    }
                    if (scraped.xuid != null) {
                        xuid = scraped.xuid;
                    }
                    pubKey = scraped.identityPublicKey != null ? scraped.identityPublicKey : "";
                    LOG.warning("Xbox chain soft-fail (" + result.failReason() + ") using scrape for " + username);
                }
                if (loginBody.isReadable()) {
                    int skinLen = readUnsignedVarInt(loginBody);
                    loginBody.skipBytes(Math.min(skinLen, loginBody.readableBytes()));
                }
            } catch (Exception e) {
                LOG.fine("Login parse fallback: " + e.getMessage());
                if (!offlineFallback) {
                    throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
                }
            }
        }

        if (xuid.isBlank()) {
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

    public static UUID uuidFromXuid(String xuid) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(("Floodgate:" + xuid).getBytes(StandardCharsets.UTF_8));
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xff);
            }
            // Set version/variant bits for a name-based UUID feel
            msb = (msb & 0xffffffffffff0fffL) | 0x0000000000003000L;
            lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(msb, lsb);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + xuid).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String offlineXuid(String username, String address) {
        return Long.toUnsignedString(Math.abs((username + "|" + address).hashCode() * 31L + 0xF100D6A7EL));
    }

    private static ParsedChain scrapeChain(String chainJson) {
        ParsedChain p = new ParsedChain();
        p.username = extractJsonString(chainJson, "displayName");
        String xuid = extractJsonString(chainJson, "XUID");
        if (xuid == null) {
            xuid = extractJsonString(chainJson, "xuid");
        }
        p.xuid = xuid;
        p.identityPublicKey = extractJsonString(chainJson, "identityPublicKey");
        return p;
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

    private static final class ParsedChain {
        String username;
        String xuid;
        String identityPublicKey;
    }
}
