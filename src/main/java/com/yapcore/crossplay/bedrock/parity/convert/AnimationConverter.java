package com.yapcore.crossplay.bedrock.parity.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Converts Bedrock animation JSON into yap.animation/1 intermediate. */
public final class AnimationConverter {

    public static final String OUTPUT_FORMAT = "yap.animation/1";

    private AnimationConverter() {}

    public static JsonObject convert(String bedrockAnimationJson) {
        JsonObject root = JsonParser.parseString(bedrockAnimationJson).getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty("format", OUTPUT_FORMAT);
        result.addProperty("converter", "AnimationConverter");
        result.addProperty("source_format_version", root.has("format_version")
                ? root.get("format_version").getAsString() : "");

        if (root.has("animations") && root.get("animations").isJsonObject()) {
            JsonObject animations = root.getAsJsonObject("animations");
            JsonObject outAnims = new JsonObject();
            List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(animations.entrySet());
            sorted.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonElement> e : sorted) {
                outAnims.add(e.getKey(), portAnim(e.getValue().getAsJsonObject()));
            }
            result.add("animations", outAnims);
            return result;
        }
        if (root.has("animation_controllers") && root.get("animation_controllers").isJsonObject()) {
            // Port controllers as-is (Bedrock state machines) — no invented states.
            JsonObject controllers = root.getAsJsonObject("animation_controllers");
            JsonObject out = new JsonObject();
            List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(controllers.entrySet());
            sorted.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonElement> e : sorted) {
                out.add(e.getKey(), e.getValue());
            }
            result.add("animation_controllers", out);
            return result;
        }
        throw new IllegalArgumentException("Bedrock animation doc missing animations{} or animation_controllers{}");
    }

    private static JsonObject portAnim(JsonObject src) {
        JsonObject out = new JsonObject();
        if (src.has("loop")) {
            out.add("loop", src.get("loop"));
        }
        if (src.has("animation_length")) {
            out.add("length", src.get("animation_length"));
        }
        if (src.has("bones")) {
            JsonObject bonesOut = new JsonObject();
            List<Map.Entry<String, JsonElement>> bones = new ArrayList<>(src.getAsJsonObject("bones").entrySet());
            bones.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonElement> b : bones) {
                JsonObject boneSrc = b.getValue().getAsJsonObject();
                JsonObject boneDst = new JsonObject();
                for (String channel : List.of("rotation", "position", "scale")) {
                    if (boneSrc.has(channel)) {
                        boneDst.add(channel, sortKeys(boneSrc.get(channel)));
                    }
                }
                bonesOut.add(b.getKey(), boneDst);
            }
            out.add("bones", bonesOut);
        }
        return out;
    }

    private static JsonElement sortKeys(JsonElement el) {
        if (!el.isJsonObject()) {
            return el;
        }
        JsonObject src = el.getAsJsonObject();
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(src.entrySet());
        sorted.sort(Comparator.comparing(Map.Entry::getKey));
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : sorted) {
            out.add(e.getKey(), e.getValue());
        }
        return out;
    }
}
