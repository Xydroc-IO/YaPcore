package com.yapcore.admin.action;

import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

/** Kick / warn / mute / tempban staff actions (Folia-safe). */
public final class AdminModerationActions {

    private final AdminActions actions;

    public AdminModerationActions(AdminActions actions) {
        this.actions = actions;
    }

    public void kick(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.kick")) {
            YapMessages.noPermission(admin, "yapmod.kick");
            return;
        }
        YapSched.entity(actions.plugin(), target, () ->
                target.kick(Component.text(reason, NamedTextColor.RED)));
        admin.sendMessage("§aKicked §f" + target.getName() + "§a.");
    }

    public void warn(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.warn")) {
            YapMessages.noPermission(admin, "yapmod.warn");
            return;
        }
        actions.moderation().ifPresentOrElse(svc -> {
            svc.warn(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason)
                    .whenComplete((p, err) -> YapSched.entity(actions.plugin(), admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cWarn failed: " + err.getMessage());
                        } else {
                            admin.sendMessage("§aWarned §f" + target.getName() + "§a.");
                            target.sendMessage("§cYou were warned: §f" + reason);
                        }
                    }));
        }, () -> actions.runAs(admin, "warn " + target.getName() + " " + reason));
    }

    public void muteHour(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.mute")) {
            YapMessages.noPermission(admin, "yapmod.mute");
            return;
        }
        long expires = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        actions.moderation().ifPresentOrElse(svc -> {
            svc.mute(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason, expires)
                    .whenComplete((p, err) -> YapSched.entity(actions.plugin(), admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cMute failed: " + err.getMessage());
                        } else {
                            admin.sendMessage("§aMuted §f" + target.getName() + " §afor 1h.");
                        }
                    }));
        }, () -> actions.runAs(admin, "tempmute " + target.getName() + " 1h " + reason));
    }

    public void tempbanDay(Player admin, Player target, String reason) {
        if (!admin.hasPermission("yapmod.ban")) {
            YapMessages.noPermission(admin, "yapmod.ban");
            return;
        }
        long expires = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        actions.moderation().ifPresentOrElse(svc -> {
            svc.ban(target.getUniqueId(), target.getName(),
                            admin.getUniqueId(), admin.getName(), reason, expires, false)
                    .whenComplete((p, err) -> YapSched.entity(actions.plugin(), admin, () -> {
                        if (err != null) {
                            admin.sendMessage("§cTempban failed: " + err.getMessage());
                            return;
                        }
                        admin.sendMessage("§aTempbanned §f" + target.getName() + " §afor 1d.");
                        if (target.isOnline()) {
                            YapSched.entity(actions.plugin(), target, () ->
                                    target.kick(Component.text(reason, NamedTextColor.RED)));
                        }
                    }));
        }, () -> actions.runAs(admin, "tempban " + target.getName() + " 1d " + reason));
    }
}
