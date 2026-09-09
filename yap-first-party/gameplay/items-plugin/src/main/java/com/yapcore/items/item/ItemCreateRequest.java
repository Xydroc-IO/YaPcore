package com.yapcore.items.item;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wizard / command payload for writing a custom item YAML under {@code items/custom/}. */
public record ItemCreateRequest(
        String id,
        Material base,
        String name,
        List<String> lore,
        int cmd,
        boolean glow,
        boolean rainbow,
        boolean unbreakable,
        List<AbilityWrite> abilities,
        boolean furniture,
        int gearAttack,
        int gearStrength,
        Map<String, Integer> enchants) {

    public ItemCreateRequest {
        enchants = enchants == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(enchants));
    }

    /** One ability entry written under {@code abilities:}. */
    public record AbilityWrite(
            String type,
            String trigger,
            String cooldown,
            Map<String, Object> params) {
    }

    /** Convenience for single-ability creates. */
    public static ItemCreateRequest singleAbility(
            String id,
            Material base,
            String name,
            List<String> lore,
            int cmd,
            boolean glow,
            boolean rainbow,
            boolean unbreakable,
            String abilityType,
            String abilityCooldown,
            Map<String, Object> abilityParams,
            boolean furniture,
            int gearAttack,
            int gearStrength) {
        List<AbilityWrite> abilities = List.of();
        if (abilityType != null && !abilityType.isBlank()) {
            abilities = List.of(new AbilityWrite(
                    abilityType,
                    "RIGHT_CLICK",
                    abilityCooldown == null ? "5s" : abilityCooldown,
                    abilityParams == null ? Map.of() : abilityParams));
        }
        return new ItemCreateRequest(
                id, base, name, lore, cmd, glow, rainbow, unbreakable, abilities, furniture, gearAttack, gearStrength,
                Map.of());
    }
}
