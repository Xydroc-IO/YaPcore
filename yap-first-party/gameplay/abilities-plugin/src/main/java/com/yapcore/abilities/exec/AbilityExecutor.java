package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityCombatServices;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.CastResult;
import com.yapcore.abilities.TargetMode;
import com.yapcore.mmo.CombatServices;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityExecutor {

    private final JavaPlugin plugin;
    private final EffectRunner effects;
    private final ProjectileTracker projectiles;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public AbilityExecutor(JavaPlugin plugin, EffectRunner effects, ProjectileTracker projectiles) {
        this.plugin = plugin;
        this.effects = effects;
        this.projectiles = projectiles;
    }

    public CastResult cast(Player caster, AbilityDefinition ability, LivingEntity explicitTarget) {
        if (isOnCooldown(caster, ability.id())) {
            return CastResult.ON_COOLDOWN;
        }
        CastResult levelCheck = checkLevels(caster, ability);
        if (levelCheck != CastResult.SUCCESS) {
            return levelCheck;
        }
        CastResult conditionCheck = checkConditions(caster, ability);
        if (conditionCheck != CastResult.SUCCESS) {
            return conditionCheck;
        }
        if (!hasCosts(caster, ability)) {
            return CastResult.MISSING_COST;
        }

        LivingEntity target = explicitTarget;
        if (target == null && ability.targetMode() == TargetMode.RAYCAST) {
            target = TargetResolver.resolve(caster, ability);
        }
        if (ability.targetMode() == TargetMode.RAYCAST && target == null) {
            return CastResult.NO_TARGET;
        }
        if (target != null && !TargetResolver.matchesFilter(target, ability)) {
            return CastResult.INVALID_TARGET;
        }
        if (target instanceof Player victim) {
            if (!AbilityCombatServices.find()
                    .map(b -> b.isPvpAllowed(caster, victim))
                    .orElse(true)) {
                return CastResult.PVP_DENIED;
            }
        }

        consumeCosts(caster, ability);
        effects.runCast(caster, ability);

        if (ability.hasProjectile()) {
            projectiles.launch(caster, ability, target, (player, hit) ->
                    effects.runHit(player, hit, ability.hitEffects(), ability));
        } else if (ability.targetMode() == TargetMode.AREA || ability.targetMode() == TargetMode.GROUND) {
            Location center = AoeHelper.areaCenter(caster, ability, target);
            List<LivingEntity> victims = AoeHelper.targetsAt(caster, center, ability, ability.range());
            for (LivingEntity victim : victims) {
                effects.runHit(caster, victim, ability.hitEffects(), ability);
            }
            if (victims.isEmpty() && ability.targetMode() == TargetMode.GROUND) {
                effects.runHit(caster, caster, ability.hitEffects(), ability);
            }
        } else if (ability.targetMode() == TargetMode.SELF) {
            effects.runHit(caster, caster, ability.hitEffects(), ability);
        } else if (target != null) {
            effects.runHit(caster, target, ability.hitEffects(), ability);
        }

        if (ability.cooldownTicks() > 0) {
            long expires = System.currentTimeMillis() + ability.cooldownTicks() * 50L;
            cooldowns.computeIfAbsent(caster.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(ability.id(), expires);
        }
        caster.sendActionBar(net.kyori.adventure.text.Component.text(
                "§d" + ability.displayName() + " §7cast"));
        return CastResult.SUCCESS;
    }

    private CastResult checkConditions(Player caster, AbilityDefinition ability) {
        return CastConditionEvaluator.firstFailure(caster, ability)
                .map(failed -> {
                    caster.sendMessage("§c" + CastConditionEvaluator.failureMessage(failed));
                    return CastResult.CONDITION_FAILED;
                })
                .orElse(CastResult.SUCCESS);
    }

    public long cooldownRemainingTicks(Player player, String abilityId) {
        ConcurrentHashMap<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long expires = map.get(abilityId);
        if (expires == null) {
            return 0;
        }
        long remainMs = expires - System.currentTimeMillis();
        if (remainMs <= 0) {
            map.remove(abilityId);
            return 0;
        }
        return (remainMs + 49) / 50;
    }

    public boolean isOnCooldown(Player player, String abilityId) {
        return cooldownRemainingTicks(player, abilityId) > 0;
    }

    private CastResult checkLevels(Player caster, AbilityDefinition ability) {
        if (ability.minLevels().isEmpty()) {
            return CastResult.SUCCESS;
        }
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            return CastResult.SUCCESS;
        }
        for (Map.Entry<String, Integer> entry : ability.minLevels().entrySet()) {
            int have = skills.get(caster.getUniqueId(), SkillId.of(entry.getKey()))
                    .join()
                    .level();
            if (have < entry.getValue()) {
                caster.sendMessage("§cRequires " + entry.getKey() + " level §e" + entry.getValue() + "§c.");
                return CastResult.LEVEL_TOO_LOW;
            }
        }
        return CastResult.SUCCESS;
    }

    private boolean hasCosts(Player caster, AbilityDefinition ability) {
        var costs = ability.costs();
        if (costs.prayer() > 0) {
            int prayer = AbilityCombatServices.find()
                    .map(b -> b.currentPrayer(caster))
                    .orElseGet(() -> CombatServices.find()
                            .map(c -> c.stats(caster).currentPrayer())
                            .orElse(999));
            if (prayer < costs.prayer()) {
                caster.sendMessage("§cNot enough prayer (need §e" + costs.prayer() + "§c).");
                return false;
            }
        }
        if (costs.requiresStaff()) {
            ItemStack main = caster.getInventory().getItemInMainHand();
            ItemStack off = caster.getInventory().getItemInOffHand();
            if (main.getType() != costs.requiredStaff() && off.getType() != costs.requiredStaff()) {
                caster.sendMessage("§cRequires §e" + costs.requiredStaff().name() + "§c in hand.");
                return false;
            }
        }
        if (!hasRunes(caster, costs.runes())) {
            caster.sendMessage("§cMissing spell runes.");
            return false;
        }
        return true;
    }

    private void consumeCosts(Player caster, AbilityDefinition ability) {
        var costs = ability.costs();
        if (costs.prayer() > 0) {
            AbilityCombatServices.find().ifPresent(b -> b.drainPrayer(caster, costs.prayer()));
        }
        consumeRunes(caster, costs.runes());
    }

    private static boolean hasRunes(Player caster, Map<Material, Integer> runes) {
        if (runes.isEmpty()) {
            return true;
        }
        for (Map.Entry<Material, Integer> entry : runes.entrySet()) {
            if (countMaterial(caster.getInventory(), entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeRunes(Player caster, Map<Material, Integer> runes) {
        if (runes.isEmpty()) {
            return;
        }
        PlayerInventory inv = caster.getInventory();
        for (Map.Entry<Material, Integer> entry : runes.entrySet()) {
            removeMaterial(inv, entry.getKey(), entry.getValue());
        }
    }

    private static int countMaterial(PlayerInventory inv, Material material) {
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void removeMaterial(PlayerInventory inv, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
        }
        inv.setContents(contents);
    }
}
