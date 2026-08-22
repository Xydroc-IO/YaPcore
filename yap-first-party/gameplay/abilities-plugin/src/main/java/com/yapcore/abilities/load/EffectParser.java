package com.yapcore.abilities.load;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.EffectKind;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EffectParser {

    private EffectParser() {
    }

    public static List<AbilityEffect> parseList(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<AbilityEffect> out = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof ConfigurationSection section) {
                out.add(parseSection(section));
            } else if (item instanceof Map<?, ?> map) {
                out.add(parseMap(map));
            }
        }
        return List.copyOf(out);
    }

    public static List<AbilityEffect> parseSectionList(ConfigurationSection parent, String key) {
        List<Map<?, ?>> maps = parent.getMapList(key);
        if (maps.isEmpty()) {
            ConfigurationSection section = parent.getConfigurationSection(key);
            if (section == null) {
                return List.of();
            }
            List<AbilityEffect> out = new ArrayList<>();
            for (String child : section.getKeys(false)) {
                ConfigurationSection step = section.getConfigurationSection(child);
                if (step != null) {
                    out.add(parseSection(step));
                }
            }
            return List.copyOf(out);
        }
        return parseList(new ArrayList<>(maps));
    }

    private static AbilityEffect parseSection(ConfigurationSection section) {
        EffectKind kind = EffectKind.parse(section.getString("type"));
        Map<String, String> params = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if ("type".equalsIgnoreCase(key)) {
                continue;
            }
            Object value = section.get(key);
            if (value != null) {
                params.put(key, String.valueOf(value));
            }
        }
        return new AbilityEffect(kind, params);
    }

    private static AbilityEffect parseMap(Map<?, ?> map) {
        Object typeObj = map.get("type");
        EffectKind kind = EffectKind.parse(typeObj == null ? null : String.valueOf(typeObj));
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if ("type".equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                continue;
            }
            if (entry.getValue() != null) {
                params.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return new AbilityEffect(kind, params);
    }
}
