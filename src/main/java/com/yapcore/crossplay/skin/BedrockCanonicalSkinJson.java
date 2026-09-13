package com.yapcore.crossplay.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JSON serde for {@link BedrockCanonicalSkin} (split for the ≤500-line domain gate).
 */
final class BedrockCanonicalSkinJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private BedrockCanonicalSkinJson() {
    }

    static String toJson(BedrockCanonicalSkin skin) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", skin.uuid().toString());
        o.addProperty("skinId", skin.skinId());
        o.addProperty("playFabId", skin.playFabId());
        o.addProperty("skinResourcePatch", skin.skinResourcePatch());
        o.addProperty("skinPngBase64", skin.skinPng() == null ? ""
                : java.util.Base64.getEncoder().encodeToString(skin.skinPng()));
        o.addProperty("capePngBase64", skin.capePng() == null ? ""
                : java.util.Base64.getEncoder().encodeToString(skin.capePng()));
        o.addProperty("geometryName", skin.geometryName());
        o.addProperty("geometryData", skin.geometryData());
        o.addProperty("geometryDataVersion", skin.geometryDataVersion());
        o.addProperty("animationData", skin.animationData());
        o.addProperty("capeId", skin.capeId());
        o.addProperty("fullSkinId", skin.fullSkinId());
        o.addProperty("slim", skin.slim());
        o.addProperty("skinColor", skin.skinColor());
        o.add("personaPieces", piecesToJson(skin.personaPieces()));
        o.add("pieceTints", tintsToJson(skin.pieceTints()));
        o.addProperty("premium", skin.premium());
        o.addProperty("persona", skin.persona());
        o.addProperty("capeOnClassic", skin.capeOnClassic());
        o.addProperty("primaryUser", skin.primaryUser());
        o.addProperty("overridingPlayerAppearance", skin.overridingPlayerAppearance());
        o.addProperty("trusted", skin.trusted());
        o.addProperty("profileHash", skin.profileHash());
        o.add("animations", animationsToJson(skin.animations()));
        o.addProperty("contentSha256", skin.contentSha256());
        return PRETTY.toJson(o);
    }

    static BedrockCanonicalSkin fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        UUID uuid = UUID.fromString(o.get("uuid").getAsString());
        boolean slim = o.has("slim") && o.get("slim").getAsBoolean();
        return new BedrockCanonicalSkin(
                uuid,
                str(o, "skinId"),
                str(o, "playFabId"),
                str(o, "skinResourcePatch"),
                b64(o, "skinPngBase64"),
                b64(o, "capePngBase64"),
                str(o, "geometryName"),
                str(o, "geometryData"),
                str(o, "geometryDataVersion"),
                str(o, "animationData"),
                str(o, "capeId"),
                str(o, "fullSkinId"),
                slim,
                str(o, "skinColor"),
                parsePieces(o.get("personaPieces")),
                parseTints(o.get("pieceTints")),
                bool(o, "premium"),
                bool(o, "persona"),
                bool(o, "capeOnClassic"),
                !o.has("primaryUser") || o.get("primaryUser").getAsBoolean(),
                bool(o, "overridingPlayerAppearance"),
                str(o, "trusted"),
                str(o, "profileHash"),
                parseAnims(o.get("animations")));
    }

    static JsonArray piecesToJson(List<BedrockCanonicalSkin.PersonaPiece> pieces) {
        JsonArray arr = new JsonArray();
        for (BedrockCanonicalSkin.PersonaPiece p : pieces) {
            JsonObject o = new JsonObject();
            o.addProperty("pieceId", p.pieceId());
            o.addProperty("pieceType", p.pieceType());
            o.addProperty("packId", p.packId());
            o.addProperty("isDefault", p.isDefault());
            o.addProperty("productId", p.productId());
            arr.add(o);
        }
        return arr;
    }

    static JsonArray tintsToJson(List<BedrockCanonicalSkin.PieceTint> tints) {
        JsonArray arr = new JsonArray();
        for (BedrockCanonicalSkin.PieceTint t : tints) {
            JsonObject o = new JsonObject();
            o.addProperty("pieceType", t.pieceType());
            JsonArray colors = new JsonArray();
            t.colors().forEach(colors::add);
            o.add("colors", colors);
            arr.add(o);
        }
        return arr;
    }

    static JsonArray animationsToJson(List<BedrockCanonicalSkin.SkinAnimation> anims) {
        JsonArray arr = new JsonArray();
        for (BedrockCanonicalSkin.SkinAnimation a : anims) {
            JsonObject o = new JsonObject();
            o.addProperty("imageWidth", a.imageWidth());
            o.addProperty("imageHeight", a.imageHeight());
            o.addProperty("imageDataBase64", a.imageData() == null ? ""
                    : java.util.Base64.getEncoder().encodeToString(a.imageData()));
            o.addProperty("type", a.type());
            o.addProperty("frames", a.frames());
            arr.add(o);
        }
        return arr;
    }

    static List<BedrockCanonicalSkin.PersonaPiece> parsePieces(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<BedrockCanonicalSkin.PersonaPiece> list = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            list.add(new BedrockCanonicalSkin.PersonaPiece(
                    str(o, "pieceId"), str(o, "pieceType"), str(o, "packId"),
                    bool(o, "isDefault"), str(o, "productId")));
        }
        return Collections.unmodifiableList(list);
    }

    static List<BedrockCanonicalSkin.PieceTint> parseTints(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<BedrockCanonicalSkin.PieceTint> list = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            List<String> colors = new ArrayList<>();
            if (o.has("colors") && o.get("colors").isJsonArray()) {
                o.getAsJsonArray("colors").forEach(c -> colors.add(c.getAsString()));
            }
            list.add(new BedrockCanonicalSkin.PieceTint(str(o, "pieceType"), List.copyOf(colors)));
        }
        return Collections.unmodifiableList(list);
    }

    static List<BedrockCanonicalSkin.SkinAnimation> parseAnims(JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<BedrockCanonicalSkin.SkinAnimation> list = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            list.add(new BedrockCanonicalSkin.SkinAnimation(
                    o.has("imageWidth") ? o.get("imageWidth").getAsInt() : 0,
                    o.has("imageHeight") ? o.get("imageHeight").getAsInt() : 0,
                    b64(o, "imageDataBase64"),
                    o.has("type") ? o.get("type").getAsInt() : 0,
                    o.has("frames") ? o.get("frames").getAsFloat() : 0f));
        }
        return Collections.unmodifiableList(list);
    }

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    static boolean bool(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }

    static byte[] b64(JsonObject o, String k) {
        String s = str(o, k);
        if (s.isBlank()) {
            return null;
        }
        try {
            return java.util.Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Gson gson() {
        return GSON;
    }
}
