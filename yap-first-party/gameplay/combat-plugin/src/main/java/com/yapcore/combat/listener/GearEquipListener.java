package com.yapcore.combat.listener;

import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GearEquipListener implements Listener {

    private final JavaPlugin plugin;
    private final CombatServiceImpl combat;

    public GearEquipListener(JavaPlugin plugin, CombatServiceImpl combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        YapSched.entityLater(plugin, player, () -> combat.recalculate(player), 1L);
    }
}
