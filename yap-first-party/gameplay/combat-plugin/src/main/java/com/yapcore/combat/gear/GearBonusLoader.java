package com.yapcore.combat.gear;

import com.yapcore.mmo.GearBonus;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GearBonusLoader {

    public record ItemDef(GearBonus bonus, String tier) {
    }

    private final JavaPlugin plugin;
    private Map<Material, ItemDef> byMaterial = Map.of();
    private Map<String, GearBonus> tiers = Map.of();
    private org.bukkit.NamespacedKey tierKey;

    public GearBonusLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new org.bukkit.NamespacedKey(plugin, "yap_gear_tier");
    }

    public void reload(Path itemsFile) throws IOException {
        if (!Files.exists(itemsFile)) {
            plugin.saveResource("items.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(itemsFile.toFile());
        Map<String, GearBonus> tierMap = new HashMap<>();
        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String tier : tiersSection.getKeys(false)) {
                ConfigurationSection ts = tiersSection.getConfigurationSection(tier);
                if (ts != null) {
                    tierMap.put(tier.toLowerCase(Locale.ROOT), readBonus(ts));
                }
            }
        }
        tiers = Map.copyOf(tierMap);

        Map<Material, ItemDef> materialMap = new EnumMap<>(Material.class);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Unknown gear material: " + key);
                    continue;
                }
                ConfigurationSection is = items.getConfigurationSection(key);
                if (is == null) {
                    continue;
                }
                GearBonus bonus = readBonus(is);
                String tier = is.getString("tier");
                if (tier != null) {
                    GearBonus tierBonus = tiers.get(tier.toLowerCase(Locale.ROOT));
                    if (tierBonus != null) {
                        bonus = bonus.add(tierBonus);
                    }
                }
                materialMap.put(mat, new ItemDef(bonus, tier));
            }
        }
        byMaterial = Collections.unmodifiableMap(materialMap);
    }

    public org.bukkit.NamespacedKey tierKey() {
        return tierKey;
    }

    public Optional<GearBonus> bonusFor(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        GearBonus bonus = GearBonus.ZERO;
        ItemDef def = byMaterial.get(stack.getType());
        if (def != null) {
            bonus = bonus.add(def.bonus());
        }
        String tier = readTier(stack);
        if (tier != null) {
            GearBonus tierBonus = tiers.get(tier.toLowerCase(Locale.ROOT));
            if (tierBonus != null) {
                bonus = bonus.add(tierBonus);
            }
        }
        if (bonus.equals(GearBonus.ZERO)) {
            return Optional.empty();
        }
        return Optional.of(bonus);
    }

    public GearBonus aggregateEquipped(org.bukkit.entity.Player player) {
        GearBonus total = GearBonus.ZERO;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            total = total.add(bonusFor(piece).orElse(GearBonus.ZERO));
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main == null || main.getType().isAir()) {
            ItemDef fists = byMaterial.get(Material.AIR);
            if (fists != null) {
                total = total.add(fists.bonus());
            }
        } else {
            total = total.add(bonusFor(main).orElse(GearBonus.ZERO));
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        total = total.add(bonusFor(off).orElse(GearBonus.ZERO));
        return total;
    }

    private String readTier(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(tierKey, PersistentDataType.STRING);
    }

    private static GearBonus readBonus(ConfigurationSection section) {
        return new GearBonus(
                section.getInt("attack-bonus", 0),
                section.getInt("strength-bonus", 0),
                section.getInt("defence-bonus", 0),
                section.getInt("prayer-bonus", 0),
                section.getInt("ranged-bonus", 0),
                section.getInt("magic-bonus", 0));
    }
}
