package com.yapcore.admin.action;

import com.yapcore.items.api.ItemServices;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

    /**
     * Give a YaPItems stack to {@code targetName} (online player). Prefer ItemService;
     * returns false if the item or target cannot be resolved.
     */
    public boolean giveYapItem(Player admin, String targetName, String itemId, int amount) {
        if (admin == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        if (!admin.hasPermission("yapadmin.give")
                && !admin.hasPermission("yap420.admin")
                && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapadmin.give");
            return false;
        }
        Player target = Bukkit.getPlayerExact(targetName == null ? "" : targetName);
        if (target == null || !target.isOnline()) {
            admin.sendMessage("§cPlayer not online: §f" + targetName);
            return false;
        }
        int qty = Math.max(1, Math.min(64, amount));
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        var stackOpt = ItemServices.find().flatMap(s -> s.create(id, qty));
        if (stackOpt.isEmpty()) {
            admin.sendMessage("§cUnknown YaP item §f" + id + "§c (is YaPItems loaded with yap420.yml?).");
            return false;
        }
        ItemStack stack = stackOpt.get();
        YapSched.entity(actions.plugin(), target, () -> {
            var leftover = target.getInventory().addItem(stack);
            leftover.values().forEach(left ->
                    target.getWorld().dropItemNaturally(target.getLocation(), left));
            target.sendMessage("§aReceived §f" + qty + "× " + id + "§a.");
        });
        if (!target.equals(admin)) {
            admin.sendMessage("§aGave §f" + qty + "× " + id + " §ato §f" + target.getName() + "§a.");
        } else {
            admin.sendMessage("§aGave §f" + qty + "× " + id + "§a.");
        }
        return true;
    }
}
