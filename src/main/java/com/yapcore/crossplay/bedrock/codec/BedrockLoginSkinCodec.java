package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.skin.BedrockCanonicalSkin;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * PlayerSkin / canonical skin helpers (split from {@link BedrockLoginCodec} for the ≤500-line domain gate).
 */
final class BedrockLoginSkinCodec {
    private BedrockLoginSkinCodec() {}

    /**
     * Legacy string-payload PlayerSkin (pre-full Skin structure). Prefer the byte[] overload.
     */
    static ByteBuf playerSkin(UUID uuid, String skinId, String skinDataBase64, String capeData, String geometry) {
        byte[] skin = BedrockLoginSkinImages.decodeMaybeBase64(skinDataBase64);
        byte[] cape = BedrockLoginSkinImages.decodeMaybeBase64(capeData);
        boolean slim = (skinId != null && skinId.toLowerCase().contains("slim"))
                || (geometry != null && geometry.toLowerCase().contains("slim"));
        return playerSkin(uuid, skinId, skin, cape, geometry, slim, 2207);
    }

    /**
     * Full PlayerSkin with real SkinImage bytes (PNG decoded to RGBA, or raw RGBA).
     * Builds a temporary classic {@link BedrockCanonicalSkin} so {@code geometry_data} is never empty
     * when a PNG is present.
     */
    static ByteBuf playerSkin(UUID uuid, String skinId, byte[] skinRgbaOrPng, byte[] cape,
                                     String geometry, boolean slim, int protocol) {
        BedrockCanonicalSkin canonical = BedrockCanonicalSkin.classicPng(uuid, skinRgbaOrPng, cape, slim);
        if (skinId != null && !skinId.isBlank()
                || geometry != null && !geometry.isBlank()) {
            String id = skinId == null || skinId.isBlank()
                    ? (slim ? "Standard_CustomSlim" : "Standard_Custom") : skinId;
            String geo = geometry == null || geometry.isBlank()
                    ? (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom") : geometry;
            canonical = new BedrockCanonicalSkin(
                    uuid, id, "", BedrockCanonicalSkin.defaultResourcePatch(geo, slim),
                    skinRgbaOrPng, cape, geo, BedrockCanonicalSkin.loadParityGeometryJson(geo),
                    "1.14.0", "", "", id, slim, "#0", List.of(), List.of(),
                    false, false, false, true, false, "true", "", List.of());
        }
        return playerSkin(canonical.ensureGeometryData(), protocol);
    }

    /**
     * PlayerSkin from Bedrock-canonical payload (Phase 1). Writes full Skin fields including
     * non-empty {@code geometry_data}, persona pieces, and animations when present.
     */
    static ByteBuf playerSkin(BedrockCanonicalSkin skin, int protocol) {
        if (skin == null) {
            throw new IllegalArgumentException("skin");
        }
        BedrockCanonicalSkin s = skin.ensureGeometryData();
        int proto = protocol > 0 ? protocol : 2207;
        byte[] png = s.skinPng();
        byte[] cape = s.capePng();
        int est = 1024 + (png == null ? 0 : png.length * 4) + (cape == null ? 0 : cape.length * 4)
                + s.geometryData().length() + s.animationData().length();
        ByteBuf out = Unpooled.buffer(est);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_PLAYER_SKIN);
        out.writeLongLE(s.uuid().getMostSignificantBits());
        out.writeLongLE(s.uuid().getLeastSignificantBits());
        if (proto >= 2168) {
            writeCanonicalSkinV2168(out, s);
        } else {
            writeCanonicalSkin(out, s);
        }
        writeString(out, s.skinId()); // new_skin_name
        writeString(out, ""); // old_skin_name
        out.writeBoolean(true); // trusted / local
        return out;
    }

    /**
     * Parse a PlayerSkin packet body (UUID + Skin [+ optional new/old names + trusted]) into
     * {@link BedrockCanonicalSkin}. Prefer Cloudburst {@code helper.readSkin} when
     * {@code protocol >= 2168}; otherwise mirror {@link #writeCanonicalSkin}.
     */
    static BedrockCanonicalSkin readPlayerSkinBody(ByteBuf body, int protocol) {
        if (body == null || body.readableBytes() < 16) {
            throw new IllegalArgumentException("PlayerSkin body too short");
        }
        long msb = body.readLongLE();
        long lsb = body.readLongLE();
        UUID uuid = new UUID(msb, lsb);
        int proto = protocol > 0 ? protocol : 2207;
        BedrockCanonicalSkin skin;
        if (proto >= 2168) {
            int skinMark = body.readerIndex();
            try {
                org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin ss =
                        com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession.create(proto)
                                .helper()
                                .readSkin(body);
                skin = BedrockCanonicalSkin.fromSerializedSkin(uuid, ss);
            } catch (Exception e) {
                body.readerIndex(skinMark);
                skin = readCanonicalSkinV2168(body, uuid);
            }
        } else {
            skin = readCanonicalSkin(body, uuid);
        }
        // Trailing new_skin_name / old_skin_name / trusted — ignore if present
        try {
            if (body.isReadable()) {
                readString(body);
            }
            if (body.isReadable()) {
                readString(body);
            }
            if (body.isReadable()) {
                body.readBoolean();
            }
        } catch (Exception ignored) {
            // partial trailing fields are fine
        }
        return skin;
    }

    /** Mirror of {@link #writeCanonicalSkin} (pre-2168 string arm_size / piece types). */
    static BedrockCanonicalSkin readCanonicalSkin(ByteBuf in, UUID uuid) {
        String skinId = readString(in);
        String playFabId = readString(in);
        String resourcePatch = readString(in);
        BedrockLoginSkinImages.SkinImage skinImg = BedrockLoginSkinImages.readSkinImage(in);
        int animCount = in.readIntLE();
        List<BedrockCanonicalSkin.SkinAnimation> anims = new java.util.ArrayList<>(Math.max(0, animCount));
        for (int i = 0; i < animCount; i++) {
            anims.add(readAnimationLegacy(in));
        }
        BedrockLoginSkinImages.SkinImage capeImg = BedrockLoginSkinImages.readSkinImage(in);
        String geometryData = readString(in);
        String geometryDataVersion = readString(in);
        String animationData = readString(in);
        String capeId = readString(in);
        String fullSkinId = readString(in);
        String armSize = readString(in);
        String skinColor = readString(in);
        int pieceCount = in.readIntLE();
        List<BedrockCanonicalSkin.PersonaPiece> pieces = new java.util.ArrayList<>(Math.max(0, pieceCount));
        for (int i = 0; i < pieceCount; i++) {
            pieces.add(new BedrockCanonicalSkin.PersonaPiece(
                    readString(in), readString(in), readString(in), in.readBoolean(), readString(in)));
        }
        int tintCount = in.readIntLE();
        List<BedrockCanonicalSkin.PieceTint> tints = new java.util.ArrayList<>(Math.max(0, tintCount));
        for (int i = 0; i < tintCount; i++) {
            String pieceType = readString(in);
            int colorCount = in.readIntLE();
            List<String> colors = new java.util.ArrayList<>(Math.max(0, colorCount));
            for (int c = 0; c < colorCount; c++) {
                colors.add(readString(in));
            }
            tints.add(new BedrockCanonicalSkin.PieceTint(pieceType, colors));
        }
        boolean premium = in.readBoolean();
        boolean persona = in.readBoolean();
        boolean capeOnClassic = in.readBoolean();
        boolean primaryUser = in.readBoolean();
        boolean overriding = in.readBoolean();
        return finishReadCanonical(uuid, skinId, playFabId, resourcePatch, skinImg, capeImg,
                geometryData, geometryDataVersion, animationData, capeId, fullSkinId,
                armSize, skinColor, pieces, tints, anims,
                premium, persona, capeOnClassic, primaryUser, overriding, "true", "");
    }

    /** Mirror of {@link #writeCanonicalSkinV2168} (byte arm_size, ARGB colour, varint arrays). */
    static BedrockCanonicalSkin readCanonicalSkinV2168(ByteBuf in, UUID uuid) {
        String skinId = readString(in);
        String playFabId = readString(in);
        String resourcePatch = readString(in);
        BedrockLoginSkinImages.SkinImage skinImg = BedrockLoginSkinImages.readSkinImage(in);
        int animCount = readUnsignedVarInt(in);
        List<BedrockCanonicalSkin.SkinAnimation> anims = new java.util.ArrayList<>(Math.max(0, animCount));
        for (int i = 0; i < animCount; i++) {
            anims.add(readAnimationV2168(in));
        }
        BedrockLoginSkinImages.SkinImage capeImg = BedrockLoginSkinImages.readSkinImage(in);
        String geometryData = readString(in);
        String geometryDataVersion = readString(in);
        String animationData = readString(in);
        String capeId = readString(in);
        String fullSkinId = readString(in);
        int armByte = in.readUnsignedByte();
        String armSize = armByte == 1 ? "wide" : "slim";
        int colorArgb = in.readIntLE();
        String skinColor = colorArgb == 0 ? "#0" : String.format("#%08X", colorArgb);
        int pieceCount = readUnsignedVarInt(in);
        List<BedrockCanonicalSkin.PersonaPiece> pieces = new java.util.ArrayList<>(Math.max(0, pieceCount));
        for (int i = 0; i < pieceCount; i++) {
            String pieceId = readString(in);
            int typeOrd = in.readIntLE();
            UUID packUuid = new UUID(in.readLongLE(), in.readLongLE());
            boolean isDefault = in.readBoolean();
            String productId = readString(in);
            pieces.add(new BedrockCanonicalSkin.PersonaPiece(
                    pieceId, BedrockLoginSkinImages.personaPieceTypeName(typeOrd), packUuid.toString(), isDefault, productId));
        }
        int tintCount = readUnsignedVarInt(in);
        List<BedrockCanonicalSkin.PieceTint> tints = new java.util.ArrayList<>(Math.max(0, tintCount));
        for (int i = 0; i < tintCount; i++) {
            String pieceType = readString(in);
            List<String> colors = new java.util.ArrayList<>(4);
            for (int c = 0; c < 4; c++) {
                int argb = in.readIntLE();
                colors.add(argb == 0 ? "#0" : String.format("#%08X", argb));
            }
            tints.add(new BedrockCanonicalSkin.PieceTint(pieceType, colors));
        }
        boolean premium = in.readBoolean();
        boolean persona = in.readBoolean();
        boolean capeOnClassic = in.readBoolean();
        boolean primaryUser = in.readBoolean();
        boolean overriding = in.readBoolean();
        String trusted = in.isReadable() ? readString(in) : "true";
        String profileHash = in.isReadable() ? readString(in) : "";
        return finishReadCanonical(uuid, skinId, playFabId, resourcePatch, skinImg, capeImg,
                geometryData, geometryDataVersion, animationData, capeId, fullSkinId,
                armSize, skinColor, pieces, tints, anims,
                premium, persona, capeOnClassic, primaryUser, overriding, trusted, profileHash);
    }

    static BedrockCanonicalSkin finishReadCanonical(
            UUID uuid, String skinId, String playFabId, String resourcePatch,
            BedrockLoginSkinImages.SkinImage skinImg, BedrockLoginSkinImages.SkinImage capeImg,
            String geometryData, String geometryDataVersion, String animationData,
            String capeId, String fullSkinId, String armSize, String skinColor,
            List<BedrockCanonicalSkin.PersonaPiece> pieces,
            List<BedrockCanonicalSkin.PieceTint> tints,
            List<BedrockCanonicalSkin.SkinAnimation> anims,
            boolean premium, boolean persona, boolean capeOnClassic, boolean primaryUser,
            boolean overriding, String trusted, String profileHash) {
        boolean slim = armSize != null && armSize.equalsIgnoreCase("slim");
        String geoName = BedrockCanonicalSkin.geometryNameFromResourcePatch(resourcePatch);
        if (geoName == null || geoName.isBlank()) {
            geoName = BedrockCanonicalSkin.geometryNameFromGeometryData(geometryData);
        }
        if (geoName == null || geoName.isBlank()) {
            geoName = slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom";
        }
        if (!slim && geoName.toLowerCase().contains("slim")) {
            slim = true;
        }
        byte[] skinPng = BedrockLoginSkinImages.skinImageToPng(skinImg);
        byte[] capePng = BedrockLoginSkinImages.skinImageToPng(capeImg);
        String geoData = geometryData == null ? "" : geometryData;
        return new BedrockCanonicalSkin(
                uuid, skinId, playFabId,
                resourcePatch == null || resourcePatch.isBlank()
                        ? BedrockCanonicalSkin.defaultResourcePatch(geoName, slim) : resourcePatch,
                skinPng, capePng, geoName, geoData,
                geometryDataVersion, animationData, capeId, fullSkinId, slim,
                skinColor == null || skinColor.isBlank() ? "#0" : skinColor,
                pieces, tints, premium, persona, capeOnClassic, primaryUser, overriding,
                trusted, profileHash, anims);
    }


    static BedrockCanonicalSkin.SkinAnimation readAnimationLegacy(ByteBuf in) {
        BedrockLoginSkinImages.SkinImage img = BedrockLoginSkinImages.readSkinImage(in);
        int type = in.readIntLE();
        float frames = in.readFloatLE();
        return new BedrockCanonicalSkin.SkinAnimation(
                img.width(), img.height(), img.data() == null ? new byte[0] : img.data(), type, frames);
    }

    static BedrockCanonicalSkin.SkinAnimation readAnimationV2168(ByteBuf in) {
        BedrockLoginSkinImages.SkinImage img = BedrockLoginSkinImages.readSkinImage(in);
        int type = readUnsignedVarInt(in);
        float frames = in.readFloatLE();
        readUnsignedVarInt(in); // expression type (Cloudburst v2168)
        return new BedrockCanonicalSkin.SkinAnimation(
                img.width(), img.height(), img.data() == null ? new byte[0] : img.data(), type, frames);
    }

    /** Skin matching 1.21.50–1.21.60 with real image payloads (legacy path → canonical). */
    static void writeSkin(ByteBuf out, String skinId, String geometry, boolean slim,
                                  BedrockLoginSkinImages.SkinImage skin, BedrockLoginSkinImages.SkinImage cape) {
        UUID uuid = new UUID(0L, 0L);
        byte[] skinBytes = skin == null ? null : skin.data();
        byte[] capeBytes = cape == null ? null : cape.data();
        String geo = geometry == null || geometry.isBlank()
                ? (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom") : geometry;
        String id = skinId == null ? "Standard_Custom" : skinId;
        BedrockCanonicalSkin canonical = new BedrockCanonicalSkin(
                uuid, id, "", BedrockCanonicalSkin.defaultResourcePatch(geo, slim),
                skinBytes, capeBytes, geo, BedrockCanonicalSkin.loadParityGeometryJson(geo),
                "1.14.0", "", "", id, slim, "#0", List.of(), List.of(),
                false, false, false, true, false, "true", "", List.of()).ensureGeometryData();
        // Prefer pre-decoded SkinImage dimensions when raw RGBA was already sized
        writeCanonicalSkin(out, canonical, skin, cape);
    }

    /** Skin for proto ≥2168 with real image payloads (legacy path → canonical). */
    static void writeSkinV2168(ByteBuf out, String skinId, String geometry, boolean slim,
                                       BedrockLoginSkinImages.SkinImage skin, BedrockLoginSkinImages.SkinImage cape) {
        UUID uuid = new UUID(0L, 0L);
        byte[] skinBytes = skin == null ? null : skin.data();
        byte[] capeBytes = cape == null ? null : cape.data();
        String geo = geometry == null || geometry.isBlank()
                ? (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom") : geometry;
        String id = skinId == null ? "Standard_Custom" : skinId;
        BedrockCanonicalSkin canonical = new BedrockCanonicalSkin(
                uuid, id, "", BedrockCanonicalSkin.defaultResourcePatch(geo, slim),
                skinBytes, capeBytes, geo, BedrockCanonicalSkin.loadParityGeometryJson(geo),
                "1.14.0", "", "", id, slim, "#0", List.of(), List.of(),
                false, false, false, true, false, "true", "", List.of()).ensureGeometryData();
        writeCanonicalSkinV2168(out, canonical, skin, cape);
    }

    static void writeCanonicalSkin(ByteBuf out, BedrockCanonicalSkin skin) {
        writeCanonicalSkin(out, skin, BedrockLoginSkinImages.toSkinImage(skin.skinPng()), BedrockLoginSkinImages.toSkinImage(skin.capePng()));
    }

    /** Full Skin structure (pre-2168 / 1.21.x layout) from {@link BedrockCanonicalSkin}. */
    static void writeCanonicalSkin(ByteBuf out, BedrockCanonicalSkin skin,
                                           BedrockLoginSkinImages.SkinImage skinImg, BedrockLoginSkinImages.SkinImage capeImg) {
        BedrockCanonicalSkin s = skin.ensureGeometryData();
        writeString(out, s.skinId());
        writeString(out, s.playFabId());
        writeString(out, s.skinResourcePatch());
        BedrockLoginSkinImages.writeSkinImage(out, skinImg == null ? BedrockLoginSkinImages.SkinImage.empty() : skinImg);
        List<BedrockCanonicalSkin.SkinAnimation> anims = s.animations();
        out.writeIntLE(anims.size());
        for (BedrockCanonicalSkin.SkinAnimation a : anims) {
            writeAnimationLegacy(out, a);
        }
        BedrockLoginSkinImages.writeSkinImage(out, capeImg == null ? BedrockLoginSkinImages.SkinImage.empty() : capeImg);
        writeString(out, s.geometryData());
        writeString(out, s.geometryDataVersion());
        writeString(out, s.animationData());
        writeString(out, s.capeId());
        writeString(out, s.fullSkinId());
        writeString(out, s.slim() ? "slim" : "wide");
        writeString(out, s.skinColor() == null || s.skinColor().isBlank() ? "#0" : s.skinColor());
        out.writeIntLE(s.personaPieces().size());
        for (BedrockCanonicalSkin.PersonaPiece p : s.personaPieces()) {
            writeString(out, p.pieceId() == null ? "" : p.pieceId());
            writeString(out, p.pieceType() == null ? "" : p.pieceType());
            writeString(out, p.packId() == null ? "" : p.packId());
            out.writeBoolean(p.isDefault());
            writeString(out, p.productId() == null ? "" : p.productId());
        }
        out.writeIntLE(s.pieceTints().size());
        for (BedrockCanonicalSkin.PieceTint t : s.pieceTints()) {
            writeString(out, t.pieceType() == null ? "" : t.pieceType());
            List<String> colors = t.colors() == null ? List.of() : t.colors();
            out.writeIntLE(colors.size());
            for (String c : colors) {
                writeString(out, c == null ? "" : c);
            }
        }
        out.writeBoolean(s.premium());
        out.writeBoolean(s.persona());
        out.writeBoolean(s.capeOnClassic());
        out.writeBoolean(s.primaryUser());
        out.writeBoolean(s.overridingPlayerAppearance());
    }

    static void writeCanonicalSkinV2168(ByteBuf out, BedrockCanonicalSkin skin) {
        writeCanonicalSkinV2168(out, skin, BedrockLoginSkinImages.toSkinImage(skin.skinPng()), BedrockLoginSkinImages.toSkinImage(skin.capePng()));
    }

    /** Full Skin for proto ≥2168 (Cloudburst {@code BedrockCodecHelper_v2168}) from canonical. */
    static void writeCanonicalSkinV2168(ByteBuf out, BedrockCanonicalSkin skin,
                                                BedrockLoginSkinImages.SkinImage skinImg, BedrockLoginSkinImages.SkinImage capeImg) {
        BedrockCanonicalSkin s = skin.ensureGeometryData();
        writeString(out, s.skinId());
        writeString(out, s.playFabId());
        writeString(out, s.skinResourcePatch());
        BedrockLoginSkinImages.writeSkinImage(out, skinImg == null ? BedrockLoginSkinImages.SkinImage.empty() : skinImg);
        List<BedrockCanonicalSkin.SkinAnimation> anims = s.animations();
        writeUnsignedVarInt(out, anims.size());
        for (BedrockCanonicalSkin.SkinAnimation a : anims) {
            writeAnimationV2168(out, a);
        }
        BedrockLoginSkinImages.writeSkinImage(out, capeImg == null ? BedrockLoginSkinImages.SkinImage.empty() : capeImg);
        writeString(out, s.geometryData());
        writeString(out, s.geometryDataVersion());
        writeString(out, s.animationData());
        writeString(out, s.capeId());
        writeString(out, s.fullSkinId());
        out.writeByte(s.slim() ? 0 : 1); // arm_size: 0=slim, 1=wide
        out.writeIntLE(BedrockLoginSkinImages.parseSkinColorArgb(s.skinColor()));
        writeUnsignedVarInt(out, s.personaPieces().size());
        for (BedrockCanonicalSkin.PersonaPiece p : s.personaPieces()) {
            writeString(out, p.pieceId() == null ? "" : p.pieceId());
            out.writeIntLE(BedrockLoginSkinImages.personaPieceTypeOrdinal(p.pieceType()));
            BedrockLoginSkinImages.writeUuidLe(out, BedrockLoginSkinImages.parseUuidOrNil(p.packId()));
            out.writeBoolean(p.isDefault());
            writeString(out, p.productId() == null ? "" : p.productId());
        }
        writeUnsignedVarInt(out, s.pieceTints().size());
        for (BedrockCanonicalSkin.PieceTint t : s.pieceTints()) {
            writeString(out, t.pieceType() == null ? "" : t.pieceType());
            List<String> colors = t.colors() == null ? List.of() : t.colors();
            // Cloudburst v2168 expects exactly 4 ARGB colours
            for (int i = 0; i < 4; i++) {
                String c = i < colors.size() ? colors.get(i) : "#0";
                out.writeIntLE(BedrockLoginSkinImages.parseSkinColorArgb(c));
            }
        }
        out.writeBoolean(s.premium());
        out.writeBoolean(s.persona());
        out.writeBoolean(s.capeOnClassic());
        out.writeBoolean(s.primaryUser());
        out.writeBoolean(s.overridingPlayerAppearance());
        writeString(out, s.trusted() == null || s.trusted().isBlank() ? "true" : s.trusted());
        writeString(out, s.profileHash() == null ? "" : s.profileHash());
    }

    static void writeAnimationLegacy(ByteBuf out, BedrockCanonicalSkin.SkinAnimation a) {
        byte[] data = a.imageData() == null ? new byte[0] : a.imageData();
        BedrockLoginSkinImages.writeSkinImage(out, new BedrockLoginSkinImages.SkinImage(a.imageWidth(), a.imageHeight(), data));
        out.writeIntLE(a.type());
        out.writeFloatLE(a.frames());
    }

    static void writeAnimationV2168(ByteBuf out, BedrockCanonicalSkin.SkinAnimation a) {
        byte[] data = a.imageData() == null ? new byte[0] : a.imageData();
        BedrockLoginSkinImages.writeSkinImage(out, new BedrockLoginSkinImages.SkinImage(a.imageWidth(), a.imageHeight(), data));
        writeUnsignedVarInt(out, a.type());
        out.writeFloatLE(a.frames());
        writeUnsignedVarInt(out, 0); // expression type
    }
}
