package com.yapcore.admin.action;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.moderation.ModerationService;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Optional;
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
            YapMessages.noPermission(admin, "yapessentials.teleport");
            return;
        }
        teleport(admin, target.getLocation());
        admin.sendMessage("§aTeleported to §f" + target.getName() + "§a.");
    }

    public void teleportHere(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.teleport") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapessentials.teleport");
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
            YapMessages.noPermission(admin, "yapadmin.give");
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
            YapMessages.noPermission(admin, "yapadmin.give");
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
            YapMessages.noPermission(admin, "yapadmin.economy");
            return;
        }
        if (!economyPluginEnabled()) {
            admin.sendMessage("§cYaPPlayerData is not loaded.");
            return;
        }
        if (amount <= 0) {
            admin.sendMessage("§cAmount must be positive.");
            return;
        }
        var data = Bukkit.getServicesManager().load(com.yapcore.playerdata.PlayerDataService.class);
        if (data == null || !data.economyEnabled()) {
            admin.sendMessage("§cEconomy is disabled.");
            return;
        }
        // Deposit on the target's region thread — never dispatch /eco via global scheduler (Folia).
        YapSched.entity(plugin, target, () -> {
            var next = data.deposit(target.getUniqueId(), amount);
            YapSched.entity(plugin, admin, () -> {
                if (next.isEmpty()) {
                    admin.sendMessage("§cCould not deposit for §f" + target.getName() + "§c.");
                    return;
                }
                admin.sendMessage("§aGave §f$" + amount + " §ato §f" + target.getName()
                        + " §7(bal $" + String.format("%.2f", next.get()) + "§7).");
                if (!target.equals(admin)) {
                    target.sendMessage("§aYou received §f$" + amount + " §afrom staff."
                            + " §7Balance: §f$" + String.format("%.2f", next.get()));
                }
            });
        });
    }

    public void runAs(Player admin, String command) {
        // Folia: command dispatch must run on the command sender's region thread.
        YapSched.entity(plugin, admin, () -> Bukkit.dispatchCommand(admin, command));
    }

    public void kick(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.kick")) {
            YapMessages.noPermission(admin, "yapmod.kick");
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.kick(Component.text(reason, NamedTextColor.RED)));
        admin.sendMessage("§aKicked §f" + target.getName() + "§a.");
    }

    public void warn(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.warn")) {
            YapMessages.noPermission(admin, "yapmod.warn");
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
            YapMessages.noPermission(admin, "yapmod.mute");
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
            YapMessages.noPermission(admin, "yapmod.ban");
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

    public void toggleNightVision(Player admin) {
        YapSched.entity(plugin, admin, () -> {
            if (admin.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                admin.removePotionEffect(PotionEffectType.NIGHT_VISION);
                admin.sendMessage("§7Night vision off.");
            } else {
                admin.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 300, 0, false, false));
                admin.sendMessage("§aNight vision on (5m).");
            }
        });
    }

    public boolean requireTroll(Player admin) {
        if (!admin.hasPermission("yapadmin.troll") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapadmin.troll");
            return false;
        }
        return true;
    }

    /** Strike lightning at the target (does not set them on fire beyond the bolt). */
    public void trollSmite(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.getWorld().strikeLightning(target.getLocation()));
        admin.sendMessage("§eSmote §f" + target.getName() + "§e.");
    }

    public void trollLaunch(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.setVelocity(new Vector(0, 2.8, 0)));
        admin.sendMessage("§eLaunched §f" + target.getName() + "§e.");
    }

    public void trollBurn(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () -> target.setFireTicks(20 * 8));
        admin.sendMessage("§eSet §f" + target.getName() + " §eon fire.");
    }

    public void trollRocket(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () -> {
            target.setVelocity(new Vector(0, 3.5, 0));
            target.getWorld().strikeLightningEffect(target.getLocation());
        });
        admin.sendMessage("§eRocketed §f" + target.getName() + "§e.");
    }

    public void trollSquash(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () -> {
            Location up = target.getLocation().clone().add(0, 25, 0);
            target.teleport(up);
            target.setVelocity(new Vector(0, -3.5, 0));
        });
        admin.sendMessage("§eSquashed §f" + target.getName() + "§e.");
    }

    public void trollBlind(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 12, 0)));
        admin.sendMessage("§eBlinded §f" + target.getName() + "§e.");
    }

    public void trollConfuse(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 15, 1)));
        admin.sendMessage("§eConfused §f" + target.getName() + "§e.");
    }

    public void trollSlap(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () -> {
            Vector away = target.getLocation().toVector().subtract(admin.getLocation().toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            away = away.normalize().multiply(1.8).setY(0.6);
            target.setVelocity(away);
            target.damage(0.1);
        });
        admin.sendMessage("§eSlapped §f" + target.getName() + "§e.");
    }

    public void trollDropHand(Player admin, Player target) {
        if (!requireTroll(admin)) {
            return;
        }
        YapSched.entity(plugin, target, () -> {
            ItemStack hand = target.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                admin.sendMessage("§c" + target.getName() + " holds nothing.");
                return;
            }
            ItemStack drop = hand.clone();
            target.getInventory().setItemInMainHand(null);
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
            admin.sendMessage("§eDropped §f" + target.getName() + "§e's held item.");
        });
    }

    public void runTroll(Player admin, Player target, String type) {
        switch (type.toLowerCase(Locale.ROOT)) {
            case "smite", "lightning", "strike" -> trollSmite(admin, target);
            case "launch", "yeet" -> trollLaunch(admin, target);
            case "burn", "fire" -> trollBurn(admin, target);
            case "rocket" -> trollRocket(admin, target);
            case "squash", "slam" -> trollSquash(admin, target);
            case "blind" -> trollBlind(admin, target);
            case "confuse", "nausea", "dizzy" -> trollConfuse(admin, target);
            case "slap" -> trollSlap(admin, target);
            case "drop", "drophand" -> trollDropHand(admin, target);
            default -> admin.sendMessage("§cUnknown troll: " + type
                    + " §7(smite, launch, burn, rocket, squash, blind, confuse, slap, drop)");
        }
    }

    public void clearInventory(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.clear") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapessentials.clear");
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
            YapMessages.noPermission(admin, "yapadmin.server");
            return;
        }
        if (pluginEnabled("YaPEssentials") && admin.hasPermission("yapessentials.broadcast")) {
            runAs(admin, "broadcast " + message);
            return;
        }
        Bukkit.broadcast(Component.text("[Broadcast] " + message, NamedTextColor.GOLD));
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
