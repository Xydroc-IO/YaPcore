package com.yapcore.web;

import com.yapcore.config.ServerConfig;
import com.yapcore.network.publicity.PublicEndpoint;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.logging.Logger;

/** Resolves admin web dashboard URLs for operators (GUI, console, docs). */
public final class DashboardAccessInfo {

    private static final Logger LOG = Logger.getLogger("YaPcore.WebDash");

    public record AccessLink(
            boolean enabled,
            String localLoginUrl,
            String lanLoginUrl,
            String token,
            String hint
    ) {
        public String primaryLoginUrl() {
            if (lanLoginUrl != null && !lanLoginUrl.isBlank()) {
                return lanLoginUrl;
            }
            return localLoginUrl;
        }
    }

    private DashboardAccessInfo() {
    }

    public static AccessLink resolve(ServerConfig cfg) {
        if (!cfg.isWebDashboardEnabled()) {
            return new AccessLink(false, "", "", "",
                    "Web dashboard disabled — set web-dashboard-enabled=true in config/server.properties");
        }
        try {
            ensureToken(cfg);
        } catch (IOException e) {
            return new AccessLink(false, "", "", "",
                    "Could not read dashboard token: " + e.getMessage());
        }
        String token = cfg.getWebDashboardToken();
        int port = cfg.getWebDashboardPort();
        String localBase = "http://127.0.0.1:" + port + "/";
        String localLogin = localBase + "?token=" + token;
        String lanLogin = "";
        if (!cfg.isWebDashboardLocalhostOnly()) {
            String host = resolveAdvertisedHost(cfg);
            if (!"127.0.0.1".equals(host)) {
                lanLogin = "http://" + host + ":" + port + "/?token=" + token;
            }
        }
        return new AccessLink(true, localLogin, lanLogin, token,
                "Paste token at login if the link does not open signed in.");
    }

    public static String formatConsole(ServerConfig cfg) {
        AccessLink link = resolve(cfg);
        if (!link.enabled()) {
            return link.hint();
        }
        StringBuilder sb = new StringBuilder("Web admin dashboard\n");
        sb.append("  open:  ").append(link.localLoginUrl()).append('\n');
        if (link.lanLoginUrl() != null && !link.lanLoginUrl().isBlank()) {
            sb.append("  lan:   ").append(link.lanLoginUrl()).append('\n');
        }
        sb.append("  token: ").append(link.token()).append('\n');
        sb.append("  (also config/server.properties → web-dashboard-token)");
        return sb.toString();
    }

    public static void ensureToken(ServerConfig cfg) throws IOException {
        String token = cfg.getWebDashboardToken();
        if (token != null && !token.isBlank()) {
            return;
        }
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        token = HexFormat.of().formatHex(raw);
        cfg.setWebDashboardToken(token);
        cfg.save();
        LOG.warning("Generated web-dashboard-token and saved to config/server.properties");
    }

    public static boolean openInBrowser(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI.create(url));
                return true;
            }
        } catch (Exception e) {
            LOG.fine("Could not open browser: " + e.getMessage());
        }
        return false;
    }

    public static void copyToClipboard(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.trim()), null);
    }

    private static String resolveAdvertisedHost(ServerConfig cfg) {
        String bind = cfg.getWebDashboardBind();
        if (bind != null && !bind.isBlank() && !"0.0.0.0".equals(bind)) {
            return bind;
        }
        return PublicEndpoint.guessLocalIpv4().orElse("127.0.0.1");
    }
}
