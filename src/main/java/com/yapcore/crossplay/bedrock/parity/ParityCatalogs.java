package com.yapcore.crossplay.bedrock.parity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads frozen Bedrock-keyed parity catalogs for a band. */
public final class ParityCatalogs {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final ParityBand band;
    private final Catalog blocks;
    private final Catalog emotes;
    private final Catalog animations;
    private final Catalog movement;

    private ParityCatalogs(ParityBand band, Catalog blocks, Catalog emotes, Catalog animations, Catalog movement) {
        this.band = band;
        this.blocks = blocks;
        this.emotes = emotes;
        this.animations = animations;
        this.movement = movement;
    }

    public static ParityCatalogs load(ParityBand band) {
        return new ParityCatalogs(
                band,
                loadOne(band, "blocks.v1.json", "blocks"),
                loadOne(band, "emotes.v1.json", "emotes"),
                loadOne(band, "animations.v1.json", "animations"),
                loadOne(band, "movement.v1.json", "movement"));
    }

    public static ParityCatalogs loadDefault() {
        return load(ParityBand.of(ParityBand.DEFAULT));
    }

    private static Catalog loadOne(ParityBand band, String file, String expectedCatalog) {
        String path = band.catalogPath(file);
        try (InputStream in = open(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String catalog = root.get("catalog").getAsString();
            if (!expectedCatalog.equals(catalog)) {
                throw new IllegalStateException("Expected catalog " + expectedCatalog + " in " + path + " got " + catalog);
            }
            if (!band.id().equals(root.get("band").getAsString())) {
                throw new IllegalStateException("Catalog band mismatch in " + path);
            }
            int version = root.get("version").getAsInt();
            String rule = root.has("rule") ? root.get("rule").getAsString() : "";
            List<CatalogEntry> entries = new ArrayList<>();
            JsonArray arr = root.getAsJsonArray("entries");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                Map<String, JsonElement> extras = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (!"id".equals(e.getKey())) {
                        extras.put(e.getKey(), e.getValue());
                    }
                }
                entries.add(new CatalogEntry(id, Collections.unmodifiableMap(extras)));
            }
            return new Catalog(catalog, version, rule, Collections.unmodifiableList(entries));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load catalog " + path, e);
        }
    }

    private static InputStream open(String path) {
        InputStream in = ParityCatalogs.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing parity catalog: " + path);
        }
        return in;
    }

    public ParityBand band() {
        return band;
    }

    public Catalog blocks() {
        return blocks;
    }

    public Catalog emotes() {
        return emotes;
    }

    public Catalog animations() {
        return animations;
    }

    public Catalog movement() {
        return movement;
    }

    public Optional<CatalogEntry> block(String id) {
        return blocks.find(id);
    }

    public Optional<CatalogEntry> emote(String id) {
        return emotes.find(id);
    }

    public record Catalog(String name, int version, String rule, List<CatalogEntry> entries) {
        public Optional<CatalogEntry> find(String id) {
            for (CatalogEntry e : entries) {
                if (e.id().equals(id)) {
                    return Optional.of(e);
                }
            }
            return Optional.empty();
        }

        public int size() {
            return entries.size();
        }
    }

    public record CatalogEntry(String id, Map<String, JsonElement> extras) {
        public String string(String key) {
            JsonElement el = extras.get(key);
            return el == null || el.isJsonNull() ? null : el.getAsString();
        }

        public Double number(String key) {
            JsonElement el = extras.get(key);
            return el == null || el.isJsonNull() ? null : el.getAsDouble();
        }
    }

    /** Pretty printer for tooling. */
    public static String toPretty(JsonElement el) {
        return new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(el);
    }

    public static Gson gson() {
        return GSON;
    }
}
