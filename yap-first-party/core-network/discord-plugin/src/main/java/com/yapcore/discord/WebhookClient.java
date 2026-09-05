package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/** Async Discord webhook POST (no bot token). */
public final class WebhookClient {

    private static final Pattern WEBHOOK_URL = Pattern.compile(
            "^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+/?$",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MS = 500L;

    private final JavaPlugin plugin;
    private final HttpClient client;

    public WebhookClient(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendEmbed(String webhookUrl, String title, String description, int color) {
        if (!isValidWebhookUrl(webhookUrl)) {
            return;
        }
        String json = buildEmbedJson(title, description, color);
        YapSched.async(plugin, () -> postWithRetry(webhookUrl.trim(), json));
    }

    public void sendPlain(String webhookUrl, String content) {
        if (!isValidWebhookUrl(webhookUrl) || content == null || content.isBlank()) {
            return;
        }
        String json = "{\"content\":" + quote(escape(content)) + ",\"allowed_mentions\":{\"parse\":[]}}";
        YapSched.async(plugin, () -> postWithRetry(webhookUrl.trim(), json));
    }

    public static boolean isValidWebhookUrl(String webhookUrl) {
        return webhookUrl != null && !webhookUrl.isBlank() && WEBHOOK_URL.matcher(webhookUrl.trim()).matches();
    }

    private void postWithRetry(String url, String json) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return;
                }
                if ((code == 429 || code >= 500) && attempt < MAX_ATTEMPTS) {
                    long sleepMs = backoffMs(attempt, resp);
                    plugin.getLogger().fine("Discord webhook HTTP " + code + " — retry " + attempt
                            + " after " + sleepMs + "ms");
                    Thread.sleep(sleepMs);
                    continue;
                }
                plugin.getLogger().warning("Discord webhook HTTP " + code + ": " + truncate(resp.body()));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BASE_BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
            }
        }
    }

    private static long backoffMs(int attempt, HttpResponse<String> resp) {
        String retryAfter = resp.headers().firstValue("Retry-After").orElse("");
        if (!retryAfter.isBlank()) {
            try {
                double seconds = Double.parseDouble(retryAfter.trim());
                return Math.min(30_000L, Math.max(BASE_BACKOFF_MS, (long) (seconds * 1000L)));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Math.min(8_000L, BASE_BACKOFF_MS * (1L << (attempt - 1)));
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    private static String buildEmbedJson(String title, String description, int color) {
        return "{\"embeds\":[{\"title\":" + quote(escape(title))
                + ",\"description\":" + quote(escape(description))
                + ",\"color\":" + color + "}],\"allowed_mentions\":{\"parse\":[]}}";
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
