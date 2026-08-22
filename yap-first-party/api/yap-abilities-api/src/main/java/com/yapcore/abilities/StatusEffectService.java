package com.yapcore.abilities;

import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusEffectService {

    Collection<StatusEffectDefinition> definitions();

    Optional<StatusEffectDefinition> get(String id);

    void apply(LivingEntity target, String effectId, UUID sourceId, int stacks);

    void remove(LivingEntity target, String effectId);

    List<ActiveStatusEffect> active(UUID entityId);

    void clearAll(LivingEntity target);

    StatModifiers aggregateModifiers(LivingEntity entity);
}
