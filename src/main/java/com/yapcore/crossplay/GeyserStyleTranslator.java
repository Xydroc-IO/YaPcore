package com.yapcore.crossplay;

import com.yapcore.client.ClientEdition;
import com.yapcore.crossplay.bedrock.BedrockPaperWorldSync;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import com.yaplabs.yapengine.YapEngine;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * First-party Geyser parity translator: Bedrock (and Java) intents → shared world.
 * <p>
 * Phase 4 DoD: full Geyser (+ Floodgate-class auth) feature parity in YaP code —
 * not the Geyser jar. See {@code docs/PHASE4_PROTOCOL.md}.
 * <p>
 * When {@code game-authority=paper}, BREAK/PLACE also hit Paper via
 * {@link com.yapcore.crossplay.bedrock.BedrockPaperWorldSync} (BE still joins
 * through DualStack/YapEngine for roster; world mutation is Paper-backed).
 */
public final class GeyserStyleTranslator {

    private static final Logger LOG = Logger.getLogger("YaPcore.GeyserXlate");

    private final AtomicLong translated = new AtomicLong();
    private volatile FloodgateAuth floodgate;
    private volatile SkinService skins;
    private volatile FormService forms;
    private volatile BedrockPaperWorldSync paperWorld;

    public void attachUx(FloodgateAuth floodgate, SkinService skins, FormService forms) {
        this.floodgate = floodgate;
        this.skins = skins;
        this.forms = forms;
    }

    public void attachPaperWorld(BedrockPaperWorldSync paperWorld) {
        this.paperWorld = paperWorld;
        if (paperWorld != null && paperWorld.isEnabled()) {
            LOG.info("Translator Paper world sync enabled (BE BREAK/PLACE → Paper)");
        }
    }

    public BedrockPaperWorldSync paperWorld() {
        return paperWorld;
    }

    public void onJoin(UnifiedPlayer player) {
        LOG.info("Translator attach " + player.getUsername()
                + " protocol-lane=" + (player.getEdition() == ClientEdition.BEDROCK
                ? "Bedrock→Engine" : "Java→Engine")
                + (player.getLinkedUuid() != null ? " uuid=" + player.getLinkedUuid() : ""));
        if (forms != null && player.getEdition() == ClientEdition.BEDROCK) {
            forms.sendSimple(player.getUsername(), "YaPcore",
                    "Welcome to shared-world crossplay.", "Play");
        }
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
                // Slash chat from BE TEXT also runs on Paper (COMMAND_REQUEST is primary)
                if (msg.startsWith("/") && player.getEdition() == ClientEdition.BEDROCK) {
                    String result = com.yapcore.paper.PaperCommandBridge.dispatchToPaper(msg, null);
                    LOG.info("BE chat-command " + player.getUsername() + " → " + result);
                }
                engine.trafficCop().ingest("CHAT", player.getUsername(), Map.of(
                        "text", msg,
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
            }
            case "COMMAND" -> engine.trafficCop().ingest("COMMAND", player.getUsername(), Map.of(
                    "text", payload.getOrDefault("msg", ""),
                    "result", payload.getOrDefault("result", ""),
                    "edition", player.getEdition().name(),
                    "crossplay", "true"
            ));
            case "INTERACT", "CLICK" -> {
                engine.trafficCop().ingest("GUI_CLICK", player.getUsername(), Map.of(
                        "item", payload.getOrDefault("item", "air"),
                        "x", Integer.toString(player.getBlockX()),
                        "z", Integer.toString(player.getBlockZ()),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
            }
            case "OPEN_CONTAINER" -> {
                engine.trafficCop().ingest("OPEN_CONTAINER", player.getUsername(), Map.copyOf(payload));
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    Map<String, String> p = new java.util.HashMap<>(payload);
                    p.put("player", player.getUsername());
                    paperWorld.apply("OPEN_CONTAINER", p);
                }
            }
            case "CLOSE_CONTAINER" -> {
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    paperWorld.apply("CLOSE_CONTAINER", Map.of("player", player.getUsername()));
                }
            }
            case "BREAK" -> {
                engine.trafficCop().ingest("BLOCK_BREAK", player.getUsername(), Map.of(
                        "x", payload.getOrDefault("x", Integer.toString(player.getBlockX())),
                        "y", payload.getOrDefault("y", Integer.toString(player.getBlockY())),
                        "z", payload.getOrDefault("z", Integer.toString(player.getBlockZ())),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    paperWorld.apply("BREAK", payload);
                }
            }
            case "PLACE" -> {
                engine.trafficCop().ingest("BLOCK_PLACE", player.getUsername(), Map.of(
                        "x", payload.getOrDefault("x", Integer.toString(player.getBlockX())),
                        "y", payload.getOrDefault("y", Integer.toString(player.getBlockY())),
                        "z", payload.getOrDefault("z", Integer.toString(player.getBlockZ())),
                        "block", payload.getOrDefault("block", "stone"),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    paperWorld.apply("PLACE", payload);
                }
            }
            case "ATTACK" -> {
                engine.trafficCop().ingest("ATTACK", player.getUsername(), Map.of(
                        "target", payload.getOrDefault("target", ""),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    Map<String, String> atk = new java.util.HashMap<>(payload);
                    atk.put("attacker", player.getUsername());
                    paperWorld.apply("ATTACK", atk);
                }
            }
            case "INV", "HOTBAR" -> {
                engine.trafficCop().ingest("INVENTORY", player.getUsername(), Map.of(
                        "slot", payload.getOrDefault("slot", "0"),
                        "item", payload.getOrDefault("item", "air"),
                        "edition", player.getEdition().name(),
                        "crossplay", "true"
                ));
                if (player.getEdition() == ClientEdition.BEDROCK && paperWorld != null) {
                    Map<String, String> inv = new java.util.HashMap<>(payload);
                    inv.put("player", player.getUsername());
                    paperWorld.apply("INV", inv);
                }
            }
            case "FORM" -> {
                if (forms != null) {
                    forms.sendSimple(player.getUsername(),
                            payload.getOrDefault("title", "YaPcore"),
                            payload.getOrDefault("content", "Form"),
                            "OK", "Cancel");
                }
            }
            case "SKIN" -> {
                if (skins != null) {
                    LOG.info("Skin refresh request " + player.getUsername());
                }
            }
            case "LINK" -> {
                if (floodgate != null) {
                    floodgate.linkAccounts(
                            payload.getOrDefault("java", player.getUsername()),
                            payload.getOrDefault("bedrock", player.getUsername()));
                }
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
