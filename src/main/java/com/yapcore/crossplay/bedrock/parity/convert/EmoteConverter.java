package com.yapcore.crossplay.bedrock.parity.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Converts a Bedrock emote definition (UUID + embedded animation_data) into a YaP
 * intermediate animation clip. Keyframes are ported, not redrawn.
 */
public final class EmoteConverter {

    public static final String OUTPUT_FORMAT = "yap.emote/1";

    private EmoteConverter() {}

    public static JsonObject convert(String bedrockEmoteJson) {
        JsonObject root = JsonParser.parseString(bedrockEmoteJson).getAsJsonObject();
        if (!root.has("emote_id")) {
            throw new IllegalArgumentException("Emote extract missing emote_id");
        }
        String emoteId = root.get("emote_id").getAsString();
        String animName = root.has("animation") ? root.get("animation").getAsString() : "";
        double duration = root.has("duration_seconds") ? root.get("duration_seconds").getAsDouble() : 0;

        JsonObject clip = new JsonObject();
        clip.addProperty("loop", false);
        clip.addProperty("length", duration);
        JsonObject bonesOut = new JsonObject();

        if (root.has("animation_data") && root.get("animation_data").isJsonObject()) {
            JsonObject animData = root.getAsJsonObject("animation_data");
            if (animData.has("animations") && animData.get("animations").isJsonObject()) {
                JsonObject animations = animData.getAsJsonObject("animations");
                JsonObject chosen = null;
                if (!animName.isBlank() && animations.has(animName)) {
                    chosen = animations.getAsJsonObject(animName);
                } else if (!animations.entrySet().isEmpty()) {
                    chosen = animations.entrySet().iterator().next().getValue().getAsJsonObject();
                    if (animName.isBlank()) {
                        animName = animations.entrySet().iterator().next().getKey();
                    }
                }
                if (chosen != null) {
                    if (chosen.has("loop")) {
                        clip.add("loop", chosen.get("loop"));
                    }
                    if (chosen.has("animation_length")) {
                        clip.addProperty("length", chosen.get("animation_length").getAsDouble());
                    }
                    if (chosen.has("bones")) {
                        portBones(chosen.getAsJsonObject("bones"), bonesOut);
                    }
                }
            }
        }
        clip.add("bones", bonesOut);

        JsonObject result = new JsonObject();
        result.addProperty("format", OUTPUT_FORMAT);
        result.addProperty("converter", "EmoteConverter");
        result.addProperty("bedrock_emote_id", emoteId);
        if (root.has("name")) {
            result.addProperty("name", root.get("name").getAsString());
        }
        result.addProperty("bedrock_animation", animName);
        result.add("clip", clip);
        return result;
    }

    private static void portBones(JsonObject bones, JsonObject bonesOut) {
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(bones.entrySet());
        sorted.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, JsonElement> bone : sorted) {
            JsonObject src = bone.getValue().getAsJsonObject();
            JsonObject dst = new JsonObject();
            if (src.has("rotation")) {
                dst.add("rotation", sortKeyframeObject(src.get("rotation")));
            }
            if (src.has("position")) {
                dst.add("position", sortKeyframeObject(src.get("position")));
            }
            if (src.has("scale")) {
                dst.add("scale", sortKeyframeObject(src.get("scale")));
            }
            bonesOut.add(bone.getKey(), dst);
        }
    }

    private static JsonElement sortKeyframeObject(JsonElement el) {
        if (!el.isJsonObject()) {
            return el;
        }
        JsonObject src = el.getAsJsonObject();
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(src.entrySet());
        sorted.sort(Comparator.comparing(e -> Double.parseDouble(e.getKey())));
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : sorted) {
            out.add(e.getKey(), e.getValue());
        }
        return out;
    }
}
