package com.yapcore.presence.ui;

import com.yapcore.presence.PresenceSkinApplier;
import com.yapcore.presence.PresencePlayerSkins;
import com.yapcore.presence.PresenceTextureCache;
import com.yapcore.presence.YapPresenceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Skin resolve / preview / wear / file-upload helpers for {@link WardrobeScreen}.
 */
final class WardrobeSkinSupport {

    private final WardrobeScreen host;

    WardrobeSkinSupport(WardrobeScreen host) {
        this.host = host;
    }

    void selectSlot(PresenceUiMessages.SlotView slot) {
        TailorPreviewStore.setSelectedSlotId(slot.id());
        TailorPreviewStore.setSlim(slot.slim());
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier skin = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        if (skin != null && PresenceTextureCache.isRegistered(skin)) {
            TailorPreviewStore.setSkinTexture(skin);
        }
        Identifier cape = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        if (cape != null && PresenceTextureCache.isRegistered(cape)) {
            TailorPreviewStore.setCapeTexture(cape);
        } else {
            TailorPreviewStore.setCapeTexture(null);
        }
        TailorPreviewStore.setStatus("Previewing " + slot.name());
        host.refreshLiveLabels();
    }

    void applyLocalFile(LocalSkinLibrary.Entry entry, boolean cape) {
        if (entry == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(entry.path());
            if (bytes.length > 1_048_576) {
                TailorPreviewStore.setStatus("File too large (max 1 MB)");
                return;
            }
            if (!cape) {
                LocalSkinLibrary.Entry check = LocalSkinLibrary.tryRead(entry.path());
                if (check == null) {
                    TailorPreviewStore.setStatus("Not a Minecraft skin PNG");
                    return;
                }
            }
            loadPngBytes(cape, bytes, entry.name());
        } catch (Exception e) {
            TailorPreviewStore.setStatus("Failed: " + e.getMessage());
        }
    }

    void loadPngBytes(boolean cape, byte[] bytes, String label) {
        UUID uuid = host.client() != null && host.client().player != null
                ? host.client().player.getUUID()
                : UUID.randomUUID();
        Identifier id = cape
                ? TailorPreviewStore.previewCapeId(uuid)
                : TailorPreviewStore.previewSkinId(uuid);
        // Sync register so preview / in-world apply can use the texture this frame.
        PresenceTextureCache.registerBytesNow(id, bytes);
        TailorPreviewStore.setSelectedSlotId(-1L);
        if (cape) {
            TailorPreviewStore.setCapeTexture(id);
            TailorPreviewStore.setStatus("Cape: " + label + " — uploading…");
        } else {
            TailorPreviewStore.setSkinTexture(id);
            TailorPreviewStore.setStatus("Skin: " + label + " — uploading…");
            if (host.client() != null && host.client().player != null) {
                PresenceSkinApplier.applyLocal(
                        host.client().player.getUUID(),
                        TailorPreviewStore.slim(),
                        "",
                        id);
            }
            if (host.saveNameBox != null && (host.saveNameBox.getValue().isBlank()
                    || "My skin".equals(host.saveNameBox.getValue()))) {
                host.saveNameBox.setValue(label);
            }
        }
        YapPresenceClient.uploadPngFile(cape, bytes);
    }

    /** Preview + optimistic in-world apply, then ask Tailor to persist. */
    void wearSlotNow(PresenceUiMessages.SlotView slot) {
        if (slot == null) {
            return;
        }
        TailorPreviewStore.setSlim(slot.slim());
        if (host.client() != null && host.client().player != null) {
            PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
            Identifier ready = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
            PresenceSkinApplier.applyLocal(
                    host.client().player.getUUID(),
                    slot.slim(),
                    slot.skinUrl(),
                    ready);
        }
        YapPresenceClient.sendRaw("WARDROBE|APPLY|" + slot.id());
        TailorPreviewStore.setStatus("Wearing " + slot.name() + "…");
        TailorPreviewStore.setActiveSlotId(slot.id());
    }

    PlayerSkinWidget buildPreviewWidget() {
        Minecraft mc = host.client() != null ? host.client() : Minecraft.getInstance();
        int w = Math.min(160, Math.max(110, host.previewWidth() - 8));
        int h = (int) (w * 1.7f);
        return new PlayerSkinWidget(w, h, mc.getEntityModels(), this::resolvePreviewSkin);
    }

    PlayerSkin resolvePreviewSkin() {
        // Rotator index is source of truth while browsing — don't wait for a rebuild.
        int slots = host.wardrobeSlotCount();
        if (host.carouselIndex >= 0 && host.carouselIndex < host.carouselSize()) {
            if (host.carouselIndex < slots) {
                return resolveSlotSkin(PresenceUiStore.wardrobe().slots().get(host.carouselIndex));
            }
            return resolveLocalSkin(host.localSkins.get(host.carouselIndex - slots));
        }
        long selected = TailorPreviewStore.selectedSlotId();
        if (selected > 0) {
            for (PresenceUiMessages.SlotView slot : PresenceUiStore.wardrobe().slots()) {
                if (slot.id() == selected) {
                    return resolveSlotSkin(slot);
                }
            }
        }
        return resolveActiveOrImportSkin();
    }

    PlayerSkin resolveSlotSkin(PresenceUiMessages.SlotView slot) {
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier bodyId = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        Identifier capeId = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        PlayerModelType model = slot.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        if (bodyId != null && PresenceTextureCache.isRegistered(bodyId)) {
            return PlayerSkin.insecure(
                    PresencePlayerSkins.body(bodyId),
                    PresencePlayerSkins.optional(
                            capeId != null && PresenceTextureCache.isRegistered(capeId) ? capeId : null),
                    null,
                    model);
        }
        return resolveActiveOrImportSkin();
    }

    PlayerSkin resolveActiveOrImportSkin() {
        Minecraft mc = Minecraft.getInstance();
        Identifier bodyId = TailorPreviewStore.skinTexture();
        if (bodyId == null && mc.player != null) {
            bodyId = PresenceTextureCache.getIfReady(mc.player.getUUID());
        }
        if (bodyId != null && PresenceTextureCache.isRegistered(bodyId)) {
            Identifier capeId = TailorPreviewStore.capeTexture();
            return PlayerSkin.insecure(
                    PresencePlayerSkins.body(bodyId),
                    PresencePlayerSkins.optional(
                            capeId != null && PresenceTextureCache.isRegistered(capeId) ? capeId : null),
                    null,
                    TailorPreviewStore.modelType());
        }
        if (mc.player != null) {
            return mc.player.getSkin();
        }
        return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
    }

    void pickAndUpload(boolean cape) {
        String title = cape ? "Choose cape PNG" : "Choose skin PNG";
        Path start = LocalSkinLibrary.preferredBrowseDir();
        TailorPreviewStore.setStatus("Opening " + start.getFileName() + "…");
        SkinFileDialogs.openPng(title, start).thenAccept(opt -> {
            Minecraft.getInstance().execute(() -> {
                if (opt.isEmpty()) {
                    TailorPreviewStore.setStatus("File dialog cancelled");
                    return;
                }
                try {
                    Path path = opt.get();
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length > 1_048_576) {
                        TailorPreviewStore.setStatus("File too large (max 1 MB)");
                        return;
                    }
                    if (!cape && LocalSkinLibrary.tryRead(path) == null) {
                        TailorPreviewStore.setStatus("Not a Minecraft skin PNG (need 64×64 etc.)");
                        return;
                    }
                    String name = path.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        name = name.substring(0, dot);
                    }
                    loadPngBytes(cape, bytes, name);
                } catch (Exception e) {
                    TailorPreviewStore.setStatus("Failed to read file: " + e.getMessage());
                }
            });
        });
    }

    PlayerSkin resolveLocalSkin(LocalSkinLibrary.Entry entry) {
        PresenceTextureCache.ensureLocalFile(entry.path());
        Identifier ready = PresenceTextureCache.getIfReady(PresenceTextureCache.localFileCacheKey(entry.path()));
        if (ready != null && PresenceTextureCache.isRegistered(ready)) {
            return PlayerSkin.insecure(
                    PresencePlayerSkins.body(ready), null, null, TailorPreviewStore.modelType());
        }
        return resolveActiveOrImportSkin();
    }

    void focusLocal(LocalSkinLibrary.Entry entry) {
        int slots = host.wardrobeSlotCount();
        for (int i = 0; i < host.localSkins.size(); i++) {
            if (host.localSkins.get(i).path().equals(entry.path())) {
                host.carouselIndex = slots + i;
                break;
            }
        }
    }

    void previewSlotOnly(PresenceUiMessages.SlotView slot) {
        TailorPreviewStore.setSelectedSlotId(slot.id());
        TailorPreviewStore.setSlim(slot.slim());
        PresenceTextureCache.ensureWardrobeSlot(slot.id(), slot.skinUrl(), slot.capeUrl());
        Identifier skin = PresenceTextureCache.getIfReady("wardrobe/" + slot.id());
        if (skin != null && PresenceTextureCache.isRegistered(skin)) {
            TailorPreviewStore.setSkinTexture(skin);
        }
        Identifier cape = PresenceTextureCache.getIfReady("wardrobe/" + slot.id() + "_cape");
        if (cape != null && PresenceTextureCache.isRegistered(cape)) {
            TailorPreviewStore.setCapeTexture(cape);
        } else {
            TailorPreviewStore.setCapeTexture(null);
        }
        TailorPreviewStore.setStatus("Preview · " + slot.name());
    }

    void previewLocalOnly(LocalSkinLibrary.Entry entry) {
        PresenceTextureCache.ensureLocalFile(entry.path());
        Identifier ready = PresenceTextureCache.getIfReady(PresenceTextureCache.localFileCacheKey(entry.path()));
        TailorPreviewStore.setSelectedSlotId(-1L);
        // Never point PlayerSkin at an unregistered DynamicTexture id — MC logs
        // "Missing resource yap-presence:local/…" and can thrash the texture manager.
        if (ready != null && PresenceTextureCache.isRegistered(ready)) {
            TailorPreviewStore.setSkinTexture(ready);
            TailorPreviewStore.setStatus("Preview · " + entry.name());
        } else {
            TailorPreviewStore.setStatus("Loading · " + entry.name());
        }
    }
}
