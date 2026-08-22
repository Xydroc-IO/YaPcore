package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.api.Vehicle;
import com.yapcore.vehicles.api.VehicleSeat;
import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.api.event.VehicleDamageEvent;
import com.yapcore.vehicles.api.event.VehicleFuelEmptyEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VehicleInstance implements Vehicle {

    private final VehicleServiceImpl service;
    private final UUID id;
    private final VehicleType type;
    private final ArmorStand chassis;
    private final List<ArmorStand> seats;
    private final List<Display> displays;
    private final @Nullable UUID ownerId;

    private float yaw;
    private double speed;
    private double lateralSpeed;
    private double yawRate;
    private double verticalSpeed;
    private double fuel;
    private double health;
    private boolean alive = true;
    private boolean fuelEmptyFired;
    private long emptySinceTick = -1;
    private final Player[] occupants;
    private final java.util.List<Entity> foreignVisuals = new java.util.ArrayList<>();
    private final java.util.EnumMap<com.yapcore.vehicles.api.UpgradeSlot, String> installedUpgrades =
            new java.util.EnumMap<>(com.yapcore.vehicles.api.UpgradeSlot.class);

    public VehicleInstance(
            VehicleServiceImpl service,
            UUID id,
            VehicleType type,
            ArmorStand chassis,
            List<ArmorStand> seats,
            List<Display> displays,
            @Nullable Player owner
    ) {
        this.service = service;
        this.id = id;
        this.type = type;
        this.chassis = chassis;
        this.seats = List.copyOf(seats);
        this.displays = new ArrayList<>(displays);
        this.ownerId = owner == null ? null : owner.getUniqueId();
        this.yaw = chassis.getLocation().getYaw();
        this.fuel = type.maxFuel() > 0 ? type.maxFuel() : 0;
        this.health = type.maxHealth() > 0 ? type.maxHealth() : 0;
        this.occupants = new Player[type.seatCount()];
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public VehicleType getType() {
        return type;
    }

    @Override
    public Entity getChassis() {
        return chassis;
    }

    @Override
    public Location getLocation() {
        return chassis.getLocation();
    }

    @Override
    public float getYaw() {
        return yaw;
    }

    void setYaw(float yaw) {
        this.yaw = yaw;
    }

    @Override
    public double getSpeed() {
        return speed;
    }

    void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public double getLateralSpeed() {
        return lateralSpeed;
    }

    void setLateralSpeed(double lateralSpeed) {
        this.lateralSpeed = lateralSpeed;
    }

    double yawRate() {
        return yawRate;
    }

    void setYawRate(double yawRate) {
        this.yawRate = yawRate;
    }

    double verticalSpeed() {
        return verticalSpeed;
    }

    void setVerticalSpeed(double verticalSpeed) {
        this.verticalSpeed = verticalSpeed;
    }

    @Override
    public Vector getVelocity() {
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);
        // forward * speed + right * lateral
        double dx = sin * speed + cos * lateralSpeed;
        double dz = cos * speed - sin * lateralSpeed;
        return new Vector(dx, verticalSpeed, dz);
    }

    @Override
    public @Nullable Player getDriver() {
        for (int i = 0; i < type.seats().size(); i++) {
            if (type.seats().get(i).driver()) {
                return occupants[i];
            }
        }
        return null;
    }

    @Override
    public List<Player> getOccupants() {
        List<Player> out = new ArrayList<>();
        for (Player p : occupants) {
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        for (Player p : occupants) {
            if (p != null && p.isOnline()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public double getFuel() {
        return fuel;
    }

    @Override
    public double getMaxFuel() {
        return type.maxFuel() + effectiveStats().maxFuelAdd();
    }

    @Override
    public double refuel(double amount) {
        if (!type.usesFuel() || amount <= 0) {
            return 0;
        }
        double before = fuel;
        fuel = Math.min(getMaxFuel(), fuel + amount);
        if (fuel > 0) {
            fuelEmptyFired = false;
        }
        return fuel - before;
    }

    void consumeFuel(double amount) {
        if (!type.usesFuel() || !service.plugin().config().fuelEnabled()) {
            return;
        }
        double burn = amount * service.plugin().config().fuelBurnMultiplier()
                * effectiveStats().fuelBurnMul();
        fuel = Math.max(0, fuel - burn);
        if (fuel <= 0 && !fuelEmptyFired) {
            fuelEmptyFired = true;
            Bukkit.getPluginManager().callEvent(new VehicleFuelEmptyEvent(this));
        }
    }

    boolean hasFuelForThrottle() {
        if (!type.usesFuel() || !service.plugin().config().fuelEnabled()) {
            return true;
        }
        return fuel > 0;
    }

    @Override
    public double getHealth() {
        return health;
    }

    @Override
    public double getMaxHealth() {
        return type.maxHealth() + effectiveStats().maxHealthAdd();
    }

    public com.yapcore.vehicles.api.StatModifier effectiveStats() {
        com.yapcore.vehicles.api.StatModifier combined = com.yapcore.vehicles.api.StatModifier.none();
        var ups = service.plugin().upgrades();
        if (ups == null) {
            return combined;
        }
        for (String id : installedUpgrades.values()) {
            var u = ups.get(id);
            if (u.isPresent()) {
                combined = combined.and(u.get().stats());
            }
        }
        return combined;
    }

    public void installUpgrade(com.yapcore.vehicles.api.VehicleUpgrade upgrade) {
        installedUpgrades.put(upgrade.slot(), upgrade.id());
        if (type.usesDamage() && upgrade.stats().maxHealthAdd() > 0) {
            health = Math.min(getMaxHealth(), health + upgrade.stats().maxHealthAdd());
        }
        persistUpgrades();
    }

    public Optional<com.yapcore.vehicles.api.VehicleUpgrade> uninstallUpgrade(
            com.yapcore.vehicles.api.UpgradeSlot slot
    ) {
        String id = installedUpgrades.remove(slot);
        persistUpgrades();
        if (id == null) {
            return Optional.empty();
        }
        return service.plugin().upgrades() != null
                ? service.plugin().upgrades().get(id)
                : Optional.empty();
    }

    public java.util.Map<com.yapcore.vehicles.api.UpgradeSlot, String> installedUpgradeIds() {
        return java.util.Map.copyOf(installedUpgrades);
    }

    private void persistUpgrades() {
        var pdc = chassis.getPersistentDataContainer();
        StringBuilder sb = new StringBuilder();
        for (var e : installedUpgrades.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        pdc.set(service.keys().upgrades, VehicleKeys.STRING, sb.toString());
    }

    void loadUpgradesFromPdc() {
        String raw = chassis.getPersistentDataContainer().get(service.keys().upgrades, VehicleKeys.STRING);
        if (raw == null || raw.isBlank()) {
            return;
        }
        installedUpgrades.clear();
        for (String part : raw.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            try {
                var slot = com.yapcore.vehicles.api.UpgradeSlot.valueOf(kv[0]);
                installedUpgrades.put(slot, kv[1]);
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    public double effMaxSpeed() {
        return type.maxSpeed() * effectiveStats().maxSpeedMul();
    }

    public double effAcceleration() {
        return type.acceleration() * effectiveStats().accelerationMul();
    }

    public double effBrake() {
        return type.brakeForce() * effectiveStats().brakeMul();
    }

    public double effTurnRate() {
        return type.turnRate() * effectiveStats().turnRateMul();
    }

    public double effTraction() {
        return type.traction() * effectiveStats().tractionMul();
    }

    public double effLateralGrip() {
        return type.lateralGrip() * effectiveStats().lateralGripMul();
    }

    public double effRolling() {
        return type.rollingResistance() * effectiveStats().rollingResistanceMul();
    }

    public double effBoostMul() {
        return effectiveStats().boostMul();
    }

    public double effRideHeight() {
        return type.rideHeight() + effectiveStats().rideHeightAdd();
    }

    public double effTireScale() {
        return effectiveStats().tireScaleMul();
    }

    public double effSlopeGrip() {
        return effectiveStats().slopeGripMul();
    }

    @Override
    public boolean damage(double amount, String cause) {
        if (!type.usesDamage() || !service.plugin().config().damageEnabled() || amount <= 0) {
            return false;
        }
        VehicleDamageEvent event = new VehicleDamageEvent(this, amount, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        health = Math.max(0, health - event.getAmount());
        if (health <= 0) {
            service.destroy(this, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean enter(Player player, int seatIndex) {
        if (!alive || player == null || !player.isOnline()) {
            return false;
        }
        if (player.getWorld() != chassis.getWorld()) {
            return false;
        }
        if (seatOf(player).isPresent()) {
            return true;
        }
        int idx = seatIndex;
        if (idx < 0) {
            idx = findFreeSeatPreferDriver();
        }
        if (idx < 0 || idx >= occupants.length || occupants[idx] != null) {
            return false;
        }
        if (!service.tryEnter(this, player, idx)) {
            return false;
        }
        // Dismount anything else
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        ArmorStand seat = seats.get(idx);
        seat.addPassenger(player);
        occupants[idx] = player;
        clearEmptyTimer();
        return true;
    }

    @Override
    public boolean exit(Player player) {
        Optional<Integer> seat = seatOf(player);
        if (seat.isEmpty()) {
            return false;
        }
        if (!service.tryExit(this, player)) {
            return false;
        }
        int idx = seat.get();
        occupants[idx] = null;
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Location exit = chassis.getLocation().clone().add(0, 0.5, 0);
        player.teleport(exit);
        return true;
    }

    @Override
    public void destroy(boolean dropItem) {
        service.destroy(this, dropItem);
    }

    @Override
    public Optional<Integer> seatOf(Player player) {
        for (int i = 0; i < occupants.length; i++) {
            if (occupants[i] != null && occupants[i].getUniqueId().equals(player.getUniqueId())) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public boolean isAlive() {
        return alive && chassis.isValid();
    }

    @Nullable
    UUID ownerId() {
        return ownerId;
    }

    List<ArmorStand> seats() {
        return seats;
    }

    List<Display> displays() {
        return displays;
    }

    public void attachForeignVisual(Entity foreign) {
        if (foreign != null && foreign.isValid() && !foreignVisuals.contains(foreign)) {
            foreignVisuals.add(foreign);
            foreign.getPersistentDataContainer().set(
                    service.keys().vehicleId, VehicleKeys.STRING, id.toString());
        }
    }

    /** Hide YaP BlockDisplay frame so a foreign plugin model is the only visible body. */
    public void clearFrameVisuals() {
        for (Display d : List.copyOf(displays)) {
            service.unregisterEntity(d.getUniqueId());
            d.remove();
        }
        displays.clear();
    }

    void syncTransforms() {
        Location base = chassis.getLocation();
        base.setYaw(yaw);
        base.setPitch(0);
        chassis.teleport(base);
        chassis.setRotation(yaw, 0);

        List<VehicleSeat> defs = type.seats();
        for (int i = 0; i < seats.size(); i++) {
            ArmorStand seat = seats.get(i);
            VehicleSeat def = defs.get(i);
            Location seatLoc = VehicleServiceImpl.localToWorld(base, yaw, def.offset());
            seatLoc.setYaw(yaw + def.yawOffset());
            seatLoc.setPitch(0);
            seat.teleport(seatLoc);
            seat.setRotation(yaw + def.yawOffset(), 0);
            Player p = occupants[i];
            if (p != null && p.isOnline()) {
                if (!seat.getPassengers().contains(p)) {
                    seat.addPassenger(p);
                }
            } else if (p != null) {
                occupants[i] = null;
            }
        }

        var visuals = type.visuals();
        double tireScale = effTireScale();
        for (int i = 0; i < displays.size() && i < visuals.size(); i++) {
            Display d = displays.get(i);
            var vis = visuals.get(i);
            Location loc = VehicleServiceImpl.localToWorld(base, yaw, vis.offset());
            loc.setYaw(yaw + vis.yawOffset());
            loc.setPitch(vis.pitchOffset());
            d.teleport(loc);
            d.setRotation(yaw + vis.yawOffset(), vis.pitchOffset());
            if (vis.role() == com.yapcore.vehicles.api.VehicleVisual.Role.WHEEL) {
                vis.applyTransform(d, tireScale);
            }
        }

        // Keep foreign plugin models glued to chassis (compat layer)
        for (Entity foreign : List.copyOf(foreignVisuals)) {
            if (!foreign.isValid()) {
                foreignVisuals.remove(foreign);
                service.untrackForeignVisual(foreign.getUniqueId());
                continue;
            }
            Location fl = base.clone();
            fl.setYaw(yaw);
            fl.setPitch(0);
            foreign.teleport(fl);
            foreign.setVelocity(new Vector());
            if (foreign instanceof org.bukkit.entity.Minecart cart) {
                cart.setMaxSpeed(0);
            }
        }
    }

    void destroyInternal(boolean dropItem) {
        if (!alive) {
            return;
        }
        alive = false;
        for (Player p : List.copyOf(getOccupants())) {
            if (p.isInsideVehicle()) {
                p.leaveVehicle();
            }
            p.teleport(chassis.getLocation().clone().add(0, 1, 0));
        }
        for (int i = 0; i < occupants.length; i++) {
            occupants[i] = null;
        }
        if (dropItem) {
            chassis.getWorld().dropItemNaturally(chassis.getLocation(), service.createSpawnItem(type));
        }
        for (Entity foreign : List.copyOf(foreignVisuals)) {
            service.untrackForeignVisual(foreign.getUniqueId());
            service.unregisterEntity(foreign.getUniqueId());
            foreign.remove();
        }
        foreignVisuals.clear();
        for (Display d : displays) {
            service.unregisterEntity(d.getUniqueId());
            d.remove();
        }
        for (ArmorStand s : seats) {
            service.unregisterEntity(s.getUniqueId());
            s.remove();
        }
        service.unregisterEntity(chassis.getUniqueId());
        chassis.remove();
    }

    private int findFreeSeatPreferDriver() {
        for (int i = 0; i < type.seats().size(); i++) {
            if (type.seats().get(i).driver() && occupants[i] == null) {
                return i;
            }
        }
        for (int i = 0; i < occupants.length; i++) {
            if (occupants[i] == null) {
                return i;
            }
        }
        return -1;
    }

    boolean noteEmptyTick(int despawnSeconds) {
        long now = chassis.getWorld().getFullTime();
        if (emptySinceTick < 0) {
            emptySinceTick = now;
            return false;
        }
        return (now - emptySinceTick) >= despawnSeconds * 20L;
    }

    void clearEmptyTimer() {
        emptySinceTick = -1;
    }

    void clearOccupant(Player player) {
        for (int i = 0; i < occupants.length; i++) {
            if (occupants[i] != null && occupants[i].getUniqueId().equals(player.getUniqueId())) {
                occupants[i] = null;
            }
        }
    }
}
