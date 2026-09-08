package com.yapcore.items.gui;

import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Chest browser for giving custom items. */
public final class ItemsGui {

    public static final int SLOT_PREV = 45;
    public static final int SLOT_NEXT = 53;
    public static final int SLOT_CLOSE = 49;
    public static final int PAGE_SIZE = 28;

    private final ItemsPlugin plugin;

    public ItemsGui(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<ItemDefinition> defs = new ArrayList<>(plugin.registry().all().values());
        defs.sort(Comparator.comparing(ItemDefinition::id));
        int maxPage = Math.max(0, (defs.size() - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(page, maxPage));
        Holder holder = new Holder(p);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP Items", NamedTextColor.AQUA));
        holder.bind(inv);

        int start = p * PAGE_SIZE;
        int slot = 10;
        for (int i = start; i < Math.min(start + PAGE_SIZE, defs.size()); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemDefinition def = defs.get(i);
            ItemStack icon = plugin.factory().build(def, 1);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("Click to give ×1", NamedTextColor.GREEN));
                lore.add(Component.text("Shift-click ×64", NamedTextColor.DARK_GREEN));
                lore.add(Component.text("id: " + def.id(), NamedTextColor.DARK_GRAY));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        if (p > 0) {
            inv.setItem(SLOT_PREV, named(Material.ARROW, "Previous"));
        }
        if (p < maxPage) {
            inv.setItem(SLOT_NEXT, named(Material.ARROW, "Next"));
        }
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "Close"));
        player.openInventory(inv);
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.YELLOW)));
        return stack;
    }

    public static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        public Holder(int page) {
            this.page = page;
        }

        public int page() {
            return page;
        }

        public void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
