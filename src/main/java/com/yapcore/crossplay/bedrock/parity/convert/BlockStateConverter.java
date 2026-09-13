package com.yapcore.crossplay.bedrock.parity.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Converts a Bedrock block palette extract (name + states) into a JE intermediate
 * blockstate document. States are ported verbatim; no invented properties.
 */
public final class BlockStateConverter {

    public static final String OUTPUT_FORMAT = "yap.blockstate/1";

    private BlockStateConverter() {}

    public static JsonObject convert(String bedrockBlockExtractJson) {
        JsonObject root = JsonParser.parseString(bedrockBlockExtractJson).getAsJsonObject();
        if (!root.has("id")) {
            throw new IllegalArgumentException("Block extract missing id");
        }
        String id = root.get("id").getAsString();
        if (!id.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Block id must be Bedrock minecraft: namespaced: " + id);
        }

        JsonObject statesOut = new JsonObject();
        if (root.has("states") && root.get("states").isJsonObject()) {
            JsonObject states = root.getAsJsonObject("states");
            List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(states.entrySet());
            sorted.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonElement> e : sorted) {
                statesOut.add(e.getKey(), e.getValue());
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("format", OUTPUT_FORMAT);
        result.addProperty("converter", "BlockStateConverter");
        result.addProperty("bedrock_id", id);
        result.addProperty("je_port_id", "yapbedrock:" + id.substring("minecraft:".length()));
        if (root.has("band")) {
            result.addProperty("band", root.get("band").getAsString());
        }
        if (root.has("runtime_index")) {
            result.addProperty("bedrock_runtime_index", root.get("runtime_index").getAsInt());
        }
        if (root.has("source")) {
            result.addProperty("source", root.get("source").getAsString());
        }
        result.add("bedrock_states", statesOut);
        result.addProperty("model", "yapbedrock:block/" + id.substring("minecraft:".length()));
        return result;
    }
}
