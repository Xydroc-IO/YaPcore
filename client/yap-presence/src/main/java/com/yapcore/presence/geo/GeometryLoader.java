package com.yapcore.presence.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.presence.geo.GeometryModel.Bone;
import com.yapcore.presence.geo.GeometryModel.Cube;
import com.yapcore.presence.geo.GeometryModel.PerFaceUv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Accepts Bedrock {@code minecraft:geometry} JSON or {@code yap.geometry/1} and builds
 * bone/cube lists (ported from chassis {@code GeometryConverter} + cube fields).
 */
public final class GeometryLoader {

    public static final String YAP_FORMAT = "yap.geometry/1";

    private GeometryLoader() {
    }

    public static GeometryModel load(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("minecraft:geometry")) {
                return fromBedrock(root);
            }
            if (root.has("format") && YAP_FORMAT.equals(root.get("format").getAsString())) {
                return fromYap(root);
            }
            // Single geometry object with bones
            if (root.has("bones") && root.has("description")) {
                return fromSingleGeo(root);
            }
            return empty();
        } catch (Exception e) {
            return empty();
        }
    }

    private static GeometryModel empty() {
        return new GeometryModel("", 64, 64, List.of());
    }

    private static GeometryModel fromBedrock(JsonObject root) {
        JsonArray geos = root.getAsJsonArray("minecraft:geometry");
        if (geos == null || geos.isEmpty()) {
            return empty();
        }
        // Prefer first geometry (humanoid custom)
        return fromSingleGeo(geos.get(0).getAsJsonObject());
    }

    private static GeometryModel fromYap(JsonObject root) {
        if (!root.has("geometries") || !root.get("geometries").isJsonArray()) {
            return empty();
        }
        JsonArray arr = root.getAsJsonArray("geometries");
        if (arr.isEmpty()) {
            return empty();
        }
        return fromYapGeometry(arr.get(0).getAsJsonObject());
    }

    private static GeometryModel fromYapGeometry(JsonObject geo) {
        String id = str(geo, "identifier");
        int tw = geo.has("texture_width") ? geo.get("texture_width").getAsInt() : 64;
        int th = geo.has("texture_height") ? geo.get("texture_height").getAsInt() : 64;
        List<Bone> bones = new ArrayList<>();
        if (geo.has("bones") && geo.get("bones").isJsonArray()) {
            for (JsonElement el : geo.getAsJsonArray("bones")) {
                bones.add(portBone(el.getAsJsonObject()));
            }
        }
        bones.sort(Comparator.comparing(Bone::name));
        return new GeometryModel(id, tw, th, bones);
    }

    private static GeometryModel fromSingleGeo(JsonObject geo) {
        JsonObject desc = geo.has("description") && geo.get("description").isJsonObject()
                ? geo.getAsJsonObject("description")
                : new JsonObject();
        String id = str(desc, "identifier");
        int tw = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
        int th = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
        List<Bone> bones = new ArrayList<>();
        if (geo.has("bones") && geo.get("bones").isJsonArray()) {
            for (JsonElement el : geo.getAsJsonArray("bones")) {
                bones.add(portBone(el.getAsJsonObject()));
            }
        }
        bones.sort(Comparator.comparing(Bone::name));
        return new GeometryModel(id, tw, th, bones);
    }

    private static Bone portBone(JsonObject bone) {
        String name = str(bone, "name");
        String parent = str(bone, "parent");
        float[] pivot = float3(bone, "pivot");
        float[] rotation = float3(bone, "rotation");
        List<Cube> cubes = new ArrayList<>();
        if (bone.has("cubes") && bone.get("cubes").isJsonArray()) {
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                cubes.add(portCube(c.getAsJsonObject()));
            }
            cubes.sort(Comparator
                    .comparing((Cube o) -> arrKey(o.origin()))
                    .thenComparing(o -> arrKey(o.size())));
        }
        return new Bone(name, parent, pivot, rotation, cubes);
    }

    private static Cube portCube(JsonObject cube) {
        float[] origin = float3(cube, "origin");
        float[] size = float3(cube, "size");
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
        boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
        float[] uvBox = null;
        PerFaceUv perFace = null;
        if (cube.has("uv")) {
            JsonElement uvEl = cube.get("uv");
            if (uvEl.isJsonArray()) {
                uvBox = floatArr(uvEl.getAsJsonArray());
            } else if (uvEl.isJsonObject()) {
                JsonObject uvo = uvEl.getAsJsonObject();
                perFace = new PerFaceUv(
                        faceUv(uvo, "north"),
                        faceUv(uvo, "east"),
                        faceUv(uvo, "south"),
                        faceUv(uvo, "west"),
                        faceUv(uvo, "up"),
                        faceUv(uvo, "down"));
            }
        }
        float[] rotation = cube.has("rotation") ? float3(cube, "rotation") : null;
        float[] pivot = cube.has("pivot") ? float3(cube, "pivot") : null;
        return new Cube(origin, size, inflate, mirror, uvBox, perFace, rotation, pivot);
    }

    private static float[] faceUv(JsonObject uvo, String face) {
        if (!uvo.has(face)) {
            return null;
        }
        JsonElement el = uvo.get(face);
        if (el.isJsonArray()) {
            return floatArr(el.getAsJsonArray());
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("uv")) {
            JsonElement inner = el.getAsJsonObject().get("uv");
            if (inner.isJsonArray()) {
                float[] uv = floatArr(inner.getAsJsonArray());
                if (el.getAsJsonObject().has("uv_size") && el.getAsJsonObject().get("uv_size").isJsonArray()) {
                    float[] size = floatArr(el.getAsJsonObject().getAsJsonArray("uv_size"));
                    if (uv.length >= 2 && size.length >= 2) {
                        return new float[]{uv[0], uv[1], uv[0] + size[0], uv[1] + size[1]};
                    }
                }
                return uv;
            }
        }
        return null;
    }

    private static float[] float3(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return new float[]{0, 0, 0};
        }
        float[] a = floatArr(o.getAsJsonArray(key));
        if (a.length >= 3) {
            return new float[]{a[0], a[1], a[2]};
        }
        return new float[]{0, 0, 0};
    }

    private static float[] floatArr(JsonArray arr) {
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String arrKey(float[] a) {
        if (a == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (float v : a) {
            sb.append(v).append(',');
        }
        return sb.toString();
    }
}
