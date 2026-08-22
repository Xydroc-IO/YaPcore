package com.yapcore.discord;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight HTTP inbound bridge for Discord→MC relay.
 * Point a Discord bot (or automation) at POST /discord/inbound with JSON
 * {@code {"author":"name","content":"message"}} and Authorization bearer secret.
 */
public final class DiscordInboundServer {

    private static final Pattern AUTHOR = Pattern.compile("\"author\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CONTENT = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern USERNAME = Pattern.compile("\"username\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final DiscordPlugin plugin;
    private HttpServer server;

    public DiscordInboundServer(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(DiscordConfig config) {
        stop();
        if (!config.discordToMc() || !config.inboundEnabled()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(config.inboundPort()), 0);
            server.createContext(config.inboundPath(), this::handle);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "YaPDiscord-Inbound");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            logger().info("Discord→MC inbound listening on :" + config.inboundPort() + config.inboundPath());
        } catch (IOException e) {
            logger().warning("Discord inbound server failed: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        DiscordConfig config = plugin.config();
        if (config == null || !config.discordToMc()) {
            respond(ex, 503, "{\"error\":\"relay disabled\"}");
            return;
        }
        if (!authorized(ex, config.inboundSecret())) {
            respond(ex, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String author = extract(body, AUTHOR);
        if (author.isBlank()) {
            author = extract(body, USERNAME);
        }
        String content = extract(body, CONTENT);
        if (content.isBlank()) {
            content = extract(body, MESSAGE);
        }
        if (content.isBlank()) {
            respond(ex, 400, "{\"error\":\"content required\"}");
            return;
        }
        if (author.isBlank()) {
            author = "Discord";
        }
        plugin.mcRelay().relay(unescape(author), unescape(content));
        respond(ex, 200, "{\"ok\":true}");
    }

    private static boolean authorized(HttpExchange ex, String secret) {
        if (secret == null || secret.isBlank() || "change-me".equals(secret)) {
            return false;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return secret.equals(auth.substring("Bearer ".length()).trim());
        }
        String header = ex.getRequestHeaders().getFirst("X-YaP-Discord-Secret");
        return secret.equals(header);
    }

    private static String extract(String body, Pattern pattern) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static String unescape(String raw) {
        return raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static void respond(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Logger logger() {
        return plugin.getLogger();
    }
}
