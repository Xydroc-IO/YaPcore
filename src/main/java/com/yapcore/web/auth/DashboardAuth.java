package com.yapcore.web.auth;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.config.ServerConfig;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Logger;

public final class DashboardAuth {

    private static final Logger LOG = Logger.getLogger("YaPcore.WebDash");

    private String token;

    public String getToken() {
        return token;
    }

    public void ensureToken(ServerConfig cfg) throws IOException {
        token = cfg.getWebDashboardToken();
        if (token == null || token.isBlank()) {
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            token = HexFormat.of().formatHex(raw);
            cfg.setWebDashboardToken(token);
            cfg.save();
            LOG.warning("Generated web-dashboard-token and saved to config/server.properties");
        }
    }

    public void setToken(String newToken) {
        this.token = newToken == null ? "" : newToken.trim();
    }

    public boolean authorized(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.equals(auth.substring(7).trim());
        }
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && "token".equals(part.substring(0, eq))) {
                    return token.equals(URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String p = part.trim();
                if (p.startsWith("yap_token=")) {
                    return token.equals(p.substring("yap_token=".length()));
                }
            }
        }
        return false;
    }

    public boolean requireAuth(HttpExchange ex) throws IOException {
        if (authorized(ex)) {
            return true;
        }
        DashboardHttp.json(ex, 401, Map.of("error", "unauthorized", "hint", "Send Authorization: Bearer <token>"));
        return false;
    }
}
