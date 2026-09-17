package com.yapcore.playerdata.npc;

import com.yapcore.playerdata.NpcTraderAccess;
import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.NpcTraderRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Offer catalogs + buy/sell GUIs for YaPNpcs hub shops.
 * One icon per item: left-click buy, right-click sell, with a quantity picker.
 */
public final class NpcTraderService implements NpcTraderAccess {

    public record TraderGuiCtx(long traderId, int page) {
    }

    /** Quantity confirm screen: left-click buy / right-click sell from the catalog. */
    public record QtyGuiCtx(long traderId, int returnPage, long offerId, boolean buying, int qty) {
    }

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final NpcTraderRepository repo;
    private final NpcTraderTradeGui gui;

    public NpcTraderService(JavaPlugin plugin, PlayerDataConfig config,
                            NpcTraderRepository repo, BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.gui = new NpcTraderTradeGui(plugin, repo, balances);
    }

    public void start() {
        YapSched.global(plugin, this::despawnLegacyEntities);
    }

    public void stop() {
        gui.clearSessions();
    }

    private void despawnLegacyEntities() {
        try {
            int removed = 0;
            for (var t : repo.listForServer(config.serverId())) {
                if (t.entityUuid() == null) {
                    continue;
                }
                Entity e = Bukkit.getEntity(t.entityUuid());
                if (e != null) {
                    e.remove();
                    removed++;
                }
                repo.setEntityUuid(t.id(), null);
            }
            if (removed > 0) {
                plugin.getLogger().info("Removed " + removed
                        + " legacy trader villager(s) — shops are YaPNpcs-only now");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "legacy trader despawn", e);
        }
    }

    @Override
    public long createCatalog(String name) {
        try {
            String world = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName();
            var draft = new NpcTraderRepository.Trader(
                    0, config.serverId(), world,
                    0.5, -64, 0.5, 0f,
                    name == null || name.isBlank() ? "Shop" : name, null);
            return repo.create(draft);
        } catch (SQLException e) {
            throw new IllegalStateException("createCatalog failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long addOffer(long traderId, String mode, Material material, int amount, double price, int stock) {
        return addOffer(traderId, mode, material, amount, price, stock, null);
    }

    @Override
    public long addOffer(long traderId, String mode, Material material, int amount, double price, int stock,
                         String metaJson) {
        try {
            return repo.addOffer(traderId, mode, material, amount, price, stock, metaJson);
        } catch (SQLException e) {
            throw new IllegalStateException("addOffer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<OfferView> listOffers(long traderId) {
        try {
            return repo.offers(traderId).stream()
                    .map(o -> new OfferView(o.id(), o.mode(), o.material().name(),
                            o.amount(), o.price(), o.stock(), o.metaJson()))
                    .toList();
        } catch (SQLException e) {
            throw new IllegalStateException("listOffers failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteOffer(long offerId) {
        try {
            return repo.deleteOffer(offerId);
        } catch (SQLException e) {
            throw new IllegalStateException("deleteOffer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateOffer(long offerId, double price, int amount, int stock) {
        try {
            return repo.updateOffer(offerId, price, amount, stock);
        } catch (SQLException e) {
            throw new IllegalStateException("updateOffer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int clearOffers(long traderId) {
        try {
            return repo.clearOffers(traderId);
        } catch (SQLException e) {
            throw new IllegalStateException("clearOffers failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteCatalog(long traderId) {
        try {
            return repo.delete(traderId);
        } catch (SQLException e) {
            throw new IllegalStateException("deleteCatalog failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> shopPresetIds() {
        return ShopPresets.ids();
    }

    @Override
    public int applyShopPreset(long traderId, String presetId, boolean replace) {
        List<ShopPresets.OfferSpec> specs = ShopPresets.get(presetId);
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("Unknown preset: " + presetId
                    + " — try " + String.join(", ", ShopPresets.ids()));
        }
        try {
            if (repo.get(traderId).isEmpty()) {
                throw new IllegalArgumentException("Shop catalog #" + traderId + " missing");
            }
            if (replace) {
                repo.clearOffers(traderId);
            }
            int inserted = 0;
            for (ShopPresets.OfferSpec spec : specs) {
                String meta = OfferItemMeta.encode(spec.enchants());
                repo.addOffer(traderId, "BUY", spec.material(), spec.amount(),
                        spec.buyPrice(), -1, meta);
                inserted++;
                if (!spec.enchanted() && spec.sellPrice() > 0) {
                    repo.addOffer(traderId, "SELL", spec.material(), spec.amount(),
                            spec.sellPrice(), -1, null);
                    inserted++;
                }
            }
            return inserted;
        } catch (SQLException e) {
            throw new IllegalStateException("applyShopPreset failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean tradersEnabled() {
        return true;
    }

    @Override
    public boolean traderExists(long traderId) {
        try {
            return repo.get(traderId).isPresent();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void openTradeGui(Player player, long traderId) {
        gui.openTradeGui(player, traderId, 0);
    }

    public void openTradeGui(Player player, long traderId, int page) {
        gui.openTradeGui(player, traderId, page);
    }

    public boolean handleTradeClick(Player player, long traderId, int page, int slot,
                                    String itemName, ClickType click) {
        return gui.handleTradeClick(player, traderId, page, slot, itemName, click);
    }

    public boolean handleQtyClick(Player player, QtyGuiCtx ctx, int slot, String itemName) {
        return gui.handleQtyClick(player, ctx, slot, itemName);
    }

    public void clearClicks(Player player) {
        gui.clearClicks(player);
    }
}
