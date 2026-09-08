package com.yapcore.admin.action;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;

/** Troll / prank staff actions (Folia-safe). */
public final class AdminTrollActions {

    private final AdminPlugin plugin;

    public AdminTrollActions(AdminPlugin plugin) {
        this.plugin = plugin;
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
}
