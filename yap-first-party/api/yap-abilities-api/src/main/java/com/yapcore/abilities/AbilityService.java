package com.yapcore.abilities;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

public interface AbilityService {

    Collection<AbilityDefinition> definitions();

    Optional<AbilityDefinition> get(String id);

    CastResult cast(Player caster, String abilityId);

    CastResult castAt(Player caster, String abilityId, LivingEntity target);

    long cooldownRemainingTicks(Player player, String abilityId);

    boolean isOnCooldown(Player player, String abilityId);
}
