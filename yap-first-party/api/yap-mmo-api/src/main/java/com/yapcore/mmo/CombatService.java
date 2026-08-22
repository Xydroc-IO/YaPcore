package com.yapcore.mmo;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CombatService {

    CombatStats stats(Player player);

    void recalculate(Player player);

    CompletableFuture<Integer> getHp(UUID playerId);

    CompletableFuture<Void> setHp(UUID playerId, int hp);

    Optional<GearBonus> gearBonusFor(org.bukkit.inventory.ItemStack stack);
}
