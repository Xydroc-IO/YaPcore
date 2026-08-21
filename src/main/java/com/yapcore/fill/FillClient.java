package com.yapcore.fill;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PaperMC Fill v3 — resolve server jar URL for {@code paper}.
 */
public final class FillClient {

    private static final String USER_AGENT = "YaPcore/Fill (+https://github.com/yaplabs)";
    private static final Pattern STABLE_URL = Pattern.compile(
            "\"channel\"\\s*:\\s*\"STABLE\"[\\s\\S]*?\"server:default\"\\s*:\\s*\\{[\\s\\S]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BETA_URL = Pattern.compile(
            "\"channel\"\\s*:\\s*\"BETA\"[\\s\\S]*?\"server:default\"\\s*:\\s*\\{[\\s\\S]*?\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ANY_URL = Pattern.compile(
            "\"server:default\"\\s*:\\s*\\{[\\s\\S]*?\"url\"\\s*:\\s*\"([^\"]+)\"");

    private FillClient() {
    }

    public static String latestServerJarUrl(String project, String minecraftVersion) throws IOException {
        String json = get("https://fill.papermc.io/v3/projects/" + project + "/versions/"
                + minecraftVersion + "/builds");
        Matcher stable = STABLE_URL.matcher(json);
        if (stable.find()) {
            return stable.group(1);
        }
        Matcher beta = BETA_URL.matcher(json);
        if (beta.find()) {
            return beta.group(1);
        }
        Matcher any = ANY_URL.matcher(json);
        if (any.find()) {
            return any.group(1);
        }
        throw new IOException("No " + project + " server:default download for version " + minecraftVersion);
    }

    public static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            throw new IOException("HTTP " + code + " empty body for " + url);
        }
        try (in) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) {
                throw new IOException("HTTP " + code + " from " + url + ": " + body);
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }
}
