package com.yapcore.combat.service;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.formula.PrayerPoints;
import com.yapcore.combat.model.PlayerCombatState;
import com.yapcore.combat.prayer.PrayerBookLoader;
import com.yapcore.combat.prayer.PrayerDefinition;
import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PrayerService {

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatServiceImpl combat;
    private final PrayerBookLoader prayers;

    public PrayerService(
            JavaPlugin plugin,
            CombatConfig config,
            CombatServiceImpl combat,
            PrayerBookLoader prayers) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
        this.prayers = prayers;
    }

    public List<PrayerDefinition> available(Player player) {
        int level = combat.stats(player).prayer();
        List<PrayerDefinition> out = new ArrayList<>();
        for (PrayerDefinition def : prayers.prayers().values()) {
            if (def.minPrayerLevel() <= level) {
                out.add(def);
            }
        }
        out.sort(Comparator.comparing(PrayerDefinition::minPrayerLevel));
        return out;
    }

    public boolean togglePrayer(Player player, String prayerId, boolean on) {
        PrayerDefinition def = prayers.get(prayerId.toLowerCase(Locale.ROOT));
        if (def == null) {
            player.sendMessage("§cUnknown prayer.");
            return false;
        }
        if (combat.stats(player).prayer() < def.minPrayerLevel()) {
            player.sendMessage("§cRequires Prayer level §e" + def.minPrayerLevel() + "§c.");
            return false;
        }
        PlayerCombatState state = combat.state(player);
        if (on) {
            deactivateGroup(state, def.group(), def.id());
            state.togglePrayer(def.id(), true);
            player.sendMessage("§aPrayer activated: §f" + def.displayName());
        } else {
            state.togglePrayer(def.id(), false);
            player.sendMessage("§7Prayer deactivated: §f" + def.displayName());
        }
        combat.persistAsync(state);
        return true;
    }

    public void clearPrayers(Player player) {
        PlayerCombatState state = combat.state(player);
        state.setActivePrayers(Set.of());
        combat.persistAsync(state);
        player.sendMessage("§7All prayers deactivated.");
    }

    public void drainTick(Player player) {
        PlayerCombatState state = combat.state(player);
        if (state.activePrayers().isEmpty()) {
            return;
        }
        int totalDrain = 0;
        for (String id : state.activePrayers()) {
            PrayerDefinition def = prayers.get(id);
            if (def != null) {
                totalDrain += def.drainPerTick();
            }
        }
        if (totalDrain <= 0) {
            return;
        }
        int prayerBonus = combat.stats(player).gear().prayerBonus();
        if (prayerBonus > 0) {
            double multiplier = Math.max(0.25, 1.0 - prayerBonus * 0.01);
            totalDrain = Math.max(1, (int) Math.ceil(totalDrain * multiplier));
        }
        int maxPrayer = PrayerPoints.maxPoints(combat.stats(player).prayer());
        int next = PrayerPoints.clamp(state.currentPrayer() - totalDrain, maxPrayer);
        state.setCurrentPrayer(next);
        if (next <= 0) {
            state.setActivePrayers(Set.of());
            YapSched.entity(plugin, player, () -> player.sendMessage("§cYou have run out of prayer points."));
        }
        combat.persistAsync(state);
        awardPrayerXp(player, totalDrain);
    }

    private void deactivateGroup(PlayerCombatState state, String group, String exceptId) {
        if (group == null || group.isBlank()) {
            return;
        }
        Set<String> next = new HashSet<>(state.activePrayers());
        for (String activeId : state.activePrayers()) {
            if (activeId.equals(exceptId)) {
                continue;
            }
            PrayerDefinition other = prayers.get(activeId);
            if (other != null && group.equals(other.group())) {
                next.remove(activeId);
            }
        }
        state.setActivePrayers(next);
    }

    private void awardPrayerXp(Player player, int drained) {
        if (drained <= 0) {
            return;
        }
        SkillServices.find().ifPresent(skills -> {
            SkillDefinition def = skills.definition(SkillId.of("prayer")).orElse(null);
            double rate = def != null && def.prayerDrain() != null ? def.prayerDrain().xpPerPoint() : 0.5;
            skills.addXp(player.getUniqueId(), SkillId.of("prayer"), drained * rate, XpSource.ACTION);
        });
    }
}
