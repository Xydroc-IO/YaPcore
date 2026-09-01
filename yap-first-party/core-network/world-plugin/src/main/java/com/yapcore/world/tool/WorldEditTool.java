package com.yapcore.world.tool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Unified world-edit wand — selection, brush, and GUI opener. */
public final class WorldEditTool {

    public static final Material MATERIAL = Material.GOLDEN_AXE;

    private final NamespacedKey toolKey;

    public WorldEditTool(JavaPlugin plugin) {
        this.toolKey = new NamespacedKey(plugin, "world_edit_tool");
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(MATERIAL);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("YaP World Edit Tool")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("Left-click block → pos1").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click block → pos2 / brush").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift + right-click → open editor").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return stack;
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null || stack.getType() != MATERIAL) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }
}
