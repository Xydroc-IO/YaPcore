package com.yapcore.mechanics.tool;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ToolRuleLoader {

    private static final Logger LOG = Logger.getLogger("YaPMechanics");

    public enum ToolType {
        PICKAXE, AXE, SHOVEL, HOE, SHEARS, SWORD
    }

    public record ToolRule(Material block, ToolType tool, int minTier) {
    }

    private Map<Material, ToolRule> rules = Map.of();

    public void load(Path file) {
        Map<Material, ToolRule> loaded = new EnumMap<>(Material.class);
        if (!Files.isRegularFile(file)) {
            rules = Map.copyOf(loaded);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            var section = yaml.getConfigurationSection("rules");
            if (section == null) {
                rules = Map.copyOf(loaded);
                return;
            }
            for (String key : section.getKeys(false)) {
                Material block = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
                if (block == null) {
                    continue;
                }
                var row = section.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                ToolType tool = ToolType.valueOf(row.getString("tool", "PICKAXE").toUpperCase(Locale.ROOT));
                int tier = row.getInt("min-tier", 1);
                loaded.put(block, new ToolRule(block, tool, tier));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load tools.yml", e);
        }
        rules = Map.copyOf(loaded);
    }

    public ToolRule ruleFor(Material block) {
        return rules.get(block);
    }

    public int ruleCount() {
        return rules.size();
    }

    public static int tierOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        return tierOfName(stack.getType().name());
    }

    static int tierOfName(String name) {
        return switch (name) {
            case String n when n.startsWith("WOODEN_") -> 1;
            case String n when n.startsWith("STONE_") -> 2;
            case String n when n.startsWith("IRON_") -> 3;
            case String n when n.startsWith("DIAMOND_") -> 4;
            case String n when n.startsWith("NETHERITE_") -> 5;
            case String n when n.startsWith("GOLDEN_") -> 2;
            case String n when n.startsWith("COPPER_") -> 1;
            default -> 0;
        };
    }

    public static boolean matchesTool(ItemStack stack, ToolType required) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return matchesToolName(stack.getType().name(), required);
    }

    static boolean matchesToolName(String name, ToolType required) {
        return switch (required) {
            case PICKAXE -> name.endsWith("_PICKAXE");
            case AXE -> name.endsWith("_AXE");
            case SHOVEL -> name.endsWith("_SHOVEL");
            case HOE -> name.endsWith("_HOE");
            case SHEARS -> "SHEARS".equals(name);
            case SWORD -> name.endsWith("_SWORD");
        };
    }
}
