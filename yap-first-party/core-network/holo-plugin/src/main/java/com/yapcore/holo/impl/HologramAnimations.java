package com.yapcore.holo.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named frame animations from {@code plugins/YaPHolo/animations.yml}. */
public final class HologramAnimations {

    public record Clip(int intervalTicks, List<String> frames) {
        String frame(long tick) {
            if (frames == null || frames.isEmpty()) {
                return "";
            }
            int step = Math.max(1, intervalTicks);
            int index = (int) ((tick / step) % frames.size());
            return frames.get(index);
        }
    }

    private final Map<String, Clip> clips = new LinkedHashMap<>();
    private long tick;

    public HologramAnimations(JavaPlugin plugin) {
        reload(plugin);
    }

    public void reload(JavaPlugin plugin) {
        clips.clear();
        File file = new File(plugin.getDataFolder(), "animations.yml");
        if (!file.isFile()) {
            clips.put("wave", new Clip(4, List.of("&e&l✦", "&6&l✦", "&c&l✦", "&6&l✦")));
            clips.put("blink", new Clip(8, List.of("&f", "&7")));
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            int interval = Math.max(1, s.getInt("interval", 4));
            List<String> frames = new ArrayList<>(s.getStringList("frames"));
            if (frames.isEmpty()) {
                frames.add("&f");
            }
            clips.put(id.toLowerCase(Locale.ROOT), new Clip(interval, frames));
        }
        if (clips.isEmpty()) {
            clips.put("wave", new Clip(4, List.of("&e&l✦")));
        }
    }

    public void advance() {
        tick++;
    }

    public String frame(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        Clip clip = clips.get(name.toLowerCase(Locale.ROOT));
        if (clip == null) {
            return "&cUnknown anim &f" + name;
        }
        return clip.frame(tick);
    }
}
