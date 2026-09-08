package com.yapcore.items.combat;

import com.yapcore.items.item.ItemFactory;
import com.yapcore.items.item.ItemRegistry;
import com.yapcore.mmo.CombatBuffs;
import com.yapcore.mmo.CombatService;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.GearBonus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal CombatService: sums gear bonuses from equipped/held YaPItems.
 * HP APIs are no-ops until YaPSkills owns the full combat pool.
 */
public final class ItemsCombatService implements CombatService {

    private final ItemRegistry registry;
    private final ItemFactory factory;

    public ItemsCombatService(ItemRegistry registry, ItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    @Override
    public CombatStats stats(Player player) {
        GearBonus gear = sumGear(player);
        return new CombatStats(1, 1, 1, 10, 1, 1, 1, gear, CombatBuffs.NONE, 10, 10, 1, 1);
    }

    @Override
    public void recalculate(Player player) {
        // gear is derived live from inventory
    }

    @Override
    public CompletableFuture<Integer> getHp(UUID playerId) {
        return CompletableFuture.completedFuture(10);
    }

    @Override
    public CompletableFuture<Void> setHp(UUID playerId, int hp) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<GearBonus> gearBonusFor(ItemStack stack) {
        return factory.definitionOf(stack).map(ItemDefinitionGear::of);
    }

    private GearBonus sumGear(Player player) {
        GearBonus total = GearBonus.ZERO;
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : new ItemStack[]{
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getHelmet(),
                inv.getChestplate(),
                inv.getLeggings(),
                inv.getBoots()
        }) {
            Optional<GearBonus> bonus = gearBonusFor(stack);
            if (bonus.isPresent()) {
                total = total.add(bonus.get());
            }
        }
        return total;
    }

    private static final class ItemDefinitionGear {
        static GearBonus of(com.yapcore.items.item.ItemDefinition def) {
            return def.gear() == null ? GearBonus.ZERO : def.gear();
        }
    }
}
