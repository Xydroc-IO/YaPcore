package com.yapcore.playerdata.service;

import com.yapcore.playerdata.HomeAccess;
import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.HomesRepository;
import com.yapcore.playerdata.db.LocationRow;
import com.yapcore.playerdata.util.Teleports;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Homes soft API for portals and other first-party plugins. */
public final class HomeAccessImpl implements HomeAccess {

    private final PlayerDataConfig config;
    private final HomesRepository homes;
    private final Logger log;

    public HomeAccessImpl(PlayerDataConfig config, HomesRepository homes, Logger log) {
        this.config = config;
        this.homes = homes;
        this.log = log;
    }

    @Override
    public boolean hasHome(UUID uuid, String homeName) {
        if (!config.featureHomes() || uuid == null) {
            return false;
        }
        String name = normalize(homeName);
        try {
            return homes.get(uuid, name).isPresent();
        } catch (Exception e) {
            log.log(Level.FINE, "hasHome " + name, e);
            return false;
        }
    }

    @Override
    public Optional<String> teleportHome(Player player, String homeName) {
        if (player == null || !config.featureHomes()) {
            return Optional.empty();
        }
        String name = normalize(homeName);
        try {
            Optional<LocationRow> opt = homes.get(player.getUniqueId(), name);
            if (opt.isEmpty()) {
                return Optional.empty();
            }
            if (!Teleports.tryTeleport(player, opt.get(), config.serverId())) {
                return Optional.empty();
            }
            return Optional.of(name);
        } catch (Exception e) {
            log.log(Level.WARNING, "teleportHome " + name + " for " + player.getName(), e);
            return Optional.empty();
        }
    }

    private static String normalize(String homeName) {
        if (homeName == null || homeName.isBlank()) {
            return "home";
        }
        return homeName.trim().toLowerCase(Locale.ROOT);
    }
}
