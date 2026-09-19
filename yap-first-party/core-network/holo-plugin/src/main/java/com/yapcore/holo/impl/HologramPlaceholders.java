package com.yapcore.holo.impl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** PlaceholderAPI + a few always-on tokens so holograms work without expansions. */
public final class HologramPlaceholders {

    private Method setPlaceholders;
    private volatile boolean enabled;

    public HologramPlaceholders(boolean enabled) {
        this.enabled = enabled;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
        } catch (Exception ignored) {
            setPlaceholders = null;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String apply(Player player, String text) {
        if (text == null || text.isEmpty() || player == null || !enabled) {
            return text == null ? "" : text;
        }
        String out = builtin(player, text);
        if (setPlaceholders == null || !out.contains("%")) {
            return out;
        }
        try {
            Object applied = setPlaceholders.invoke(null, player, out);
            return applied == null ? out : applied.toString();
        } catch (Exception e) {
            return out;
        }
    }

    public static String builtin(Player player, String text) {
        String out = text;
        out = out.replace("%player_name%", player.getName());
        out = out.replace("%player%", player.getName());
        out = out.replace("{player}", player.getName());
        String display = player.getDisplayName() == null ? player.getName() : player.getDisplayName();
        out = out.replace("%player_displayname%", display);
        String world = player.getWorld() == null ? "" : player.getWorld().getName();
        out = out.replace("%world%", world);
        out = out.replace("%player_world%", world);
        out = out.replace("%online%", Integer.toString(Bukkit.getOnlinePlayers().size()));
        out = out.replace("%server_online%", Integer.toString(Bukkit.getOnlinePlayers().size()));
        var loc = player.getLocation();
        out = out.replace("%player_x%", Integer.toString(loc.getBlockX()));
        out = out.replace("%player_y%", Integer.toString(loc.getBlockY()));
        out = out.replace("%player_z%", Integer.toString(loc.getBlockZ()));
        return out;
    }
}
