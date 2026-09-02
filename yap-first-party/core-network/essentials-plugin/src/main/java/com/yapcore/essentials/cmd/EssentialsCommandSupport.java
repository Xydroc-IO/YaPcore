package com.yapcore.essentials.cmd;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.essentials.store.AfkService;
import com.yapcore.essentials.store.BackStore;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.store.StaffService;
import com.yapcore.essentials.store.TpaService;
import com.yapcore.essentials.store.VanishService;
import com.yapcore.essentials.util.TeleportHelper;
import com.yapcore.moderation.ModerationService;
import com.yapcore.moderation.Punishment;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Locale;
import java.net.InetSocketAddress;


/** Shared essentials command dependencies and helpers. */
final class EssentialsCommandSupport {
    final EssentialsPlugin plugin;
    final EssentialsConfig config;
    final SpawnStore spawnStore;
    final BackStore back;
    final TpaService tpa;
    final AfkService afk;
    final VanishService vanish;
    final StaffService staff;

    EssentialsCommandSupport(EssentialsPlugin plugin, EssentialsConfig config, SpawnStore spawnStore,
                             BackStore back, TpaService tpa, AfkService afk, VanishService vanish,
                             StaffService staff) {
        this.plugin = plugin;
        this.config = config;
        this.spawnStore = spawnStore;
        this.back = back;
        this.tpa = tpa;
        this.afk = afk;
        this.vanish = vanish;
        this.staff = staff;
    }

    boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage("Players only.");
        return false;
    }

    boolean disabled(CommandSender sender, String feature) {
        if (config.feature(feature)) {
            return false;
        }
        sender.sendMessage("§cThat feature is disabled on this server.");
        return true;
    }

    Player targetPlayer(CommandSender sender, String[] args, int otherArgIndex, String selfPerm) {
        if (sender instanceof Player player) {
            if (args.length > otherArgIndex && !sender.hasPermission(selfPerm)) {
                sender.sendMessage("§cNo permission for others.");
                return null;
            }
            if (args.length > otherArgIndex) {
                Player other = Bukkit.getPlayer(args[otherArgIndex]);
                if (other == null) {
                    sender.sendMessage("§cPlayer not online.");
                    return null;
                }
                return other;
            }
            if (!sender.hasPermission(selfPerm)) {
                sender.sendMessage("§cNo permission.");
                return null;
            }
            return player;
        }
        if (args.length <= otherArgIndex) {
            sender.sendMessage("Console must specify a player.");
            return null;
        }
        Player other = Bukkit.getPlayer(args[otherArgIndex]);
        if (other == null) {
            sender.sendMessage("§cPlayer not online.");
            return null;
        }
        return other;
    }

    static void repairItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            item.setItemMeta(damageable);
        }
    }

    static float clamp(float speed) {
        return Math.max(0.0f, Math.min(1.0f, speed));
    }

    static void msg(CommandSender sender, Player target, String message) {
        if (sender.equals(target)) {
            target.sendMessage("§a" + message);
        } else {
            sender.sendMessage("§a" + target.getName() + ": " + message);
            target.sendMessage("§a" + message);
        }
    }

    static Material matchItem(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        Material mat = Material.matchMaterial(key);
        if (mat != null && mat.isItem() && !mat.isAir()) {
            return mat;
        }
        try {
            mat = Material.valueOf(key.toUpperCase(Locale.ROOT));
            if (mat.isItem() && !mat.isAir()) {
                return mat;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    static GameMode parseGameMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "surv", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "crea", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adv", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spec", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    static String join(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
