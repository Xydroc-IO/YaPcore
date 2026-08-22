package com.yapcore.link.discord;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.PluginMessageEvent;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;

/** Proxy-side moderation webhook relay via {@code yap:mod} plugin messages from backends. */
public final class DiscordProxyPlugin implements LinkPlugin {

    public static final ChannelIdentifier CHANNEL = ChannelIdentifier.of("yap", "mod");

    private LinkProxy proxy;
    private Logger logger;
    private Path dataDirectory;
    private String moderationWebhook = "";
    private HttpClient client;

    @Override
    public void onLoad(LinkPluginContext context) {
        this.proxy = context.proxy();
        this.logger = context.logger();
        this.dataDirectory = context.dataDirectory();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void onEnable() {
        proxy.registerChannel(CHANNEL);
        reloadConfig();
        logger.info("YaP Link Discord proxy ready — mod webhook="
                + (!moderationWebhook.isBlank()));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.channel().equals(CHANNEL)) {
            return;
        }
        if (event.sourceKind() != PluginMessageEvent.SourceKind.BACKEND) {
            return;
        }
        if (moderationWebhook.isBlank()) {
            return;
        }
        event.setResult(PluginMessageEvent.Result.HANDLED);
        String payload = new String(event.data(), StandardCharsets.UTF_8);
        if (!payload.startsWith("MOD|")) {
            return;
        }
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) {
            return;
        }
        String title = parts[1];
        String body = parts[2];
        int color;
        try {
            color = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            color = 0x95A5A6;
        }
        postEmbed(title, body, color);
    }

    private void reloadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.properties");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }
            Properties props = new Properties();
            if (Files.exists(configFile)) {
                try (InputStream in = Files.newInputStream(configFile)) {
                    props.load(in);
                }
            }
            moderationWebhook = props.getProperty("moderation-webhook", "").trim();
        } catch (Exception e) {
            logger.warning("Discord proxy config load failed: " + e.getMessage());
        }
    }

    private void postEmbed(String title, String description, int color) {
        String json = "{\"embeds\":[{\"title\":" + quote(escape(title))
                + ",\"description\":" + quote(escape(description))
                + ",\"color\":" + color + "}],\"allowed_mentions\":{\"parse\":[]}}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(moderationWebhook))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                logger.warning("Discord proxy webhook HTTP " + resp.statusCode());
            }
        } catch (Exception e) {
            logger.warning("Discord proxy webhook failed: " + e.getMessage());
        }
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
