package com.yapcore.games.kit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KitLoader {

    private static final Logger LOG = Logger.getLogger("YaPGames");

    private final Path kitsFile;
    private Map<String, KitDefinition> kits = Map.of();

    public KitLoader(Path kitsFile) {
        this.kitsFile = kitsFile;
    }

    public void reload() {
        if (!Files.exists(kitsFile)) {
            kits = Map.of();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(kitsFile.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        Map<String, KitDefinition> loaded = new HashMap<>();
        if (root != null) {
            for (String kitId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(kitId);
                if (section == null) {
                    continue;
                }
                KitDefinition def = parseKit(kitId, section);
                if (def != null) {
                    loaded.put(kitId, def);
                }
            }
        }
        kits = Map.copyOf(loaded);
    }

    public KitDefinition get(String id) {
        return kits.get(id);
    }

    static KitDefinition parseKit(String id, ConfigurationSection section) {
        ItemStack[] armor = new ItemStack[4];
        ConfigurationSection armorSection = section.getConfigurationSection("armor");
        if (armorSection != null) {
            armor[0] = stack(armorSection.getString("boots"));
            armor[1] = stack(armorSection.getString("leggings"));
            armor[2] = stack(armorSection.getString("chestplate"));
            armor[3] = stack(armorSection.getString("helmet"));
        }
        List<KitDefinition.KitItem> items = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            Object matObj = raw.get("material");
            if (matObj == null) {
                continue;
            }
            var material = KitDefinition.parseMaterial(String.valueOf(matObj));
            if (material == null) {
                continue;
            }
            int amount = 1;
            Object amountObj = raw.get("amount");
            if (amountObj instanceof Number n) {
                amount = Math.max(1, n.intValue());
            }
            int slot = 0;
            Object slotObj = raw.get("slot");
            if (slotObj instanceof Number n) {
                slot = n.intValue();
            }
            items.add(new KitDefinition.KitItem(slot, new ItemStack(material, amount)));
        }
        return new KitDefinition(id, armor, List.copyOf(items));
    }

    private static ItemStack stack(String materialName) {
        var material = KitDefinition.parseMaterial(materialName);
        return material == null ? null : new ItemStack(material);
    }
}
