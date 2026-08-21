package com.yapcore.network.publicity;

import com.yapcore.config.ServerConfig;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves how players reach this server over the internet / a custom domain.
 * Separates <em>bind</em> (local listen) from <em>advertise</em> (DNS / NAT ports).
 */
public final class PublicEndpoint {

    private final ServerConfig config;

    public PublicEndpoint(ServerConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public boolean isInternetExposed() {
        return config.isInternetExposed();
    }

    /**
     * Hostname or IP players type in the multiplayer screen.
     * Prefers {@code public-host} / {@code server-domain}, then pack host, then a local guess.
     */
    public String publicHost() {
        String configured = firstNonBlank(
                config.getPublicHost(),
                config.getServerDomain(),
                config.getResourcePackPublicHost());
        if (configured != null) {
            return stripScheme(configured);
        }
        return guessLocalIpv4().orElse("127.0.0.1");
    }

    public boolean hasConfiguredDomain() {
        String h = firstNonBlank(config.getPublicHost(), config.getServerDomain());
        return h != null && looksLikeDomain(h);
    }

    public int advertisedJavaPort() {
        int pub = config.getPublicPort();
        if (pub > 0) {
            return pub;
        }
        // Prefer nginx stream front when domain is configured
        if (hasNginxEdge()) {
            return config.getNginxPublicPort();
        }
        return config.getPort();
    }

    public int advertisedBedrockPort() {
        int pub = config.getPublicBedrockPort();
        if (pub > 0) {
            return pub;
        }
        if (hasNginxEdge()) {
            return config.getNginxPublicPort();
        }
        return config.effectiveBedrockPort();
    }

    /** Single join host:port when shared-listen-port is on. */
    public String crossplayJoinAddress() {
        return javaJoinAddress();
    }

    public boolean isSharedListenPort() {
        return config.isSharedListenPort();
    }

    /** Same-PC Direct Connect address (always the local bind port, not nginx). */
    public String localLoopbackJoin() {
        return "127.0.0.1:" + config.getPort();
    }

    /** Ensure same-machine clients can reach us. */
    public void applyLocalhostFriendlyBind() {
        if (!config.isAllowLocalhost()) {
            return;
        }
        // 0.0.0.0 accepts 127.0.0.1 + LAN + public; never bind-only to a public IP.
        String bind = config.getBindHost();
        if (bind == null || bind.isBlank()) {
            config.setBindHost("0.0.0.0");
            return;
        }
        if (!"0.0.0.0".equals(bind)
                && !"127.0.0.1".equals(bind)
                && !bind.contains(":")) {
            // Explicit NIC bind can break 127.0.0.1 — widen for same-PC play.
            config.setBindHost("0.0.0.0");
        }
    }

    public int advertisedPackPort() {
        int pub = config.getPublicPackPort();
        if (pub > 0) {
            return pub;
        }
        if (hasNginxEdge()) {
            // Cloudflare orange-cloud typically terminates TLS → nginx :80
            int nginx = config.getNginxPackPort();
            return nginx == 80 ? 443 : nginx;
        }
        return config.getResourcePackHttpPort();
    }

    /** Java Edition address for Direct Connect / server list. */
    public String javaJoinAddress() {
        return publicHost() + ":" + advertisedJavaPort();
    }

    /** Bedrock add-server style host:port (UDP). */
    public String bedrockJoinAddress() {
        return publicHost() + ":" + advertisedBedrockPort();
    }

    public String packBaseUrl() {
        String host = publicHost();
        int port = advertisedPackPort();
        boolean https = port == 443;
        String scheme = https ? "https" : "http";
        if (port == 80 || port == 443) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    public String packUrl(String fileName) {
        return packBaseUrl() + "/pack/" + fileName;
    }

    /** Pack URL reachable from a specific client (loopback → local HTTP, else public/CDN). */
    public String packUrlForClient(String fileName, java.net.InetSocketAddress client) {
        if (client != null && client.getAddress() != null && client.getAddress().isLoopbackAddress()) {
            return "http://127.0.0.1:" + config.getResourcePackHttpPort() + "/pack/" + fileName;
        }
        return packUrl(fileName);
    }

    private boolean hasNginxEdge() {
        String d = config.getNginxDomain();
        if (d != null && !d.isBlank() && !"_".equals(d)) {
            return true;
        }
        return hasConfiguredDomain();
    }

    /**
     * Example DNS SRV for Java clients connecting via domain only
     * ({@code play.example.com} without typing the port).
     */
    public String srvRecordExample() {
        String domain = firstNonBlank(config.getServerDomain(), config.getPublicHost());
        if (domain == null || !looksLikeDomain(domain)) {
            domain = "play.example.com";
        }
        domain = stripScheme(domain).toLowerCase(Locale.ROOT);
        String target = domain;
        int priority = config.getSrvPriority();
        int weight = config.getSrvWeight();
        return "_minecraft._tcp." + domain + ". 3600 IN SRV "
                + priority + " " + weight + " " + advertisedJavaPort() + " " + target + ".";
    }

    /** Ensure internet mode listens on all interfaces. */
    public void applyInternetBind() {
        if (!isInternetExposed()) {
            return;
        }
        String bind = config.getBindHost();
        if (bind == null || bind.isBlank()
                || "127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind)) {
            config.setBindHost("0.0.0.0");
        }
        // Prefer domain/public-host for pack URLs when pack host unset
        if (config.getResourcePackPublicHost() == null || config.getResourcePackPublicHost().isBlank()) {
            String pub = firstNonBlank(config.getPublicHost(), config.getServerDomain());
            if (pub != null) {
                config.setResourcePackPublicHost(stripScheme(pub));
            }
        }
    }

    public String banner() {
        StringBuilder sb = new StringBuilder();
        sb.append("Public endpoints\n");
        sb.append("  internet-exposed=").append(isInternetExposed()).append('\n');
        sb.append("  bind=").append(config.getBindHost())
                .append(" local-port=").append(config.getPort()).append('\n');
        sb.append("  public-host=").append(publicHost());
        if (hasConfiguredDomain()) {
            sb.append(" (domain)");
        }
        sb.append('\n');
        sb.append("  Same-PC         → ").append(localLoopbackJoin()).append('\n');
        sb.append("  Java Edition   → ").append(javaJoinAddress()).append(" (TCP)\n");
        if (isSharedListenPort()) {
            sb.append("  Bedrock        → ").append(bedrockJoinAddress())
                    .append(" (UDP, same port — streamlined crossplay)\n");
            sb.append("  Crossplay      → ").append(crossplayJoinAddress())
                    .append("  ← one address for both editions\n");
        } else {
            sb.append("  Bedrock        → ").append(bedrockJoinAddress()).append(" (UDP)\n");
        }
        sb.append("  Resource packs → ").append(packBaseUrl()).append("/pack/<file>\n");
        if (hasNginxEdge()) {
            sb.append("  nginx edge     → stream :").append(config.getNginxPublicPort())
                    .append(" → local :").append(config.getPort())
                    .append(" | packs HTTP :").append(config.getNginxPackPort())
                    .append(" → :").append(config.getResourcePackHttpPort()).append('\n');
        }
        if (config.isSrvEnabled() && hasConfiguredDomain()) {
            sb.append("  DNS SRV        → ").append(srvRecordExample()).append('\n');
            sb.append("  (players can add just the domain if SRV is published)\n");
        } else if (config.isSrvEnabled()) {
            sb.append("  DNS SRV tip    → ").append(srvRecordExample()).append('\n');
        }
        if (isInternetExposed()) {
            sb.append("  Forward TCP+UDP ").append(advertisedJavaPort())
                    .append(" (and TCP ").append(config.getNginxPackPort())
                    .append(" for packs) on your router/firewall to this host.\n");
        }
        return sb.toString().trim();
    }

    public static Optional<String> guessLocalIpv4() {
        List<String> found = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) {
                return Optional.empty();
            }
            for (NetworkInterface nic : Collections.list(nics)) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        found.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return found.stream().findFirst();
    }

    private static boolean looksLikeDomain(String host) {
        String h = stripScheme(host);
        if (h.indexOf(':') >= 0) {
            h = h.substring(0, h.indexOf(':'));
        }
        if (h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return false;
        }
        return h.contains(".") || h.equalsIgnoreCase("localhost");
    }

    private static String stripScheme(String host) {
        String h = host.trim();
        int scheme = h.indexOf("://");
        if (scheme >= 0) {
            h = h.substring(scheme + 3);
        }
        int slash = h.indexOf('/');
        if (slash >= 0) {
            h = h.substring(0, slash);
        }
        return h;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
