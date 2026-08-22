package com.yapcore.vehicles.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A craftable / shoppable car part that improves vehicle stats.
 * Items use {@link #baseMaterial()} + {@link #customModelData()} for resource-pack icons.
 */
public final class VehicleUpgrade {

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final UpgradeSlot slot;
    private final StatModifier stats;
    private final Material baseMaterial;
    private final int customModelData;
    private final List<ItemStack> craftIngredients;
    private final List<ItemStack> shopPrice;
    private final int shopSlot;

    private VehicleUpgrade(Builder b) {
        this.id = b.id.toLowerCase();
        this.displayName = b.displayName;
        this.lore = List.copyOf(b.lore);
        this.slot = b.slot;
        this.stats = b.stats;
        this.baseMaterial = b.baseMaterial;
        this.customModelData = b.customModelData;
        this.craftIngredients = List.copyOf(b.craftIngredients);
        this.shopPrice = List.copyOf(b.shopPrice);
        this.shopSlot = b.shopSlot;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public UpgradeSlot slot() {
        return slot;
    }

    public StatModifier stats() {
        return stats;
    }

    public Material baseMaterial() {
        return baseMaterial;
    }

    /** Resource-pack CustomModelData (paper overrides in yap-vehicles pack). */
    public int customModelData() {
        return customModelData;
    }

    /** Shapeless craft cost (all ingredients required once). Empty = not craftable. */
    public List<ItemStack> craftIngredients() {
        return craftIngredients;
    }

    /** Material barter cost for the in-game shop. Empty = not sold. */
    public List<ItemStack> shopPrice() {
        return shopPrice;
    }

    public int shopSlot() {
        return shopSlot;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private final List<String> lore = new ArrayList<>();
        private UpgradeSlot slot = UpgradeSlot.UTILITY;
        private StatModifier stats = StatModifier.none();
        private Material baseMaterial = Material.PAPER;
        private int customModelData = 77100;
        private final List<ItemStack> craftIngredients = new ArrayList<>();
        private final List<ItemStack> shopPrice = new ArrayList<>();
        private int shopSlot = -1;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id);
            this.displayName = id;
        }

        public Builder displayName(String name) {
            this.displayName = Objects.requireNonNull(name);
            return this;
        }

        public Builder lore(String... lines) {
            for (String line : lines) {
                this.lore.add(line);
            }
            return this;
        }

        public Builder slot(UpgradeSlot slot) {
            this.slot = Objects.requireNonNull(slot);
            return this;
        }

        public Builder stats(StatModifier stats) {
            this.stats = Objects.requireNonNull(stats);
            return this;
        }

        public Builder icon(Material material, int customModelData) {
            this.baseMaterial = Objects.requireNonNull(material);
            this.customModelData = customModelData;
            return this;
        }

        public Builder craft(Material mat, int amount) {
            this.craftIngredients.add(new ItemStack(mat, amount));
            return this;
        }

        public Builder craft(ItemStack stack) {
            this.craftIngredients.add(stack.clone());
            return this;
        }

        public Builder shopPrice(Material mat, int amount) {
            this.shopPrice.add(new ItemStack(mat, amount));
            return this;
        }

        public Builder shopSlot(int slot) {
            this.shopSlot = slot;
            return this;
        }

        public VehicleUpgrade build() {
            return new VehicleUpgrade(this);
        }
    }
}
