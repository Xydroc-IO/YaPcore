package com.yapcore.vehicles.compat;

import com.yapcore.vehicles.api.Vehicle;
import com.yapcore.vehicles.api.VehicleCompatAPI;
import com.yapcore.vehicles.api.VehicleCompatHook;
import com.yapcore.vehicles.engine.VehicleInstance;
import com.yapcore.vehicles.engine.VehicleKeys;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import com.yapcore.vehicles.engine.VehiclesConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Claims other plugins' minecart/boat vehicles and remaps them onto YaP chassis + mechanics.
 * Foreign entity can be kept as a synced visual so resource-pack / ModelEngine models still show.
 */
public final class VehicleCompatBridge implements VehicleCompatAPI {

    private static final Set<String> KNOWN_PLUGIN_HINTS = Set.of(
            "vehicles", "vehiclesplus", "vc", "modularvehicles", "mvw", "craftasy",
            "vehicleplus", "steering", "car", "truck", "bike", "helicopter"
    );

    private final VehicleServiceImpl api;
    private final VehicleKeys keys;
    private final VehiclesConfig config;
    private final Logger log;
    private final List<VehicleCompatHook> hooks = new CopyOnWriteArrayList<>();

    public VehicleCompatBridge(VehicleServiceImpl api, VehicleKeys keys, VehiclesConfig config, Logger log) {
        this.api = api;
        this.keys = keys;
        this.config = config;
        this.log = log;
    }

    @Override
    public boolean shouldClaim(Entity entity) {
        if (!config.compatEnabled() || entity == null || !entity.isValid()) {
            return false;
        }
        if (isAlreadyOurs(entity) || isForeignVisual(entity)) {
            return false;
        }
        if (!isClaimableKind(entity)) {
            return false;
        }
        for (VehicleCompatHook hook : hooks) {
            Optional<String> mapped = hook.mapType(entity);
            if (mapped.isPresent()) {
                return !"skip".equalsIgnoreCase(mapped.get());
            }
        }
        if (config.compatIgnoreVanilla() && looksPlainVanilla(entity)) {
            return false;
        }
        return hasVehicleMarker(entity) || ownedByKnownVehiclePlugin(entity) || !config.compatRequireMarker();
    }

    @Override
    public Optional<Vehicle> adapt(Entity foreign, @Nullable Player driver, @Nullable String typeId) {
        if (foreign == null || !foreign.isValid()) {
            return Optional.empty();
        }
        if (isAlreadyOurs(foreign)) {
            return api.getByEntity(foreign);
        }
        if (isForeignVisual(foreign)) {
            return api.getByEntity(foreign);
        }

        String resolved = typeId;
        if (resolved == null || resolved.isBlank()) {
            resolved = resolveTypeId(foreign).orElse(config.compatDefaultType());
        }
        if ("skip".equalsIgnoreCase(resolved)) {
            return Optional.empty();
        }
        if (api.getType(resolved).isEmpty()) {
            log.warning("Compat adapt: unknown YaP type '" + resolved + "', falling back to "
                    + config.compatDefaultType());
            resolved = config.compatDefaultType();
        }
        if (api.getType(resolved).isEmpty()) {
            return Optional.empty();
        }

        List<Entity> passengers = new ArrayList<>(foreign.getPassengers());
        for (Entity p : passengers) {
            foreign.removePassenger(p);
        }

        var loc = foreign.getLocation().clone();
        Vehicle spawned;
        try {
            spawned = api.spawn(loc, resolved, driver);
        } catch (RuntimeException ex) {
            log.warning("Compat adapt spawn failed: " + ex.getMessage());
            return Optional.empty();
        }

        if (!(spawned instanceof VehicleInstance instance)) {
            spawned.destroy(false);
            return Optional.empty();
        }

        if (config.compatPreserveModel()) {
            prepareForeignVisual(foreign);
            instance.clearFrameVisuals();
            instance.attachForeignVisual(foreign);
            api.trackForeignVisual(foreign.getUniqueId(), instance.getId());
        } else {
            foreign.remove();
        }

        if (driver != null && driver.isOnline()) {
            instance.enter(driver, -1);
        }
        int seat = 0;
        for (Entity p : passengers) {
            if (p instanceof Player pl && pl.isOnline() && (driver == null || !pl.equals(driver))) {
                instance.enter(pl, seat++);
            }
        }

        log.info("Compat: adapted " + foreign.getType() + " → YaP type '" + resolved
                + "' preserveModel=" + config.compatPreserveModel());
        return Optional.of(instance);
    }

    @Override
    public void registerHook(VehicleCompatHook hook) {
        hooks.add(hook);
        log.info("Compat hook registered"
                + (hook.pluginName() != null ? " (" + hook.pluginName() + ")" : ""));
    }

    @Override
    public void unregisterHook(VehicleCompatHook hook) {
        hooks.remove(hook);
    }

    public Optional<String> resolveTypeId(Entity foreign) {
        for (VehicleCompatHook hook : hooks) {
            Optional<String> mapped = hook.mapType(foreign);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        String name = plainName(foreign);
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (var e : config.compatNameMap().entrySet()) {
                if (lower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    return Optional.of(e.getValue());
                }
            }
        }
        for (String tag : foreign.getScoreboardTags()) {
            String mapped = config.compatTagMap().get(tag.toLowerCase(Locale.ROOT));
            if (mapped != null) {
                return Optional.of(mapped);
            }
            if (tag.toLowerCase(Locale.ROOT).startsWith("yaptype:")) {
                return Optional.of(tag.substring("yaptype:".length()));
            }
        }
        return Optional.of(config.compatDefaultType());
    }

    private void prepareForeignVisual(Entity foreign) {
        PersistentDataContainer pdc = foreign.getPersistentDataContainer();
        pdc.set(keys.role, VehicleKeys.STRING, "foreign_visual");
        foreign.setGravity(false);
        foreign.setSilent(true);
        foreign.setInvulnerable(true);
        foreign.setPersistent(true);
        if (foreign instanceof org.bukkit.entity.LivingEntity living) {
            living.setAI(false);
            living.setRemoveWhenFarAway(false);
            living.setCollidable(false);
        }
        for (Entity p : List.copyOf(foreign.getPassengers())) {
            foreign.removePassenger(p);
        }
        if (foreign instanceof Minecart cart) {
            cart.setMaxSpeed(0);
            cart.setVelocity(new Vector());
        }
    }

    private boolean isClaimableKind(Entity entity) {
        if (entity instanceof Minecart) {
            return config.compatClaimMinecarts();
        }
        if (entity instanceof Boat) {
            return config.compatClaimBoats();
        }
        return config.compatClaimOtherVehicles()
                && entity instanceof org.bukkit.entity.Vehicle
                && !(entity instanceof Player);
    }

    private boolean isAlreadyOurs(Entity entity) {
        return api.getByEntity(entity).isPresent();
    }

    private boolean isForeignVisual(Entity entity) {
        String role = entity.getPersistentDataContainer().get(keys.role, VehicleKeys.STRING);
        return "foreign_visual".equals(role);
    }

    private boolean looksPlainVanilla(Entity entity) {
        if (hasVehicleMarker(entity)) {
            return false;
        }
        if (ownedByKnownVehiclePlugin(entity)) {
            return false;
        }
        return plainName(entity) == null && entity.getScoreboardTags().isEmpty();
    }

    private boolean hasVehicleMarker(Entity entity) {
        if (plainName(entity) != null) {
            return true;
        }
        if (!entity.getScoreboardTags().isEmpty()) {
            for (String tag : entity.getScoreboardTags()) {
                String t = tag.toLowerCase(Locale.ROOT);
                if (t.contains("vehicle") || t.contains("car") || t.contains("yap")
                        || config.compatTagMap().containsKey(t)) {
                    return true;
                }
            }
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        for (var key : pdc.getKeys()) {
            String ns = key.getNamespace().toLowerCase(Locale.ROOT);
            if (!ns.equals("bukkit") && !ns.equals("papermc") && !ns.equals("minecraft")) {
                if (!ns.equals(keys.vehicleId.getNamespace())) {
                    return true;
                }
            }
            String keyKey = key.getKey().toLowerCase(Locale.ROOT);
            if (keyKey.contains("vehicle") || keyKey.contains("model") || keyKey.contains("car")) {
                return true;
            }
        }
        return false;
    }

    private boolean ownedByKnownVehiclePlugin(Entity entity) {
        for (String name : config.compatKnownPlugins()) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                if (hasVehicleMarker(entity) || !config.compatRequireMarker()) {
                    return true;
                }
            }
        }
        String name = plainName(entity);
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String hint : KNOWN_PLUGIN_HINTS) {
                if (lower.contains(hint)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @Nullable String plainName(Entity entity) {
        var component = entity.customName();
        if (component == null) {
            return null;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain.isBlank() ? null : plain;
    }
}
