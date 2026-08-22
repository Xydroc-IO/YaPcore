package com.yapcore.mechanics;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** World interaction rules: tools, stamina, nodes, farming, physics profiles. */
public interface MechanicsService {

    boolean canBreak(Player player, Material block, ItemStack tool);

    Optional<String> breakDeniedReason(Player player, Material block, ItemStack tool);

    StaminaState stamina(Player player);

    boolean consumeStamina(Player player, double amount);

    void regenStamina(Player player, double amount);

    double fishingXpMultiplier(Player player);

    double fallDamageMultiplier(Player player);

    double projectileDamageMultiplier(Player player);
}
