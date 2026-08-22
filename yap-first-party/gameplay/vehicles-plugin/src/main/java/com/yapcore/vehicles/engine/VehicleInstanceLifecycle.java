package com.yapcore.vehicles.engine;

import com.yapcore.vehicles.api.VehicleSeat;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;

/** Seat entry/exit, transform sync, and teardown. */
final class VehicleInstanceLifecycle {

    private VehicleInstanceLifecycle() {
    }

    static boolean enter(VehicleInstance vehicle, Player player, int seatIndex) {
        if (!vehicle.alive || player == null || !player.isOnline()) {
            return false;
        }
        if (player.getWorld() != vehicle.chassis.getWorld()) {
            return false;
        }
        if (vehicle.seatOf(player).isPresent()) {
            return true;
        }
        int idx = seatIndex;
        if (idx < 0) {
            idx = findFreeSeatPreferDriver(vehicle);
        }
        if (idx < 0 || idx >= vehicle.occupants.length || vehicle.occupants[idx] != null) {
            return false;
        }
        if (!vehicle.service.tryEnter(vehicle, player, idx)) {
            return false;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        ArmorStand seat = vehicle.seats.get(idx);
        seat.addPassenger(player);
        vehicle.occupants[idx] = player;
        clearEmptyTimer(vehicle);
        return true;
    }

    static boolean exit(VehicleInstance vehicle, Player player) {
        Optional<Integer> seat = vehicle.seatOf(player);
        if (seat.isEmpty()) {
            return false;
        }
        if (!vehicle.service.tryExit(vehicle, player)) {
            return false;
        }
        int idx = seat.get();
        vehicle.occupants[idx] = null;
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Location exit = vehicle.chassis.getLocation().clone().add(0, 0.5, 0);
        player.teleport(exit);
        return true;
    }

    static void syncTransforms(VehicleInstance vehicle) {
        Location base = vehicle.chassis.getLocation();
        base.setYaw(vehicle.yaw);
        base.setPitch(0);
        vehicle.chassis.teleport(base);
        vehicle.chassis.setRotation(vehicle.yaw, 0);

        List<VehicleSeat> defs = vehicle.type.seats();
        for (int i = 0; i < vehicle.seats.size(); i++) {
            ArmorStand seat = vehicle.seats.get(i);
            VehicleSeat def = defs.get(i);
            Location seatLoc = VehicleServiceImpl.localToWorld(base, vehicle.yaw, def.offset());
            seatLoc.setYaw(vehicle.yaw + def.yawOffset());
            seatLoc.setPitch(0);
            seat.teleport(seatLoc);
            seat.setRotation(vehicle.yaw + def.yawOffset(), 0);
            Player p = vehicle.occupants[i];
            if (p != null && p.isOnline()) {
                if (!seat.getPassengers().contains(p)) {
                    seat.addPassenger(p);
                }
            } else if (p != null) {
                vehicle.occupants[i] = null;
            }
        }

        var visuals = vehicle.type.visuals();
        double tireScale = vehicle.effTireScale();
        for (int i = 0; i < vehicle.displays.size() && i < visuals.size(); i++) {
            Display d = vehicle.displays.get(i);
            var vis = visuals.get(i);
            Location loc = VehicleServiceImpl.localToWorld(base, vehicle.yaw, vis.offset());
            loc.setYaw(vehicle.yaw + vis.yawOffset());
            loc.setPitch(vis.pitchOffset());
            d.teleport(loc);
            d.setRotation(vehicle.yaw + vis.yawOffset(), vis.pitchOffset());
            if (vis.role() == com.yapcore.vehicles.api.VehicleVisual.Role.WHEEL) {
                vis.applyTransform(d, tireScale);
            }
        }

        for (Entity foreign : List.copyOf(vehicle.foreignVisuals)) {
            if (!foreign.isValid()) {
                vehicle.foreignVisuals.remove(foreign);
                vehicle.service.untrackForeignVisual(foreign.getUniqueId());
                continue;
            }
            Location fl = base.clone();
            fl.setYaw(vehicle.yaw);
            fl.setPitch(0);
            foreign.teleport(fl);
            foreign.setVelocity(new Vector());
            if (foreign instanceof org.bukkit.entity.Minecart cart) {
                cart.setMaxSpeed(0);
            }
        }
    }

    static void destroyInternal(VehicleInstance vehicle, boolean dropItem) {
        if (!vehicle.alive) {
            return;
        }
        vehicle.alive = false;
        for (Player p : List.copyOf(vehicle.getOccupants())) {
            if (p.isInsideVehicle()) {
                p.leaveVehicle();
            }
            p.teleport(vehicle.chassis.getLocation().clone().add(0, 1, 0));
        }
        for (int i = 0; i < vehicle.occupants.length; i++) {
            vehicle.occupants[i] = null;
        }
        if (dropItem) {
            vehicle.chassis.getWorld().dropItemNaturally(vehicle.chassis.getLocation(),
                    vehicle.service.createSpawnItem(vehicle.type));
        }
        for (Entity foreign : List.copyOf(vehicle.foreignVisuals)) {
            vehicle.service.untrackForeignVisual(foreign.getUniqueId());
            vehicle.service.unregisterEntity(foreign.getUniqueId());
            foreign.remove();
        }
        vehicle.foreignVisuals.clear();
        for (Display d : vehicle.displays) {
            vehicle.service.unregisterEntity(d.getUniqueId());
            d.remove();
        }
        for (ArmorStand s : vehicle.seats) {
            vehicle.service.unregisterEntity(s.getUniqueId());
            s.remove();
        }
        vehicle.service.unregisterEntity(vehicle.chassis.getUniqueId());
        vehicle.chassis.remove();
    }

    private static int findFreeSeatPreferDriver(VehicleInstance vehicle) {
        for (int i = 0; i < vehicle.type.seats().size(); i++) {
            if (vehicle.type.seats().get(i).driver() && vehicle.occupants[i] == null) {
                return i;
            }
        }
        for (int i = 0; i < vehicle.occupants.length; i++) {
            if (vehicle.occupants[i] == null) {
                return i;
            }
        }
        return -1;
    }

    static boolean noteEmptyTick(VehicleInstance vehicle, int despawnSeconds) {
        long now = vehicle.chassis.getWorld().getFullTime();
        if (vehicle.emptySinceTick < 0) {
            vehicle.emptySinceTick = now;
            return false;
        }
        return (now - vehicle.emptySinceTick) >= despawnSeconds * 20L;
    }

    static void clearEmptyTimer(VehicleInstance vehicle) {
        vehicle.emptySinceTick = -1;
    }

    static void clearOccupant(VehicleInstance vehicle, Player player) {
        for (int i = 0; i < vehicle.occupants.length; i++) {
            if (vehicle.occupants[i] != null && vehicle.occupants[i].getUniqueId().equals(player.getUniqueId())) {
                vehicle.occupants[i] = null;
            }
        }
    }
}
