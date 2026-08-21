package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.VehiclesPlugin;
import com.yapcore.vehicles.api.ChassisKit;
import com.yapcore.vehicles.api.Vehicle;
import com.yapcore.vehicles.api.VehicleAPI;
import com.yapcore.vehicles.api.VehicleCompatAPI;
import com.yapcore.vehicles.api.VehicleSeat;
import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.api.VehicleVisual;
import com.yapcore.vehicles.api.event.VehicleDestroyEvent;
import com.yapcore.vehicles.api.event.VehicleEnterEvent;
import com.yapcore.vehicles.api.event.VehicleExitEvent;
import com.yapcore.vehicles.api.event.VehicleSpawnEvent;
import com.yapcore.vehicles.api.VehicleUpgradeAPI;
import com.yapcore.vehicles.compat.VehicleCompatBridge;
import com.yapcore.vehicles.upgrades.UpgradeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleServiceImpl implements VehicleAPI {

    private final VehiclesPlugin plugin;
    private final VehicleKeys keys;
    private final Map<String, VehicleType> types = new ConcurrentHashMap<>();
    private final Map<UUID, VehicleInstance> vehicles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entityToVehicle = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> foreignVisualToVehicle = new ConcurrentHashMap<>();
    private VehicleCompatBridge compat;
    private UpgradeService upgrades;

    public VehicleServiceImpl(VehiclesPlugin plugin, VehicleKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public void setCompat(VehicleCompatBridge compat) {
        this.compat = compat;
    }

    public void setUpgrades(UpgradeService upgrades) {
        this.upgrades = upgrades;
    }

    @Override
    public VehicleUpgradeAPI upgrades() {
        if (upgrades == null) {
            throw new IllegalStateException("UpgradeService not initialized");
        }
        return upgrades;
    }

    @Override
    public VehicleCompatAPI compat() {
        if (compat == null) {
            throw new IllegalStateException("VehicleCompatBridge not initialized");
        }
        return compat;
    }

    public void trackForeignVisual(UUID foreignId, UUID vehicleId) {
        foreignVisualToVehicle.put(foreignId, vehicleId);
        entityToVehicle.put(foreignId, vehicleId);
    }

    public void untrackForeignVisual(UUID foreignId) {
        foreignVisualToVehicle.remove(foreignId);
        entityToVehicle.remove(foreignId);
    }

    public VehicleKeys keys() {
        return keys;
    }

    public VehiclesPlugin plugin() {
        return plugin;
    }

    @Override
    public void registerType(VehicleType type) {
        types.put(type.id(), type);
        plugin.getLogger().info("Registered vehicle type: " + type.id());
    }

    @Override
    public boolean unregisterType(String typeId) {
        return types.remove(typeId.toLowerCase()) != null;
    }

    @Override
    public Optional<VehicleType> getType(String typeId) {
        return Optional.ofNullable(types.get(typeId.toLowerCase()));
    }

    @Override
    public Collection<VehicleType> getTypes() {
        return Collections.unmodifiableCollection(types.values());
    }

    @Override
    public Vehicle spawn(Location location, String typeId, @Nullable Player owner) {
        VehicleType type = types.get(typeId.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Unknown vehicle type: " + typeId);
        }
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }

        UUID id = UUID.randomUUID();
        Location chassisLoc = location.clone();
        float yaw = location.getYaw();

        ArmorStand chassis = world.spawn(chassisLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setCustomNameVisible(false);
            stand.customName(net.kyori.adventure.text.Component.text(type.displayName()));
            tag(stand, id, type.id(), "chassis", -1);
            stand.setRotation(yaw, 0);
        });

        List<ArmorStand> seats = new ArrayList<>();
        List<VehicleSeat> defs = type.seats();
        for (int i = 0; i < defs.size(); i++) {
            VehicleSeat def = defs.get(i);
            Location seatLoc = localToWorld(chassisLoc, yaw, def.offset());
            final int seatIndex = i;
            ArmorStand seat = world.spawn(seatLoc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(false);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.setInvulnerable(true);
                stand.setPersistent(true);
                stand.setCollidable(false);
                stand.setSilent(true);
                stand.setCustomNameVisible(false);
                tag(stand, id, type.id(), "seat", seatIndex);
                stand.setRotation(yaw + def.yawOffset(), 0);
            });
            seats.add(seat);
            entityToVehicle.put(seat.getUniqueId(), id);
        }

        List<Display> displays = new ArrayList<>();
        for (VehicleVisual visual : type.visuals()) {
            Location visLoc = localToWorld(chassisLoc, yaw, visual.offset());
            Display display;
            if (visual.kind() == VehicleVisual.Kind.ITEM) {
                display = world.spawn(visLoc, ItemDisplay.class, d -> {
                    tag(d, id, type.id(), "visual", -1);
                    visual.applyTransform(d);
                });
            } else {
                display = world.spawn(visLoc, BlockDisplay.class, d -> {
                    d.setBlock(visual.material().createBlockData());
                    tag(d, id, type.id(), "visual", -1);
                    visual.applyTransform(d);
                });
            }
            display.setRotation(yaw + visual.yawOffset(), visual.pitchOffset());
            displays.add(display);
            entityToVehicle.put(display.getUniqueId(), id);
        }

        entityToVehicle.put(chassis.getUniqueId(), id);
        VehicleInstance instance = new VehicleInstance(this, id, type, chassis, seats, displays, owner);
        instance.loadUpgradesFromPdc();
        vehicles.put(id, instance);

        VehicleSpawnEvent event = new VehicleSpawnEvent(instance, owner);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            instance.destroyInternal(false);
            vehicles.remove(id);
            throw new IllegalStateException("Vehicle spawn cancelled");
        }
        return instance;
    }

    @Override
    public Optional<Vehicle> getVehicle(UUID vehicleId) {
        return Optional.ofNullable(vehicles.get(vehicleId));
    }

    @Override
    public Optional<Vehicle> getByEntity(Entity entity) {
        UUID vid = entityToVehicle.get(entity.getUniqueId());
        if (vid == null) {
            vid = foreignVisualToVehicle.get(entity.getUniqueId());
        }
        if (vid == null) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            String raw = pdc.get(keys.vehicleId, VehicleKeys.STRING);
            if (raw != null) {
                try {
                    vid = UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return vid == null ? Optional.empty() : Optional.ofNullable(vehicles.get(vid));
    }

    @Override
    public Optional<Vehicle> getByPassenger(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            Optional<Vehicle> byMount = getByEntity(vehicle);
            if (byMount.isPresent()) {
                return byMount;
            }
        }
        for (VehicleInstance v : vehicles.values()) {
            if (v.seatOf(player).isPresent()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<Vehicle> getVehicles() {
        return Collections.unmodifiableCollection(vehicles.values());
    }

    @Override
    public boolean destroy(Vehicle vehicle, boolean dropItem) {
        if (!(vehicle instanceof VehicleInstance instance)) {
            return false;
        }
        VehicleDestroyEvent event = new VehicleDestroyEvent(instance, dropItem);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        instance.destroyInternal(dropItem && plugin.config().dropItemOnDestroy());
        vehicles.remove(instance.getId());
        return true;
    }

    void unregisterEntity(UUID entityId) {
        entityToVehicle.remove(entityId);
    }

    public void tickAll() {
        if (!plugin.config().enabled()) {
            return;
        }
        for (VehicleInstance v : List.copyOf(vehicles.values())) {
            if (!v.isAlive()) {
                vehicles.remove(v.getId());
                continue;
            }
            VehiclePhysics.tick(v, plugin.config());
            if (plugin.config().emptyDespawnSeconds() > 0 && v.isEmpty()) {
                if (v.noteEmptyTick(plugin.config().emptyDespawnSeconds())) {
                    destroy(v, false);
                }
            } else {
                v.clearEmptyTimer();
            }
        }
    }

    public void destroyAll() {
        for (VehicleInstance v : List.copyOf(vehicles.values())) {
            v.destroyInternal(false);
        }
        vehicles.clear();
        entityToVehicle.clear();
        foreignVisualToVehicle.clear();
    }

    boolean tryEnter(VehicleInstance vehicle, Player player, int seatIndex) {
        VehicleEnterEvent event = new VehicleEnterEvent(vehicle, player, seatIndex);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    boolean tryExit(VehicleInstance vehicle, Player player) {
        VehicleExitEvent event = new VehicleExitEvent(vehicle, player);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    static Location localToWorld(Location origin, float yawDeg, Vector local) {
        double rad = Math.toRadians(yawDeg);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        // local: +X right, +Y up, +Z forward
        double x = local.getX() * cos + local.getZ() * sin;
        double z = -local.getX() * sin + local.getZ() * cos;
        return origin.clone().add(x, local.getY(), z);
    }

    private void tag(Entity entity, UUID vehicleId, String typeId, String role, int seatIndex) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keys.vehicleId, VehicleKeys.STRING, vehicleId.toString());
        pdc.set(keys.typeId, VehicleKeys.STRING, typeId);
        pdc.set(keys.role, VehicleKeys.STRING, role);
        if (seatIndex >= 0) {
            pdc.set(keys.seatIndex, VehicleKeys.INT, seatIndex);
        }
    }

    public ItemStack createSpawnItem(VehicleType type) {
        // Always brand as a YaP chassis token — never a minecart/boat item appearance by default.
        Material mat = type.spawnItem();
        if (mat.name().contains("MINECART") || mat.name().contains("BOAT")) {
            mat = ChassisKit.SPAWN_TOKEN;
        }
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("YaP Chassis: " + type.displayName()));
            meta.lore(java.util.List.of(
                    net.kyori.adventure.text.Component.text("Right-click to place"),
                    net.kyori.adventure.text.Component.text("Type: " + type.id()),
                    net.kyori.adventure.text.Component.text("Not a minecart — YaP Vehicles frame")
            ));
            meta.setCustomModelData(77_000 + Math.floorMod(type.id().hashCode(), 9000));
            meta.getPersistentDataContainer().set(keys.typeId, VehicleKeys.STRING, type.id());
            meta.getPersistentDataContainer().set(keys.role, VehicleKeys.STRING, "spawn_item");
        });
        return stack;
    }

    public Optional<String> spawnItemType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String role = pdc.get(keys.role, VehicleKeys.STRING);
        if (!"spawn_item".equals(role)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pdc.get(keys.typeId, VehicleKeys.STRING));
    }

    Map<String, VehicleType> typesMutable() {
        return types;
    }
}
