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
                PlayerProfile profile = player.getPlayerProfile();
                if (skin.textureValueBase64() != null && !skin.textureValueBase64().isBlank()) {
                    profile.setProperty(new ProfileProperty("textures", skin.textureValueBase64()));
                } else if (skin.sourceUrl() != null && !skin.sourceUrl().isBlank()) {
                    PlayerTextures textures = profile.getTextures();
                    URL skinUrl = URI.create(skin.sourceUrl()).toURL();
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
                }
                player.setPlayerProfile(profile);
                hideShowRefresh(player);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply skin to " + player.getName(), e);
            }
        });
        pushChassisAsync(player, skin);
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
