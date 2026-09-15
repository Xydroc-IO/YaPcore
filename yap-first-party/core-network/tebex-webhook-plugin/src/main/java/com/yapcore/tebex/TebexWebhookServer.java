package com.yapcore.tebex;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Localhost HTTP receiver for Tebex webhooks ({@code POST /tebex/webhook}).
 */
public final class TebexWebhookServer {

    private final TebexWebhookPlugin plugin;
    private HttpServer server;

    public TebexWebhookServer(TebexWebhookPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(TebexWebhookConfig config) {
        stop();
        if (!config.inboundEnabled()) {
            return;
        }
        if (config.inboundSecretUnsafe()) {
            logger().warning("Tebex webhook not started: set inbound.secret to a real value "
                    + "(not blank / change-me) when inbound.enabled is true.");
            return;
        }
        try {
            InetSocketAddress addr = new InetSocketAddress(config.inboundBind(), config.inboundPort());
            server = HttpServer.create(addr, 0);
            server.createContext(config.inboundPath(), this::handle);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "YaPTebex-Webhook");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            logger().info("Tebex webhook listening on "
                    + config.inboundBind() + ":" + config.inboundPort() + config.inboundPath());
        } catch (IOException e) {
            logger().warning("Tebex webhook server failed: " + e.getMessage());
            plugin.statusStore().record("start", e.getMessage(), false);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    private void handle(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        TebexWebhookConfig config = plugin.config();
        if (config == null || !config.inboundEnabled()) {
            respond(ex, 503, "{\"error\":\"webhook disabled\"}");
            return;
        }
        if (config.inboundSecretUnsafe()) {
            respond(ex, 503, "{\"error\":\"webhook secret not configured\"}");
            return;
        }
        if (config.enforceTebexIps() && !ipAllowed(ex)) {
            respond(ex, 404, "{\"error\":\"not found\"}");
            return;
        }

        byte[] raw = readBodyLimited(ex.getRequestBody(), config.maxBodyBytes());
        if (raw == null) {
            respond(ex, 413, "{\"error\":\"body too large\"}");
            return;
        }
        String signature = ex.getRequestHeaders().getFirst("X-Signature");
        if (!TebexWebhookSignature.verify(raw, config.inboundSecret(), signature)) {
            plugin.statusStore().record("auth", "invalid signature", false);
            respond(ex, 401, "{\"error\":\"unauthorized\"}");
            return;
        }

        String body = new String(raw, StandardCharsets.UTF_8);
        TebexWebhookPayload.Parsed parsed;
        try {
            parsed = TebexWebhookPayload.parse(body);
        } catch (Exception e) {
            plugin.statusStore().record("parse", e.getMessage(), false);
            respond(ex, 400, "{\"error\":\"invalid json\"}");
            return;
        }

        String type = parsed.type() == null ? "" : parsed.type();
        if ("validation.webhook".equals(type)) {
            String json = TebexWebhookPayload.validationResponseJson(parsed.id());
            plugin.statusStore().record(type, "validated " + parsed.id(), true);
            respond(ex, 200, json);
            return;
        }

        if (!"payment.completed".equals(type)) {
            plugin.statusStore().record(type, "ignored", true);
            respond(ex, 200, "{\"ok\":true,\"ignored\":true}");
            return;
        }

        if (parsed.id() != null && !parsed.id().isBlank()
                && !plugin.dedupe().tryMark("webhook", parsed.id())) {
            plugin.statusStore().record(type, "duplicate webhook " + parsed.id(), true);
            respond(ex, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        if (parsed.transactionId() != null && !parsed.transactionId().isBlank()
                && !plugin.dedupe().tryMark("tx", parsed.transactionId())) {
            plugin.statusStore().record(type, "duplicate tx " + parsed.transactionId(), true);
            respond(ex, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }

        List<String> toRun = new ArrayList<>();
        int mapped = 0;
        for (TebexWebhookPayload.Product product : parsed.products()) {
            List<String> templates = config.commandsForPackage(product.id());
            if (templates.isEmpty()) {
                continue;
            }
            mapped++;
            String username = product.username();
            if (username == null || username.isBlank()) {
                username = parsed.username();
            }
            toRun.addAll(TebexWebhookCommands.substitute(
                    templates, username, parsed.transactionId(), product.id()));
        }

        if (mapped == 0) {
            logger().info("Tebex payment.completed with no mapped packages (tx="
                    + parsed.transactionId() + ")");
            plugin.statusStore().record(type, "no mapped packages", true);
            respond(ex, 200, "{\"ok\":true,\"mapped\":0}");
            return;
        }

        final List<String> commands = List.copyOf(toRun);
        YapSched.global(plugin, () -> {
            for (String cmd : commands) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    logger().info("Tebex webhook ran: " + cmd);
                } catch (Exception e) {
                    logger().warning("Tebex command failed: " + cmd + " — " + e.getMessage());
                }
            }
        });

        plugin.statusStore().record(type,
                "dispatched " + commands.size() + " cmds for tx " + parsed.transactionId(), true);
        respond(ex, 200, "{\"ok\":true,\"commands\":" + commands.size() + "}");
    }

    private boolean ipAllowed(HttpExchange ex) {
        InetSocketAddress remote = ex.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        String host = remote.getAddress().getHostAddress();
        if (host == null) {
            return false;
        }
        // Strip IPv6-mapped IPv4 prefix if present
        if (host.startsWith("::ffff:")) {
            host = host.substring("::ffff:".length());
        }
        return TebexWebhookConfig.TEBEX_SOURCE_IPS.contains(host);
    }

    /** @return body bytes, or null if over max */
    private static byte[] readBodyLimited(InputStream in, int maxBytes) throws IOException {
        byte[] buf = in.readNBytes(maxBytes + 1);
        if (buf.length > maxBytes) {
            return null;
        }
        return buf;
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
