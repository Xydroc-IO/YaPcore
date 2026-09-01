package com.yapcore.combat.service;

import com.yapcore.abilities.AbilityServices;
import com.yapcore.abilities.CastResult;
import com.yapcore.combat.spell.SpellBookLoader;
import com.yapcore.combat.spell.SpellDefinition;
import com.yapcore.combat.status.StatusEffectService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpellBookService {

    private final CombatServiceImpl combat;
    private final SpellBookLoader spells;

    public SpellBookService(
            JavaPlugin plugin,
            CombatServiceImpl combat,
            CombatXpAwarder xp,
            SpellBookLoader spells,
            StatusEffectService status,
            CombatEntityDamager entityDamager) {
        this.combat = combat;
        this.spells = spells;
    }

    public List<SpellDefinition> knownSpells(Player player) {
        int magic = combat.stats(player).magic();
        List<SpellDefinition> out = new ArrayList<>();
        for (SpellDefinition spell : spells.spells().values()) {
            if (spell.minMagicLevel() <= magic) {
                out.add(spell);
            }
        }
        out.sort(Comparator.comparingInt(SpellDefinition::minMagicLevel));
        return out;
    }

    public boolean cast(Player player, String spellId) {
        var abilities = AbilityServices.find();
        if (abilities.isEmpty()) {
            player.sendMessage("§cMagic spells require §eyap-abilities§c (gameplay tier).");
            return false;
        }
        CastResult result = abilities.get().cast(player, spellId);
        if (result == CastResult.UNKNOWN_ABILITY) {
            player.sendMessage("§cUnknown spell.");
            return false;
        }
        return result.ok();
    }
}
