package com.yapcore.dungeons.gui;

import com.yapcore.dungeons.DungeonGate;
import com.yapcore.dungeons.DungeonProgress;
import com.yapcore.dungeons.DungeonService;
import com.yapcore.dungeons.gate.GateEvaluator;
import com.yapcore.dungeons.service.UnlockMath;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonMenu {

    private final JavaPlugin plugin;
    private final DungeonService service;
    private final GateEvaluator gates;

    public DungeonMenu(JavaPlugin plugin, DungeonService service, GateEvaluator gates) {
        this.plugin = plugin;
        this.service = service;
        this.gates = gates;
    }

    public void open(Player player, int page) {
        service.progress(player.getUniqueId()).thenCombine(
                service.selectableLevels(player.getUniqueId()),
                (progress, selectable) -> new Object[]{progress, selectable})
                .thenAccept(arr -> YapSched.entity(plugin, player, () ->
                        openSync(player, page, (DungeonProgress) arr[0], new HashSet<>((List<Integer>) arr[1]))));
    }

    @SuppressWarnings("unchecked")
    private void openSync(Player player, int page, DungeonProgress progress, Set<Integer> selectable) {
        DungeonMenuHolder holder = new DungeonMenuHolder(page);
        String title = page == 2 ? "Prestige Dungeons (51-100)" : "Dungeons (1-50)";
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(title));
        holder.inventory(inv);

        if (page == 2) {
            fillLevels(inv, 51, 100, selectable, progress);
        } else if (page == 1) {
            fillLevels(inv, 28, 50, selectable, progress);
        } else {
            fillLevels(inv, 1, 27, selectable, progress);
        }

        // Nav
        inv.setItem(45, nav(Material.ARROW, "Core 1-27", NamedTextColor.AQUA));
        inv.setItem(46, nav(Material.ARROW, "Core 28-50", NamedTextColor.AQUA));
        inv.setItem(47, nav(Material.NETHER_STAR, "Prestige 51-100", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(49, info(progress, selectable));
        inv.setItem(53, nav(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(inv);
    }

    private void fillLevels(Inventory inv, int from, int to, Set<Integer> selectable, DungeonProgress progress) {
        int slot = 0;
        for (int level = from; level <= to && slot < 45; level++, slot++) {
            boolean unlocked = selectable.contains(level);
            Material mat = unlocked
                    ? (level >= 51 ? Material.NETHERITE_INGOT : Material.IRON_INGOT)
                    : Material.GRAY_DYE;
            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Dungeon Level " + level,
                        unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
                List<Component> lore = new ArrayList<>();
                DungeonGate gate = gates.gate(level);
                lore.add(Component.text("Overall ≥ " + Math.max(10, gate.overallMin()), NamedTextColor.GRAY));
                if (gate.miningMin() > 0) {
                    lore.add(Component.text("Mining ≥ " + gate.miningMin(), NamedTextColor.GRAY));
                }
                if (gate.strengthMin() > 0) {
                    lore.add(Component.text("Strength ≥ " + gate.strengthMin(), NamedTextColor.GRAY));
                }
                if (!unlocked) {
                    lore.add(Component.text("LOCKED", NamedTextColor.RED));
                    if (level >= 51 && progress.highestCleared() < UnlockMath.CORE_MAX) {
                        lore.add(Component.text("Clear dungeon 50 first", NamedTextColor.DARK_RED));
                    }
                } else {
                    lore.add(Component.text("Click to enter", NamedTextColor.YELLOW));
                }
                meta.lore(lore);
                meta.setCustomModelData(level);
                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }
    }

    private ItemStack nav(Material mat, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack info(DungeonProgress progress, Set<Integer> selectable) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Your progress", NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Highest cleared: " + progress.highestCleared(), NamedTextColor.GRAY),
                    Component.text("Prestige cleared: " + progress.prestigeCleared(), NamedTextColor.GRAY),
                    Component.text("Completions: " + progress.totalCompletions(), NamedTextColor.GRAY),
                    Component.text("Selectable: " + selectable.size(), NamedTextColor.GRAY)));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
