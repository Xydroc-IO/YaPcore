package com.yapcore.stacker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-spawner stack UI (wand right-click). */
public final class SpawnerGui implements Listener {

    public static final String TITLE_PREFIX = "Stacked Spawner";

    private final SpawnerStackService spawners;
    private final Map<UUID, Block> open = new ConcurrentHashMap<>();

    public SpawnerGui(SpawnerStackService spawners) {
        this.spawners = spawners;
    }

    public void open(Player player, Block block) {
        CreatureSpawner cs = spawners.asSpawner(block);
        if (cs == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX + " · " + spawners.spawnType(cs)));
        refresh(inv, cs);
        open.put(player.getUniqueId(), block);
        player.openInventory(inv);
    }

    private void refresh(Inventory inv, CreatureSpawner cs) {
        inv.clear();
        int size = spawners.getStack(cs);
        inv.setItem(11, icon(Material.SPAWNER, "Type: " + spawners.spawnType(cs),
                "Stack size: " + size));
        inv.setItem(13, icon(Material.LIME_DYE, "+1 stack", "Add one (creative helper)"));
        inv.setItem(15, icon(Material.RED_DYE, "-1 stack", "Remove one"));
        inv.setItem(22, icon(Material.HOPPER, "Absorb nearby", "Merge nearby same-type spawners"));
    }

    private static ItemStack icon(Material mat, String name, String lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(Component.text(lore).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        });
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.startsWith(TITLE_PREFIX)) {
            return;
        }
        event.setCancelled(true);
        Block block = open.get(player.getUniqueId());
        if (block == null) {
            return;
        }
        CreatureSpawner cs = spawners.asSpawner(block);
        if (cs == null) {
            player.closeInventory();
            return;
        }
        switch (event.getSlot()) {
            case 13 -> {
                spawners.setStack(cs, spawners.getStack(cs) + 1);
                refresh(event.getInventory(), cs);
            }
            case 15 -> {
                int s = spawners.getStack(cs);
                if (s > 1) {
                    spawners.setStack(cs, s - 1);
                }
                refresh(event.getInventory(), spawners.asSpawner(block));
            }
            case 22 -> {
                int n = spawners.absorbNearbyInto(block);
                player.sendMessage("Absorbed " + n);
                CreatureSpawner updated = spawners.asSpawner(block);
                if (updated != null) {
                    refresh(event.getInventory(), updated);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
