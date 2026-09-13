package com.yapcore.crossplay.skin;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yapcore.crossplay.bedrock.parity.ParityBand;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bedrock-canonical skin payload (Phase 1). This is the source-of-truth shape for
 * Tailor storage and PlayerSkinPacket fields — not a YaP-invented persona schema.
 * JSON serde lives in {@link BedrockCanonicalSkinJson}.
 */
public final class BedrockCanonicalSkin {

    private final UUID uuid;
    private final String skinId;
    private final String playFabId;
    private final String skinResourcePatch;
    private final byte[] skinPng;
    private final byte[] capePng;
    private final String geometryName;
    private final String geometryData;
    private final String geometryDataVersion;
    private final String animationData;
    private final String capeId;
    private final String fullSkinId;
    private final boolean slim;
    private final String skinColor;
    private final List<PersonaPiece> personaPieces;
    private final List<PieceTint> pieceTints;
    private final boolean premium;
    private final boolean persona;
    private final boolean capeOnClassic;
    private final boolean primaryUser;
    private final boolean overridingPlayerAppearance;
    private final String trusted;
    private final String profileHash;
    private final List<SkinAnimation> animations;

    public BedrockCanonicalSkin(
            UUID uuid,
            String skinId,
            String playFabId,
            String skinResourcePatch,
            byte[] skinPng,
            byte[] capePng,
            String geometryName,
            String geometryData,
            String geometryDataVersion,
            String animationData,
            String capeId,
            String fullSkinId,
            boolean slim,
            String skinColor,
            List<PersonaPiece> personaPieces,
            List<PieceTint> pieceTints,
            boolean premium,
            boolean persona,
            boolean capeOnClassic,
            boolean primaryUser,
            boolean overridingPlayerAppearance,
            String trusted,
            String profileHash,
            List<SkinAnimation> animations) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.skinId = skinId == null || skinId.isBlank()
                ? (slim ? "Standard_CustomSlim" : "Standard_Custom") : skinId;
        this.playFabId = playFabId == null ? "" : playFabId;
        this.skinResourcePatch = skinResourcePatch == null || skinResourcePatch.isBlank()
                ? defaultResourcePatch(geometryName, slim) : skinResourcePatch;
        this.skinPng = skinPng == null ? null : skinPng.clone();
        this.capePng = capePng == null ? null : capePng.clone();
        this.geometryName = geometryName == null || geometryName.isBlank()
                ? (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom") : geometryName;
        this.geometryData = geometryData == null ? "" : geometryData;
        this.geometryDataVersion = geometryDataVersion == null || geometryDataVersion.isBlank()
                ? "1.14.0" : geometryDataVersion;
        this.animationData = animationData == null ? "" : animationData;
        this.capeId = capeId == null ? "" : capeId;
        this.fullSkinId = fullSkinId == null || fullSkinId.isBlank() ? this.skinId : fullSkinId;
        this.slim = slim;
        this.skinColor = skinColor == null || skinColor.isBlank() ? "#0" : skinColor;
        this.personaPieces = personaPieces == null
                ? List.of() : List.copyOf(personaPieces);
        this.pieceTints = pieceTints == null ? List.of() : List.copyOf(pieceTints);
        this.premium = premium;
        this.persona = persona || !this.personaPieces.isEmpty();
        this.capeOnClassic = capeOnClassic;
        this.primaryUser = primaryUser;
        this.overridingPlayerAppearance = overridingPlayerAppearance;
        this.trusted = trusted == null || trusted.isBlank() ? "true" : trusted;
        this.profileHash = profileHash == null ? "" : profileHash;
        this.animations = animations == null ? List.of() : List.copyOf(animations);
    }

    public static BedrockCanonicalSkin classicPng(UUID uuid, byte[] skinPng, byte[] capePng, boolean slim) {
        String geo = slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom";
        return new BedrockCanonicalSkin(
                uuid,
                slim ? "Standard_CustomSlim" : "Standard_Custom",
                "",
                defaultResourcePatch(geo, slim),
                skinPng,
                capePng,
                geo,
                loadParityGeometryJson(geo),
                "1.14.0",
                "",
                "",
                slim ? "Standard_CustomSlim" : "Standard_Custom",
                slim,
                "#0",
                List.of(),
                List.of(),
                false,
                false,
                false,
                true,
                false,
                "true",
                "",
                List.of());
    }

    /**
     * Convert a Cloudburst {@link org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin}
     * into the Phase-1 canonical shape. Preserves packet {@code geometry_data}, persona pieces,
     * tints, and animations; does not substitute parity fixtures when geometry is present.
     */
    public static BedrockCanonicalSkin fromSerializedSkin(
            UUID uuid,
            org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin skin) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid");
        }
        if (skin == null) {
            throw new IllegalArgumentException("skin");
        }
        String patch = skin.getSkinResourcePatch();
        String geoName = skin.getGeometryName();
        if (geoName == null || geoName.isBlank()) {
            geoName = geometryNameFromResourcePatch(patch);
        }
        if (geoName == null || geoName.isBlank()) {
            geoName = geometryNameFromGeometryData(skin.getGeometryData());
        }
        String arm = skin.getArmSize();
        boolean slim = arm != null && arm.equalsIgnoreCase("slim")
                || (geoName != null && geoName.toLowerCase().contains("slim"));
        if (geoName == null || geoName.isBlank()) {
            geoName = slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom";
        }
        String geoData = skin.getGeometryData() == null ? "" : skin.getGeometryData();
        byte[] skinPng = imageDataToPng(skin.getSkinData());
        byte[] capePng = imageDataToPng(skin.getCapeData());

        List<PersonaPiece> pieces = new ArrayList<>();
        if (skin.getPersonaPieces() != null) {
            for (org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData p : skin.getPersonaPieces()) {
                if (p == null) {
                    continue;
                }
                String type = p.getPieceType() != null
                        ? p.getPieceType().getSerializeName()
                        : (p.getType() == null ? "" : p.getType());
                String pack = p.getPackUuid() != null
                        ? p.getPackUuid().toString()
                        : (p.getPackId() == null ? "" : p.getPackId());
                pieces.add(new PersonaPiece(
                        p.getId() == null ? "" : p.getId(),
                        type,
                        pack,
                        p.isDefault(),
                        p.getProductId() == null ? "" : p.getProductId()));
            }
        }

        List<PieceTint> tints = new ArrayList<>();
        if (skin.getTintColors() != null) {
            for (org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceTintData t : skin.getTintColors()) {
                if (t == null) {
                    continue;
                }
                String type = t.getPieceType() != null
                        ? t.getPieceType().getSerializeName()
                        : (t.getType() == null ? "" : t.getType());
                List<String> colors = t.getColors() == null ? List.of() : List.copyOf(t.getColors());
                tints.add(new PieceTint(type, colors));
            }
        }

        List<SkinAnimation> anims = new ArrayList<>();
        if (skin.getAnimations() != null) {
            for (org.cloudburstmc.protocol.bedrock.data.skin.AnimationData a : skin.getAnimations()) {
                if (a == null || a.getImage() == null) {
                    continue;
                }
                org.cloudburstmc.protocol.bedrock.data.skin.ImageData img = a.getImage();
                int type = a.getTextureType() == null ? 0 : a.getTextureType().ordinal();
                anims.add(new SkinAnimation(
                        img.getWidth(),
                        img.getHeight(),
                        img.getImage() == null ? new byte[0] : img.getImage().clone(),
                        type,
                        a.getFrames()));
            }
        }

        String trusted = skin.isTrusted() ? "true" : "false";
        String skinColor = skin.getSkinColor();
        if (skinColor == null || skinColor.isBlank()) {
            skinColor = "#0";
        }
        return new BedrockCanonicalSkin(
                uuid,
                skin.getSkinId(),
                skin.getPlayFabId(),
                patch == null || patch.isBlank() ? defaultResourcePatch(geoName, slim) : patch,
                skinPng,
                capePng,
                geoName,
                geoData,
                skin.getGeometryDataEngineVersion(),
                skin.getAnimationData(),
                skin.getCapeId(),
                skin.getFullSkinId(),
                slim,
                skinColor,
                pieces,
                tints,
                skin.isPremium(),
                skin.isPersona(),
                skin.isCapeOnClassic(),
                skin.isPrimaryUser(),
                skin.isOverridingPlayerAppearance(),
                trusted,
                skin.getProfileHash() == null ? "" : skin.getProfileHash(),
                anims);
    }

    /** Parse {@code geometry.default} from a Bedrock skin resource patch JSON. */
    public static String geometryNameFromResourcePatch(String patch) {
        if (patch == null || patch.isBlank()) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(patch).getAsJsonObject();
            if (o.has("geometry") && o.get("geometry").isJsonObject()) {
                JsonObject g = o.getAsJsonObject("geometry");
                if (g.has("default") && !g.get("default").isJsonNull()) {
                    return g.get("default").getAsString();
                }
            }
        } catch (Exception ignored) {
            // not JSON — fall through
        }
        return "";
    }

    /** Best-effort identifier extract from geometry JSON ({@code description.identifier}). */
    public static String geometryNameFromGeometryData(String geometryData) {
        if (geometryData == null || geometryData.isBlank()) {
            return "";
        }
        try {
            JsonElement root = JsonParser.parseString(geometryData);
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("minecraft:geometry") && o.get("minecraft:geometry").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("minecraft:geometry")) {
                        if (!el.isJsonObject()) {
                            continue;
                        }
                        JsonObject geo = el.getAsJsonObject();
                        if (geo.has("description") && geo.get("description").isJsonObject()) {
                            JsonObject desc = geo.getAsJsonObject("description");
                            if (desc.has("identifier") && !desc.get("identifier").isJsonNull()) {
                                return desc.get("identifier").getAsString();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return "";
    }

    static byte[] imageDataToPng(org.cloudburstmc.protocol.bedrock.data.skin.ImageData image) {
        if (image == null || image.getImage() == null || image.getImage().length == 0) {
            return null;
        }
        byte[] raw = image.getImage();
        if (SkinService.looksLikePng(raw)) {
            return raw.clone();
        }
        if (SkinService.isLikelyRgba(raw.length)) {
            return SkinService.rgbaToPng(raw);
        }
        // Sized ImageData that is not a standard skin size — still try RGBA→PNG via dimensions
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > 0 && h > 0 && raw.length >= w * h * 4) {
            byte[] sliced = raw.length == w * h * 4 ? raw : java.util.Arrays.copyOf(raw, w * h * 4);
            if (SkinService.isLikelyRgba(sliced.length)) {
                return SkinService.rgbaToPng(sliced);
            }
        }
        return raw.clone();
    }

    /** Load Bedrock geometry JSON extract for the identifier (parity fixtures). */
    public static String loadParityGeometryJson(String geometryIdentifier) {
        String id = geometryIdentifier == null ? "geometry.humanoid.custom" : geometryIdentifier;
        boolean slim = id.contains("Slim") || id.contains("slim");
        String primary = slim
                ? "geometry.humanoid.customSlim.json"
                : "geometry.humanoid.custom.json";
        String text = readFixtureUtf8(ParityBand.of(ParityBand.DEFAULT).fixturePath("skin/" + primary));
        if ((text == null || text.isBlank()) && slim) {
            text = readFixtureUtf8(ParityBand.of(ParityBand.DEFAULT)
                    .fixturePath("skin/geometry.humanoid.custom.json"));
        }
        return text == null ? "" : text;
    }

    private static String readFixtureUtf8(String path) {
        try (InputStream in = BedrockCanonicalSkin.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static String defaultResourcePatch(String geometryName, boolean slim) {
        String geo = geometryName == null || geometryName.isBlank()
                ? (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom")
                : geometryName;
        return "{\"geometry\":{\"default\":\"" + geo + "\"}}";
    }

    public String toJson() {
        return BedrockCanonicalSkinJson.toJson(this);
    }

    public static BedrockCanonicalSkin fromJson(String json) {
        return BedrockCanonicalSkinJson.fromJson(json);
    }

    public String contentSha256() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(skinId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(geometryName.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(geometryData.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(animationData.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            if (skinPng != null) {
                md.update(skinPng);
            }
            md.update((byte) 0);
            if (capePng != null) {
                md.update(capePng);
            }
            md.update((byte) (slim ? 1 : 0));
            md.update(BedrockCanonicalSkinJson.piecesToJson(personaPieces).toString()
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    public BedrockCanonicalSkin withGeometryData(String geoData) {
        return new BedrockCanonicalSkin(
                uuid, skinId, playFabId, skinResourcePatch, skinPng, capePng, geometryName,
                geoData, geometryDataVersion, animationData, capeId, fullSkinId, slim, skinColor,
                personaPieces, pieceTints, premium, persona, capeOnClassic, primaryUser,
                overridingPlayerAppearance, trusted, profileHash, animations);
    }

    public BedrockCanonicalSkin ensureGeometryData() {
        if (geometryData != null && !geometryData.isBlank()) {
            return this;
        }
        return withGeometryData(loadParityGeometryJson(geometryName));
    }

    // --- accessors ---
    public UUID uuid() { return uuid; }
    public String skinId() { return skinId; }
    public String playFabId() { return playFabId; }
    public String skinResourcePatch() { return skinResourcePatch; }
    public byte[] skinPng() { return skinPng == null ? null : skinPng.clone(); }
    public byte[] capePng() { return capePng == null ? null : capePng.clone(); }
    public String geometryName() { return geometryName; }
    public String geometryData() { return geometryData; }
    public String geometryDataVersion() { return geometryDataVersion; }
    public String animationData() { return animationData; }
    public String capeId() { return capeId; }
    public String fullSkinId() { return fullSkinId; }
    public boolean slim() { return slim; }
    public String skinColor() { return skinColor; }
    public List<PersonaPiece> personaPieces() { return personaPieces; }
    public List<PieceTint> pieceTints() { return pieceTints; }
    public boolean premium() { return premium; }
    public boolean persona() { return persona; }
    public boolean capeOnClassic() { return capeOnClassic; }
    public boolean primaryUser() { return primaryUser; }
    public boolean overridingPlayerAppearance() { return overridingPlayerAppearance; }
    public String trusted() { return trusted; }
    public String profileHash() { return profileHash; }
    public List<SkinAnimation> animations() { return animations; }

    public record PersonaPiece(String pieceId, String pieceType, String packId, boolean isDefault, String productId) {}
    public record PieceTint(String pieceType, List<String> colors) {}
    public record SkinAnimation(int imageWidth, int imageHeight, byte[] imageData, int type, float frames) {}

    static Gson gson() {
        return BedrockCanonicalSkinJson.gson();
    }
}
