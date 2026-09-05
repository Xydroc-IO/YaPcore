package com.yapcore.dungeons.portal;

import com.yapcore.dungeons.DungeonsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class PortalItems {

    public static final String PORTAL_ITEM_KEY = "yap_dungeon_portal_item";
    public static final String PORTAL_BLOCK_KEY = "yap_dungeon_portal_block";

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final NamespacedKey itemKey;
    private final NamespacedKey blockKey;
    private final NamespacedKey recipeKey;

    public PortalItems(JavaPlugin plugin, DungeonsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.itemKey = new NamespacedKey(plugin, PORTAL_ITEM_KEY);
        this.blockKey = new NamespacedKey(plugin, PORTAL_BLOCK_KEY);
        this.recipeKey = new NamespacedKey(plugin, "dungeon_portal");
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }

    public NamespacedKey blockKey() {
        return blockKey;
    }

    public ItemStack createPortalItem() {
        ItemStack stack = new ItemStack(config.portalItemMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Dungeon Portal", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text("Place and interact to open dungeons", NamedTextColor.GRAY),
                    Component.text("Requires overall level 10+", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isPortalItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ItemStack result = createPortalItem();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("ABC", "DEF", "GHI");
        List<String> mats = config.portalRecipe();
        char[] keys = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'};
        for (int i = 0; i < 9; i++) {
            Material mat = Material.AIR;
            try {
                mat = Material.valueOf(mats.get(i).trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
            if (mat != Material.AIR) {
                recipe.setIngredient(keys[i], mat);
            }
        }
        Bukkit.addRecipe(recipe);
    }
}
