package com.yapcore.tailor;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yapcore.sched.YapSched;
import com.yapcore.tailor.db.TailorDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Online profile apply, chassis push, and default restore for {@link TailorServiceImpl}. */
final class TailorOnlineApply {

    private final TailorPlugin plugin;
    private final TailorConfig config;
    private final TailorDatabase database;
    private final SkinImageService images;
    private PresenceChannel presenceChannel;

    TailorOnlineApply(
            TailorPlugin plugin,
            TailorConfig config,
            TailorDatabase database,
            SkinImageService images) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.images = images;
    }

    void setPresenceChannel(PresenceChannel presenceChannel) {
        this.presenceChannel = presenceChannel;
    }

    void applyToOnline(Player player, ActiveSkin skin) {
        YapSched.entity(plugin, player, () -> {
            try {
                applyProfileTextures(player, skin);
                hideShowRefresh(player);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply skin to " + player.getName(), e);
            }
        });
        pushChassisAsync(player, skin);
    }

    /**
     * Mutate the Paper profile other clients read.
     *
     * <p><b>Never</b> write an unsigned {@code YaPTailor} textures property. That replaces
     * Velocity-forwarded Mojang-signed textures; other clients reject it and fall back to
     * Steve/Alex — which matches “not Tailor, not my account skin.”
     */
    private void applyProfileTextures(Player player, ActiveSkin skin) throws Exception {
        String source = skin.sourceUrl();
        if (source == null || source.isBlank()) {
            return;
        }

        if (TailorMojangLookup.isMojangTextureUrl(source)) {
            applySignedMojangOrKeepLogin(player, skin, source);
            return;
        }

        // Custom / chassis-hosted URL — Paper PlayerTextures path (no fake YaPTailor JSON).
        PlayerProfile profile = player.getPlayerProfile();
        PlayerTextures textures = profile.getTextures();
        URL skinUrl = URI.create(source).toURL();
        PlayerTextures.SkinModel bukkitModel = skin.model() == SkinModel.SLIM
                ? PlayerTextures.SkinModel.SLIM
                : PlayerTextures.SkinModel.CLASSIC;
        textures.setSkin(skinUrl, bukkitModel);
        if (skin.capeUrl() != null && !skin.capeUrl().isBlank()) {
            textures.setCape(URI.create(skin.capeUrl()).toURL());
        } else {
            textures.setCape(null);
        }
        profile.setTextures(textures);
        player.setPlayerProfile(profile);
    }

    private void applySignedMojangOrKeepLogin(Player player, ActiveSkin skin, String source) {
        try {
            TailorMojangLookup.MojangTextures signed =
                    TailorMojangLookup.lookupByName(player.getName(), skin.model());
            if (signed.textureValue() == null || signed.textureValue().isBlank()
                    || signed.textureSignature() == null || signed.textureSignature().isBlank()) {
                plugin.getLogger().info(
                        "Keeping login textures for " + player.getName()
                                + " — Mojang lookup returned no signature (refusing unsigned apply)");
                return;
            }
            boolean sameTexture = urlsSameTexture(source, signed.skinUrl());
            if (!sameTexture && !TailorMojangLookup.isUnsignedYapTailorValue(skin.textureValueBase64())) {
                // Tailor points at a different Mojang CDN hash than this account — cannot sign it.
                // Use PlayerTextures URL apply; presence INLINE still covers yap-presence clients.
                applyUrlTextures(player, skin, source);
                return;
            }
            PlayerProfile profile = player.getPlayerProfile();
            profile.setProperty(new ProfileProperty(
                    "textures", signed.textureValue(), signed.textureSignature()));
            player.setPlayerProfile(profile);
            persistSignedValueBestEffort(skin, signed);
        } catch (Exception e) {
            plugin.getLogger().log(Level.INFO,
                    "Keeping login textures for " + player.getName()
                            + " — signed Mojang refresh failed: " + e.getMessage());
        }
    }

    private void applyUrlTextures(Player player, ActiveSkin skin, String source) throws Exception {
        PlayerProfile profile = player.getPlayerProfile();
        PlayerTextures textures = profile.getTextures();
        URL skinUrl = URI.create(source).toURL();
        PlayerTextures.SkinModel bukkitModel = skin.model() == SkinModel.SLIM
                ? PlayerTextures.SkinModel.SLIM
                : PlayerTextures.SkinModel.CLASSIC;
        textures.setSkin(skinUrl, bukkitModel);
        if (skin.capeUrl() != null && !skin.capeUrl().isBlank()) {
            textures.setCape(URI.create(skin.capeUrl()).toURL());
        } else {
            textures.setCape(null);
        }
        profile.setTextures(textures);
        player.setPlayerProfile(profile);
    }

    private void persistSignedValueBestEffort(ActiveSkin skin, TailorMojangLookup.MojangTextures signed) {
        try {
            ActiveSkin updated = ActiveSkin.of(
                    skin.playerUuid(),
                    signed.skinUrl() != null ? signed.skinUrl() : skin.sourceUrl(),
                    signed.capeUrl() != null ? signed.capeUrl() : skin.capeUrl(),
                    signed.model() != null ? signed.model() : skin.model(),
                    signed.textureValue(),
                    System.currentTimeMillis(),
                    skin.activeSlotId());
            if (skin.bedrockCanonicalJson() != null) {
                updated = updated.withBedrockCanonicalJson(skin.bedrockCanonicalJson());
            }
            database.saveActive(updated);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "persist signed textures: " + e.getMessage());
        }
    }

    private static boolean urlsSameTexture(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String ha = hashTail(a);
        String hb = hashTail(b);
        return !ha.isEmpty() && ha.equals(hb);
    }

    private static String hashTail(String url) {
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }

    void restoreDefault(Player player) {
        YapSched.entity(plugin, player, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfile(player.getUniqueId(), player.getName());
                profile.complete(true);
                player.setPlayerProfile(profile);
                hideShowRefresh(player);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to restore default skin for " + player.getName(), e);
            }
        });
    }

    private void pushChassisAsync(Player player, ActiveSkin skin) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        YapSched.async(plugin, () -> {
            byte[] png = images.readStoredSkin(uuid);
            byte[] cape = images.readStoredCape(uuid);
            if ((png == null || png.length == 0) && skin.sourceUrl() != null && !skin.sourceUrl().isBlank()) {
                try {
                    png = images.downloadAndValidate(skin.sourceUrl());
                } catch (TailorException ignored) {
                    // push with whatever we have
                }
            }
            if ((cape == null || cape.length == 0) && skin.capeUrl() != null && !skin.capeUrl().isBlank()) {
                try {
                    cape = images.downloadAndValidate(skin.capeUrl());
                } catch (TailorException ignored) {
                }
            }
            Optional<String> canonical = ChassisSkinPush.push(plugin, config, username, uuid, skin, png, cape);
            if (canonical.isPresent()) {
                ActiveSkin withCanonical = skin.withBedrockCanonicalJson(canonical.get());
                try {
                    database.saveActive(withCanonical);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE,
                            "Failed to persist bedrockCanonicalJson for " + username + ": " + e.getMessage());
                }
            }
            PresenceChannel channel = presenceChannel;
            Player online = Bukkit.getPlayer(uuid);
            if (channel != null && online != null && online.isOnline()) {
                channel.sendSkin(online);
            }
        });
    }

    private void hideShowRefresh(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            other.hidePlayer(plugin, player);
            other.showPlayer(plugin, player);
        }
    }
}
