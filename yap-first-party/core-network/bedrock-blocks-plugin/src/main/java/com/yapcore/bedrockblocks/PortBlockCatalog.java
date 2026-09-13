package com.yapcore.bedrockblocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the 24 Bedrock catalog blocks + item-model CMD map for Folia placement.
 */
public final class PortBlockCatalog {

    public record ItemModel(Material material, int customModelData) {
    }

    private final Map<String, PortBlockDefinition> byBedrockId;
    private final Map<String, PortBlockDefinition> byJePortId;
    private final Map<String, PortBlockDefinition> byShort;
    private final Map<String, ItemModel> itemModels;

    private PortBlockCatalog(
            Map<String, PortBlockDefinition> byBedrockId,
            Map<String, PortBlockDefinition> byJePortId,
            Map<String, PortBlockDefinition> byShort,
            Map<String, ItemModel> itemModels) {
        this.byBedrockId = byBedrockId;
        this.byJePortId = byJePortId;
        this.byShort = byShort;
        this.itemModels = itemModels;
    }

    public static PortBlockCatalog loadFromClasspath() {
        JsonObject blocksRoot = readJson("parity/band_26_50/blocks.v1.json");
        JsonObject modelsRoot = readJson("parity/band_26_50/item-models.v1.json");
        return fromJson(blocksRoot, modelsRoot);
    }

    /** Package-visible for unit tests with in-memory JSON. */
    static PortBlockCatalog fromJson(JsonObject blocksRoot, JsonObject modelsRoot) {
        Map<String, ItemModel> models = new LinkedHashMap<>();
        JsonArray modelEntries = modelsRoot.getAsJsonArray("entries");
        for (JsonElement el : modelEntries) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            Material mat = Material.matchMaterial(o.get("material").getAsString());
            if (mat == null) {
                mat = Material.PAPER;
            }
            models.put(id, new ItemModel(mat, o.get("custom_model_data").getAsInt()));
        }

        Map<String, PortBlockDefinition> byBe = new LinkedHashMap<>();
        Map<String, PortBlockDefinition> byJe = new LinkedHashMap<>();
        Map<String, PortBlockDefinition> byShort = new LinkedHashMap<>();

        JsonArray entries = blocksRoot.getAsJsonArray("entries");
        for (JsonElement el : entries) {
            JsonObject o = el.getAsJsonObject();
            String bedrockId = o.get("id").getAsString();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "exclusive";
            String shortName = shortName(bedrockId);
            String jePortId = "yapbedrock:" + shortName;
            PlacementKind placement = placementFor(bedrockId);
            int light = lightLevelFor(bedrockId);
            boolean glow = "minecraft:glow_frame".equals(bedrockId);
            PortBlockDefinition def = new PortBlockDefinition(
                    bedrockId, jePortId, shortName, kind, placement, light, glow);
            byBe.put(bedrockId, def);
            byJe.put(jePortId, def);
            byShort.put(shortName.toLowerCase(Locale.ROOT), def);
            if (!models.containsKey(shortName)) {
                throw new IllegalStateException("Missing item model for " + shortName);
            }
        }
        if (byBe.size() != 24) {
            throw new IllegalStateException("Expected 24 catalog blocks, got " + byBe.size());
        }
        return new PortBlockCatalog(
                Collections.unmodifiableMap(byBe),
                Collections.unmodifiableMap(byJe),
                Collections.unmodifiableMap(byShort),
                Collections.unmodifiableMap(models));
    }

    static PlacementKind placementFor(String bedrockId) {
        if (bedrockId.startsWith("minecraft:light_block_")) {
            return PlacementKind.NATIVE_LIGHT;
        }
        if ("minecraft:stonecutter_block".equals(bedrockId)) {
            return PlacementKind.NATIVE_STONECUTTER;
        }
        if ("minecraft:frame".equals(bedrockId) || "minecraft:glow_frame".equals(bedrockId)) {
            return PlacementKind.NATIVE_FRAME;
        }
        return PlacementKind.CARRIER_DISPLAY;
    }

    static int lightLevelFor(String bedrockId) {
        if (!bedrockId.startsWith("minecraft:light_block_")) {
            return -1;
        }
        try {
            return Integer.parseInt(bedrockId.substring("minecraft:light_block_".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String shortName(String bedrockId) {
        int i = bedrockId.indexOf(':');
        return i >= 0 ? bedrockId.substring(i + 1) : bedrockId;
    }

    private static JsonObject readJson(String path) {
        ClassLoader cl = PortBlockCatalog.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed reading " + path, e);
        }
    }

    public Optional<PortBlockDefinition> resolve(String bedrockOrJeOrShort) {
        if (bedrockOrJeOrShort == null || bedrockOrJeOrShort.isBlank()) {
            return Optional.empty();
        }
        String s = bedrockOrJeOrShort.trim();
        PortBlockDefinition hit = byBedrockId.get(s);
        if (hit != null) {
            return Optional.of(hit);
        }
        hit = byJePortId.get(s);
        if (hit != null) {
            return Optional.of(hit);
        }
        if (!s.contains(":")) {
            hit = byBedrockId.get("minecraft:" + s);
            if (hit != null) {
                return Optional.of(hit);
            }
            hit = byJePortId.get("yapbedrock:" + s);
            if (hit != null) {
                return Optional.of(hit);
            }
            hit = byShort.get(s.toLowerCase(Locale.ROOT));
            if (hit != null) {
                return Optional.of(hit);
            }
        }
        String lower = s.toLowerCase(Locale.ROOT);
        for (PortBlockDefinition def : byBedrockId.values()) {
            if (def.shortName().equalsIgnoreCase(lower) || def.jePortId().equalsIgnoreCase(s)) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    public Optional<ItemModel> itemModel(String shortName) {
        if (shortName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(itemModels.get(shortName));
    }

    public List<PortBlockDefinition> all() {
        return new ArrayList<>(byBedrockId.values());
    }

    public int size() {
        return byBedrockId.size();
    }
}
