package com.yapcore.stacker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Chest admin GUI for status, toggles overview, and tool give. */
public final class AdminGui implements Listener {

    public static final String TITLE = "YaP Stacker";

    private final StackerPlugin plugin;

    public AdminGui(StackerPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE));
        StackerConfig c = plugin.stackerConfig();
        StackerMetrics m = plugin.metrics();

        inv.setItem(10, icon(Material.ZOMBIE_SPAWN_EGG, "Mobs",
                "enabled=" + c.mobsEnabled(),
                "mode=" + c.killMode(),
                "radius=" + c.mergeRadius(),
                "max=" + c.maxStack()));
        inv.setItem(12, icon(Material.DIAMOND, "Items",
                "enabled=" + c.itemsEnabled(),
                "radius=" + c.itemMergeRadius(),
                "max=" + c.itemMaxStack()));
        inv.setItem(14, icon(Material.SPAWNER, "Spawners",
                "enabled=" + c.spawnersEnabled(),
                "radius=" + c.spawnerMergeRadius(),
                "max=" + c.spawnerMaxStack()));
        inv.setItem(16, icon(Material.PAPER, "Metrics",
                "mobMerges=" + m.mobMerges(),
                "mobKills=" + m.mobKillsProcessed(),
                "itemMerges=" + m.itemMerges(),
                "spawners=" + m.spawnerStacks(),
                "aura=" + m.auraKills()));

        inv.setItem(20, icon(Material.BLAZE_ROD, "Give Wand", "Click to receive"));
        inv.setItem(22, icon(Material.GOLDEN_HOE, "Give Tool", "Click to receive"));
        inv.setItem(24, icon(Material.NETHER_STAR, "Give Aura", "Click to receive"));

        player.openInventory(inv);
    }

    private static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            var lines = new java.util.ArrayList<Component>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!TITLE.equals(title)) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        if (!player.hasPermission("yapstacker.give") && !player.hasPermission("yapstacker.admin")) {
            return;
        }
        switch (event.getSlot()) {
            case 20 -> plugin.tools().give(player, StackerItems.WAND);
            case 22 -> plugin.tools().give(player, StackerItems.TOOL);
            case 24 -> plugin.tools().give(player, StackerItems.AURA);
            default -> {
            }
        }
    }
}
