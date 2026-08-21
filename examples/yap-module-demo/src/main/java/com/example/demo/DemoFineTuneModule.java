package com.example.demo;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Example module — shows SYNC inventory + HEAVY placeholder work. */
public final class DemoFineTuneModule extends YaPModule {

    @Override
    public void onEnable() {
        getLogger().info("DemoFineTune module enabled — drop into modules/ to load");
        getScheduler().runHeavy(() -> {
            // pretend DB / HTTP
            getScheduler().runSync(() -> {
                Inventory inv = Bukkit.createInventory(null, 27, "Demo Module");
                inv.setItem(13, new ItemStack(Material.EMERALD, 1));
                getLogger().info("Prepared demo inventory on SYNC (title=" + inv.getTitle() + ")");
            });
        });
    }
}
