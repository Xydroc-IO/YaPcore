package com.yapcore.items.item;

import com.yapcore.items.ItemsKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Builds and recognizes tagged custom item stacks. */
public final class ItemFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static volatile Map<String, Enchantment> enchantByKey;

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
            String display = def.rainbow()
                    ? RainbowNameService.colorize(def.name(), 0)
                    : def.name();
            meta.displayName(LEGACY.deserialize(display));
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
        boolean hideEnchants = false;
        for (ItemFlag flag : def.hideFlags()) {
            meta.addItemFlags(flag);
            if (flag == ItemFlag.HIDE_ENCHANTS) {
                hideEnchants = true;
            }
        }
        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, def.id());
        meta.getPersistentDataContainer().set(keys.itemRev(), PersistentDataType.INTEGER, def.revision());
        if (def.rainbow()) {
            meta.getPersistentDataContainer().set(keys.rainbow(), PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(keys.rainbow());
        }
        applyAttributes(meta, def);

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> e : def.enchants().entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                enchants.put(e.getKey(), e.getValue());
            }
        }
        // Glow with no enchants: seed a hidden enchant so the shine always renders on 26.2 clients.
        if (def.glow() && enchants.isEmpty()) {
            enchants.put(Enchantment.LUCK_OF_THE_SEA, 1);
            if (!hideEnchants) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        if (def.glow()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        stack.setItemMeta(meta);

        // Authoritative 26.2 data-component write (survives meta/handle sync quirks).
        if (!enchants.isEmpty()) {
            ItemEnchantments.Builder builder = ItemEnchantments.itemEnchantments();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                builder.add(e.getKey(), e.getValue());
            }
            stack.setData(DataComponentTypes.ENCHANTMENTS, builder);
        }
        if (def.glow()) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        } else {
            stack.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
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
                    new NamespacedKey(plugin, "yap_" + def.id() + "_" + e.getKey()),
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

    /** Resolve enchantment id from YAML / CLI ({@code sharpness}, {@code minecraft:sharpness}). */
    public static Enchantment resolveEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        Enchantment indexed = enchantIndex().get(key);
        if (indexed != null) {
            return indexed;
        }
        try {
            Enchantment byAccess = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(key));
            if (byAccess != null) {
                return byAccess;
            }
        } catch (Exception ignored) {
        }
        try {
            Enchantment byReg = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            if (byReg != null) {
                return byReg;
            }
        } catch (Exception ignored) {
        }
        return Enchantment.getByName(key.toUpperCase(Locale.ROOT));
    }

    private static Map<String, Enchantment> enchantIndex() {
        Map<String, Enchantment> cached = enchantByKey;
        if (cached != null) {
            return cached;
        }
        synchronized (ItemFactory.class) {
            if (enchantByKey == null) {
                enchantByKey = buildEnchantIndex();
            }
            return enchantByKey;
        }
    }

    private static Map<String, Enchantment> buildEnchantIndex() {
        Map<String, Enchantment> map = new LinkedHashMap<>();
        for (Field field : Enchantment.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Enchantment.class) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof Enchantment ench) {
                    map.put(ench.getKey().getKey(), ench);
                    map.putIfAbsent(field.getName().toLowerCase(Locale.ROOT), ench);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Map.copyOf(map);
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
