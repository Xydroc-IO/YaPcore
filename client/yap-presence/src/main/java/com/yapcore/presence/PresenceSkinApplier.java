package com.yapcore.presence;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies Tailor wardrobe skins to the live JE avatar ({@link net.minecraft.client.player.AbstractClientPlayer#getSkin()})
 * — not only the wardrobe menu preview.
 */
public final class PresenceSkinApplier {

    private PresenceSkinApplier() {
    }

    /** Optimistic local wear: texture may already be ready (slot / browse) while the server catches up. */
    public static void applyLocal(UUID uuid, boolean slim, String skinUrl, Identifier readyBody) {
        if (uuid == null) {
            return;
        }
        String url = skinUrl == null ? "" : skinUrl.trim();
        PresenceSkinStore.put(new PresenceSkin(uuid, slim, url, ""));
        if (readyBody != null) {
            PresenceTextureCache.bindReady(uuid, readyBody);
        } else if (!url.isBlank()) {
            PresenceTextureCache.ensureDownloaded(uuid, url);
        }
    }

    public static void applyModelOnly(UUID uuid, boolean slim) {
        if (uuid == null) {
            return;
        }
        Optional<PresenceSkin> existing = PresenceSkinStore.get(uuid);
        if (existing.isPresent()) {
            PresenceSkin prev = existing.get();
            PresenceSkinStore.put(new PresenceSkin(uuid, slim, prev.skinPngUrl(), prev.geometryJson()));
            return;
        }
        Identifier ready = PresenceTextureCache.getIfReady(uuid);
        PresenceSkinStore.put(new PresenceSkin(uuid, slim, "", ""));
        if (ready != null) {
            PresenceTextureCache.bindReady(uuid, ready);
        }
    }

    public static void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PresenceSkinStore.remove(uuid);
        PresenceTextureCache.clearPlayer(uuid);
    }

    /**
     * Replace Mojang/session skin with the presence wardrobe skin when we have one.
     * Keeps cape/elytra from {@code base} when present.
     */
    public static PlayerSkin overlay(UUID uuid, PlayerSkin base) {
        if (uuid == null || base == null) {
            return base;
        }
        Optional<PresenceSkin> opt = PresenceSkinStore.get(uuid);
        if (opt.isEmpty()) {
            return base;
        }
        PresenceSkin presence = opt.get();
        Identifier bodyId = PresenceTextureCache.getIfReady(uuid);
        if (bodyId == null) {
            if (presence.skinPngUrl() != null && !presence.skinPngUrl().isBlank()) {
                PresenceTextureCache.ensureDownloaded(uuid, presence.skinPngUrl());
            }
            // Model type can still flip immediately (Wide / Slim).
            PlayerModelType model = presence.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            if (base.model() == model) {
                return base;
            }
            return PlayerSkin.insecure(base.body(), base.cape(), base.elytra(), model);
        }
        PlayerModelType model = presence.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        ClientAsset.Texture body = new ClientAsset.ResourceTexture(bodyId);
        return PlayerSkin.insecure(body, base.cape(), base.elytra(), model);
    }
}
