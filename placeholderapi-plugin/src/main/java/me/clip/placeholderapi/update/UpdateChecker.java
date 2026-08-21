package me.clip.placeholderapi.update;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.util.Msg;

/**
 * Optional update check. Default URL empty → logs YaPcore release guidance only.
 */
public final class UpdateChecker {

    private final PlaceholderAPIPlugin plugin;

    public UpdateChecker(PlaceholderAPIPlugin plugin) {
        this.plugin = plugin;
    }

    public void fetch() {
        String url = plugin.getConfig().getString("update-check-url", "");
        if (url == null || url.isBlank()) {
            Msg.info("YaP PlaceholderAPI %s — updates ship with YaPcore releases.",
                    plugin.getDescription().getVersion());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "YaP-PlaceholderAPI");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String remote = reader.readLine();
                    if (remote != null && !remote.isBlank()
                            && !remote.trim().equalsIgnoreCase(plugin.getDescription().getVersion())) {
                        Msg.warn("Update available: remote=%s local=%s",
                                remote.trim(), plugin.getDescription().getVersion());
                    } else {
                        Msg.info("PlaceholderAPI is up to date (%s).", plugin.getDescription().getVersion());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Update check failed", e);
            }
        });
    }
}
