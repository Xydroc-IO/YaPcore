package com.yapcore.vehicles.upgrades;

import com.yapcore.vehicles.api.StatModifier;
import com.yapcore.vehicles.api.UpgradeSlot;
import com.yapcore.vehicles.api.Vehicle;
import com.yapcore.vehicles.api.VehicleUpgrade;
import com.yapcore.vehicles.api.VehicleUpgradeAPI;
import com.yapcore.vehicles.engine.VehicleInstance;
import com.yapcore.vehicles.engine.VehicleKeys;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import com.yapcore.vehicles.engine.VehiclesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class UpgradeService implements VehicleUpgradeAPI {

    private final VehicleServiceImpl vehicles;
    private final VehicleKeys keys;
    private final VehiclesConfig config;
    private final Logger log;
    private final NamespacedKey upgradeIdKey;
    private final NamespacedKey dealershipKey;
    private UpgradeShop shopUi;
    private final Map<String, VehicleUpgrade> upgrades = new ConcurrentHashMap<>();
    private final List<NamespacedKey> recipeKeys = new java.util.ArrayList<>();

    public UpgradeService(VehicleServiceImpl vehicles, VehicleKeys keys, VehiclesConfig config, Logger log) {
        this.vehicles = vehicles;
        this.keys = keys;
        this.config = config;
        this.log = log;
        this.upgradeIdKey = new NamespacedKey(vehicles.plugin(), "upgrade_id");
        this.dealershipKey = new NamespacedKey(vehicles.plugin(), "dealership_type");
    }

    public void setShopUi(UpgradeShop shopUi) {
        this.shopUi = shopUi;
    }

    public NamespacedKey upgradeIdKey() {
        return upgradeIdKey;
    }

    public NamespacedKey dealershipKey() {
        return dealershipKey;
    }

    @Override
    public void register(VehicleUpgrade upgrade) {
        upgrades.put(upgrade.id(), upgrade);
        registerRecipe(upgrade);
        log.info("Registered upgrade: " + upgrade.id() + " [" + upgrade.slot() + "]");
    }

    @Override
    public boolean unregister(String upgradeId) {
        VehicleUpgrade removed = upgrades.remove(upgradeId.toLowerCase());
        if (removed != null) {
            Bukkit.removeRecipe(new NamespacedKey(vehicles.plugin(), "upgrade_" + removed.id()));
        }
        return removed != null;
    }

    @Override
    public Optional<VehicleUpgrade> get(String upgradeId) {
        return Optional.ofNullable(upgrades.get(upgradeId.toLowerCase()));
    }

    @Override
    public Collection<VehicleUpgrade> getAll() {
        return Collections.unmodifiableCollection(upgrades.values());
    }

    @Override
    public ItemStack createItem(VehicleUpgrade upgrade) {
        ItemStack stack = new ItemStack(upgrade.baseMaterial());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(upgrade.displayName())
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text("Slot: " + upgrade.slot().name())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            for (String line : upgrade.lore()) {
                lore.add(Component.text(line)
                        .color(NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Sneak + right-click vehicle to install")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.setCustomModelData(upgrade.customModelData());
            meta.getPersistentDataContainer().set(upgradeIdKey, PersistentDataType.STRING, upgrade.id());
        });
        return stack;
    }

    @Override
    public Optional<String> itemUpgradeId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(upgradeIdKey, PersistentDataType.STRING);
        return Optional.ofNullable(id);
    }

    @Override
    public boolean install(Vehicle vehicle, VehicleUpgrade upgrade, @Nullable Player installer) {
        if (!(vehicle instanceof VehicleInstance instance)) {
            return false;
        }
        instance.installUpgrade(upgrade);
        if (installer != null) {
            installer.sendMessage("Installed " + upgrade.displayName() + " [" + upgrade.slot() + "]");
        }
        return true;
    }

    @Override
    public boolean uninstall(Vehicle vehicle, UpgradeSlot slot, boolean giveItem, @Nullable Player to) {
        if (!(vehicle instanceof VehicleInstance instance)) {
            return false;
        }
        Optional<VehicleUpgrade> removed = instance.uninstallUpgrade(slot);
        if (removed.isEmpty()) {
            return false;
        }
        if (giveItem && to != null) {
            to.getInventory().addItem(createItem(removed.get()));
        }
        return true;
    }

    @Override
    public Map<UpgradeSlot, VehicleUpgrade> installed(Vehicle vehicle) {
        if (!(vehicle instanceof VehicleInstance instance)) {
            return Map.of();
        }
        Map<UpgradeSlot, VehicleUpgrade> out = new EnumMap<>(UpgradeSlot.class);
        for (var e : instance.installedUpgradeIds().entrySet()) {
            VehicleUpgrade u = upgrades.get(e.getValue());
            if (u != null) {
                out.put(e.getKey(), u);
            }
        }
        return out;
    }

    @Override
    public StatModifier combined(Vehicle vehicle) {
        StatModifier combined = StatModifier.none();
        for (VehicleUpgrade u : installed(vehicle).values()) {
            combined = combined.and(u.stats());
        }
        return combined;
    }

    @Override
    public void openShop(Player player) {
        if (shopUi != null) {
            shopUi.open(player);
        } else {
            player.sendMessage("Shop not ready.");
        }
    }

    public void clearRecipes() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
    }

    private void registerRecipe(VehicleUpgrade upgrade) {
        if (upgrade.craftIngredients().isEmpty() || !config.upgradesCraftEnabled()) {
            return;
        }
        NamespacedKey key = new NamespacedKey(vehicles.plugin(), "upgrade_" + upgrade.id());
        try {
            Bukkit.removeRecipe(key);
            List<ItemStack> ings = compactShapelessIngredients(upgrade.craftIngredients());
            int slots = ings.stream().mapToInt(ItemStack::getAmount).sum();
            if (slots > 9 || slots < 1) {
                log.warning("Skip craft recipe for " + upgrade.id()
                        + ": shapeless needs 1–9 ingredient slots (have " + slots
                        + ") — buy via /yapvehicle shop");
                return;
            }
            ShapelessRecipe recipe = new ShapelessRecipe(key, createItem(upgrade));
            for (ItemStack ing : ings) {
                recipe.addIngredient(ing.getAmount(), ing.getType());
            }
            Bukkit.addRecipe(recipe);
            recipeKeys.add(key);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            log.warning("Could not register recipe for " + upgrade.id() + ": " + ex.getMessage());
        }
    }

    /**
     * Merge by material and fold common 9∶1 block conversions so totals fit
     * Minecraft's 9-slot shapeless limit.
     */
    static List<ItemStack> compactShapelessIngredients(List<ItemStack> raw) {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack ing : raw) {
            if (ing == null || ing.getType().isAir() || ing.getAmount() <= 0) {
                continue;
            }
            counts.merge(ing.getType(), ing.getAmount(), Integer::sum);
        }
        foldNineToBlock(counts, Material.IRON_INGOT, Material.IRON_BLOCK);
        foldNineToBlock(counts, Material.GOLD_INGOT, Material.GOLD_BLOCK);
        foldNineToBlock(counts, Material.COPPER_INGOT, Material.COPPER_BLOCK);
        foldNineToBlock(counts, Material.REDSTONE, Material.REDSTONE_BLOCK);
        foldNineToBlock(counts, Material.COAL, Material.COAL_BLOCK);
        foldNineToBlock(counts, Material.DIAMOND, Material.DIAMOND_BLOCK);
        foldNineToBlock(counts, Material.SLIME_BALL, Material.SLIME_BLOCK);

        List<ItemStack> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            int n = e.getValue();
            if (n > 0) {
                // One shapeless "ingredient line" may use count, but total across
                // all lines must stay ≤ 9 — keep a single stack per material.
                out.add(new ItemStack(e.getKey(), n));
            }
        }
        return out;
    }

    private static void foldNineToBlock(Map<Material, Integer> counts, Material item, Material block) {
        if (block == null) {
            return;
        }
        Integer n = counts.get(item);
        if (n == null || n < 9) {
            return;
        }
        int blocks = n / 9;
        int rem = n % 9;
        counts.remove(item);
        if (rem > 0) {
            counts.put(item, rem);
        }
        counts.merge(block, blocks, Integer::sum);
    }

    /** Take shop price materials from player inventory. */
    public boolean takePrice(Player player, List<ItemStack> price) {
        if (price.isEmpty()) {
            return true;
        }
        // Check
        Map<Material, Integer> need = new LinkedHashMap<>();
        for (ItemStack p : price) {
            need.merge(p.getType(), p.getAmount(), Integer::sum);
        }
        for (var e : need.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())) {
                return false;
            }
        }
        for (var e : need.entrySet()) {
            ItemStack remove = new ItemStack(e.getKey(), e.getValue());
            player.getInventory().removeItem(remove);
        }
        return true;
    }
}
