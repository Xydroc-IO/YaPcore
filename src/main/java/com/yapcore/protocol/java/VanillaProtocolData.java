package com.yapcore.protocol.java;

import com.yapcore.protocol.java.codec.NbtReader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Versioned vanilla configuration dumps under {@code /protocol/vanilla/<version>/}:
 * <ul>
 *   <li>{@code registryEntries.json} — synchronized registry entry ID lists</li>
 *   <li>{@code networkTags.nbt} — full Update Tags (gzip NBT)</li>
 * </ul>
 * Generate 26.2 with {@code scripts/generate-vanilla-protocol-26.2.py}.
 * Other releases: same script pattern against that version's server jar + registries.json.
 */
public final class VanillaProtocolData {

    private static final Logger LOG = Logger.getLogger("YaPcore.JE.VanillaData");
    private static final ConcurrentHashMap<String, Map<String, Map<String, int[]>>> TAG_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, String[]>> REG_CACHE =
            new ConcurrentHashMap<>();

    private VanillaProtocolData() {
    }

    public static String resolveDumpVersion(String mcVersionLabel, int protocolVersion) {
        if (mcVersionLabel != null) {
            String v = mcVersionLabel.trim();
            if (dumpExists(v)) {
                return v;
            }
        }
        if (protocolVersion >= 776 && dumpExists("26.2")) {
            return "26.2";
        }
        if (protocolVersion >= 775 && dumpExists("26.1")) {
            return "26.1";
        }
        if (dumpExists("26.2")) {
            return "26.2";
        }
        throw new IllegalStateException("No vanilla protocol dump found under /protocol/vanilla/");
    }

    public static boolean dumpExists(String version) {
        return VanillaProtocolData.class.getResource(
                "/protocol/vanilla/" + version + "/networkTags.nbt") != null
                && VanillaProtocolData.class.getResource(
                "/protocol/vanilla/" + version + "/registryEntries.json") != null;
    }

    public static Map<String, String[]> registriesFor(String vanillaVersion) {
        return REG_CACHE.computeIfAbsent(vanillaVersion, VanillaProtocolData::loadRegistries);
    }

    public static Map<String, Map<String, int[]>> tagsFor(String vanillaVersion) {
        return TAG_CACHE.computeIfAbsent(vanillaVersion, VanillaProtocolData::loadTags);
    }

    private static Map<String, String[]> loadRegistries(String version) {
        String path = "/protocol/vanilla/" + version + "/registryEntries.json";
        try (InputStream in = VanillaProtocolData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String[]> out = parseRegistryEntriesJson(json);
            LOG.info("Loaded vanilla registry lists " + version + ": " + out.size() + " registries");
            return Map.copyOf(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load " + path, ex);
        }
    }

    private static Map<String, Map<String, int[]>> loadTags(String version) {
        String path = "/protocol/vanilla/" + version + "/networkTags.nbt";
        try {
            Map<String, Object> root = NbtReader.readGzipResource(path);
            Map<String, Map<String, int[]>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : root.entrySet()) {
                out.put(e.getKey(), NbtReader.asIntArrayMap(e.getValue()));
            }
            LOG.info("Loaded vanilla Update Tags dump " + version + ": "
                    + out.size() + " registries");
            return Map.copyOf(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Missing/invalid vanilla tag dump: " + path, ex);
        }
    }

    /**
     * Minimal JSON object of string-array values — avoids adding a JSON library.
     * Expected shape from the generator: {@code {"BIOMES":["minecraft:plains",...],...}}.
     */
    static Map<String, String[]> parseRegistryEntriesJson(String json) {
        Map<String, String[]> out = new LinkedHashMap<>();
        int i = 0;
        int n = json.length();
        while (i < n && json.charAt(i) != '{') {
            i++;
        }
        i++;
        while (i < n) {
            while (i < n && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i < n && json.charAt(i) == '}') {
                break;
            }
            if (i < n && json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected key at " + i);
            }
            i++;
            int keyStart = i;
            while (i < n && json.charAt(i) != '"') {
                i++;
            }
            String key = json.substring(keyStart, i);
            i++;
            while (i < n && json.charAt(i) != '[') {
                i++;
            }
            i++;
            List<String> values = new ArrayList<>();
            while (i < n && json.charAt(i) != ']') {
                while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
                    i++;
                }
                if (i < n && json.charAt(i) == ']') {
                    break;
                }
                if (json.charAt(i) != '"') {
                    throw new IllegalArgumentException("Expected string at " + i);
                }
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && json.charAt(i) != '"') {
                    char c = json.charAt(i++);
                    if (c == '\\' && i < n) {
                        sb.append(json.charAt(i++));
                    } else {
                        sb.append(c);
                    }
                }
                i++;
                values.add(sb.toString());
            }
            if (i < n && json.charAt(i) == ']') {
                i++;
            }
            out.put(key, values.toArray(String[]::new));
        }
        return out;
    }
}
