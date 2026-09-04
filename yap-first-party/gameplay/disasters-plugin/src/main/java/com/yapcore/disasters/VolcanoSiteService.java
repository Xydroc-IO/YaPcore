package com.yapcore.disasters;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Soft volcano sites — tag real peaks; they can erupt and optionally smoke while idle. */
public final class VolcanoSiteService {

    private final DisastersPlugin plugin;
    private final Map<String, VolcanoSite> sites = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private YapTask ambientTask;

    public VolcanoSiteService(DisastersPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        sites.clear();
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection section = c.getConfigurationSection("volcano-sites");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            if (id == null || id.isBlank()) {
                continue;
            }
            ConfigurationSection site = section.getConfigurationSection(id);
            if (site == null) {
                continue;
            }
            String world = site.getString("world", "world");
            double x = site.getDouble("x");
            double y = site.getDouble("y");
            double z = site.getDouble("z");
            boolean dormant = site.getBoolean("dormant", false);
            String key = id.trim().toLowerCase(Locale.ROOT);
            sites.put(key, new VolcanoSite(key, world, x, y, z, dormant));
        }
    }

    public void startAmbient() {
        stopAmbient();
        if (!plugin.config().volcanoSitesAmbient()) {
            return;
        }
        ambientTask = YapSched.globalTimer(plugin, this::tickAmbient, 40L, 40L);
    }

    public void stopAmbient() {
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
    }

    public void shutdown() {
        stopAmbient();
        sites.clear();
    }

    public List<VolcanoSite> all() {
        List<VolcanoSite> list = new ArrayList<>(sites.values());
        list.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return Collections.unmodifiableList(list);
    }

    public Optional<VolcanoSite> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sites.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean add(String id, Location loc, boolean dormant) {
        if (id == null || id.isBlank() || loc == null || loc.getWorld() == null) {
            return false;
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        VolcanoSite site = new VolcanoSite(key, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), dormant);
        sites.put(key, site);
        persist();
        return true;
    }

    public boolean remove(String id) {
        if (id == null) {
            return false;
        }
        VolcanoSite removed = sites.remove(id.trim().toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        persist();
        return true;
    }

    /** Prefer nearby site, else random active site in world, else original focus. */
    public Location resolveVolcanoFocus(World world, Location focus) {
        if (world == null) {
            return focus;
        }
        List<VolcanoSite> inWorld = activeInWorld(world.getName());
        if (inWorld.isEmpty()) {
            return focus;
        }
        if (focus != null && focus.getWorld() != null && focus.getWorld().equals(world)) {
            VolcanoSite nearest = null;
            double best = plugin.config().volcanoSiteSnapBlocks();
            best = best * best;
            for (VolcanoSite site : inWorld) {
                Location at = site.toLocation();
                if (at == null) {
                    continue;
                }
                double d = at.distanceSquared(focus);
                if (d <= best) {
                    best = d;
                    nearest = site;
                }
            }
            if (nearest != null) {
                Location snapped = nearest.toLocation();
                return snapped != null ? snapped : focus;
            }
        }
        VolcanoSite pick = inWorld.get(random.nextInt(inWorld.size()));
        Location at = pick.toLocation();
        return at != null ? at : focus;
    }

    public Optional<VolcanoSite> randomActiveInWorld(World world) {
        if (world == null) {
            return Optional.empty();
        }
        List<VolcanoSite> inWorld = activeInWorld(world.getName());
        if (inWorld.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(inWorld.get(random.nextInt(inWorld.size())));
    }

    private List<VolcanoSite> activeInWorld(String worldName) {
        List<VolcanoSite> out = new ArrayList<>();
        for (VolcanoSite site : sites.values()) {
            if (site.dormant()) {
                continue;
            }
            if (site.worldName().equalsIgnoreCase(worldName)) {
                out.add(site);
            }
        }
        return out;
    }

    private void persist() {
        FileConfiguration c = plugin.getConfig();
        c.set("volcano-sites", null);
        Map<String, Object> root = new LinkedHashMap<>();
        for (VolcanoSite site : all()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("world", site.worldName());
            node.put("x", site.x());
            node.put("y", site.y());
            node.put("z", site.z());
            if (site.dormant()) {
                node.put("dormant", true);
            }
            root.put(site.id(), node);
        }
        c.createSection("volcano-sites", root);
        plugin.saveConfig();
    }

    private void tickAmbient() {
        if (!plugin.config().volcanoSitesAmbient() || sites.isEmpty()) {
            return;
        }
        for (VolcanoSite site : sites.values()) {
            if (site.dormant() || random.nextFloat() > 0.45f) {
                continue;
            }
            Location loc = site.toLocation();
            if (loc == null) {
                continue;
            }
            World world = loc.getWorld();
            if (world == null || world.getPlayers().isEmpty()) {
                continue;
            }
            // Skip ambient while a volcano disaster is already erupting here.
            if (plugin.manager().isActiveType(world, DisasterType.VOLCANO)) {
                continue;
            }
            YapSched.region(plugin, loc, () -> {
                world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, 1.2, 0.5),
                        4, 0.35, 0.6, 0.35, 0.01);
                world.spawnParticle(Particle.ASH, loc.clone().add(0.5, 2.0, 0.5),
                        6, 0.8, 0.5, 0.8, 0.0);
                if (random.nextFloat() < 0.15f) {
                    world.playSound(loc, Sound.BLOCK_LAVA_AMBIENT, 0.35f, 0.7f);
                }
            });
        }
    }
}
