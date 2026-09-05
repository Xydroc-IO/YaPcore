package com.yapcore.admin.action;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.moderation.ModerationService;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Staff actions for the admin super menu (Folia-safe). */
public final class AdminActions {

    private final AdminPlugin plugin;

    public AdminActions(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean pluginEnabled(String name) {
        var p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /** Economy UI requires YaPPlayerData. */
    public boolean economyPluginEnabled() {
        return pluginEnabled("YaPPlayerData");
    }

    public Optional<ModerationService> moderation() {
        RegisteredServiceProvider<ModerationService> rsp =
                Bukkit.getServicesManager().getRegistration(ModerationService.class);
        return rsp == null ? Optional.empty() : Optional.of(rsp.getProvider());
    }

    public Player resolveTarget(Player admin) {
        var session = plugin.session(admin.getUniqueId());
        if (!session.hasTarget()) {
            return null;
        }
        return Bukkit.getPlayer(session.targetUuid());
    }

    public Player resolveGiveTarget(Player admin) {
        Player target = resolveTarget(admin);
        return target != null ? target : admin;
    }

    public void teleport(Player who, Location dest) {
        if (who == null || dest == null || dest.getWorld() == null) {
            return;
        }
        Location copy = dest.clone();
        YapSched.entity(plugin, who, () -> who.teleport(copy));
    }

    public void teleportToPlayer(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.teleport") && !admin.isOp()) {
            admin.sendMessage("§cNo permission (yapessentials.teleport).");
            return;
        }
        teleport(admin, target.getLocation());
        admin.sendMessage("§aTeleported to §f" + target.getName() + "§a.");
    }

    public void teleportHere(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.teleport") && !admin.isOp()) {
            admin.sendMessage("§cNo permission (yapessentials.teleport).");
            return;
        }
        teleport(target, admin.getLocation());
        admin.sendMessage("§aBrought §f" + target.getName() + " §ato you.");
        target.sendMessage("§eYou were teleported by staff.");
    }

    public void teleportSpawn(Player admin, Player target) {
        Location spawn = target.getWorld().getSpawnLocation();
        teleport(target, spawn);
        admin.sendMessage("§aSent §f" + target.getName() + " §ato spawn.");
    }

    public void giveItem(Player admin, Player target, Material material, int amount) {
        if (!admin.hasPermission("yapadmin.give")) {
            admin.sendMessage("§cNo permission (yapadmin.give).");
            return;
        }
        if (material == null || !material.isItem()) {
            admin.sendMessage("§cInvalid item.");
            return;
        }
        int qty = Math.max(1, Math.min(material.getMaxStackSize() * 36, amount));
        YapSched.entity(plugin, target, () -> {
            int remaining = qty;
            while (remaining > 0) {
                int give = Math.min(remaining, material.getMaxStackSize());
                ItemStack piece = new ItemStack(material, give);
                var leftover = target.getInventory().addItem(piece);
                leftover.values().forEach(left ->
                        target.getWorld().dropItemNaturally(target.getLocation(), left));
                remaining -= give;
            }
            target.sendMessage("§aReceived §f" + qty + "× " + pretty(material) + "§a.");
        });
        if (!target.equals(admin)) {
            admin.sendMessage("§aGave §f" + qty + "× " + pretty(material) + " §ato §f" + target.getName() + "§a.");
        }
    }

    public void giveKit(Player admin, Player target, String kitId) {
        if (!admin.hasPermission("yapadmin.give") && !admin.hasPermission("yapdata.kit.give")
                && !admin.hasPermission("yapdata.admin")) {
            admin.sendMessage("§cNo permission for kits.");
            return;
        }
        if (!pluginEnabled("YaPPlayerData")) {
            admin.sendMessage("§cYaPPlayerData is not loaded.");
            return;
        }
        String cmd = "kit give " + target.getName() + " " + kitId;
        runAs(admin, cmd);
    }

    public void giveMoney(Player admin, Player target, int amount) {
        if (!admin.hasPermission("yapadmin.economy")) {
            admin.sendMessage("§cNo permission (yapadmin.economy).");
            return;
        }
        if (!economyPluginEnabled()) {
            admin.sendMessage("§cYaPPlayerData is not loaded.");
            return;
        }
        runAs(admin, "eco give " + target.getName() + " " + amount);
    }

    public void kick(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.kick")) {
            admin.sendMessage("§cNo permission (yapmod.kick).");
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.kick(Component.text(reason, NamedTextColor.RED)));
        admin.sendMessage("§aKicked §f" + target.getName() + "§a.");
    }

    public void warn(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.warn")) {
            admin.sendMessage("§cNo permission (yapmod.warn).");
            return;
        }
        moderation().ifPresentOrElse(svc -> {
            svc.warn(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason)
                    .whenComplete((p, err) -> YapSched.entity(plugin, admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cWarn failed: " + err.getMessage());
                        } else {
                            admin.sendMessage("§aWarned §f" + target.getName() + "§a.");
                            target.sendMessage("§cYou were warned: §f" + reason);
                        }
                    }));
        }, () -> runAs(admin, "warn " + target.getName() + " " + reason));
    }

    public void muteHour(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.mute")) {
            admin.sendMessage("§cNo permission (yapmod.mute).");
            return;
        }
        long expires = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        moderation().ifPresentOrElse(svc -> {
            svc.mute(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason, expires)
                    .whenComplete((p, err) -> YapSched.entity(plugin, admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cMute failed: " + err.getMessage());
                        } else {
                            admin.sendMessage("§aMuted §f" + target.getName() + " §afor 1h.");
                        }
                    }));
        }, () -> runAs(admin, "tempmute " + target.getName() + " 1h " + reason));
    }

    public void tempbanDay(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.ban")) {
            admin.sendMessage("§cNo permission (yapmod.ban).");
            return;
        }
        long expires = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        moderation().ifPresentOrElse(svc -> {
            svc.ban(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason, expires, false)
                    .whenComplete((p, err) -> YapSched.entity(plugin, admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cTempban failed: " + err.getMessage());
                            return;
                        }
                        admin.sendMessage("§aTempbanned §f" + target.getName() + " §afor 1d.");
                        if (target.isOnline()) {
                            YapSched.entity(plugin, target, () ->
                                    target.kick(Component.text(reason, NamedTextColor.RED)));
                        }
                    }));
        }, () -> runAs(admin, "tempban " + target.getName() + " 1d " + reason));
    }

    public void heal(Player admin, Player target) {
        YapSched.entity(plugin, target, () -> {
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
        });
        admin.sendMessage("§aHealed §f" + target.getName() + "§a.");
    }

    public void feed(Player admin, Player target) {
        YapSched.entity(plugin, target, () -> {
            target.setFoodLevel(20);
            target.setSaturation(20f);
        });
        admin.sendMessage("§aFed §f" + target.getName() + "§a.");
    }

    public void clearInventory(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.clear") && !admin.isOp()) {
            admin.sendMessage("§cNo permission (yapessentials.clear).");
            return;
        }
        YapSched.entity(plugin, target, () -> target.getInventory().clear());
        admin.sendMessage("§aCleared inventory of §f" + target.getName() + "§a.");
        if (!target.equals(admin)) {
            target.sendMessage("§eYour inventory was cleared by staff.");
        }
    }

    public void broadcast(Player admin, String message) {
        if (!admin.hasPermission("yapadmin.server") && !admin.hasPermission("yapessentials.broadcast")) {
            admin.sendMessage("§cNo permission for broadcast.");
            return;
        }
        if (pluginEnabled("YaPEssentials") && admin.hasPermission("yapessentials.broadcast")) {
            runAs(admin, "broadcast " + message);
            return;
        }
        Bukkit.broadcast(Component.text("[Broadcast] " + message, NamedTextColor.GOLD));
    }

    public void runAs(Player admin, String command) {
        YapSched.global(plugin, () -> Bukkit.dispatchCommand(admin, command));
    }

    public void closeAndRun(Player admin, String command) {
        admin.closeInventory();
        YapSched.entityLater(plugin, admin, () -> Bukkit.dispatchCommand(admin, command), 1L);
    }

    public static String pretty(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.equals("SHEARS") || n.equals("FISHING_ROD")
                || n.equals("FLINT_AND_STEEL") || n.equals("BRUSH");
    }

    public static boolean isCombat(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("BOW")
                || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("SHIELD")
                || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") || n.equals("TIPPED_ARROW")
                || n.equals("MACE");
    }
}
