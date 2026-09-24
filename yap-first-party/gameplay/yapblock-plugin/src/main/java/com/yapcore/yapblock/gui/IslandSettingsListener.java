package com.yapcore.yapblock.gui;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class IslandSettingsListener implements Listener {

    private final YapblockPlugin plugin;

    public IslandSettingsListener(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof IslandSettingsHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        IslandServiceImpl service = plugin.service();
        if (service == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        IslandFlag[] flags = IslandFlag.values();
        int index = slot - 10;
        if (index < 0 || index >= flags.length) {
            return;
        }
        IslandFlag flag = flags[index];
        service.settingsOps().toggleFlag(player, flag).thenAccept(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
            YapSched.entity(plugin, player, () -> {
                IslandSnapshot snap = service.islandById(holder.islandId()).orElse(null);
                if (snap != null
                        && player.getOpenInventory().getTopInventory().getHolder() instanceof IslandSettingsHolder) {
                    ItemStack stack = IslandSettingsMenu.flagItem(flag, snap.flag(flag));
                    player.getOpenInventory().getTopInventory().setItem(slot, stack);
                }
            });
        });
    }
}
