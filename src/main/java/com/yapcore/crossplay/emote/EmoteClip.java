package com.yapcore.crossplay.emote;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Converted {@code yap.emote/1} clip keyed by Bedrock emote UUID. */
public final class EmoteClip {

    private final String bedrockEmoteId;
    private final String name;
    private final String bedrockAnimation;
    private final boolean loop;
    private final double lengthSeconds;
    private final JsonObject clipJson;
    private final String slug;
    private final String sourceRelativePath;

    public EmoteClip(
            String bedrockEmoteId,
            String name,
            String bedrockAnimation,
            boolean loop,
            double lengthSeconds,
            JsonObject clipJson,
            String slug,
            String sourceRelativePath) {
        this.bedrockEmoteId = Objects.requireNonNull(bedrockEmoteId, "bedrockEmoteId");
        this.name = name == null ? "" : name;
        this.bedrockAnimation = bedrockAnimation == null ? "" : bedrockAnimation;
        this.loop = loop;
        this.lengthSeconds = lengthSeconds;
        this.clipJson = clipJson == null ? new JsonObject() : clipJson;
        this.slug = slug == null ? "" : slug;
        this.sourceRelativePath = sourceRelativePath == null ? "" : sourceRelativePath;
    }

    public String bedrockEmoteId() {
        return bedrockEmoteId;
    }

    public String name() {
        return name;
    }

    public String bedrockAnimation() {
        return bedrockAnimation;
    }

    public boolean loop() {
        return loop;
    }

    public double lengthSeconds() {
        return lengthSeconds;
    }

    /** Duration in Bedrock emote ticks (~20/s). */
    public int durationTicks() {
        return Math.max(1, (int) Math.round(lengthSeconds * 20.0));
    }

    public JsonObject clipJson() {
        return clipJson;
    }

    public String slug() {
        return slug;
    }

    public String sourceRelativePath() {
        return sourceRelativePath;
    }

    /** Bone → axis → time → vec3 (degrees). Empty when clip has no bones. */
    public Map<String, Map<String, Map<Double, float[]>>> boneTracks() {
        if (!clipJson.has("bones") || !clipJson.get("bones").isJsonObject()) {
            return Map.of();
        }
        Map<String, Map<String, Map<Double, float[]>>> out = new LinkedHashMap<>();
        JsonObject bones = clipJson.getAsJsonObject("bones");
        for (String bone : bones.keySet()) {
            JsonObject boneObj = bones.getAsJsonObject(bone);
            Map<String, Map<Double, float[]>> channels = new LinkedHashMap<>();
            for (String channel : new String[] {"rotation", "position", "scale"}) {
                if (!boneObj.has(channel) || !boneObj.get(channel).isJsonObject()) {
                    continue;
                }
                Map<Double, float[]> keys = new LinkedHashMap<>();
                JsonObject times = boneObj.getAsJsonObject(channel);
                for (String t : times.keySet()) {
                    try {
                        double time = Double.parseDouble(t);
                        var arr = times.getAsJsonArray(t);
                        float[] v = new float[] {
                                arr.size() > 0 ? arr.get(0).getAsFloat() : 0f,
                                arr.size() > 1 ? arr.get(1).getAsFloat() : 0f,
                                arr.size() > 2 ? arr.get(2).getAsFloat() : 0f
                        };
                        keys.put(time, v);
                    } catch (Exception ignored) {
                        // skip malformed keyframe
                    }
                }
                if (!keys.isEmpty()) {
                    channels.put(channel, Collections.unmodifiableMap(keys));
                }
            }
            if (!channels.isEmpty()) {
                out.put(bone, Collections.unmodifiableMap(channels));
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
