package com.yapcore.items.item;

import com.yapcore.items.ItemsKeys;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds and recognizes tagged custom item stacks. */
public final class ItemFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final ItemsKeys keys;
    private final ItemRegistry registry;

    public ItemFactory(JavaPlugin plugin, ItemsKeys keys, ItemRegistry registry) {
        this.plugin = plugin;
        this.keys = keys;
        this.registry = registry;
    }

    public Optional<ItemStack> create(String id) {
        return create(id, 1);
    }

    public Optional<ItemStack> create(String id, int amount) {
        return registry.get(id).map(def -> build(def, Math.max(1, amount)));
    }

    public ItemStack build(ItemDefinition def, int amount) {
        ItemStack stack = new ItemStack(def.base(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (def.name() != null && !def.name().isBlank()) {
            meta.displayName(LEGACY.deserialize(def.name()));
        }
        if (def.lore() != null && !def.lore().isEmpty()) {
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : def.lore()) {
                lines.add(LEGACY.deserialize(line));
            }
            meta.lore(lines);
        }
        if (def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }
        meta.setUnbreakable(def.unbreakable());
        if (def.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        for (ItemFlag flag : def.hideFlags()) {
            meta.addItemFlags(flag);
        }
        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, def.id());
        meta.getPersistentDataContainer().set(keys.itemRev(), PersistentDataType.INTEGER, def.revision());
        applyAttributes(meta, def);
        stack.setItemMeta(meta);
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> e : def.enchants().entrySet()) {
            stack.addUnsafeEnchantment(e.getKey(), e.getValue());
        }
        return stack;
    }

    private void applyAttributes(ItemMeta meta, ItemDefinition def) {
        if (def.attributes().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> e : def.attributes().entrySet()) {
            Attribute attr = attributeOf(e.getKey());
            if (attr == null) {
                continue;
            }
            AttributeModifier mod = new AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "yap_" + def.id() + "_" + e.getKey()),
                    e.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND);
            meta.addAttributeModifier(attr, mod);
        }
    }

    private static Attribute attributeOf(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (key) {
            case "attack_damage", "generic.attack_damage" -> Attribute.ATTACK_DAMAGE;
            case "attack_speed", "generic.attack_speed" -> Attribute.ATTACK_SPEED;
            case "armor", "generic.armor" -> Attribute.ARMOR;
            case "armor_toughness", "generic.armor_toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "max_health", "generic.max_health" -> Attribute.MAX_HEALTH;
            case "movement_speed", "generic.movement_speed" -> Attribute.MOVEMENT_SPEED;
            case "knockback_resistance", "generic.knockback_resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            default -> {
                try {
                    yield Attribute.valueOf(key.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }

    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(keys.itemId(), PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }

    public Optional<ItemDefinition> definitionOf(ItemStack stack) {
        return idOf(stack).flatMap(registry::get);
    }

    public boolean isCustom(ItemStack stack) {
        return idOf(stack).isPresent();
    }

    public int takeMatching(org.bukkit.inventory.PlayerInventory inv, String id, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            Optional<String> sid = idOf(stack);
            if (sid.isEmpty() || !sid.get().equalsIgnoreCase(id)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inv.setItem(i, null);
            }
            remaining -= take;
        }
        return amount - remaining;
    }
}
