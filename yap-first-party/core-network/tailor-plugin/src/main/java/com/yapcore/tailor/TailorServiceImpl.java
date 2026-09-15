package com.yapcore.tailor;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yapcore.tailor.db.TailorDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TailorServiceImpl implements TailorService {

    private final TailorConfig config;
    private final TailorDatabase database;
    private final SkinImageService images;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final TailorWardrobeOps wardrobe;
    private final TailorOnlineApply online;

    public TailorServiceImpl(
            TailorPlugin plugin,
            TailorConfig config,
            TailorDatabase database,
            SkinImageService images) {
        this.config = config;
        this.database = database;
        this.images = images;
        this.online = new TailorOnlineApply(plugin, config, database, images);
        this.wardrobe = new TailorWardrobeOps(plugin, config, database, images, this);
    }

    public void setPresenceChannel(PresenceChannel presenceChannel) {
        online.setPresenceChannel(presenceChannel);
    }

    public TailorConfig config() {
        return config;
    }

    public SkinImageService images() {
        return images;
    }

    void checkCooldown(UUID uuid) throws TailorException {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && config.cooldownMs() > 0 && now - last < config.cooldownMs()) {
            long left = config.cooldownMs() - (now - last);
            throw new TailorException("Please wait " + Math.max(1, left / 1000) + "s before changing skins again");
        }
        cooldowns.put(uuid, now);
    }

    @Override
    public ActiveSkin applySkin(Player player, String skinUrl) throws TailorException {
        checkCooldown(player.getUniqueId());
        ActiveSkin skin = applySkinInternal(player.getUniqueId(), skinUrl, null, null, true);
        online.applyToOnline(player, skin);
        return skin;
    }

    @Override
    public ActiveSkin applySkin(UUID playerUuid, String skinUrl) throws TailorException {
        checkCooldown(playerUuid);
        ActiveSkin skin = applySkinInternal(playerUuid, skinUrl, null, null, true);
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            online.applyToOnline(onlinePlayer, skin);
        }
        return skin;
    }

    /** Apply a skin from raw PNG bytes (Fabric file picker upload). */
    public ActiveSkin applySkinBytes(Player player, byte[] png) throws TailorException {
        checkCooldown(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        String publicUrl = images.storeSkinBytes(uuid, png);
        ActiveSkin previous;
        try {
            previous = database.loadActive(uuid).orElse(null);
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        SkinModel model = previous != null ? previous.model() : config.defaultModel();
        String cape = previous != null ? previous.capeUrl() : null;
        String textureValue = images.buildTextureValue(publicUrl, cape, model);
        ActiveSkin skin = ActiveSkin.of(uuid, publicUrl, cape, model, textureValue, System.currentTimeMillis());
        // File/URL apply is not a wardrobe wear — clear active slot highlight
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to persist active skin", e);
        }
        online.applyToOnline(player, skin);
        return skin;
    }

    /** Apply a cape from raw PNG bytes (Fabric file picker upload). */
    public ActiveSkin applyCapeBytes(Player player, byte[] png) throws TailorException {
        checkCooldown(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        ActiveSkin previous;
        try {
            previous = database.loadActive(uuid)
                    .orElseThrow(() -> new TailorException("No active skin — set a skin first"));
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        String publicUrl = images.storeCapeBytes(uuid, png);
        String textureValue = images.buildTextureValue(previous.sourceUrl(), publicUrl, previous.model());
        ActiveSkin skin = ActiveSkin.of(
                uuid, previous.sourceUrl(), publicUrl, previous.model(), textureValue, System.currentTimeMillis(),
                previous.activeSlotId());
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to save cape", e);
        }
        online.applyToOnline(player, skin);
        return skin;
    }

    /** Apply another online player's current textures, or look up Mojang by name. */
    public ActiveSkin applyFromPlayerName(Player viewer, String targetName) throws TailorException {
        checkCooldown(viewer.getUniqueId());
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            PlayerProfile profile = target.getPlayerProfile();
            Optional<ProfileProperty> textures = profile.getProperties().stream()
                    .filter(p -> "textures".equals(p.getName()))
                    .findFirst();
            if (textures.isPresent()) {
                String value = textures.get().getValue();
                String url = TailorMojangLookup.extractSkinUrl(value).orElse(null);
                SkinModel model = TailorMojangLookup.extractModel(value).orElse(config.defaultModel());
                String cape = TailorMojangLookup.extractCapeUrl(value).orElse(null);
                ActiveSkin skin = ActiveSkin.of(
                        viewer.getUniqueId(), url, cape, model, value, System.currentTimeMillis());
                try {
                    database.saveActive(skin);
                } catch (Exception e) {
                    throw new TailorException("Failed to save skin", e);
                }
                online.applyToOnline(viewer, skin);
                return skin;
            }
        }
        TailorMojangLookup.MojangTextures mojang =
                TailorMojangLookup.lookupByName(targetName, config.defaultModel());
        ActiveSkin skin = ActiveSkin.of(
                viewer.getUniqueId(),
                mojang.skinUrl(),
                mojang.capeUrl(),
                mojang.model(),
                mojang.textureValue(),
                System.currentTimeMillis());
        if (mojang.skinUrl() != null) {
            try {
                images.storeSkin(viewer.getUniqueId(), mojang.skinUrl());
            } catch (TailorException ignored) {
                // still apply texture value even if local store fails
            }
        }
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to save skin", e);
        }
        online.applyToOnline(viewer, skin);
        return skin;
    }

    private ActiveSkin applySkinInternal(
            UUID uuid,
            String skinUrl,
            String capeUrlOverride,
            SkinModel modelOverride,
            boolean replaceSkin) throws TailorException {
        ActiveSkin previous;
        try {
            previous = database.loadActive(uuid).orElse(null);
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        SkinModel model = modelOverride != null
                ? modelOverride
                : (previous != null ? previous.model() : config.defaultModel());
        String cape = capeUrlOverride != null
                ? (capeUrlOverride.isBlank() ? null : capeUrlOverride)
                : (previous != null ? previous.capeUrl() : null);

        String publicSkinUrl;
        String textureValue;
        if (replaceSkin) {
            if (skinUrl == null || skinUrl.isBlank()) {
                throw new TailorException("Skin URL is required");
            }
            publicSkinUrl = images.storeSkin(uuid, skinUrl.trim());
            if (cape != null && !cape.isBlank()) {
                try {
                    cape = images.storeCape(uuid, cape);
                } catch (TailorException e) {
                    // keep previous cape url string if re-download fails
                }
            }
            textureValue = images.buildTextureValue(publicSkinUrl, cape, model);
        } else {
            publicSkinUrl = previous != null ? previous.sourceUrl() : null;
            if (publicSkinUrl == null || publicSkinUrl.isBlank()) {
                throw new TailorException("No active skin to update");
            }
            textureValue = images.buildTextureValue(publicSkinUrl, cape, model);
        }

        ActiveSkin skin = ActiveSkin.of(
                uuid,
                publicSkinUrl,
                cape,
                model,
                textureValue,
                System.currentTimeMillis(),
                replaceSkin ? null : (previous != null ? previous.activeSlotId() : null));
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to persist active skin", e);
        }
        return skin;
    }

    @Override
    public void clearSkin(Player player) throws TailorException {
        clearSkin(player.getUniqueId());
        online.restoreDefault(player);
    }

    @Override
    public void clearSkin(UUID playerUuid) throws TailorException {
        try {
            database.deleteActive(playerUuid);
        } catch (Exception e) {
            throw new TailorException("Failed to clear skin", e);
        }
        images.deleteStored(playerUuid);
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            online.restoreDefault(onlinePlayer);
        }
    }

    @Override
    public ActiveSkin setModel(Player player, SkinModel model) throws TailorException {
        ActiveSkin skin = setModel(player.getUniqueId(), model);
        online.applyToOnline(player, skin);
        return skin;
    }

    @Override
    public ActiveSkin setModel(UUID playerUuid, SkinModel model) throws TailorException {
        ActiveSkin previous;
        try {
            previous = database.loadActive(playerUuid).orElse(null);
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        // Wide/Slim from the wardrobe must work on Mojang/default skins too — not only after
        // a custom Tailor upload (otherwise SKIN|MODEL fails with "set a skin first").
        if (previous == null) {
            Player onlinePlayer = Bukkit.getPlayer(playerUuid);
            if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                throw new TailorException("No active skin — set a skin first");
            }
            previous = seedActiveFromLiveProfile(onlinePlayer, model);
            online.applyToOnline(onlinePlayer, previous);
            return previous;
        }
        ActiveSkin skin = applySkinInternal(playerUuid, previous.sourceUrl(), previous.capeUrl(), model, false);
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            online.applyToOnline(onlinePlayer, skin);
        }
        return skin;
    }

    /**
     * First-time model toggle: copy the player's current profile skin/cape into Tailor
     * storage with the requested slim/wide model.
     */
    private ActiveSkin seedActiveFromLiveProfile(Player player, SkinModel model) throws TailorException {
        UUID uuid = player.getUniqueId();
        String skinUrl = null;
        String capeUrl = null;
        PlayerProfile profile = player.getPlayerProfile();
        Optional<ProfileProperty> textures = profile.getProperties().stream()
                .filter(p -> "textures".equals(p.getName()))
                .findFirst();
        if (textures.isPresent()) {
            String value = textures.get().getValue();
            skinUrl = TailorMojangLookup.extractSkinUrl(value).orElse(null);
            capeUrl = TailorMojangLookup.extractCapeUrl(value).orElse(null);
        }
        try {
            PlayerTextures pt = profile.getTextures();
            if (skinUrl == null && pt.getSkin() != null) {
                skinUrl = pt.getSkin().toString();
            }
            if (capeUrl == null && pt.getCape() != null) {
                capeUrl = pt.getCape().toString();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        if (skinUrl == null || skinUrl.isBlank()) {
            throw new TailorException("No skin texture available — upload a skin first");
        }
        String publicUrl;
        try {
            publicUrl = images.storeSkin(uuid, skinUrl.trim());
        } catch (TailorException e) {
            publicUrl = skinUrl.trim();
        }
        if (capeUrl != null && !capeUrl.isBlank()) {
            try {
                capeUrl = images.storeCape(uuid, capeUrl.trim());
            } catch (TailorException ignored) {
                // keep CDN cape URL
            }
        }
        String textureValue = images.buildTextureValue(publicUrl, capeUrl, model);
        ActiveSkin skin = ActiveSkin.of(uuid, publicUrl, capeUrl, model, textureValue, System.currentTimeMillis());
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to persist model", e);
        }
        return skin;
    }

    @Override
    public ActiveSkin setCape(Player player, String capeUrl) throws TailorException {
        ActiveSkin skin = setCape(player.getUniqueId(), capeUrl);
        online.applyToOnline(player, skin);
        return skin;
    }

    @Override
    public ActiveSkin setCape(UUID playerUuid, String capeUrl) throws TailorException {
        ActiveSkin previous;
        try {
            previous = database.loadActive(playerUuid)
                    .orElseThrow(() -> new TailorException("No active skin — set a skin first"));
        } catch (TailorException e) {
            throw e;
        } catch (Exception e) {
            throw new TailorException("Database error", e);
        }
        String resolvedCape = null;
        if (capeUrl != null && !capeUrl.isBlank() && !"clear".equalsIgnoreCase(capeUrl.trim())) {
            resolvedCape = images.storeCape(playerUuid, capeUrl.trim());
        }
        String textureValue = images.buildTextureValue(previous.sourceUrl(), resolvedCape, previous.model());
        ActiveSkin skin = ActiveSkin.of(
                playerUuid, previous.sourceUrl(), resolvedCape, previous.model(), textureValue, System.currentTimeMillis(),
                previous.activeSlotId());
        try {
            database.saveActive(skin);
        } catch (Exception e) {
            throw new TailorException("Failed to save cape", e);
        }
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            online.applyToOnline(onlinePlayer, skin);
        }
        return skin;
    }

    @Override
    public List<WardrobeSlot> listWardrobe(UUID playerUuid) throws TailorException {
        return wardrobe.listWardrobe(playerUuid);
    }

    @Override
    public WardrobeSlot saveWardrobeSlot(UUID playerUuid, String name) throws TailorException {
        return wardrobe.saveWardrobeSlot(playerUuid, name);
    }

    @Override
    public void deleteWardrobeSlot(UUID playerUuid, long slotId) throws TailorException {
        wardrobe.deleteWardrobeSlot(playerUuid, slotId);
    }

    @Override
    public WardrobeSlot renameSlot(UUID playerUuid, long slotId, String newName) throws TailorException {
        return wardrobe.renameSlot(playerUuid, slotId, newName);
    }

    @Override
    public ActiveSkin applyWardrobeSlot(Player player, long slotId) throws TailorException {
        return wardrobe.applyWardrobeSlot(player, slotId);
    }

    @Override
    public Optional<ActiveSkin> getActiveSkin(UUID playerUuid) throws TailorException {
        try {
            return database.loadActive(playerUuid);
        } catch (Exception e) {
            throw new TailorException("Failed to load active skin", e);
        }
    }

    @Override
    public void refreshPlayer(Player player) throws TailorException {
        Optional<ActiveSkin> active = getActiveSkin(player.getUniqueId());
        if (active.isEmpty()) {
            return;
        }
        online.applyToOnline(player, active.get());
    }

    public void applyToOnline(Player player, ActiveSkin skin) {
        online.applyToOnline(player, skin);
    }
}
