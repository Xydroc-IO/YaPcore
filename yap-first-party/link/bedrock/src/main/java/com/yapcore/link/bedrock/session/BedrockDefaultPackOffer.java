package com.yapcore.link.bedrock.session;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;

/**
 * Offers Bedrock {@code .mcpack} over the same chassis zip CDN Java uses ({@code :8081},
 * {@code Content-Type: application/zip}). Nginx {@code :80} often serves {@code .mcpack} as
 * {@code application/octet-stream}, which modern Bedrock rejects mid-download.
 */
final class BedrockDefaultPackOffer {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final String PACK_FILE = "yapcore-default.mcpack";
    /** Slim join pack when the full default is missing or CDN is public :80 only. */
    private static final String PORTALS_PACK_FILE = "yapcore-portals.mcpack";
    /** Same port Folia advertises for JE ({@code resource-pack=http://LAN:8081/pack/...}). */
    private static final int LAN_PACK_HTTP_PORT = 8081;
    private static final Pattern UUID_RE = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private static final Pattern VER_RE = Pattern.compile(
            "\"version\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");

    record Offer(UUID packId, String version, long sizeBytes, Path path, String cdnUrl) {
    }

    private BedrockDefaultPackOffer() {}

    /**
     * Full Faithful Bedrock pack is ~29MB — fine on LAN :8081 (JE pulls a similar zip).
     * Cap still blocks multi-minute CDN stalls on broken public URLs.
     */
    private static final long MAX_JOIN_PACK_BYTES = 40_000_000L;

    static Optional<Offer> resolve(BedrockSessionHost host, BedrockSessionHost.ClientState state) {
        if (host == null || host.config == null) {
            return Optional.empty();
        }
        // Default ON — same product expectation as Java. Kill-switch:
        // -Dyap.link.bedrock.offerDefaultPack=false
        if (!Boolean.parseBoolean(System.getProperty("yap.link.bedrock.offerDefaultPack", "true"))) {
            LOG.info("BE pack offer skipped (yap.link.bedrock.offerDefaultPack=false)");
            return Optional.empty();
        }
        Path path = findMcpack(host.config.linkHome());
        if (path == null) {
            return Optional.empty();
        }
        try {
            long size = Files.size(path);
            if (size > MAX_JOIN_PACK_BYTES) {
                LOG.warning("BE pack offer skipped — " + path.getFileName() + " is " + size
                        + " bytes (>" + MAX_JOIN_PACK_BYTES
                        + "); join with empty packs. Shrink the mcpack under "
                        + MAX_JOIN_PACK_BYTES + " bytes to offer it again.");
                return Optional.empty();
            }
            Manifest meta = readManifest(path);
            String cdn = cdnUrl(host, state, path.getFileName().toString());
            if (cdn == null || cdn.isBlank()) {
                return Optional.empty();
            }
            if (!cdnReachable(cdn)) {
                LOG.warning("BE pack offer skipped — CDN not usable for Bedrock (" + cdn
                        + "). Need Content-Type: application/zip (chassis :8081). "
                        + "Nginx :80 octet-stream / dead public host → empty handshake.");
                return Optional.empty();
            }
            LOG.info("BE pack offer " + path.getFileName() + " uuid=" + meta.uuid
                    + " ver=" + meta.version + " bytes=" + size + " cdn=" + cdn);
            return Optional.of(new Offer(meta.uuid, meta.version, size, path, cdn));
        } catch (Exception e) {
            LOG.warning("BE pack offer failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Probe before offering. Bedrock requires {@code Content-Type: application/zip}
     * (docs/network/CLIENTS_AND_PACKS.md) — a 200 with {@code octet-stream} still hangs
     * the client on "Loading resource packs".
     */
    private static boolean cdnReachable(String cdn) {
        if (cdn == null || cdn.isBlank()) {
            return false;
        }
        if (httpOkBedrockPack(cdn)) {
            return true;
        }
        String file = fileNameFromCdn(cdn);
        if (file == null) {
            return false;
        }
        // Hairpin: public hostname often times out from origin; chassis :8081 is authoritative.
        if (httpOkBedrockPack("http://127.0.0.1:" + LAN_PACK_HTTP_PORT + "/pack/" + file)) {
            // Only accept hairpin if the advertised URL is also on :8081 (same bytes/mime).
            // Public :80 may still be octet-stream even when local file exists.
            if (cdn.contains(":" + LAN_PACK_HTTP_PORT + "/") || cdn.contains(":" + LAN_PACK_HTTP_PORT + "?")) {
                LOG.info("BE pack CDN local :8081 OK — offering " + cdn);
                return true;
            }
        }
        LOG.info("BE pack CDN probe failed for " + cdn);
        return false;
    }

    private static String fileNameFromCdn(String cdn) {
        int slash = cdn.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= cdn.length()) {
            return null;
        }
        return cdn.substring(slash + 1);
    }

    private static boolean httpOkBedrockPack(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(1_500);
            conn.setReadTimeout(1_500);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            String type = conn.getContentType();
            if (code == java.net.HttpURLConnection.HTTP_BAD_METHOD
                    || code == java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED
                    || type == null || type.isBlank()) {
                conn.disconnect();
                conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                conn.setConnectTimeout(1_500);
                conn.setReadTimeout(1_500);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-0");
                code = conn.getResponseCode();
                type = conn.getContentType();
            }
            if (code < 200 || code >= 400) {
                return false;
            }
            String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
            // Chassis serves application/zip; some proxies use application/x-zip-compressed.
            return t.contains("zip");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static ResourcePacksInfoPacket infoPacket(Offer offer, boolean forced) {
        ResourcePacksInfoPacket packet = new ResourcePacksInfoPacket();
        packet.setForcedToAccept(forced);
        packet.setHasAddonPacks(false);
        packet.setScriptingEnabled(false);
        packet.setForcingServerPacksEnabled(forced);
        packet.setVibrantVisualsForceDisabled(false);
        packet.setWorldTemplateId(new UUID(0L, 0L));
        packet.setWorldTemplateVersion("");
        ResourcePacksInfoPacket.Entry entry = new ResourcePacksInfoPacket.Entry(
                offer.packId(),
                offer.version(),
                offer.sizeBytes(),
                "",
                "",
                offer.packId().toString(),
                false,
                false,
                false,
                offer.cdnUrl());
        packet.getResourcePackInfos().add(entry);
        return packet;
    }

    static ResourcePackStackPacket stackPacket(Offer offer, boolean forced) {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.setForcedToAccept(forced);
        packet.setGameVersion("*");
        packet.setExperimentsPreviouslyToggled(false);
        packet.setHasEditorPacks(false);
        packet.getResourcePacks().add(new ResourcePackStackPacket.Entry(
                offer.packId().toString(), offer.version(), ""));
        return packet;
    }

    private static Path findMcpack(Path linkHome) {
        if (linkHome == null) {
            return null;
        }
        // Prefer full default (same product as JE Faithful pack), then slim portals.
        Path[] candidates = {
                linkHome.resolve(PACK_FILE),
                linkHome.getParent() != null
                        ? linkHome.getParent().resolve("resourcepacks").resolve(PACK_FILE)
                        : null,
                linkHome.resolve("resourcepacks").resolve(PACK_FILE),
                linkHome.resolve(PORTALS_PACK_FILE),
                linkHome.getParent() != null
                        ? linkHome.getParent().resolve("resourcepacks").resolve(PORTALS_PACK_FILE)
                        : null,
                linkHome.resolve("resourcepacks").resolve(PORTALS_PACK_FILE),
        };
        Path best = null;
        long bestScore = Long.MIN_VALUE;
        for (Path p : candidates) {
            if (p == null || !Files.isRegularFile(p)) {
                continue;
            }
            try {
                long size = Files.size(p);
                if (size <= 0 || size > MAX_JOIN_PACK_BYTES) {
                    continue;
                }
                // Prefer the full default when present; otherwise largest under cap.
                long score = p.getFileName().toString().equals(PACK_FILE)
                        ? 1_000_000_000L + size
                        : size;
                if (score > bestScore) {
                    best = p.toAbsolutePath().normalize();
                    bestScore = score;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return best;
    }

    private static String cdnUrl(
            BedrockSessionHost host, BedrockSessionHost.ClientState state, String fileName) {
        java.net.InetAddress peerAddr = null;
        try {
            if (state != null && state.peer != null && state.peer.address() != null) {
                peerAddr = state.peer.address().getAddress();
            }
        } catch (Exception ignored) {
            // fall through
        }
        // LAN peers: same path as Folia JE — http://<matching-LAN-IP>:8081/pack/<file>
        // (application/zip). Do NOT use nginx :80 for .mcpack (octet-stream → hang).
        if (peerAddr != null && isSiteLocal(peerAddr)) {
            String local = guessLanIpv4ForPeer(peerAddr);
            if (local != null) {
                return "http://" + local + ":" + LAN_PACK_HTTP_PORT + "/pack/" + fileName;
            }
        }
        String hostName = host.config.transferHost();
        if (hostName == null || hostName.isBlank()) {
            hostName = "127.0.0.1";
        }
        // Public: prefer chassis :8081 on the transfer host only when packCdnPort says so;
        // default :80 is wrong mime for mcpack — probe will skip unless operator fixed nginx.
        int port = Math.max(1, host.config.packCdnPort());
        // Force chassis port when config still says 80 — Bedrock needs zip Content-Type.
        // Operators with a correct nginx mime can set -Dyap.link.bedrock.packCdnUseNginx=true.
        if (port == 80 && !Boolean.parseBoolean(
                System.getProperty("yap.link.bedrock.packCdnUseNginx", "false"))) {
            port = LAN_PACK_HTTP_PORT;
        }
        if (port == 443) {
            return "https://" + hostName.trim() + "/pack/" + fileName;
        }
        if (port == 80) {
            return "http://" + hostName.trim() + "/pack/" + fileName;
        }
        return "http://" + hostName.trim() + ":" + port + "/pack/" + fileName;
    }

    private static boolean isSiteLocal(java.net.InetAddress addr) {
        return addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress();
    }

    /** Prefer a host NIC on the same /24 as the Bedrock peer (Java lobby uses 10.0.0.215). */
    private static String guessLanIpv4ForPeer(java.net.InetAddress peer) {
        byte[] peerOctets = peer != null ? peer.getAddress() : null;
        String sameSubnet = null;
        String any = null;
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (!(a instanceof java.net.Inet4Address) || !a.isSiteLocalAddress()) {
                        continue;
                    }
                    String host = a.getHostAddress();
                    if (any == null) {
                        any = host;
                    }
                    byte[] local = a.getAddress();
                    if (peerOctets != null && peerOctets.length == 4 && local.length == 4
                            && peerOctets[0] == local[0]
                            && peerOctets[1] == local[1]
                            && peerOctets[2] == local[2]) {
                        sameSubnet = host;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return sameSubnet != null ? sameSubnet : any;
    }

    private static Manifest readManifest(Path mcpack) throws Exception {
        try (ZipFile zip = new ZipFile(mcpack.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                throw new IllegalStateException("no manifest.json in " + mcpack.getFileName());
            }
            String json;
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher um = UUID_RE.matcher(json);
            if (!um.find()) {
                throw new IllegalStateException("manifest missing uuid");
            }
            UUID uuid = UUID.fromString(um.group(1).toLowerCase(Locale.ROOT));
            Matcher vm = VER_RE.matcher(json);
            String ver = "1.0.0";
            if (vm.find()) {
                ver = vm.group(1) + "." + vm.group(2) + "." + vm.group(3);
            }
            return new Manifest(uuid, ver);
        }
    }

    private record Manifest(UUID uuid, String version) {
    }
}
