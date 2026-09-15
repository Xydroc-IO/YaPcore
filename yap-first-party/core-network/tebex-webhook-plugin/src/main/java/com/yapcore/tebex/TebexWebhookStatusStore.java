package com.yapcore.tebex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists last webhook handling status for dashboard snapshots. */
public final class TebexWebhookStatusStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private volatile Map<String, Object> last = Map.of();

    public TebexWebhookStatusStore(Path dataFolder) {
        this.file = dataFolder.resolve("last-status.json");
        load();
    }

    public Map<String, Object> snapshot() {
        return last;
    }

    public synchronized void record(String type, String detail, boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", Instant.now().toString());
        m.put("type", type == null ? "" : type);
        m.put("ok", ok);
        m.put("detail", detail == null ? "" : detail);
        last = Map.copyOf(m);
        try {
            Files.writeString(file, GSON.toJson(m));
        } catch (IOException ignored) {
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = GSON.fromJson(Files.readString(file), Map.class);
            if (parsed != null) {
                last = Map.copyOf(new LinkedHashMap<>(parsed));
            }
        } catch (Exception ignored) {
        }
    }
}
