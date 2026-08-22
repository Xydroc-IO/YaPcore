package com.yapcore.abilities.service;

import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.CastResult;
import com.yapcore.abilities.exec.AbilityExecutor;
import com.yapcore.abilities.load.AbilityPackLoader;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

public final class AbilityServiceImpl implements AbilityService {

    private final AbilityPackLoader loader;
    private final AbilityExecutor executor;

    public AbilityServiceImpl(AbilityPackLoader loader, AbilityExecutor executor) {
        this.loader = loader;
        this.executor = executor;
    }

    @Override
    public Collection<AbilityDefinition> definitions() {
        return loader.abilities().values();
    }

    @Override
    public Optional<AbilityDefinition> get(String id) {
        return Optional.ofNullable(loader.get(normalize(id)));
    }

    @Override
    public CastResult cast(Player caster, String abilityId) {
        AbilityDefinition ability = loader.get(normalize(abilityId));
        if (ability == null) {
            caster.sendMessage("§cUnknown ability.");
            return CastResult.UNKNOWN_ABILITY;
        }
        return executor.cast(caster, ability, null);
    }

    @Override
    public CastResult castAt(Player caster, String abilityId, LivingEntity target) {
        AbilityDefinition ability = loader.get(normalize(abilityId));
        if (ability == null) {
            caster.sendMessage("§cUnknown ability.");
            return CastResult.UNKNOWN_ABILITY;
        }
        return executor.cast(caster, ability, target);
    }

    @Override
    public long cooldownRemainingTicks(Player player, String abilityId) {
        return executor.cooldownRemainingTicks(player, normalize(abilityId));
    }

    @Override
    public boolean isOnCooldown(Player player, String abilityId) {
        return executor.isOnCooldown(player, normalize(abilityId));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
