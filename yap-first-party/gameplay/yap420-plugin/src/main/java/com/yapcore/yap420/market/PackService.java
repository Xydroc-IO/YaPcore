package com.yapcore.yap420.market;

import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.plant.StrainId;
import org.bukkit.entity.Player;

/** Pack / unpack grams ↔ ounces ↔ bricks using inventory contents. */
public final class PackService {

    public enum Result {
        OK,
        NEED_ITEMS,
        MISSING_CATALOG,
        DISABLED
    }

    public record Outcome(Result result, int produced, String productId, int need, int have, String needId) {
        static Outcome ok(int produced, String productId) {
            return new Outcome(Result.OK, produced, productId, 0, 0, null);
        }

        static Outcome need(String needId, int need, int have) {
            return new Outcome(Result.NEED_ITEMS, 0, null, need, have, needId);
        }

        static Outcome missing() {
            return new Outcome(Result.MISSING_CATALOG, 0, null, 0, 0, null);
        }

        static Outcome disabled() {
            return new Outcome(Result.DISABLED, 0, null, 0, 0, null);
        }
    }

    private final ItemBridge items;
    private MarketSettings market;

    public PackService(ItemBridge items, MarketSettings market) {
        this.items = items;
        this.market = market;
    }

    public void setMarket(MarketSettings market) {
        this.market = market;
    }

    public MarketSettings market() {
        return market;
    }

    /**
     * Pack as many {@code unit} packs as possible for {@code strain} (or one if {@code maxPacks==1}).
     * Gram packs consume cured buds; ounce packs consume cured+gram; brick packs consume ounces.
     */
    public Outcome pack(Player player, PackUnit unit, StrainId strain, int maxPacks) {
        if (unit == null || strain == null || maxPacks <= 0) {
            return Outcome.need("?", 1, 0);
        }
        String outId = Yap420ItemIds.packId(unit, strain);
        if (!items.has(outId)) {
            return Outcome.missing();
        }
        PackMath math = market.pack();
        return switch (unit) {
            case GRAM -> packGrams(player, strain, maxPacks, outId);
            case OUNCE -> packFromGrams(player, strain, math.gramsPerOunce(), maxPacks, outId);
            case BRICK -> packBricks(player, strain, math.ouncesPerBrick(), maxPacks, outId);
        };
    }

    public Outcome unpack(Player player, PackUnit unit, StrainId strain, int maxPacks) {
        if (unit == null || strain == null || maxPacks <= 0) {
            return Outcome.need("?", 1, 0);
        }
        String inId = Yap420ItemIds.packId(unit, strain);
        int have = items.count(player.getInventory(), inId);
        if (have <= 0) {
            return Outcome.need(inId, 1, 0);
        }
        int take = Math.min(maxPacks, have);
        PackMath math = market.pack();
        String outId;
        int outAmount;
        switch (unit) {
            case GRAM -> {
                // Bagged gram → cured bud
                outId = Yap420ItemIds.curedBud(strain);
                outAmount = take;
            }
            case OUNCE -> {
                outId = Yap420ItemIds.gram(strain);
                outAmount = take * math.gramsPerOunce();
            }
            case BRICK -> {
                outId = Yap420ItemIds.ounce(strain);
                outAmount = take * math.ouncesPerBrick();
            }
            default -> {
                return Outcome.need(inId, 1, have);
            }
        }
        if (!items.has(outId)) {
            return Outcome.missing();
        }
        int removed = items.take(player.getInventory(), inId, take);
        if (removed <= 0) {
            return Outcome.need(inId, 1, have);
        }
        // Recompute if partial remove
        int produced = switch (unit) {
            case GRAM -> removed;
            case OUNCE -> removed * math.gramsPerOunce();
            case BRICK -> removed * math.ouncesPerBrick();
        };
        items.giveOrDrop(player, outId, produced);
        return Outcome.ok(produced, outId);
    }

    private Outcome packGrams(Player player, StrainId strain, int maxPacks, String outId) {
        String cured = Yap420ItemIds.curedBud(strain);
        int have = items.count(player.getInventory(), cured);
        if (have <= 0) {
            return Outcome.need(cured, 1, 0);
        }
        int take = Math.min(maxPacks, have);
        int removed = items.take(player.getInventory(), cured, take);
        if (removed <= 0) {
            return Outcome.need(cured, 1, have);
        }
        items.giveOrDrop(player, outId, removed);
        return Outcome.ok(removed, outId);
    }

    private Outcome packFromGrams(Player player, StrainId strain, int gramsNeeded, int maxPacks, String outId) {
        String cured = Yap420ItemIds.curedBud(strain);
        String gram = Yap420ItemIds.gram(strain);
        int haveCured = items.count(player.getInventory(), cured);
        int haveGram = items.count(player.getInventory(), gram);
        int have = haveCured + haveGram;
        if (have < gramsNeeded) {
            return Outcome.need(cured + "/" + gram, gramsNeeded, have);
        }
        int packs = Math.min(maxPacks, have / gramsNeeded);
        if (packs <= 0) {
            return Outcome.need(cured + "/" + gram, gramsNeeded, have);
        }
        int need = packs * gramsNeeded;
        int takeCured = Math.min(haveCured, need);
        int takeGram = need - takeCured;
        items.take(player.getInventory(), cured, takeCured);
        if (takeGram > 0) {
            items.take(player.getInventory(), gram, takeGram);
        }
        items.giveOrDrop(player, outId, packs);
        return Outcome.ok(packs, outId);
    }

    private Outcome packBricks(Player player, StrainId strain, int ouncesNeeded, int maxPacks, String outId) {
        String ounce = Yap420ItemIds.ounce(strain);
        int have = items.count(player.getInventory(), ounce);
        if (have < ouncesNeeded) {
            return Outcome.need(ounce, ouncesNeeded, have);
        }
        int packs = Math.min(maxPacks, have / ouncesNeeded);
        int need = packs * ouncesNeeded;
        items.take(player.getInventory(), ounce, need);
        items.giveOrDrop(player, outId, packs);
        return Outcome.ok(packs, outId);
    }
}
