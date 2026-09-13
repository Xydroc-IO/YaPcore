package com.yapcore.world.schem;

import com.yapcore.world.WorldPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/** Schem preview hotbar tool item create/detect helpers. */
final class SchematicPreviewTools {

    static final String LORE_MARK = "Schem preview tool";

    private final WorldPlugin plugin;
    private final NamespacedKey actionKey;

    SchematicPreviewTools(WorldPlugin plugin, NamespacedKey actionKey) {
        this.plugin = plugin;
        this.actionKey = actionKey;
    }

    ItemStack tool(Material mat, String name, String action, String lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(LORE_MARK, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("yap_schem:" + action, NamedTextColor.BLACK)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        NamespacedKey key = actionKey != null
                ? actionKey
                : new NamespacedKey(plugin, SchematicPreviewControls.PDC_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action.toLowerCase(Locale.ROOT));
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        } catch (Throwable ignored) {
        }
        try {
            meta.setCustomModelData(switch (action) {
                case "confirm" -> 91001;
                case "move" -> 91002;
                case "rotate" -> 91003;
                case "flip" -> 91004;
                case "cancel" -> 91005;
                case "menu" -> 91006;
                default -> 91000;
            });
        } catch (Throwable ignored) {
        }
        stack.setItemMeta(meta);
        return stack;
    }

    boolean isTool(ItemStack stack) {
        return actionOf(stack) != null;
    }

    String actionOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey key = actionKey != null
                ? actionKey
                : new NamespacedKey(plugin, SchematicPreviewControls.PDC_KEY);
        String fromPdc = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (fromPdc != null && !fromPdc.isBlank()) {
            return fromPdc.toLowerCase(Locale.ROOT);
        }
        // Hidden lore tag
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                for (Component line : lore) {
                    if (line == null) {
                        continue;
                    }
                    String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    if (plain.startsWith("yap_schem:")) {
                        return plain.substring("yap_schem:".length()).toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
        try {
            if (meta.hasCustomModelData()) {
                return switch (meta.getCustomModelData()) {
                    case 91001 -> "confirm";
                    case 91002 -> "move";
                    case 91003 -> "rotate";
                    case 91004 -> "flip";
                    case 91005 -> "cancel";
                    case 91006 -> "menu";
                    default -> null;
                };
            }
        } catch (Throwable ignored) {
        }
        if (!hasLoreMark(meta)) {
            return null;
        }
        return switch (stack.getType()) {
            case LIME_DYE, LIME_CONCRETE -> "confirm";
            case COMPASS -> "move";
            case CLOCK, REPEATER -> "rotate";
            case FEATHER, PISTON -> "flip";
            case BARRIER, RED_CONCRETE -> "cancel";
            case NETHER_STAR, CHEST -> "menu";
            default -> null;
        };
    }

    private static boolean hasLoreMark(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        for (Component line : lore) {
            if (line == null) {
                continue;
            }
            if (LORE_MARK.equals(PlainTextComponentSerializer.plainText().serialize(line))) {
                return true;
            }
        }
        return false;
    }
}
