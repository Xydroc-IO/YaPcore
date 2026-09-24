package com.yapcore.regions.service;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.BuiltinRegionTemplates;
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
    private final RegionFlagAccessOps access = new RegionFlagAccessOps(this);
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
    public void setGameMode(String name, String gameMode) throws SQLException {
        AdminRegion region = requireNamed(name);
        repository.setGameMode(region.id(), gameMode);
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
        templates.save(config.serverId(), templateName.trim(), region.flags(), msgs, region.gameMode());
    }

    @Override
    public void applyTemplate(String regionName, String templateName) throws SQLException {
        if (templates == null) {
            throw new SQLException("Region templates unavailable");
        }
        AdminRegion region = requireNamed(regionName);
        var template = resolveTemplate(templateName.trim())
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
        if (template.gameMode() != null && !template.gameMode().isBlank()) {
            repository.setGameMode(region.id(), template.gameMode());
        }
        reload();
    }

    @Override
    public List<String> listTemplates() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(BuiltinRegionTemplates.names());
        if (templates == null) {
            return List.copyOf(names);
        }
        try {
            names.addAll(templates.listNames(config.serverId()));
            if (!"default".equalsIgnoreCase(config.serverId())) {
                names.addAll(templates.listNames("default"));
            }
            return List.copyOf(names);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to list region templates", e);
            return List.copyOf(names);
        }
    }

    private Optional<RegionTemplateRepository.Template> resolveTemplate(String name) throws SQLException {
        if (templates != null) {
            Optional<RegionTemplateRepository.Template> saved = templates.find(config.serverId(), name);
            if (saved.isEmpty() && !"default".equalsIgnoreCase(config.serverId())) {
                saved = templates.find("default", name);
            }
            if (saved.isPresent()) {
                return saved;
            }
        }
        BuiltinRegionTemplates.Preset preset = BuiltinRegionTemplates.get(name);
        if (preset == null) {
            return Optional.empty();
        }
        return Optional.of(new RegionTemplateRepository.Template(
                preset.flags(), preset.messages(), preset.gameMode()));
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
        return access.canBuild(player, location);
    }

    public boolean canEnter(Player player, Location location) {
        return access.canEnter(player, location);
    }

    public boolean isPvpAllowed(Player attacker, Player victim) {
        return access.isPvpAllowed(attacker, victim);
    }

    /** When false, players in the region take no damage and deal no damage. */
    public boolean isDamageAllowed(Location location) {
        return access.isDamageAllowed(location);
    }

    public boolean isMobDamageAllowed(Player victim) {
        return access.isMobDamageAllowed(victim);
    }

    public boolean isFireSpreadAllowed(Location location) {
        return access.isFireSpreadAllowed(location);
    }

    public boolean isMobSpawningAllowed(Location location) {
        return access.isMobSpawningAllowed(location);
    }

    /** Hostile mobs may path/spawn into this location when allowed (default). */
    public boolean isMobEntryAllowed(Location location) {
        return access.isMobEntryAllowed(location);
    }

    /**
     * When the covering region has {@code weather deny}, force clear client weather for players.
     */
    public boolean forcesClearWeather(Location location) {
        return access.forcesClearWeather(location);
    }

    public boolean canOpenContainer(Player player, Location location) {
        return access.canOpenContainer(player, location);
    }

    public boolean canInteract(Player player, Location location) {
        return access.canInteract(player, location);
    }

    /** Doors / buttons / pressure plates / levers / gates. */
    public boolean canUse(Player player, Location location) {
        return access.canUse(player, location);
    }

    public boolean canDropItems(Player player, Location location) {
        return access.canDropItems(player, location);
    }

    public boolean canPickupItems(Player player, Location location) {
        return access.canPickupItems(player, location);
    }

    public boolean isTntAllowed(Location location) {
        return access.isTntAllowed(location);
    }

    public boolean isCreeperExplosionAllowed(Location location) {
        return access.isCreeperExplosionAllowed(location);
    }

    public boolean isHungerAllowed(Location location) {
        return access.isHungerAllowed(location);
    }

    public boolean isFarmlandTrampleAllowed(Location location) {
        return access.isFarmlandTrampleAllowed(location);
    }

    public boolean isItemFrameAllowed(Location location) {
        return access.isItemFrameAllowed(location);
    }

    public boolean isArmorStandAllowed(Location location) {
        return access.isArmorStandAllowed(location);
    }

    /**
     * Whether damaging tagged YaP NPCs is allowed here.
     * Explicit {@link RegionFlag#NPC_DAMAGE}; when unset, inherits {@link RegionFlag#DAMAGE}.
     * Outside any admin region, returns {@code false} (managed NPCs stay protected).
     */
    public boolean isNpcDamageAllowed(Location location) {
        return access.isNpcDamageAllowed(location);
    }

    public boolean isLeafDecayAllowed(Location location) {
        return access.isLeafDecayAllowed(location);
    }

    public boolean isPistonsAllowed(Location location) {
        return access.isPistonsAllowed(location);
    }

    public boolean isVehiclePlaceAllowed(Location location) {
        return access.isVehiclePlaceAllowed(location);
    }

    public boolean isVehicleDestroyAllowed(Location location) {
        return access.isVehicleDestroyAllowed(location);
    }

    public boolean isSafeServer() {
        return config.isSafeServer();
    }

    /**
     * Ensure classic hub/spawn region names deny hunger + all damage (migrates older hubs).
     */
    public void ensureNamedSafeFlags() {
        for (String name : List.of("spawn", "hub", "lobby")) {
            if (named(name).isEmpty()) {
                continue;
            }
            try {
                setFlag(name, RegionFlag.DAMAGE, FlagValue.DENY);
                setFlag(name, RegionFlag.HUNGER, FlagValue.DENY);
                setFlag(name, RegionFlag.PVP, FlagValue.DENY);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Could not harden region flags for " + name + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void ensureSpawnPad(Location center) throws SQLException {
        if (!config.spawnPadEnabled() || center == null || center.getWorld() == null) {
            return;
        }
        String name = config.spawnPadRegionName();
        String world = center.getWorld().getName();
        int r = config.spawnPadRadius();
        int yPad = config.spawnPadHeight();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        int minY = center.getWorld().getMinHeight();
        int maxY = center.getWorld().getMaxHeight() - 1;
        int x1 = x - r;
        int x2 = x + r;
        int y1 = Math.max(minY, y - yPad);
        int y2 = Math.min(maxY, y + yPad);
        int z1 = z - r;
        int z2 = z + r;
        if (named(name).isPresent()) {
            redefine(name, world, x1, y1, z1, x2, y2, z2);
        } else {
            define(name, world, x1, y1, z1, x2, y2, z2);
        }
        applyTemplate(name, "spawn");
        AdminRegion region = named(name).orElse(null);
        if (region != null && region.priority() < 10) {
            setPriority(name, 10);
        }
    }

    /**
     * First-boot helper: create spawn pad from {@code preferred} or the first world's spawn
     * when no spawn region exists yet.
     */
    public void bootstrapSpawnPadIfNeeded(Location preferred) {
        if (!config.spawnPadEnabled()) {
            return;
        }
        if (named(config.spawnPadRegionName()).isPresent()) {
            ensureNamedSafeFlags();
            return;
        }
        Location loc = preferred;
        if (loc == null || loc.getWorld() == null) {
            var worlds = org.bukkit.Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                return;
            }
            loc = worlds.getFirst().getSpawnLocation();
        }
        try {
            ensureSpawnPad(loc);
            LOG.info("Created spawn-pad region '" + config.spawnPadRegionName()
                    + "' (hunger + damage denied)");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Spawn-pad bootstrap failed: " + e.getMessage());
        }
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
