package com.yapcore.crossplay;

import com.yapcore.client.ClientEdition;
import com.yaplabs.yapengine.YapEngine;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Geyser-style translator: Bedrock (and Java) client intents → shared engine ops.
 * Full RakNet/JE packet codecs plug into {@link #translate}; high-level actions
 * already mutate the same spatial world for both editions.
 */
public final class GeyserStyleTranslator {

    private static final Logger LOG = Logger.getLogger("YaPcore.GeyserXlate");

    private final AtomicLong translated = new AtomicLong();

    public void onJoin(UnifiedPlayer player) {
        LOG.info("Translator attach " + player.getUsername()
                + " protocol-lane=" + (player.getEdition() == ClientEdition.BEDROCK
                ? "Bedrock→Engine" : "Java→Engine"));
    }

    public void onLeave(UnifiedPlayer player) {
        LOG.fine("Translator detach " + player.getUsername());
    }

    public void translate(UnifiedPlayer player,
                          String action,
                          Map<String, String> payload,
                          YapEngine engine) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(engine);
        String act = action == null ? "" : action.trim().toUpperCase();
        translated.incrementAndGet();
        switch (act) {
            case "MOVE", "POS" -> {
                int x = parse(payload.get("x"), player.getBlockX());
                int y = parse(payload.get("y"), player.getBlockY());
                int z = parse(payload.get("z"), player.getBlockZ());
                player.setPosition(x, y, z);
                engine.gameCore().getPartition().registerEntity(player.getUsername(), x, z);
                engine.trafficCop().ingest("MOVE", player.getUsername(), Map.of(
                        "x", Integer.toString(x),
                        "z", Integer.toString(z),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
                LOG.fine(() -> "Xlate MOVE " + player.getUsername()
                        + " @" + x + "," + y + "," + z
                        + " [" + player.getEdition() + "]");
            }
            case "CHAT", "MESSAGE" -> {
                String msg = payload.getOrDefault("msg", payload.getOrDefault("text", ""));
                engine.trafficCop().ingest("CHAT", player.getUsername(), Map.of(
                        "text", msg,
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
            }
            case "INTERACT", "CLICK" -> {
                engine.trafficCop().ingest("GUI_CLICK", player.getUsername(), Map.of(
                        "item", payload.getOrDefault("item", "air"),
                        "x", Integer.toString(player.getBlockX()),
                        "z", Integer.toString(player.getBlockZ()),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
            }
            default -> LOG.fine("Xlate passthrough " + act + " from " + player.getEdition());
        }
    }

    public long translatedCount() {
        return translated.get();
    }

    private static int parse(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
