package com.yapcore.regions.service;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionService;
import com.yapcore.regions.RegionsConfig;
import com.yapcore.regions.db.AdminRegionRepository;
import com.yapcore.sched.StaffBypass;
import com.yapcore.world.CuboidSelection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RegionServiceImpl implements RegionService {

    private static final Logger LOG = Logger.getLogger("YaPRegions");

    private final RegionsConfig config;
    private final AdminRegionRepository repository;
    private List<AdminRegion> regions = List.of();

    public RegionServiceImpl(RegionsConfig config, AdminRegionRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public void reload() {
        try {
            regions = List.copyOf(repository.loadForServer(config.serverId()));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to load admin regions", e);
            regions = List.of();
        }
    }

    public List<AdminRegion> listRegions() {
        return regions;
    }

    public AdminRegion define(String name, CuboidSelection selection) throws SQLException {
        long id = repository.create(
                config.serverId(),
                name,
                selection.world(),
                selection.minX(),
                selection.maxX(),
                selection.minY(),
                selection.maxY(),
                selection.minZ(),
                selection.maxZ());
        reload();
        return regions.stream().filter(r -> r.id() == id).findFirst()
                .orElseThrow(() -> new SQLException("Region not found after create"));
    }

    public AdminRegion defineAt(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException {
        return define(name, new CuboidSelection(world, x1, y1, z1, x2, y2, z2));
    }

    public void setFlag(String name, RegionFlag flag, FlagValue value) throws SQLException {
        AdminRegion region = repository.findByName(config.serverId(), name)
                .orElseThrow(() -> new SQLException("Unknown region: " + name));
        repository.setFlag(region.id(), flag, value);
        reload();
    }

    @Override
    public Optional<AdminRegion> at(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        AdminRegion found = null;
        long bestVolume = Long.MAX_VALUE;
        for (AdminRegion region : regions) {
            if (!region.contains(world, x, y, z)) {
                continue;
            }
            long volume = volumeOf(region);
            if (volume < bestVolume) {
                bestVolume = volume;
                found = region;
            }
        }
        return Optional.ofNullable(found);
    }

    @Override
    public Optional<AdminRegion> named(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return regions.stream()
                .filter(r -> r.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    @Override
    public FlagValue flagAt(Location location, RegionFlag flag) {
        return at(location).map(r -> resolve(r, flag)).orElse(FlagValue.ALLOW);
    }

    public FlagValue resolve(AdminRegion region, RegionFlag flag) {
        FlagValue explicit = region.flags().get(flag);
        return explicit != null ? explicit : FlagValue.ALLOW;
    }

    public boolean canBuild(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.BUILD) == FlagValue.ALLOW;
    }

    public boolean canEnter(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.ENTRY) == FlagValue.ALLOW;
    }

    public boolean isPvpAllowed(Player attacker, Player victim) {
        if (StaffBypass.land(attacker)) {
            return true;
        }
        Optional<AdminRegion> region = at(victim.getLocation());
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.PVP) == FlagValue.ALLOW;
    }

    public boolean isMobDamageAllowed(Player victim) {
        Optional<AdminRegion> region = at(victim.getLocation());
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.MOB_DAMAGE) == FlagValue.ALLOW;
    }

    public boolean isFireSpreadAllowed(Location location) {
        Optional<AdminRegion> region = at(location);
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.FIRE_SPREAD) == FlagValue.ALLOW;
    }

    public boolean isMobSpawningAllowed(Location location) {
        Optional<AdminRegion> region = at(location);
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.MOB_SPAWNING) == FlagValue.ALLOW;
    }

    public boolean canOpenContainer(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.CHEST_ACCESS) == FlagValue.ALLOW;
    }

    public boolean canInteract(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.INTERACT) == FlagValue.ALLOW;
    }

    private static long volumeOf(AdminRegion region) {
        long dx = (long) region.maxX() - region.minX() + 1;
        long dy = (long) region.maxY() - region.minY() + 1;
        long dz = (long) region.maxZ() - region.minZ() + 1;
        return dx * dy * dz;
    }
}
