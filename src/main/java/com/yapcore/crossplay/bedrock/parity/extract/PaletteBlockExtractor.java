package com.yapcore.crossplay.bedrock.parity.extract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.crossplay.bedrock.parity.ParityBand;
import com.yapcore.crossplay.bedrock.parity.ParityCatalogs;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts Bedrock block state fixtures for frozen catalog ids from the in-repo
 * Cloudburst block palette. Optionally copies geometry/emote files from a vanilla RP.
 */
public final class PaletteBlockExtractor {

    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private PaletteBlockExtractor() {}

    public static Map<String, JsonObject> extractCatalogBlocks(ParityBand band) throws IOException {
        ParityCatalogs catalogs = ParityCatalogs.load(band);
        List<NbtMap> blocks = readBlockPalette(band);
        Map<String, Integer> firstIndex = new LinkedHashMap<>();
        Map<String, NbtMap> firstStates = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            NbtMap tag = blocks.get(i);
            String name = tag.getString("name", "minecraft:air");
            if (!firstIndex.containsKey(name)) {
                firstIndex.put(name, i);
                NbtMap states = tag.getCompound("states");
                firstStates.put(name, states == null ? NbtMap.EMPTY : states);
            }
        }

        Map<String, JsonObject> out = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (ParityCatalogs.CatalogEntry entry : catalogs.blocks().entries()) {
            String id = entry.id();
            if (!firstIndex.containsKey(id)) {
                missing.add(id);
                continue;
            }
            JsonObject fixture = new JsonObject();
            fixture.addProperty("band", band.id());
            fixture.addProperty("source", band.cloudburstPaletteDir() + "block_palette." + band.paletteSuffix() + ".nbt");
            fixture.addProperty("id", id);
            fixture.addProperty("runtime_index", firstIndex.get(id));
            fixture.add("states", nbtCompoundToJson(firstStates.get(id)));
            out.put(id, fixture);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Catalog blocks missing from palette " + band + ": " + missing);
        }
        return out;
    }

    public static void writeBlockFixtures(Path bandDir, Map<String, JsonObject> fixtures) throws IOException {
        Path dir = bandDir.resolve("fixtures").resolve("block");
        Files.createDirectories(dir);
        for (Map.Entry<String, JsonObject> e : fixtures.entrySet()) {
            String file = e.getKey().replace("minecraft:", "minecraft_").replace(':', '_') + ".json";
            Files.writeString(dir.resolve(file), PRETTY.toJson(e.getValue()) + "\n", StandardCharsets.UTF_8);
        }
    }

    /** Copy selected files from a Bedrock vanilla resource pack into fixtures (port path). */
    public static int copyFromVanillaRp(Path vanillaRp, Path bandDir, List<CopySpec> specs) throws IOException {
        int n = 0;
        for (CopySpec spec : specs) {
            Path src = vanillaRp.resolve(spec.sourceRelative());
            if (!Files.isRegularFile(src)) {
                throw new IOException("Vanilla RP missing " + src + " (required for port, not recreation)");
            }
            Path dst = bandDir.resolve("fixtures").resolve(spec.fixtureRelative());
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            n++;
        }
        return n;
    }

    public record CopySpec(String sourceRelative, String fixtureRelative) {}

    @SuppressWarnings("unchecked")
    private static List<NbtMap> readBlockPalette(ParityBand band) throws IOException {
        String path = band.cloudburstPaletteDir() + "block_palette." + band.paletteSuffix() + ".nbt";
        try (InputStream in = PaletteBlockExtractor.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing block palette: " + path);
            }
            try (var nbt = NbtUtils.createGZIPReader(in)) {
                Object root = nbt.readTag();
                if (!(root instanceof NbtMap map)) {
                    throw new IOException("block palette root is not compound: " + path);
                }
                List<NbtMap> blocks = map.getList("blocks", NbtType.COMPOUND);
                if (blocks == null || blocks.isEmpty()) {
                    throw new IOException("block palette empty: " + path);
                }
                return new ArrayList<>(blocks);
            }
        }
    }

    private static JsonObject nbtCompoundToJson(NbtMap map) {
        JsonObject o = new JsonObject();
        for (String key : map.keySet().stream().sorted().toList()) {
            Object v = map.get(key);
            if (v instanceof Boolean b) {
                o.addProperty(key, b);
            } else if (v instanceof Number n) {
                o.addProperty(key, n);
            } else if (v instanceof String s) {
                o.addProperty(key, s);
            } else if (v instanceof NbtMap nested) {
                o.add(key, nbtCompoundToJson(nested));
            } else {
                o.addProperty(key, String.valueOf(v));
            }
        }
        return o;
    }

    /** Validate catalog JSON files parse and band matches. */
    public static JsonObject catalogSummary(ParityBand band) {
        ParityCatalogs c = ParityCatalogs.load(band);
        JsonObject o = new JsonObject();
        o.addProperty("band", band.id());
        o.addProperty("blocks", c.blocks().size());
        o.addProperty("emotes", c.emotes().size());
        o.addProperty("animations", c.animations().size());
        o.addProperty("movement", c.movement().size());
        JsonArray blockIds = new JsonArray();
        c.blocks().entries().forEach(e -> blockIds.add(e.id()));
        o.add("block_ids", blockIds);
        return o;
    }

    public static String pretty(JsonObject o) {
        return PRETTY.toJson(o);
    }
}
