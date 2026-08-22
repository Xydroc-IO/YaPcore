package com.yapcore.crafting.economy;

import com.yapcore.crafting.gear.GearTierRegistry;
import com.yapcore.crafting.recipe.RecipePackLoader;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class SellPriceRegistry {

    private final JavaPlugin plugin;
    private final Path pricesFile;
    private Map<Material, Double> materialPrices = Map.of();
    private Map<String, Double> tierPrices = Map.of();

    public SellPriceRegistry(JavaPlugin plugin, Path pricesFile) {
        this.plugin = plugin;
        this.pricesFile = pricesFile;
    }

    public void reload() {
        if (!Files.exists(pricesFile)) {
            materialPrices = Map.of();
            tierPrices = Map.of();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pricesFile.toFile());
        Map<Material, Double> materials = new HashMap<>();
        var section = yaml.getConfigurationSection("prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = RecipePackLoader.parseMaterial(key, null);
                if (material != null) {
                    materials.put(material, section.getDouble(key));
                }
            }
        }
        Map<String, Double> tiers = new HashMap<>();
        var tierSection = yaml.getConfigurationSection("tier-prices");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                tiers.put(key.toLowerCase(Locale.ROOT), tierSection.getDouble(key));
            }
        }
        materialPrices = Map.copyOf(materials);
        tierPrices = Map.copyOf(tiers);
    }

    public void ensureDefaultFile() {
        if (Files.exists(pricesFile)) {
            return;
        }
        try {
            Files.createDirectories(pricesFile.getParent());
            Files.writeString(pricesFile, """
                    # Sell prices for /sell (per item). Tier prices override when yap_gear_tier is set.
                    prices:
                      IRON_INGOT: 5.0
                      COPPER_INGOT: 3.0
                      COOKED_SALMON: 12.0
                      COOKED_COD: 10.0
                    tier-prices:
                      bronze: 25.0
                      iron: 50.0
                      steel: 120.0
                      mithril: 300.0
                    """);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write default sell prices", e);
        }
    }

    public double priceFor(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        String tier = GearTierRegistry.readTier(stack);
        if (tier != null) {
            Double tierPrice = tierPrices.get(tier.toLowerCase(Locale.ROOT));
            if (tierPrice != null) {
                return tierPrice;
            }
        }
        return materialPrices.getOrDefault(stack.getType(), 0.0);
    }
}
