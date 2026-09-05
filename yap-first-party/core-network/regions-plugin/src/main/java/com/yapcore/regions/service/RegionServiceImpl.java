package com.yapcore.regions.service;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.PolyDraftService;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionLookup;
import com.yapcore.regions.RegionMessageKind;
import com.yapcore.regions.RegionService;
import com.yapcore.regions.RegionVertex;
import com.yapcore.regions.RegionsConfig;
import com.yapcore.regions.db.AdminRegionRepository;
import com.yapcore.regions.db.RegionMessageRepository;
import com.yapcore.regions.db.RegionTemplateRepository;
import com.yapcore.sched.StaffBypass;
import com.yapcore.world.CuboidSelection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RegionServiceImpl implements RegionService {

    private static final Logger LOG = Logger.getLogger("YaPRegions");

    private final RegionsConfig config;
    private final AdminRegionRepository repository;
    private final RegionMessageRepository messages;
    private final RegionTemplateRepository templates;
    private final PolyDraftService polyDrafts = new PolyDraftService();
    private List<AdminRegion> regions = List.of();

    public RegionServiceImpl(RegionsConfig config, AdminRegionRepository repository) {
        this(config, repository, null, null);
    }

    public RegionServiceImpl(RegionsConfig config, AdminRegionRepository repository,
                             RegionMessageRepository messages) {
        this(config, repository, messages, null);
    }

    public RegionServiceImpl(RegionsConfig config, AdminRegionRepository repository,
                             RegionMessageRepository messages, RegionTemplateRepository templates) {
        this.config = config;
        this.repository = repository;
        this.messages = messages;
        this.templates = templates;
    }

    public PolyDraftService polyDrafts() {
        return polyDrafts;
    }

    public void reload() {
        try {
            regions = List.copyOf(repository.loadForServer(config.serverId()));
            if (messages != null) {
                messages.invalidateAll();
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to load admin regions", e);
            regions = List.of();
        }
    }

    @Override
    public List<AdminRegion> listRegions() {
        return regions;
    }

    @Override
    public Optional<String> message(long regionId, RegionMessageKind kind) {
        if (messages == null) {
            return Optional.empty();
        }
        return messages.get(regionId, kind);
    }

    @Override
    public void setMessage(String name, RegionMessageKind kind, String text) throws SQLException {
        AdminRegion region = requireNamed(name);
        if (messages == null) {
            throw new SQLException("Region messages unavailable");
        }
        messages.set(region.id(), kind, text);
    }

    @Override
    public void clearMessage(String name, RegionMessageKind kind) throws SQLException {
        AdminRegion region = requireNamed(name);
        if (messages == null) {
            throw new SQLException("Region messages unavailable");
        }
        messages.clear(region.id(), kind);
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
        return requireId(id);
    }

    @Override
    public AdminRegion define(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException {
        return define(name, new CuboidSelection(world, x1, y1, z1, x2, y2, z2));
    }

    public AdminRegion defineAt(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException {
        return define(name, world, x1, y1, z1, x2, y2, z2);
    }

    @Override
    public AdminRegion definePolygon(String name, String world, int minY, int maxY,
                                     List<RegionVertex> vertices) throws SQLException {
        long id = repository.createPolygon(config.serverId(), name, world, minY, maxY, vertices);
        reload();
        return requireId(id);
    }

    @Override
    public void setFlag(String name, RegionFlag flag, FlagValue value) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.setFlag(region.id(), flag, value);
        reload();
    }

    @Override
    public void setPriority(String name, int priority) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.setPriority(region.id(), priority);
        reload();
    }

    @Override
    public void remove(String name) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.delete(region.id());
        reload();
    }

    public AdminRegion redefine(String name, CuboidSelection selection) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.updateBounds(
                region.id(),
                selection.world(),
                selection.minX(),
                selection.maxX(),
                selection.minY(),
                selection.maxY(),
                selection.minZ(),
                selection.maxZ());
        reload();
        return requireId(region.id());
    }

    @Override
    public AdminRegion redefine(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException {
        return redefine(name, new CuboidSelection(world, x1, y1, z1, x2, y2, z2));
    }

    public AdminRegion redefineAt(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException {
        return redefine(name, world, x1, y1, z1, x2, y2, z2);
    }

    @Override
    public AdminRegion redefinePolygon(String name, String world, int minY, int maxY,
                                       List<RegionVertex> vertices) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.updatePolygon(region.id(), world, minY, maxY, vertices);
        reload();
        return requireId(region.id());
    }

    @Override
    public void saveTemplate(String templateName, String fromRegion) throws SQLException {
        if (templates == null) {
            throw new SQLException("Region templates unavailable");
        }
        AdminRegion region = requireNamed(fromRegion);
        Map<RegionMessageKind, String> msgs = new EnumMap<>(RegionMessageKind.class);
        if (messages != null) {
            for (RegionMessageKind kind : RegionMessageKind.values()) {
                messages.get(region.id(), kind).ifPresent(text -> msgs.put(kind, text));
            }
        }
        templates.save(config.serverId(), templateName.trim(), region.flags(), msgs);
    }

    @Override
    public void applyTemplate(String regionName, String templateName) throws SQLException {
        if (templates == null) {
            throw new SQLException("Region templates unavailable");
        }
        AdminRegion region = requireNamed(regionName);
        var template = templates.find(config.serverId(), templateName.trim())
                .orElseThrow(() -> new SQLException("Unknown template: " + templateName));
        repository.clearFlags(region.id());
        for (var e : template.flags().entrySet()) {
            repository.setFlag(region.id(), e.getKey(), e.getValue());
        }
        if (messages != null) {
            for (RegionMessageKind kind : RegionMessageKind.values()) {
                String text = template.messages().get(kind);
                if (text == null || text.isBlank()) {
                    messages.clear(region.id(), kind);
                } else {
                    messages.set(region.id(), kind, text);
                }
            }
        }
        reload();
    }

    @Override
    public List<String> listTemplates() {
        if (templates == null) {
            return List.of();
        }
        try {
            return templates.listNames(config.serverId());
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to list region templates", e);
            return List.of();
        }
    }

    @Override
    public Optional<AdminRegion> at(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        return RegionLookup.at(
                regions,
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
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

    public boolean canDropItems(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.ITEM_DROP) == FlagValue.ALLOW;
    }

    public boolean canPickupItems(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return flagAt(location, RegionFlag.ITEM_PICKUP) == FlagValue.ALLOW;
    }

    public boolean isTntAllowed(Location location) {
        Optional<AdminRegion> region = at(location);
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.TNT) == FlagValue.ALLOW;
    }

    public boolean isCreeperExplosionAllowed(Location location) {
        Optional<AdminRegion> region = at(location);
        if (region.isEmpty()) {
            return true;
        }
        return resolve(region.get(), RegionFlag.CREEPER_EXPLOSION) == FlagValue.ALLOW;
    }

    private AdminRegion requireNamed(String name) throws SQLException {
        return repository.findByName(config.serverId(), name)
                .orElseThrow(() -> new SQLException("Unknown region: " + name));
    }

    private AdminRegion requireId(long id) throws SQLException {
        return regions.stream().filter(r -> r.id() == id).findFirst()
                .orElseThrow(() -> new SQLException("Region not found after write id=" + id));
    }
}
