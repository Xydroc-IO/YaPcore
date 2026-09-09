package com.yapcore.items.item;

import com.yapcore.items.ability.AbilityDefinition;
import com.yapcore.mmo.GearBonus;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable parsed custom-item definition. */
public record ItemDefinition(
        String id,
        Material base,
        String name,
        List<String> lore,
        int customModelData,
        boolean unbreakable,
        boolean glow,
        boolean rainbow,
        List<ItemFlag> hideFlags,
        Map<Enchantment, Integer> enchants,
        Map<String, Double> attributes,
        String permission,
        GearBonus gear,
        List<AbilityDefinition> abilities,
        boolean consume,
        FurnitureDef furniture,
        RecipeDef recipe,
        int revision) {

    public ItemDefinition {
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
    }

    /** First ability, if any (legacy helper). */
    public AbilityDefinition ability() {
        return abilities.isEmpty() ? null : abilities.getFirst();
    }

    public Optional<AbilityDefinition> abilityOpt() {
        return Optional.ofNullable(ability());
    }

    public boolean hasAbilityTrigger(AbilityDefinition.Trigger trigger) {
        AbilityDefinition.Trigger primary = null;
        for (AbilityDefinition a : abilities) {
            if (a.trigger() != AbilityDefinition.Trigger.TOGETHER) {
                primary = a.trigger();
                break;
            }
        }
        if (primary == null) {
            primary = AbilityDefinition.Trigger.RIGHT_CLICK;
        }
        for (AbilityDefinition a : abilities) {
            if (a.trigger() == trigger) {
                return true;
            }
            if (a.trigger() == AbilityDefinition.Trigger.TOGETHER && trigger == primary) {
                return true;
            }
        }
        return false;
    }

    public boolean isFurniture() {
        return furniture != null && furniture.enabled();
    }

    public record FurnitureDef(
            boolean enabled,
            float scale,
            boolean solid,
            String placeSound,
            boolean breakDrops) {
    }

    public record RecipeDef(
            String type,
            List<String> shape,
            Map<Character, Material> ingredients,
            List<Material> shapeless) {
    }
}
