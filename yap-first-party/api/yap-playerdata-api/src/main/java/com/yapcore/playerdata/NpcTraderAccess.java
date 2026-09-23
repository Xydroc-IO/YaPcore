package com.yapcore.playerdata;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Offer catalogs + trade GUIs for YaPNpcs hub shops ({@code /npc shop}).
 * Registered on {@code ServicesManager} when {@code features.traders} is on.
 * There is no standalone {@code /trader} command.
 */
public interface NpcTraderAccess {

    record OfferView(long id, String mode, String material, int amount, double price, int stock, String metaJson) {
        public OfferView(long id, String mode, String material, int amount, double price, int stock) {
            this(id, mode, material, amount, price, stock, null);
        }
    }

    boolean tradersEnabled();

    /** Open the buy/sell GUI for an existing catalog id. */
    void openTradeGui(Player player, long traderId);

    boolean traderExists(long traderId);

    long createCatalog(String name);

    long addOffer(long traderId, String mode, Material material, int amount, double price, int stock);

    /** {@code metaJson} may encode enchants (see playerdata OfferItemMeta). */
    long addOffer(long traderId, String mode, Material material, int amount, double price, int stock,
                  String metaJson);

    List<OfferView> listOffers(long traderId);

    boolean deleteOffer(long offerId);

    /** Update price / amount / stock for an existing offer. Pass negatives to leave unchanged. */
    boolean updateOffer(long offerId, double price, int amount, int stock);

    /** Remove all offers but keep the catalog. */
    int clearOffers(long traderId);

    boolean deleteCatalog(long traderId);

    /** Built-in shop preset ids: weapons, armor, tools, food, blocks, redstone, crafting, enchants, farming, fishing. */
    Set<String> shopPresetIds();

    /**
     * Apply a built-in catalog. Creates BUY offers (shop sells) and SELL offers (shop buys)
     * with unlimited stock. Enchanted lines are BUY-only.
     *
     * @return number of offer rows inserted
     */
    int applyShopPreset(long traderId, String presetId, boolean replace);
}
