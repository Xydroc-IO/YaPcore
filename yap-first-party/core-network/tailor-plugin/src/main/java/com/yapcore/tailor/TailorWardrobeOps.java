package com.yapcore.tailor;

import com.yapcore.tailor.db.TailorDatabase;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Wardrobe slot CRUD and apply paths for {@link TailorServiceImpl}. */
final class TailorWardrobeOps {

    private final TailorPlugin plugin;
    private final TailorConfig config;
    private final TailorDatabase database;
    private final SkinImageService images;
    private final TailorServiceImpl service;

    TailorWardrobeOps(
            TailorPlugin plugin,
            TailorConfig config,
            TailorDatabase database,
            SkinImageService images,
            TailorServiceImpl service) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.images = images;
        this.service = service;
    }

    List<WardrobeSlot> listWardrobe(UUID playerUuid) throws TailorException {
        try {
            List<WardrobeSlot> slots = database.listWardrobe(playerUuid);
            List<WardrobeSlot> out = new ArrayList<>(slots.size());
            for (WardrobeSlot slot : slots) {
                out.add(ensureSlotAssets(slot));
            }
            return out;
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Failed to list wardrobe", e);
        }
    }

    WardrobeSlot saveWardrobeSlot(UUID playerUuid, String name) throws TailorException {
        if (name == null || name.isBlank()) {
            throw new TailorException("Slot name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 32) {
            throw new TailorException("Slot name too long (max 32)");
        }
        if (!config.hasPublicSkinHost()) {
            throw new TailorException(
                    "Set skin-host-public-base-url in YaPTailor config so wardrobe thumbnails can be served");
        }
        ActiveSkin active;
        try {
            active = database.loadActive(playerUuid)
                    .orElseThrow(() -> new TailorException("No active skin to save"));
            byte[] skinBytes = images.readStoredSkin(playerUuid);
            if (skinBytes == null || skinBytes.length == 0) {
                if (active.sourceUrl() == null || active.sourceUrl().isBlank()) {
                    throw new TailorException("No skin PNG on disk — apply a skin first");
                }
                skinBytes = images.downloadAndValidate(active.sourceUrl());
                images.storeSkinBytes(playerUuid, skinBytes, active.sourceUrl());
            }
            byte[] capeBytes = images.readStoredCape(playerUuid);
            if ((capeBytes == null || capeBytes.length == 0)
                    && active.capeUrl() != null && !active.capeUrl().isBlank()) {
                try {
                    capeBytes = images.downloadAndValidate(active.capeUrl());
                    images.storeCapeBytes(playerUuid, capeBytes, active.capeUrl());
                } catch (TailorException ignored) {
                    capeBytes = null;
                }
            }

            Optional<WardrobeSlot> existing = database.findWardrobeByName(playerUuid, trimmed);
            long slotId;
            long createdAt;
            if (existing.isPresent()) {
                slotId = existing.get().id();
                createdAt = existing.get().createdAtMs();
            } else {
                int count = database.countWardrobe(playerUuid);
                if (count >= config.maxSlots()) {
                    throw new TailorException("Wardrobe full (max " + config.maxSlots() + " slots)");
                }
                // Insert placeholder row to obtain id, then overwrite URLs with wardrobe public paths
                WardrobeSlot inserted = database.insertWardrobe(
                        playerUuid,
                        trimmed,
                        active.sourceUrl(),
                        active.capeUrl(),
                        active.model(),
                        active.bedrockCanonicalJson(),
                        active.geometryName(),
                        active.skinId());
                slotId = inserted.id();
                createdAt = inserted.createdAtMs();
            }

            String slotSkinUrl = images.storeWardrobeSkin(playerUuid, slotId, skinBytes);
            String slotCapeUrl = null;
            if (capeBytes != null && capeBytes.length > 0) {
                slotCapeUrl = images.storeWardrobeCape(playerUuid, slotId, capeBytes);
            } else {
                images.storeWardrobeCape(playerUuid, slotId, null);
            }
            WardrobeSlot saved = new WardrobeSlot(
                    slotId,
                    playerUuid,
                    trimmed,
                    slotSkinUrl,
                    slotCapeUrl,
                    active.model(),
                    createdAt,
                    System.currentTimeMillis(),
                    active.bedrockCanonicalJson(),
                    active.geometryName(),
                    active.skinId());
            database.updateWardrobe(saved);
            return saved;
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Failed to save wardrobe slot", e);
        }
    }

    void deleteWardrobeSlot(UUID playerUuid, long slotId) throws TailorException {
        try {
            if (database.findWardrobe(playerUuid, slotId).isEmpty()) {
                throw new TailorException("Wardrobe slot not found");
            }
            database.deleteWardrobe(playerUuid, slotId);
            images.deleteWardrobeFiles(playerUuid, slotId);
            Optional<ActiveSkin> active = database.loadActive(playerUuid);
            if (active.isPresent() && active.get().activeSlotId() != null
                    && active.get().activeSlotId() == slotId) {
                database.saveActive(active.get().withActiveSlotId(null));
            }
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Failed to delete wardrobe slot", e);
        }
    }

    WardrobeSlot renameSlot(UUID playerUuid, long slotId, String newName) throws TailorException {
        if (newName == null || newName.isBlank()) {
            throw new TailorException("New name is required");
        }
        String trimmed = newName.trim();
        if (trimmed.length() > 32) {
            throw new TailorException("Slot name too long (max 32)");
        }
        try {
            WardrobeSlot slot = database.findWardrobe(playerUuid, slotId)
                    .orElseThrow(() -> new TailorException("Wardrobe slot not found"));
            WardrobeSlot renamed = slot.withName(trimmed, System.currentTimeMillis());
            database.updateWardrobe(renamed);
            return renamed;
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Failed to rename wardrobe slot", e);
        }
    }

    ActiveSkin applyWardrobeSlot(Player player, long slotId) throws TailorException {
        service.checkCooldown(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        WardrobeSlot slot;
        try {
            slot = database.findWardrobe(uuid, slotId)
                    .orElseThrow(() -> new TailorException("Wardrobe slot not found"));
            slot = ensureSlotAssets(slot);
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        byte[] skinBytes = images.resolveWardrobeSkinBytes(uuid, slotId, slot.skinUrl());
        String publicUrl = images.storeSkinBytes(uuid, skinBytes, slot.skinUrl());
        String cape = null;
        byte[] capeBytes = images.readWardrobeCape(uuid, slotId);
        if (capeBytes == null || capeBytes.length == 0) {
            if (slot.capeUrl() != null && !slot.capeUrl().isBlank()) {
                try {
                    capeBytes = images.downloadAndValidate(slot.capeUrl());
                    images.storeWardrobeCape(uuid, slotId, capeBytes);
                } catch (TailorException ignored) {
                    capeBytes = null;
                }
            }
        }
        if (capeBytes != null && capeBytes.length > 0) {
            cape = images.storeCapeBytes(uuid, capeBytes, slot.capeUrl());
        }
        String textureValue = images.buildTextureValue(publicUrl, cape, slot.model());
        SkinModel model = slot.model() == null ? SkinModel.WIDE : slot.model();
        boolean slim = model == SkinModel.SLIM;
        String geometry = slot.geometryName() != null && !slot.geometryName().isBlank()
                ? slot.geometryName()
                : (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
        String skinId = slot.skinId() != null && !slot.skinId().isBlank()
                ? slot.skinId()
                : (slim ? "Standard_CustomSlim" : "Standard_Custom");
        ActiveSkin skin = new ActiveSkin(
                uuid,
                publicUrl,
                cape,
                model,
                textureValue,
                System.currentTimeMillis(),
                slot.bedrockCanonicalJson(),
                geometry,
                skinId,
                slotId);
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to apply wardrobe slot", e);
        }
        service.applyToOnline(player, skin);
        return skin;
    }

    /**
     * Ensure slot PNG exists on disk and {@code skin_url} points at the stable public wardrobe URL.
     * Backfills legacy URL-only rows.
     */
    WardrobeSlot ensureSlotAssets(WardrobeSlot slot) throws TailorException {
        if (slot == null) {
            throw new TailorException("Missing wardrobe slot");
        }
        if (!config.hasPublicSkinHost()) {
            return slot;
        }
        UUID uuid = slot.playerUuid();
        long id = slot.id();
        byte[] local = images.readWardrobeSkin(uuid, id);
        String expected = config.publicWardrobeSkinUrl(uuid, id);
        if (local == null || local.length == 0) {
            if (slot.skinUrl() == null || slot.skinUrl().isBlank()) {
                return slot;
            }
            try {
                local = images.downloadAndValidate(slot.skinUrl());
                images.storeWardrobeSkin(uuid, id, local);
            } catch (TailorException e) {
                plugin.getLogger().fine("Wardrobe backfill failed for slot " + id + ": " + e.getMessage());
                return slot;
            }
        } else if (expected != null && (slot.skinUrl() == null || !slot.skinUrl().equals(expected))) {
            // Already have bytes but URL not rewritten — rewrite DB to stable public path
            try {
                ChassisSkinPush.writeSharedWardrobeSkinPng(plugin, uuid, id, local);
            } catch (java.io.IOException e) {
                throw new TailorException("Failed to mirror wardrobe skin to chassis: " + e.getMessage(), e);
            }
        }
        String skinUrl = expected != null ? expected : slot.skinUrl();
        String capeUrl = slot.capeUrl();
        byte[] capeLocal = images.readWardrobeCape(uuid, id);
        String expectedCape = config.publicWardrobeCapeUrl(uuid, id);
        if (capeLocal == null || capeLocal.length == 0) {
            if (capeUrl != null && !capeUrl.isBlank()) {
                try {
                    capeLocal = images.downloadAndValidate(capeUrl);
                    capeUrl = images.storeWardrobeCape(uuid, id, capeLocal);
                } catch (TailorException ignored) {
                    // keep prior cape URL
                }
            }
        } else if (expectedCape != null) {
            try {
                ChassisSkinPush.writeSharedWardrobeCapePng(plugin, uuid, id, capeLocal);
            } catch (java.io.IOException e) {
                throw new TailorException("Failed to mirror wardrobe cape to chassis: " + e.getMessage(), e);
            }
            capeUrl = expectedCape;
        }
        WardrobeSlot updated = slot.withUrls(skinUrl, capeUrl, slot.updatedAtMs());
        if (!java.util.Objects.equals(updated.skinUrl(), slot.skinUrl())
                || !java.util.Objects.equals(updated.capeUrl(), slot.capeUrl())) {
            try {
                database.updateWardrobe(updated.withUrls(skinUrl, capeUrl, System.currentTimeMillis()));
            } catch (Exception e) {
                throw new TailorException("Failed to update wardrobe slot URLs", e);
            }
        }
        return updated;
    }
}
