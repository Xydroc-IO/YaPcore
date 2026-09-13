package com.yapcore.crossplay.bedrock.parity.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converts Bedrock {@code minecraft:geometry} JSON into a deterministic YaP intermediate
 * geometry document for JE runtime glue. Does not invent bones — ports cubes/pivots as-is.
 */
public final class GeometryConverter {

    public static final String OUTPUT_FORMAT = "yap.geometry/1";

    private GeometryConverter() {}

    public static JsonObject convert(String bedrockGeometryJson) {
        JsonObject root = JsonParser.parseString(bedrockGeometryJson).getAsJsonObject();
        if (!root.has("minecraft:geometry")) {
            throw new IllegalArgumentException("Not a Bedrock geometry document (missing minecraft:geometry)");
        }
        JsonArray geos = root.getAsJsonArray("minecraft:geometry");
        if (geos.isEmpty()) {
            throw new IllegalArgumentException("minecraft:geometry empty");
        }

        List<JsonObject> outGeometries = new ArrayList<>();
        for (JsonElement geoEl : geos) {
            JsonObject geo = geoEl.getAsJsonObject();
            JsonObject desc = geo.getAsJsonObject("description");
            JsonObject out = new JsonObject();
            out.addProperty("identifier", desc.get("identifier").getAsString());
            int tw = desc.has("texture_width") && !desc.get("texture_width").isJsonNull()
                    ? desc.get("texture_width").getAsInt() : 64;
            int th = desc.has("texture_height") && !desc.get("texture_height").isJsonNull()
                    ? desc.get("texture_height").getAsInt() : 64;
            out.addProperty("texture_width", tw);
            out.addProperty("texture_height", th);
            if (desc.has("visible_bounds_width")) {
                out.add("visible_bounds_width", desc.get("visible_bounds_width"));
            }
            if (desc.has("visible_bounds_height")) {
                out.add("visible_bounds_height", desc.get("visible_bounds_height"));
            }
            if (desc.has("visible_bounds_offset")) {
                out.add("visible_bounds_offset", desc.get("visible_bounds_offset"));
            }

            List<JsonObject> bones = new ArrayList<>();
            if (geo.has("bones")) {
                for (JsonElement boneEl : geo.getAsJsonArray("bones")) {
                    bones.add(portBone(boneEl.getAsJsonObject()));
                }
            }
            bones.sort(Comparator.comparing(b -> b.get("name").getAsString()));
            JsonArray boneArr = new JsonArray();
            bones.forEach(boneArr::add);
            out.add("bones", boneArr);
            outGeometries.add(out);
        }
        outGeometries.sort(Comparator.comparing(g -> g.get("identifier").getAsString()));

        JsonObject result = new JsonObject();
        result.addProperty("format", OUTPUT_FORMAT);
        result.addProperty("source_format_version", root.has("format_version")
                ? root.get("format_version").getAsString() : "");
        result.addProperty("converter", "GeometryConverter");
        JsonArray arr = new JsonArray();
        outGeometries.forEach(arr::add);
        result.add("geometries", arr);
        return result;
    }

    private static JsonObject portBone(JsonObject bone) {
        JsonObject out = new JsonObject();
        out.addProperty("name", bone.get("name").getAsString());
        if (bone.has("parent")) {
            out.addProperty("parent", bone.get("parent").getAsString());
        }
        if (bone.has("pivot")) {
            out.add("pivot", bone.get("pivot"));
        }
        if (bone.has("rotation")) {
            out.add("rotation", bone.get("rotation"));
        }
        if (bone.has("cubes")) {
            JsonArray cubes = new JsonArray();
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                list.add(portCube(c.getAsJsonObject()));
            }
            // Stable order by origin then size
            list.sort(Comparator
                    .comparing((JsonObject o) -> o.getAsJsonArray("origin").toString())
                    .thenComparing(o -> o.getAsJsonArray("size").toString()));
            list.forEach(cubes::add);
            out.add("cubes", cubes);
        }
        return out;
    }

    private static JsonObject portCube(JsonObject cube) {
        JsonObject out = new JsonObject();
        out.add("origin", cube.get("origin"));
        out.add("size", cube.get("size"));
        if (cube.has("uv")) {
            out.add("uv", cube.get("uv"));
        }
        if (cube.has("inflate")) {
            out.add("inflate", cube.get("inflate"));
        }
        if (cube.has("mirror")) {
            out.add("mirror", cube.get("mirror"));
        }
        if (cube.has("rotation")) {
            out.add("rotation", cube.get("rotation"));
        }
        if (cube.has("pivot")) {
            out.add("pivot", cube.get("pivot"));
        }
        return out;
    }
}
