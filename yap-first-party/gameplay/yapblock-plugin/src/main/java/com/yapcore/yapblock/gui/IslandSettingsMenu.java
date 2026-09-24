package com.yapcore.yapblock.gui;

import com.yapcore.yapblock.IslandFlag;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class IslandSettingsMenu {

    public static final String TITLE = "Island Settings";

    private final YapblockPlugin plugin;

    public IslandSettingsMenu(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        IslandServiceImpl service = plugin.service();
        if (service == null) {
            return;
        }
        IslandSnapshot snap = service.settingsOps().requireManageable(player);
        if (snap == null) {
            return;
        }
        IslandSettingsHolder holder = new IslandSettingsHolder(snap.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(TITLE));
        holder.inventory(inv);
        int slot = 10;
        for (IslandFlag flag : IslandFlag.values()) {
            inv.setItem(slot++, flagItem(flag, snap.flag(flag)));
        }
        inv.setItem(22, closeItem());
        player.openInventory(inv);
    }

    static ItemStack flagItem(IslandFlag flag, boolean on) {
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(flag.name(), on ? NamedTextColor.GREEN : NamedTextColor.RED));
            meta.lore(List.of(Component.text("Click to toggle", NamedTextColor.GRAY)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack closeItem() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Close", NamedTextColor.RED));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
