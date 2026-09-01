package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.bar.AbilityBarConfig;
import com.yapcore.abilities.bar.AbilityBarStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class AbilityBookItems {

    private AbilityBookItems() {
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.displayName(Component.text(" ")
                .decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    public static ItemStack categoryTab(
            AbilityBookKeys keys,
            AbilityCategory category,
            boolean selected,
            String label
    ) {
        Material mat = switch (category) {
            case MAGIC -> Material.LAPIS_LAZULI;
            case RANGED -> Material.BOW;
            case MELEE -> Material.IRON_SWORD;
            case PRAYER -> Material.GLOWSTONE_DUST;
            case UTILITY -> Material.COMPASS;
        };
        ItemStack stack = new ItemStack(selected ? Material.ENCHANTED_BOOK : mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(label, selected ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(keys.navButton, PersistentDataType.STRING, "cat:" + category.name());
            if (selected) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return stack;
    }

    public static ItemStack allCategoryTab(AbilityBookKeys keys, boolean selected) {
        ItemStack stack = new ItemStack(selected ? Material.ENCHANTED_BOOK : Material.BOOK);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("All abilities", selected ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(keys.navButton, PersistentDataType.STRING, "cat:ALL");
            if (selected) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return stack;
    }

    public static ItemStack helpItem() {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("How to bind", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Drag an ability onto keys 4–9 below")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift-click ability → first empty slot")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click a bar slot to clear")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Switch to combat bar (/ability mode) to cast")
                            .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        });
        return stack;
    }

    public static ItemStack closeButton(AbilityBookKeys keys) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(keys.navButton, PersistentDataType.STRING, "close");
        });
        return stack;
    }

    public static ItemStack pageButton(AbilityBookKeys keys, String id, String label, boolean enabled) {
        Material mat = "prev".equals(id) ? Material.ARROW : Material.SPECTRAL_ARROW;
        ItemStack stack = new ItemStack(enabled ? mat : Material.GRAY_DYE);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(label, enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (enabled) {
                meta.getPersistentDataContainer().set(keys.navButton, PersistentDataType.STRING, "page:" + id);
            }
        });
        return stack;
    }

    public static ItemStack clearAllButton(AbilityBookKeys keys) {
        ItemStack stack = new ItemStack(Material.TNT);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Clear all bindings", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(keys.navButton, PersistentDataType.STRING, "clear");
        });
        return stack;
    }

    public static ItemStack hotbarLabel(AbilityBarConfig barConfig) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Combat hotbar", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Keys " + barConfig.firstKey() + "–" + barConfig.lastKey())
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Bindings apply in combat mode")
                            .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        });
        return stack;
    }

    public static ItemStack abilityIcon(
            AbilityBookKeys keys,
            AbilityDefinition ability,
            boolean unlocked,
            boolean selected,
            Collection<com.yapcore.mmo.SkillProgress> skills,
            AbilityService abilities,
            Player player
    ) {
        ItemStack stack = new ItemStack(unlocked ? Material.CLAY_BALL : Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> {
            if (unlocked) {
                int cmd = ability.resolvedIconCmd();
                if (cmd > 0) {
                    meta.setCustomModelData(cmd);
                }
                meta.getPersistentDataContainer().set(keys.bookAbility, PersistentDataType.STRING, ability.id());
            }
            String prefix = unlocked ? "§d" : "§8";
            if (selected) {
                prefix = "§e§l";
            }
            meta.displayName(Component.text(prefix + ability.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7" + ability.category().name().toLowerCase(Locale.ROOT)
                    + " §8· §e" + ability.id()).decoration(TextDecoration.ITALIC, false));
            if (!unlocked) {
                lore.add(Component.text("§cLocked").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(AbilityUnlocks.requirementsText(ability, skills))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(AbilityUnlocks.requirementsText(ability, skills))
                        .decoration(TextDecoration.ITALIC, false));
                if (ability.cooldownTicks() > 0) {
                    lore.add(Component.text("§7Cooldown: §f" + (ability.cooldownTicks() / 20.0) + "s")
                            .decoration(TextDecoration.ITALIC, false));
                }
                if (abilities.isOnCooldown(player, ability.id())) {
                    long ticks = abilities.cooldownRemainingTicks(player, ability.id());
                    lore.add(Component.text("§cOn cooldown §f" + formatCooldown(ticks))
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("§aReady to bind").decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.text("§8Drag onto bar slot · Shift-click quick bind")
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (selected) {
                lore.add(Component.text("§e▶ Selected — click a bar slot")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            if (selected && unlocked) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return stack;
    }

    public static ItemStack barSlotIcon(
            AbilityBookKeys keys,
            AbilityBarConfig barConfig,
            AbilityBarStore store,
            AbilityService abilities,
            Player player,
            int barIndex
    ) {
        int hotbarKey = barConfig.firstKey() + barIndex;
        String boundId = store.get(player.getUniqueId(), barIndex);
        Material type = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        AbilityDefinition bound = null;
        if (boundId != null && !boundId.isBlank()) {
            bound = abilities.get(boundId).orElse(null);
            if (bound != null && bound.resolvedIconCmd() > 0) {
                type = Material.CLAY_BALL;
            }
        }
        ItemStack stack = new ItemStack(type);
        AbilityDefinition finalBound = bound;
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(keys.bookBarSlot, PersistentDataType.INTEGER, barIndex);
            if (finalBound == null) {
                meta.displayName(Component.text("§7Key §f" + hotbarKey + " §8— empty")
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("§8Drop ability here").decoration(TextDecoration.ITALIC, false),
                        Component.text("§8Right-click to clear").decoration(TextDecoration.ITALIC, false)
                ));
                return;
            }
            meta.displayName(Component.text("§fKey " + hotbarKey + " §7· §d" + finalBound.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            int cmd = finalBound.resolvedIconCmd();
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
            meta.getPersistentDataContainer().set(keys.bookAbility, PersistentDataType.STRING, finalBound.id());
            meta.lore(List.of(
                    Component.text("§e" + finalBound.id()).decoration(TextDecoration.ITALIC, false),
                    Component.text("§8Right-click to unbind").decoration(TextDecoration.ITALIC, false)
            ));
        });
        return stack;
    }

    public static ItemStack createTome(JavaPlugin plugin, AbilityBookConfig config, AbilityBookKeys keys) {
        ItemStack stack = new ItemStack(config.tomeMaterial());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(config.tomeDisplayName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : config.tomeLore()) {
                lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            if (config.tomeCustomModelData() > 0) {
                meta.setCustomModelData(config.tomeCustomModelData());
            }
            meta.getPersistentDataContainer().set(keys.tome, PersistentDataType.BYTE, (byte) 1);
        });
        return stack;
    }

    public static boolean isTome(AbilityBookKeys keys, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(keys.tome, PersistentDataType.BYTE);
    }

    public static String abilityId(AbilityBookKeys keys, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.bookAbility, PersistentDataType.STRING);
    }

    public static Integer barSlotIndex(AbilityBookKeys keys, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.bookBarSlot, PersistentDataType.INTEGER);
    }

    public static String navAction(AbilityBookKeys keys, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.navButton, PersistentDataType.STRING);
    }

    private static String formatCooldown(long ticks) {
        if (ticks <= 0) {
            return "0s";
        }
        double sec = ticks / 20.0;
        return sec >= 10 ? String.format(Locale.ROOT, "%.0fs", sec) : String.format(Locale.ROOT, "%.1fs", sec);
    }
}
