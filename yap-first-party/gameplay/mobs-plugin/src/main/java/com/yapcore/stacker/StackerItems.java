package com.yapcore.stacker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Special stacker items: wand, tool, kill-aura. */
public final class StackerItems {

    public static final String WAND = "wand";
    public static final String TOOL = "tool";
    public static final String AURA = "aura";

    private final StackerConfig config;
    private final StackKeys keys;

    public StackerItems(StackerConfig config, StackKeys keys) {
        this.config = config;
        this.keys = keys;
    }

    public ItemStack create(String type) {
        return switch (type.toLowerCase()) {
            case WAND -> labeled(config.wandMaterial(), WAND, "Stack Wand",
                    "Right-click spawner: open UI",
                    "Shift-right-click: absorb nearby");
            case TOOL -> labeled(config.toolMaterial(), TOOL, "Stack Tool",
                    "Right-click mob: merge nearby",
                    "Left-click mob: show stack size");
            case AURA -> labeled(config.auraMaterial(), AURA, "Kill Aura",
                    "Hold to damage nearby stacked mobs",
                    "Requires yapstacker.aura");
            default -> throw new IllegalArgumentException("Unknown tool: " + type);
        };
    }

    private ItemStack labeled(Material mat, String type, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name).color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(loreLines[0]).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(loreLines.length > 1 ? loreLines[1] : "")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(keys.toolType, PersistentDataType.STRING, type);
        });
        return item;
    }

    public String toolType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(keys.toolType, PersistentDataType.STRING);
    }

    public boolean isWand(ItemStack stack) {
        return WAND.equals(toolType(stack));
    }

    public boolean isTool(ItemStack stack) {
        return TOOL.equals(toolType(stack));
    }

    public boolean isAura(ItemStack stack) {
        return AURA.equals(toolType(stack));
    }

    public void give(Player player, String type) {
        player.getInventory().addItem(create(type));
    }
}
