package com.yapcore.essentials.store;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishService {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public boolean toggle(Player player) {
        if (vanished.contains(player.getUniqueId())) {
            show(player);
            return false;
        }
        hide(player);
        return true;
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public void hide(Player player) {
        vanished.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        for (Player online : player.getServer().getOnlinePlayers()) {
            if (!online.hasPermission("yapessentials.vanish") && !online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }
    }

    public void show(Player player) {
        vanished.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player online : player.getServer().getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
    }
}
