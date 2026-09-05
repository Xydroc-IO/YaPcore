package com.yapcore.dungeons.gen;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ThemeTable {

    public record Theme(
            Material floor,
            Material wall,
            Material accent,
            Material light,
            Material ore,
            List<EntityType> mobs,
            EntityType boss) {
    }

    private final List<Band> bands = new ArrayList<>();

    private record Band(int min, int max, Theme theme) {
    }

    public void reload(JavaPlugin plugin, File file) {
        bands.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<java.util.Map<?, ?>> list = yaml.getMapList("bands");
        for (java.util.Map<?, ?> map : list) {
            int min = intVal(map.get("min"), 1);
            int max = intVal(map.get("max"), 10);
            Theme theme = new Theme(
                    mat(str(map.get("floor")), Material.STONE),
                    mat(str(map.get("wall")), Material.COBBLESTONE),
                    mat(str(map.get("accent")), Material.STONE_BRICKS),
                    mat(str(map.get("light")), Material.TORCH),
                    mat(str(map.get("ore")), Material.IRON_ORE),
                    parseMobs(str(map.get("mobs")) == null ? "ZOMBIE" : str(map.get("mobs"))),
                    entity(str(map.get("boss")), EntityType.ZOMBIE));
            bands.add(new Band(min, max, theme));
        }
        plugin.getLogger().info("Loaded " + bands.size() + " dungeon theme bands");
    }

    private static int intVal(Object o, int fb) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fb;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    public Theme themeFor(int level) {
        for (Band b : bands) {
            if (level >= b.min && level <= b.max) {
                return b.theme;
            }
        }
        return new Theme(Material.STONE, Material.COBBLESTONE, Material.STONE_BRICKS,
                Material.TORCH, Material.IRON_ORE, List.of(EntityType.ZOMBIE), EntityType.ZOMBIE);
    }

    private static Material mat(String raw, Material fb) {
        if (raw == null) {
            return fb;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fb;
        }
    }

    private static EntityType entity(String raw, EntityType fb) {
        if (raw == null) {
            return fb;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fb;
        }
    }

    private static List<EntityType> parseMobs(String csv) {
        List<EntityType> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            EntityType t = entity(part.trim(), null);
            if (t != null) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            out.add(EntityType.ZOMBIE);
        }
        return List.copyOf(out);
    }
}
