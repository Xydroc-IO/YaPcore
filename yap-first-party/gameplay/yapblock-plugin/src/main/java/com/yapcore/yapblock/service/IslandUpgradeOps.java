package com.yapcore.yapblock.service;

import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.PlayerDataServiceProvider;
import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class IslandUpgradeOps {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandIndex index;
    private final IslandRepository islands;
    private final IslandRoleCache roles;

    public IslandUpgradeOps(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandIndex index,
            IslandRepository islands,
            IslandRoleCache roles) {
        this.plugin = plugin;
        this.config = config;
        this.index = index;
        this.islands = islands;
        this.roles = roles;
    }

    public CompletableFuture<Boolean> upgrade(Player player, String kind) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (roles.role(player.getUniqueId(), snap.id()).orElse(null) != IslandRole.OWNER) {
            player.sendMessage(Component.text("Only the owner can upgrade.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (!player.hasPermission("yapblock.upgrade")) {
            player.sendMessage(Component.text("Missing permission yapblock.upgrade.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        return switch (kind.toLowerCase()) {
            case "size" -> upgradeSize(player, snap);
            case "members", "member" -> upgradeMembers(player, snap);
            case "generator", "gen" -> upgradeGenerator(player, snap);
            default -> {
                player.sendMessage(Component.text("Usage: /is upgrade <size|members|generator>", NamedTextColor.RED));
                yield CompletableFuture.completedFuture(false);
            }
        };
    }

    private CompletableFuture<Boolean> upgradeSize(Player player, IslandSnapshot snap) {
        Optional<YapblockConfig.UpgradeTier> next = nextTier(config.sizeUpgrades(), snap.sizeRadius());
        if (next.isEmpty()) {
            player.sendMessage(Component.text("Size already maxed.", NamedTextColor.YELLOW));
            return CompletableFuture.completedFuture(false);
        }
        return chargeAndApply(player, next.get().cost(), () -> {
            try {
                islands.updateSize(snap.id(), next.get().value());
                IslandSnapshot updated = snap.withSizeRadius(next.get().value());
                index.put(updated);
                YapSched.entity(plugin, player, () -> player.sendMessage(Component.text(
                        "Island size upgraded to radius " + next.get().value() + ".", NamedTextColor.GREEN)));
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Size upgrade failed", e);
                return false;
            }
        });
    }

    private CompletableFuture<Boolean> upgradeMembers(Player player, IslandSnapshot snap) {
        Optional<YapblockConfig.UpgradeTier> next = nextTier(config.memberUpgrades(), snap.maxMembers());
        if (next.isEmpty()) {
            player.sendMessage(Component.text("Member cap already maxed.", NamedTextColor.YELLOW));
            return CompletableFuture.completedFuture(false);
        }
        return chargeAndApply(player, next.get().cost(), () -> {
            try {
                islands.updateMaxMembers(snap.id(), next.get().value());
                IslandSnapshot updated = snap.withMaxMembers(next.get().value());
                index.put(updated);
                YapSched.entity(plugin, player, () -> player.sendMessage(Component.text(
                        "Max members upgraded to " + next.get().value() + ".", NamedTextColor.GREEN)));
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Member upgrade failed", e);
                return false;
            }
        });
    }

    private CompletableFuture<Boolean> upgradeGenerator(Player player, IslandSnapshot snap) {
        Optional<YapblockConfig.UpgradeTier> next = nextTier(config.generatorUpgrades(), snap.genTier());
        if (next.isEmpty()) {
            player.sendMessage(Component.text("Generator already maxed.", NamedTextColor.YELLOW));
            return CompletableFuture.completedFuture(false);
        }
        return chargeAndApply(player, next.get().cost(), () -> {
            try {
                islands.updateGenTier(snap.id(), next.get().value());
                IslandSnapshot updated = snap.withGenTier(next.get().value());
                index.put(updated);
                YapSched.entity(plugin, player, () -> player.sendMessage(Component.text(
                        "Generator upgraded to tier " + next.get().value() + ".", NamedTextColor.GREEN)));
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Generator upgrade failed", e);
                return false;
            }
        });
    }

    private Optional<YapblockConfig.UpgradeTier> nextTier(Map<String, YapblockConfig.UpgradeTier> tiers, int currentValue) {
        return tiers.values().stream()
                .filter(t -> t.value() > currentValue)
                .min(Comparator.comparingInt(YapblockConfig.UpgradeTier::order)
                        .thenComparingInt(YapblockConfig.UpgradeTier::value));
    }

    private CompletableFuture<Boolean> chargeAndApply(Player player, double cost, java.util.concurrent.Callable<Boolean> apply) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            Optional<PlayerDataService> eco = PlayerDataServiceProvider.find();
            if (eco.isEmpty() || !eco.get().economyEnabled()) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Economy unavailable (YaPPlayerData).", NamedTextColor.RED)));
                future.complete(false);
                return;
            }
            if (eco.get().withdraw(player.getUniqueId(), cost).isEmpty()) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(
                                "Need " + eco.get().formatMoney(cost) + " for this upgrade.", NamedTextColor.RED)));
                future.complete(false);
                return;
            }
            try {
                future.complete(Boolean.TRUE.equals(apply.call()));
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }
}
