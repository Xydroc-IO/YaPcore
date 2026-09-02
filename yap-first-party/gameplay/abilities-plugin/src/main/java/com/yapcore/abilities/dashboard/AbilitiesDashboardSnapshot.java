package com.yapcore.abilities.dashboard;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.bar.AbilityBarConfig;
import com.yapcore.abilities.bar.AbilityBarStore;
import com.yapcore.abilities.book.AbilityBookConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Live snapshot for web dashboard ({@code /yapabilities snapshot json}). */
public final class AbilitiesDashboardSnapshot {

    private AbilitiesDashboardSnapshot() {
    }

    public static Map<String, Object> snapshot(
            AbilityService abilities,
            AbilityBarConfig barConfig,
            AbilityBarStore barStore,
            AbilityBookConfig bookConfig,
            Path barsFile
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plugin", "yap-abilities");
        out.put("abilitiesInstalled", true);
        out.put("abilityCount", abilities.definitions().size());
        out.put("dualHotbar", barConfig.dualHotbar());
        out.put("abilityBarEnabled", barConfig.enabled());
        out.put("abilityBookEnabled", bookConfig.enabled());
        out.put("shiftFBook", bookConfig.openTrigger(AbilityBookConfig.OpenTrigger.SNEAK_SWAP));
        out.put("hotbarKeys", barConfig.firstKey() + "-" + barConfig.lastKey());
        out.put("barSlotCount", barConfig.slotCount());
        out.put("barBindingPlayers", countPersistedPlayers(barsFile));
        out.put("onlineWithBindings", onlineWithBindings(barStore));
        out.put("barBindingPreview", barBindingPreview(barStore, barConfig, abilities, 8));
        out.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        return out;
    }

    public static int countPersistedPlayers(Path barsFile) {
        if (barsFile == null || !java.nio.file.Files.isRegularFile(barsFile)) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(barsFile.toFile());
        var section = yaml.getConfigurationSection("players");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private static int onlineWithBindings(AbilityBarStore store) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasBindings(store, player.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasBindings(AbilityBarStore store, UUID playerId) {
        for (int i = 0; i < store.slotCount(); i++) {
            String id = store.get(playerId, i);
            if (id != null && !id.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> barBindingPreview(
            AbilityBarStore store,
            AbilityBarConfig barConfig,
            AbilityService abilities,
            int limit
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (rows.size() >= limit) {
                break;
            }
            UUID id = player.getUniqueId();
            if (!hasBindings(store, id)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("player", player.getName());
            row.put("playerId", id.toString());
            row.put("bindings", formatBindings(store, barConfig, abilities, id));
            row.put("boundCount", boundCount(store, id));
            rows.add(row);
        }
        return rows;
    }

    private static int boundCount(AbilityBarStore store, UUID playerId) {
        int n = 0;
        for (int i = 0; i < store.slotCount(); i++) {
            String abilityId = store.get(playerId, i);
            if (abilityId != null && !abilityId.isBlank()) {
                n++;
            }
        }
        return n;
    }

    private static String formatBindings(
            AbilityBarStore store,
            AbilityBarConfig barConfig,
            AbilityService abilities,
            UUID playerId
    ) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < store.slotCount(); i++) {
            String abilityId = store.get(playerId, i);
            if (abilityId == null || abilityId.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            int key = barConfig.firstKey() + i;
            String name = abilities.get(abilityId).map(AbilityDefinition::displayName).orElse(abilityId);
            sb.append(key).append(':').append(name);
        }
        return sb.toString();
    }
}
