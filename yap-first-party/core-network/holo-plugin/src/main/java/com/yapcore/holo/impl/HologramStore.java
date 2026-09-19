package com.yapcore.holo.impl;

import com.yapcore.holo.HologramAttach;
import com.yapcore.holo.HologramClick;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class HologramStore {

    private final JavaPlugin plugin;
    private final File file;

    public HologramStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public List<Stored> load() {
        List<Stored> out = new ArrayList<>();
        if (!file.isFile()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("holograms");
        if (root == null) {
            return out;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String world = s.getString("world", "world");
            Location loc = new Location(Bukkit.getWorld(world),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            List<List<String>> pages = loadPages(s);
            double view = s.getDouble("view-distance", 48.0);
            HologramAttach attach = HologramAttach.parse(s.getString("attach", ""));
            List<HologramClick> clicks = new ArrayList<>();
            for (String raw : s.getStringList("clicks")) {
                HologramClick click = HologramClick.parse(raw);
                if (click != null) {
                    clicks.add(click);
                }
            }
            String perm = s.getString("see-permission", "");
            out.add(new Stored(id, loc, world, pages, view, attach, clicks, perm));
        }
        return out;
    }

    public void save(Iterable<HologramImpl> holograms, boolean persist) {
        if (!persist) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (HologramImpl holo : holograms) {
            String path = "holograms." + holo.id();
            Location loc = holo.location();
            yaml.set(path + ".world", holo.worldName());
            yaml.set(path + ".x", loc.getX());
            yaml.set(path + ".y", loc.getY());
            yaml.set(path + ".z", loc.getZ());
            yaml.set(path + ".yaw", loc.getYaw());
            yaml.set(path + ".pitch", loc.getPitch());
            yaml.set(path + ".view-distance", holo.viewDistance());
            yaml.set(path + ".attach", holo.attachment().serialize());
            yaml.set(path + ".see-permission", holo.seePermission());
            List<String> clickRows = new ArrayList<>();
            for (HologramClick click : holo.clicks()) {
                clickRows.add(click.serialize());
            }
            yaml.set(path + ".clicks", clickRows);
            List<List<String>> pages = holo.pages();
            if (pages.size() == 1) {
                yaml.set(path + ".lines", pages.get(0));
            } else {
                yaml.set(path + ".pages", pages);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save holograms.yml", e);
        }
    }

    private static List<List<String>> loadPages(ConfigurationSection s) {
        if (s.isList("pages")) {
            List<List<String>> pages = new ArrayList<>();
            for (Object entry : s.getList("pages", List.of())) {
                if (entry instanceof List<?> list) {
                    List<String> page = new ArrayList<>();
                    for (Object row : list) {
                        page.add(String.valueOf(row));
                    }
                    pages.add(page);
                } else if (entry instanceof String str) {
                    pages.add(HologramPages.splitLines(str));
                }
            }
            if (!pages.isEmpty()) {
                return pages;
            }
        }
        return List.of(s.getStringList("lines"));
    }

    public record Stored(String id, Location location, String worldName, List<List<String>> pages, double viewDistance,
                         HologramAttach attach, List<HologramClick> clicks, String seePermission) {
        public Location resolved() {
            World world = location.getWorld();
            if (world != null) {
                return location;
            }
            World loaded = Bukkit.getWorld(worldName);
            return new Location(loaded, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }
    }
}
