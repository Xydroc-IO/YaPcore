package com.yapcore.tailor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies frozen Bedrock movement catalog constants to a JE/Folia player.
 * Values load from bundled {@code movement.v1.json} (same ids as chassis catalog).
 */
public final class ParityMovementApplier {

    private static final String RESOURCE = "parity/band_26_50/movement.v1.json";
    private static final Map<String, Double> VALUES = loadValues();

    public static final double SPEED = val("player.movement.speed", 0.1);
    public static final double SPRINT_MULT = val("player.movement.sprint_multiplier", 1.3);
    public static final double SNEAK_MULT = val("player.movement.sneak_multiplier", 0.3);
    public static final double JUMP = val("player.movement.jump_impulse", 0.42);
    public static final double GRAVITY = val("player.movement.gravity", 0.08);
    public static final double DRAG = val("player.movement.drag", 0.02);
    public static final double FLY = val("player.movement.fly_speed", 0.05);
    public static final double REACH_BLOCK = val("player.reach.block", 5.0);
    public static final double REACH_ENTITY = val("player.reach.entity", 3.0);
    public static final boolean FACE_ASSIST = val("player.placement.face_assist", 1.0) >= 0.5;
    public static final String BAND = "band_26_50";

    private ParityMovementApplier() {
    }

    public static String presencePayload() {
        return "MOVEMENT|"
                + SPEED + "|"
                + SPRINT_MULT + "|"
                + SNEAK_MULT + "|"
                + JUMP + "|"
                + GRAVITY + "|"
                + DRAG + "|"
                + FLY + "|"
                + REACH_BLOCK + "|"
                + REACH_ENTITY + "|"
                + (FACE_ASSIST ? "1" : "0") + "|"
                + BAND;
    }

    public static void apply(Player player, Logger log) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            setAttr(player, Attribute.MOVEMENT_SPEED, SPEED);
            setAttr(player, Attribute.JUMP_STRENGTH, JUMP);
            setAttr(player, Attribute.GRAVITY, GRAVITY);
            // Players do not use Attribute.FLYING_SPEED — creative fly is abilities.flyingSpeed
            // via setFlySpeed (CraftPlayer stores value/2). Do not pin FLYING_SPEED here.
            try {
                setAttr(player, Attribute.BLOCK_INTERACTION_RANGE, REACH_BLOCK);
                setAttr(player, Attribute.ENTITY_INTERACTION_RANGE, REACH_ENTITY);
            } catch (Throwable ignored) {
                // pre-1.20.5
            }
            // Catalog walk/fly are attribute / abilities scales; Bukkit walk/fly APIs are ×2.
            float walk = (float) Math.max(-1.0, Math.min(1.0, SPEED * 2.0));
            player.setWalkSpeed(walk);
            // Fly tracks effective MOVEMENT_SPEED (skills etc.). Client sprint-fly is still ×2
            // of abilities flyingSpeed — so the boost doubles whatever walk speed currently is.
            float fly = bukkitFlyFromMoveSpeed(player);
            player.setFlySpeed(fly);
        } catch (Exception e) {
            if (log != null) {
                log.log(Level.FINE, "Parity movement apply failed for " + player.getName(), e);
            }
        }
    }

    private static void setAttr(Player player, Attribute attr, double base) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(base);
        }
    }

    /**
     * Bukkit fly default is {@code FLY * 2} (vanilla 0.1). Scale by
     * {@code MOVEMENT_SPEED value / base} so skill walk buffs apply to flight too.
     */
    static float bukkitFlyFromMoveSpeed(Player player) {
        float vanillaFly = (float) Math.max(-1.0, Math.min(1.0, FLY * 2.0));
        AttributeInstance move = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (move == null) {
            return vanillaFly;
        }
        double base = move.getBaseValue();
        if (base <= 1.0e-9) {
            return vanillaFly;
        }
        double ratio = Math.max(0.0, move.getValue() / base);
        return (float) Math.max(-1.0, Math.min(1.0, vanillaFly * ratio));
    }

    private static double val(String id, double fallback) {
        Double v = VALUES.get(id);
        return v != null ? v : fallback;
    }

    private static Map<String, Double> loadValues() {
        Map<String, Double> map = new LinkedHashMap<>();
        try (InputStream in = ParityMovementApplier.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return map;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries == null) {
                return map;
            }
            for (JsonElement el : entries) {
                JsonObject e = el.getAsJsonObject();
                if (e.has("id") && e.has("value")) {
                    map.put(e.get("id").getAsString(), e.get("value").getAsDouble());
                }
            }
        } catch (Exception ignored) {
            // fall back to defaults in val()
        }
        return map;
    }
}
