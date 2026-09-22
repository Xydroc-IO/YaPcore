package com.yapcore.yap420.market;

import com.yapcore.yap420.item.ItemBridge;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Sell YaP420 stacks / buy supplies via economy. */
public final class DealerService {

    public enum TradeResult {
        OK,
        DISABLED,
        NO_ECONOMY,
        NOTHING,
        MISSING_CATALOG,
        CANNOT_AFFORD,
        INVENTORY_FULL
    }

    public record Sale(String itemId, int amount, double payout) {
    }

    public record TradeOutcome(TradeResult result, List<Sale> sales, double total, String itemId, int amount) {
        static TradeOutcome fail(TradeResult r) {
            return new TradeOutcome(r, List.of(), 0, null, 0);
        }

        static TradeOutcome ok(List<Sale> sales, double total) {
            return new TradeOutcome(TradeResult.OK, sales, total, null, 0);
        }

        static TradeOutcome one(TradeResult r, String itemId, int amount, double total) {
            return new TradeOutcome(r, List.of(), total, itemId, amount);
        }
    }

    private final ItemBridge items;
    private final EconomyBridge economy;
    private MarketSettings market;

    public DealerService(ItemBridge items, EconomyBridge economy, MarketSettings market) {
        this.items = items;
        this.economy = economy;
        this.market = market;
    }

    public void setMarket(MarketSettings market) {
        this.market = market;
    }

    public MarketSettings market() {
        return market;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public TradeOutcome sellAll(Player player) {
        if (!market.enabled()) {
            return TradeOutcome.fail(TradeResult.DISABLED);
        }
        if (!economy.available()) {
            return TradeOutcome.fail(TradeResult.NO_ECONOMY);
        }
        Map<String, Integer> counts = countSellables(player.getInventory());
        if (counts.isEmpty()) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        List<Sale> sales = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double unit = market.sellPrice(e.getKey());
            if (unit < 0) {
                continue;
            }
            int took = items.take(player.getInventory(), e.getKey(), e.getValue());
            if (took <= 0) {
                continue;
            }
            double pay = unit * took;
            total += pay;
            sales.add(new Sale(e.getKey(), took, pay));
        }
        if (sales.isEmpty()) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        economy.deposit(player, total);
        return TradeOutcome.ok(sales, total);
    }

    public TradeOutcome sellOne(Player player, String itemId, int amount) {
        if (!market.enabled()) {
            return TradeOutcome.fail(TradeResult.DISABLED);
        }
        if (!economy.available()) {
            return TradeOutcome.fail(TradeResult.NO_ECONOMY);
        }
        if (itemId == null || amount <= 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        double unit = market.sellPrice(itemId);
        if (unit < 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        int have = items.count(player.getInventory(), itemId);
        int take = Math.min(amount, have);
        if (take <= 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        int removed = items.take(player.getInventory(), itemId, take);
        if (removed <= 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        double pay = unit * removed;
        economy.deposit(player, pay);
        return TradeOutcome.one(TradeResult.OK, itemId, removed, pay);
    }

    public TradeOutcome buy(Player player, String itemId, int amount) {
        if (!market.enabled() || !market.buyEnabled()) {
            return TradeOutcome.fail(TradeResult.DISABLED);
        }
        if (!economy.available()) {
            return TradeOutcome.fail(TradeResult.NO_ECONOMY);
        }
        if (itemId == null || amount <= 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        double unit = market.buyPrice(itemId);
        if (unit < 0) {
            return TradeOutcome.fail(TradeResult.NOTHING);
        }
        if (!items.has(itemId)) {
            return TradeOutcome.fail(TradeResult.MISSING_CATALOG);
        }
        double cost = unit * amount;
        Optional<Double> bal = economy.withdraw(player, cost);
        if (bal.isEmpty()) {
            return TradeOutcome.one(TradeResult.CANNOT_AFFORD, itemId, amount, cost);
        }
        if (!items.giveOrDrop(player, itemId, amount)) {
            economy.deposit(player, cost);
            return TradeOutcome.fail(TradeResult.MISSING_CATALOG);
        }
        return TradeOutcome.one(TradeResult.OK, itemId, amount, cost);
    }

    public Map<String, Integer> countSellables(PlayerInventory inv) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemStack stack : inv.getContents()) {
            if (stack == null) {
                continue;
            }
            Optional<String> id = items.idOf(stack);
            if (id.isEmpty()) {
                continue;
            }
            String key = id.get().toLowerCase(Locale.ROOT);
            if (market.sellPrice(key) < 0) {
                continue;
            }
            out.merge(key, stack.getAmount(), Integer::sum);
        }
        return out;
    }
}
