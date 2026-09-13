package com.yapcore.crossplay.bedrock.parity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed Bedrock movement constants from {@code catalogs/movement.v1.json}.
 * No magic numbers elsewhere — read from here.
 */
public final class MovementParityTable {

    public static final String ID_SPEED = "player.movement.speed";
    public static final String ID_SPRINT = "player.movement.sprint_multiplier";
    public static final String ID_SNEAK = "player.movement.sneak_multiplier";
    public static final String ID_JUMP = "player.movement.jump_impulse";
    public static final String ID_GRAVITY = "player.movement.gravity";
    public static final String ID_DRAG = "player.movement.drag";
    public static final String ID_FLY = "player.movement.fly_speed";
    public static final String ID_REACH_BLOCK = "player.reach.block";
    public static final String ID_REACH_ENTITY = "player.reach.entity";
    public static final String ID_FACE_ASSIST = "player.placement.face_assist";

    private final ParityBand band;
    private final Map<String, Double> values;

    private MovementParityTable(ParityBand band, Map<String, Double> values) {
        this.band = band;
        this.values = values;
    }

    public static MovementParityTable load(ParityBand band) {
        ParityCatalogs catalogs = ParityCatalogs.load(band);
        Map<String, Double> map = new LinkedHashMap<>();
        for (ParityCatalogs.CatalogEntry e : catalogs.movement().entries()) {
            Double v = e.number("value");
            if (v == null) {
                throw new IllegalStateException("Movement entry missing value: " + e.id());
            }
            map.put(e.id(), v);
        }
        require(map, ID_SPEED, ID_SPRINT, ID_SNEAK, ID_JUMP, ID_GRAVITY, ID_DRAG,
                ID_FLY, ID_REACH_BLOCK, ID_REACH_ENTITY, ID_FACE_ASSIST);
        return new MovementParityTable(band, Collections.unmodifiableMap(map));
    }

    public static MovementParityTable loadDefault() {
        return load(ParityBand.of(ParityBand.DEFAULT));
    }

    private static void require(Map<String, Double> map, String... ids) {
        for (String id : ids) {
            if (!map.containsKey(id)) {
                throw new IllegalStateException("Movement catalog missing " + id);
            }
        }
    }

    public ParityBand band() {
        return band;
    }

    public int size() {
        return values.size();
    }

    public Map<String, Double> all() {
        return values;
    }

    public double get(String id) {
        Double v = values.get(id);
        if (v == null) {
            throw new IllegalArgumentException("Unknown movement id: " + id);
        }
        return v;
    }

    public Optional<Double> find(String id) {
        return Optional.ofNullable(values.get(id));
    }

    public double speed() {
        return get(ID_SPEED);
    }

    public float speedF() {
        return (float) speed();
    }

    public double sprintMultiplier() {
        return get(ID_SPRINT);
    }

    public double sneakMultiplier() {
        return get(ID_SNEAK);
    }

    public double jumpImpulse() {
        return get(ID_JUMP);
    }

    public double gravity() {
        return get(ID_GRAVITY);
    }

    public double drag() {
        return get(ID_DRAG);
    }

    public double flySpeed() {
        return get(ID_FLY);
    }

    public float flySpeedF() {
        return (float) flySpeed();
    }

    public double reachBlock() {
        return get(ID_REACH_BLOCK);
    }

    public double reachEntity() {
        return get(ID_REACH_ENTITY);
    }

    public boolean faceAssist() {
        return get(ID_FACE_ASSIST) >= 0.5;
    }

    /**
     * Bukkit {@code Player#setWalkSpeed} uses ~0.2 as vanilla default for attribute 0.1.
     * Scale: walkSpeed = speed * 2 (clamped to Bukkit's ±1 range).
     */
    public float bukkitWalkSpeed() {
        return (float) Math.max(-1.0, Math.min(1.0, speed() * 2.0));
    }

    /** Bukkit fly speed: Bedrock abilities fly maps ~1:1 onto {@code setFlySpeed}. */
    public float bukkitFlySpeed() {
        return (float) Math.max(-1.0, Math.min(1.0, flySpeed()));
    }

    /** Pipe payload for {@code yap:presence} MOVEMENT message. */
    public String presencePayload() {
        return "MOVEMENT|"
                + speed() + "|"
                + sprintMultiplier() + "|"
                + sneakMultiplier() + "|"
                + jumpImpulse() + "|"
                + gravity() + "|"
                + drag() + "|"
                + flySpeed() + "|"
                + reachBlock() + "|"
                + reachEntity() + "|"
                + (faceAssist() ? "1" : "0") + "|"
                + band.id();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MovementParityTable t
                && band.equals(t.band)
                && values.equals(t.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(band, values);
    }
}
