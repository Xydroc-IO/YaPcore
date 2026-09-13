package com.yapcore.presence.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Loads bundled {@code yap.emote/1} clips for JE playback. */
public final class EmoteClipLoader {

    public record Clip(
            String bedrockEmoteId,
            String name,
            boolean loop,
            double lengthSeconds,
            Map<String, NavigableMap<Double, float[]>> boneRotations) {
    }

    private static final Map<String, Clip> BY_ID = loadAll();

    private EmoteClipLoader() {
    }

    public static Optional<Clip> byId(String emoteId) {
        if (emoteId == null || emoteId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(emoteId.trim()));
    }

    public static java.util.Collection<Clip> all() {
        return BY_ID.values();
    }

    public static int size() {
        return BY_ID.size();
    }

    private static Map<String, Clip> loadAll() {
        Map<String, Clip> out = new LinkedHashMap<>();
        String[] files = {
                "wave", "simple_clap", "over_there", "follow_me"
        };
        for (String slug : files) {
            String path = "assets/yap-presence/parity/emote/" + slug + ".yapemote.json";
            try (InputStream in = EmoteClipLoader.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String id = root.get("bedrock_emote_id").getAsString();
                String name = root.has("name") ? root.get("name").getAsString() : slug;
                JsonObject clip = root.getAsJsonObject("clip");
                boolean loop = clip.has("loop") && clip.get("loop").getAsBoolean();
                double length = clip.has("length") ? clip.get("length").getAsDouble() : 0.0;
                Map<String, NavigableMap<Double, float[]>> bones = new LinkedHashMap<>();
                if (clip.has("bones") && clip.get("bones").isJsonObject()) {
                    JsonObject boneObj = clip.getAsJsonObject("bones");
                    for (String bone : boneObj.keySet()) {
                        JsonObject b = boneObj.getAsJsonObject(bone);
                        if (!b.has("rotation") || !b.get("rotation").isJsonObject()) {
                            continue;
                        }
                        NavigableMap<Double, float[]> keys = new TreeMap<>();
                        JsonObject rot = b.getAsJsonObject("rotation");
                        for (String t : rot.keySet()) {
                            try {
                                double time = Double.parseDouble(t);
                                var arr = rot.getAsJsonArray(t);
                                keys.put(time, new float[] {
                                        arr.size() > 0 ? arr.get(0).getAsFloat() : 0f,
                                        arr.size() > 1 ? arr.get(1).getAsFloat() : 0f,
                                        arr.size() > 2 ? arr.get(2).getAsFloat() : 0f
                                });
                            } catch (Exception ignored) {
                            }
                        }
                        if (!keys.isEmpty()) {
                            bones.put(bone, keys);
                        }
                    }
                }
                out.put(id, new Clip(id, name, loop, length, Collections.unmodifiableMap(bones)));
            } catch (Exception e) {
                // skip bad clip
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static float[] sample(NavigableMap<Double, float[]> keys, double t) {
        if (keys == null || keys.isEmpty()) {
            return new float[] {0f, 0f, 0f};
        }
        var floor = keys.floorEntry(t);
        var ceil = keys.ceilingEntry(t);
        if (floor == null) {
            return ceil.getValue().clone();
        }
        if (ceil == null || floor.getKey().equals(ceil.getKey())) {
            return floor.getValue().clone();
        }
        double t0 = floor.getKey();
        double t1 = ceil.getKey();
        float a = (float) ((t - t0) / (t1 - t0));
        float[] a0 = floor.getValue();
        float[] a1 = ceil.getValue();
        return new float[] {
                a0[0] + (a1[0] - a0[0]) * a,
                a0[1] + (a1[1] - a0[1]) * a,
                a0[2] + (a1[2] - a0[2]) * a
        };
    }
}
