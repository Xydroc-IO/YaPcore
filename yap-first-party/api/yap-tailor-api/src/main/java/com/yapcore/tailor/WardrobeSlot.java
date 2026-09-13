package com.yapcore.tailor;

import java.util.UUID;

/**
 * Saved wardrobe entry for a player.
 *
 * @param id                     database primary key
 * @param playerUuid             owner
 * @param name                   display name
 * @param skinUrl                skin texture URL (nullable when using stored texture value only)
 * @param capeUrl                cape texture URL, or null
 * @param model                  slim or wide
 * @param createdAtMs            creation epoch millis
 * @param updatedAtMs            last update epoch millis
 * @param bedrockCanonicalJson   optional Bedrock-canonical skin JSON copied from active skin on save
 * @param geometryName           Bedrock geometry identifier, or null
 * @param skinId                 Bedrock skin id (e.g. Standard_Custom), or null
 */
public record WardrobeSlot(
        long id,
        UUID playerUuid,
        String name,
        String skinUrl,
        String capeUrl,
        SkinModel model,
        long createdAtMs,
        long updatedAtMs,
        String bedrockCanonicalJson,
        String geometryName,
        String skinId
) {
    /** Compact constructor when Bedrock metadata is unknown. */
    public WardrobeSlot(
            long id,
            UUID playerUuid,
            String name,
            String skinUrl,
            String capeUrl,
            SkinModel model,
            long createdAtMs,
            long updatedAtMs) {
        this(id, playerUuid, name, skinUrl, capeUrl, model, createdAtMs, updatedAtMs, null, null, null);
    }

    public WardrobeSlot withName(String newName, long newUpdatedAtMs) {
        return new WardrobeSlot(
                id, playerUuid, newName, skinUrl, capeUrl, model, createdAtMs, newUpdatedAtMs,
                bedrockCanonicalJson, geometryName, skinId);
    }

    public WardrobeSlot withUrls(String newSkinUrl, String newCapeUrl, long newUpdatedAtMs) {
        return new WardrobeSlot(
                id, playerUuid, name, newSkinUrl, newCapeUrl, model, createdAtMs, newUpdatedAtMs,
                bedrockCanonicalJson, geometryName, skinId);
    }
}
