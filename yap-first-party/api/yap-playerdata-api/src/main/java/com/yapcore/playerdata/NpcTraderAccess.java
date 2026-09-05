package com.yapcore.playerdata;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Offer catalogs + trade GUIs for YaPNpcs hub shops ({@code /npc shop}).
 * Registered on {@code ServicesManager} when {@code features.traders} is on.
 * There is no standalone {@code /trader} command.
 */
public interface NpcTraderAccess {

    record OfferView(long id, String mode, String material, int amount, double price, int stock) {
    }

    boolean tradersEnabled();

    /** Open the buy/sell GUI for an existing catalog id. */
    void openTradeGui(Player player, long traderId);

    boolean traderExists(long traderId);

    long createCatalog(String name);

    long addOffer(long traderId, String mode, Material material, int amount, double price, int stock);

    List<OfferView> listOffers(long traderId);

    boolean deleteOffer(long offerId);

    boolean deleteCatalog(long traderId);
}
