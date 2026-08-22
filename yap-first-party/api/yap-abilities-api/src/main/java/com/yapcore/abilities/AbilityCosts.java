package com.yapcore.abilities;

import org.bukkit.Material;

import java.util.Map;

public record AbilityCosts(
        int prayer,
        Map<Material, Integer> runes,
        Material requiredStaff) {

    public AbilityCosts {
        prayer = Math.max(0, prayer);
        runes = runes == null ? Map.of() : Map.copyOf(runes);
    }

    public boolean requiresStaff() {
        return requiredStaff != null;
    }
}
