package com.yapcore.discord;

import com.yapcore.sched.YapSched;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Async Discord webhook POST (no bot token). */
public final class WebhookClient {

    private final JavaPlugin plugin;
    private final HttpClient client;

    public WebhookClient(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendEmbed(String webhookUrl, String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String json = buildEmbedJson(title, description, color);
        YapSched.async(plugin, () -> post(webhookUrl, json));
    }

    public void sendPlain(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isBlank() || content == null || content.isBlank()) {
            return;
        }
        String json = "{\"content\":" + quote(escape(content)) + ",\"allowed_mentions\":{\"parse\":[]}}";
        YapSched.async(plugin, () -> post(webhookUrl, json));
    }

    private void post(String url, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                plugin.getLogger().warning("Discord webhook HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
        }
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
