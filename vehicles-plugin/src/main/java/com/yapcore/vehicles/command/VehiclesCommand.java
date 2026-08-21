package com.yapcore.vehicles.command;

import com.yapcore.vehicles.api.Vehicle;
import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class VehiclesCommand implements CommandExecutor, TabCompleter {

    private final VehicleServiceImpl api;

    public VehiclesCommand(VehicleServiceImpl api) {
        this.api = api;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage("YaP Vehicles — /" + label
                    + " <spawn|list|destroy|types|give|adapt|shop|upgrades|reload>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "spawn" -> spawn(sender, args);
            case "list" -> list(sender);
            case "destroy" -> destroy(sender);
            case "types" -> types(sender);
            case "give" -> give(sender, args);
            case "adapt" -> adapt(sender, args);
            case "shop" -> shop(sender);
            case "upgrades" -> upgradesCmd(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage("Unknown subcommand.");
                yield true;
            }
        };
    }

    private boolean spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapvehicles.spawn")) {
            player.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /yapvehicle spawn <type>");
            return true;
        }
        try {
            Vehicle v = api.spawn(player.getLocation(), args[1], player);
            player.sendMessage("Spawned " + v.getType().displayName() + " (" + v.getId() + ")");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            player.sendMessage("Failed: " + ex.getMessage());
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        var all = api.getVehicles();
        sender.sendMessage("Live vehicles: " + all.size());
        for (Vehicle v : all) {
            sender.sendMessage("- " + v.getType().id() + " @ "
                    + formatLoc(v) + " speed=" + String.format("%.2f", v.getSpeed())
                    + " fuel=" + String.format("%.0f/%.0f", v.getFuel(), v.getMaxFuel())
                    + " hp=" + String.format("%.0f/%.0f", v.getHealth(), v.getMaxHealth()));
        }
        return true;
    }

    private boolean destroy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapvehicles.destroy")) {
            player.sendMessage("No permission.");
            return true;
        }
        var riding = api.getByPassenger(player);
        if (riding.isPresent()) {
            riding.get().destroy(true);
            player.sendMessage("Destroyed your vehicle.");
            return true;
        }
        // Nearest within 6 blocks
        Vehicle nearest = null;
        double best = 36;
        for (Vehicle v : api.getVehicles()) {
            if (v.getLocation().getWorld() != player.getWorld()) {
                continue;
            }
            double d = v.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        if (nearest == null) {
            player.sendMessage("No vehicle nearby.");
            return true;
        }
        nearest.destroy(true);
        player.sendMessage("Destroyed " + nearest.getType().id());
        return true;
    }

    private boolean types(CommandSender sender) {
        sender.sendMessage("Registered types:");
        for (VehicleType t : api.getTypes()) {
            sender.sendMessage("- " + t.id() + " — " + t.displayName()
                    + " seats=" + t.seatCount()
                    + " fuel=" + (t.usesFuel() ? (int) t.maxFuel() : "off")
                    + " hp=" + (t.usesDamage() ? (int) t.maxHealth() : "off"));
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapvehicles.spawn")) {
            player.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /yapvehicle give <type>");
            return true;
        }
        var type = api.getType(args[1]);
        if (type.isEmpty()) {
            player.sendMessage("Unknown type.");
            return true;
        }
        player.getInventory().addItem(api.createSpawnItem(type.get()));
        player.sendMessage("Gave spawn item for " + type.get().id());
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapvehicles.command")) {
            sender.sendMessage("No permission.");
            return true;
        }
        api.plugin().reloadVehicles();
        sender.sendMessage("YaP Vehicles config reloaded (types preserved).");
        return true;
    }

    private boolean adapt(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapvehicles.spawn")) {
            player.sendMessage("No permission.");
            return true;
        }
        var hit = player.getTargetEntity(6);
        if (hit == null) {
            player.sendMessage("Look at a minecart/boat (or plugin vehicle) within 6 blocks.");
            return true;
        }
        String typeId = args.length >= 2 ? args[1] : null;
        var result = api.compat().adapt(hit, player, typeId);
        if (result.isEmpty()) {
            player.sendMessage("Could not adapt that entity (vanilla unmarked, or compat off).");
            return true;
        }
        player.sendMessage("Adapted → " + result.get().getType().id());
        return true;
    }

    private boolean shop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapvehicles.drive")) {
            player.sendMessage("No permission.");
            return true;
        }
        api.upgrades().openShop(player);
        return true;
    }

    private boolean upgradesCmd(CommandSender sender, String[] args) {
        if (args.length >= 2 && "give".equalsIgnoreCase(args[1])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("yapvehicles.spawn")) {
                player.sendMessage("No permission.");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage("Usage: /yapvehicle upgrades give <id>");
                return true;
            }
            var up = api.upgrades().get(args[2]);
            if (up.isEmpty()) {
                player.sendMessage("Unknown upgrade. Use /yapvehicle upgrades");
                return true;
            }
            player.getInventory().addItem(api.upgrades().createItem(up.get()));
            player.sendMessage("Gave " + up.get().displayName());
            return true;
        }
        sender.sendMessage("Upgrades:");
        for (var u : api.upgrades().getAll()) {
            sender.sendMessage("- " + u.id() + " [" + u.slot() + "] CMD=" + u.customModelData()
                    + " — " + u.displayName());
        }
        sender.sendMessage("Craft in inventory · /yapvehicle shop · sneak+RMB vehicle to install");
        sender.sendMessage("Fuel: sneak+RMB vehicle with " + api.plugin().config().fuelItem());
        return true;
    }

    private static String formatLoc(Vehicle v) {
        var l = v.getLocation();
        return String.format("%s %.0f,%.0f,%.0f",
                l.getWorld() != null ? l.getWorld().getName() : "?",
                l.getX(), l.getY(), l.getZ());
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filter(Arrays.asList(
                    "spawn", "list", "destroy", "types", "give", "adapt", "shop", "upgrades", "reload"), args[0]);
        }
        if (args.length == 2 && ("spawn".equalsIgnoreCase(args[0]) || "give".equalsIgnoreCase(args[0])
                || "adapt".equalsIgnoreCase(args[0]))) {
            return filter(api.getTypes().stream().map(VehicleType::id).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && "upgrades".equalsIgnoreCase(args[0])) {
            return filter(List.of("give"), args[1]);
        }
        if (args.length == 3 && "upgrades".equalsIgnoreCase(args[0]) && "give".equalsIgnoreCase(args[1])) {
            return filter(api.upgrades().getAll().stream().map(u -> u.id()).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
