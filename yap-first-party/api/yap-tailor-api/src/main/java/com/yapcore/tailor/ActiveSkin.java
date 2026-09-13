package com.yapcore.tailor;

import java.util.Optional;
import java.util.UUID;

/**
 * Currently applied custom skin for a player.
 *
 * @param playerUuid             player id
 * @param sourceUrl              skin texture URL, or null if cleared
 * @param capeUrl                cape texture URL, or null
 * @param model                  slim or wide
 * @param textureValueBase64     optional Mojang textures property value (base64 JSON)
 * @param updatedAtMs            last update epoch millis
 * @param bedrockCanonicalJson   optional Bedrock-canonical skin JSON (chassis-owned; often null)
 * @param geometryName           Bedrock geometry identifier, or null
 * @param skinId                 Bedrock skin id (e.g. Standard_Custom), or null
 * @param activeSlotId           wardrobe slot currently worn, or null when not from a slot
 */
public record ActiveSkin(
        UUID playerUuid,
        String sourceUrl,
        String capeUrl,
        SkinModel model,
        String textureValueBase64,
        long updatedAtMs,
        String bedrockCanonicalJson,
        String geometryName,
        String skinId,
        Long activeSlotId
) {
    public Optional<String> textureValue() {
        return textureValueBase64 == null || textureValueBase64.isBlank()
                ? Optional.empty()
                : Optional.of(textureValueBase64);
    }

    /** Build an active skin with model-derived geometry/skinId and null canonical (chassis owns it). */
    public static ActiveSkin of(
            UUID playerUuid,
            String sourceUrl,
            String capeUrl,
            SkinModel model,
            String textureValueBase64,
            long updatedAtMs) {
        return of(playerUuid, sourceUrl, capeUrl, model, textureValueBase64, updatedAtMs, null);
    }

    public static ActiveSkin of(
            UUID playerUuid,
            String sourceUrl,
            String capeUrl,
            SkinModel model,
            String textureValueBase64,
            long updatedAtMs,
            Long activeSlotId) {
        SkinModel m = model == null ? SkinModel.WIDE : model;
        boolean slim = m == SkinModel.SLIM;
        return new ActiveSkin(
                playerUuid,
                sourceUrl,
                capeUrl,
                m,
                textureValueBase64,
                updatedAtMs,
                null,
                slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom",
                slim ? "Standard_CustomSlim" : "Standard_Custom",
                activeSlotId);
    }

    /** Copy with optional chassis-returned canonical JSON. */
    public ActiveSkin withBedrockCanonicalJson(String json) {
        return new ActiveSkin(
                playerUuid, sourceUrl, capeUrl, model, textureValueBase64, updatedAtMs,
                json, geometryName, skinId, activeSlotId);
    }

    public ActiveSkin withActiveSlotId(Long slotId) {
        return new ActiveSkin(
                playerUuid, sourceUrl, capeUrl, model, textureValueBase64, updatedAtMs,
                bedrockCanonicalJson, geometryName, skinId, slotId);
    }
}
