package com.yapcore.crossplay.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * JE Material / BlockData → Bedrock hashed block runtime ids (1.21.50).
 * <ul>
 *   <li>{@code block_runtime_ids.json} — Material defaultState</li>
 *   <li>{@code block_state_hashes.json} — full JE {@code getAsString()} via blocksJ2B</li>
 * </ul>
 */
public final class BedrockBlockRuntimeIds {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockBlocks");
    private static final String RESOURCE_MAT = "protocol/bedrock/1.21.50/block_runtime_ids.json";
    private static final String RESOURCE_STATE = "protocol/bedrock/1.21.50/block_state_hashes.json";

    private static volatile Map<String, Integer> BY_MATERIAL = Map.of();
    private static volatile Map<String, Integer> BY_JE_STATE = Map.of();

    private BedrockBlockRuntimeIds() {
    }

    public static void warm() {
        all();
        jeStates();
    }

    public static Map<String, Integer> all() {
        Map<String, Integer> local = BY_MATERIAL;
        if (!local.isEmpty()) {
            return local;
        }
        synchronized (BedrockBlockRuntimeIds.class) {
            if (BY_MATERIAL.isEmpty()) {
                BY_MATERIAL = loadMaterialMap();
            }
            return BY_MATERIAL;
        }
    }

    public static Map<String, Integer> jeStates() {
        Map<String, Integer> local = BY_JE_STATE;
        if (!local.isEmpty()) {
            return local;
        }
        synchronized (BedrockBlockRuntimeIds.class) {
            if (BY_JE_STATE.isEmpty()) {
                BY_JE_STATE = loadJeStateMap();
            }
            return BY_JE_STATE;
        }
    }

    /**
     * Prefer full JE block state string ({@code minecraft:oak_log[axis=y]});
     * fall back to Material defaultState.
     */
    public static int hashedForJeBlockData(String blockDataAsString, String materialFallback) {
        if (blockDataAsString != null && !blockDataAsString.isBlank()) {
            Integer hit = jeStates().get(normalizeJeState(blockDataAsString));
            if (hit != null) {
                return hit;
            }
            // Retry without namespace (some dumps omit minecraft:)
            String withNs = blockDataAsString.trim();
            if (!withNs.startsWith("minecraft:") && withNs.indexOf(':') < 0) {
                hit = jeStates().get(normalizeJeState("minecraft:" + withNs));
                if (hit != null) {
                    return hit;
                }
            }
        }
        return hashedForMaterial(materialFallback);
    }

    /**
     * Resolve Bukkit Material enum name / {@code minecraft:id} to hashed runtime id.
     * Unknown solids → stone; air-like → air.
     */
    public static int hashedForMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return BedrockPacketCodec.hashedAir();
        }
        String key = normalizeMaterial(materialName);
        Integer hit = all().get(key);
        if (hit != null) {
            return hit;
        }
        if (key.contains("AIR") || "STRUCTURE_VOID".equals(key)) {
            return BedrockPacketCodec.hashedAir();
        }
        String stripped = key
                .replace("WAXED_", "")
                .replace("_WALL_", "_")
                .replace("POTTED_", "");
        hit = all().get(stripped);
        if (hit != null) {
            return hit;
        }
        if (stripped.endsWith("_SLAB") || stripped.endsWith("_STAIRS") || stripped.endsWith("_WALL")) {
            String base = stripped.replace("_SLAB", "").replace("_STAIRS", "").replace("_WALL", "");
            hit = all().get(base);
            if (hit != null) {
                return hit;
            }
            hit = all().get(base + "S");
            if (hit != null) {
                return hit;
            }
        }
        return BedrockPacketCodec.hashedStone();
    }

    /** Canonical JE state key matching blocksJ2B / generator output. */
    static String normalizeJeState(String raw) {
        String s = raw.trim();
        // Bukkit sometimes emits "oak_log[axis=y]" without namespace
        if (!s.startsWith("minecraft:") && s.indexOf(':') < 0) {
            s = "minecraft:" + s;
        }
        // Empty props: minecraft:stone → minecraft:stone[]
        int bracket = s.indexOf('[');
        if (bracket < 0) {
            s = s + "[]";
        } else if (s.endsWith("[]")) {
            // ok
        }
        return s;
    }

    static String normalizeMaterial(String raw) {
        String s = raw.trim();
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        if (s.regionMatches(true, 0, "minecraft:", 0, 10)) {
            s = s.substring(10);
        }
        // Strip block-state props if a full BlockData string was passed by mistake
        int bracket = s.indexOf('[');
        if (bracket > 0) {
            s = s.substring(0, bracket);
        }
        return s.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static Map<String, Integer> loadMaterialMap() {
        try (InputStream in = BedrockBlockRuntimeIds.class.getClassLoader().getResourceAsStream(RESOURCE_MAT)) {
            if (in == null) {
                LOG.warning("Missing " + RESOURCE_MAT + " — using five-state fallback only");
                return fallbackFive();
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Integer> map = new HashMap<>(obj.size() * 2);
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                map.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue().getAsInt());
            }
            int air = map.getOrDefault("AIR", BedrockPacketCodec.hashedAir());
            map.putIfAbsent("CAVE_AIR", air);
            map.putIfAbsent("VOID_AIR", air);
            LOG.info("Loaded Bedrock block runtime ids count=" + map.size() + " from " + RESOURCE_MAT);
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            LOG.warning("block_runtime_ids load failed: " + e.getMessage());
            return fallbackFive();
        }
    }

    private static Map<String, Integer> loadJeStateMap() {
        try (InputStream in = BedrockBlockRuntimeIds.class.getClassLoader().getResourceAsStream(RESOURCE_STATE)) {
            if (in == null) {
                LOG.warning("Missing " + RESOURCE_STATE + " — Material defaults only");
                return Map.of();
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Integer> map = new HashMap<>(obj.size() * 2);
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                map.put(normalizeJeState(e.getKey()), e.getValue().getAsInt());
            }
            LOG.info("Loaded Bedrock JE block-state hashes count=" + map.size() + " from " + RESOURCE_STATE);
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            LOG.warning("block_state_hashes load failed: " + e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Integer> fallbackFive() {
        Map<String, Integer> m = new HashMap<>();
        m.put("AIR", BedrockPacketCodec.hashedAir());
        m.put("CAVE_AIR", BedrockPacketCodec.hashedAir());
        m.put("VOID_AIR", BedrockPacketCodec.hashedAir());
        m.put("STONE", BedrockPacketCodec.hashedStone());
        m.put("DIRT", BedrockPacketCodec.hashedDirt());
        m.put("GRASS_BLOCK", BedrockPacketCodec.hashedGrass());
        m.put("BEDROCK", BedrockPacketCodec.hashedBedrock());
        return Collections.unmodifiableMap(m);
    }
}
