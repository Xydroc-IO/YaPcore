package com.yapcore.admin.action;

import com.yapcore.items.api.ItemServices;
import com.yapcore.messages.YapMessages;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Custom-item ability cooldown actions via YaPItems. */
public final class AdminItemActions {

    private final AdminActions actions;

    public AdminItemActions(AdminActions actions) {
        this.actions = actions;
    }

    /** Persist ability cooldown via YaPItems API (preferred over dispatching /yapitems). */
    public boolean setItemAbilityCooldown(Player admin, String itemId, String duration) {
        if (!actions.pluginEnabled("YaPItems")) {
            admin.sendMessage("§cYaPItems is not installed.");
            return false;
        }
        if (!admin.hasPermission("yapitems.admin") && !admin.hasPermission("yapitems.create") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapitems.admin");
            return false;
        }
        String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        String cd = duration == null ? "" : duration.trim().toLowerCase(Locale.ROOT);
        var serviceOpt = ItemServices.find();
        if (serviceOpt.isEmpty()) {
            admin.sendMessage("§cYaPItems service is not ready.");
            return false;
        }
        var service = serviceOpt.get();
        try {
            if (!service.setAbilityCooldown(id, cd)) {
                admin.sendMessage("§cCould not set cooldown for §f" + id + "§c.");
                return false;
            }
            String shown = service.abilityCooldown(id).orElse(cd);
            admin.sendMessage("§aSet §f" + id + "§a ability cooldown to §f" + shown + "§a.");
            return true;
        } catch (AbstractMethodError | NoSuchMethodError e) {
            // Older YaPItems jar without the new API methods.
            actions.closeAndRun(admin, "yapitems cooldown " + id + " " + cd);
            return true;
        }
    }

    public static String itemAbilityCooldownLabel(String itemId) {
        try {
            return ItemServices.find()
                    .flatMap(s -> s.abilityCooldown(itemId))
                    .orElse("none");
        } catch (AbstractMethodError | NoSuchMethodError e) {
            return "—";
        }
    }
}
