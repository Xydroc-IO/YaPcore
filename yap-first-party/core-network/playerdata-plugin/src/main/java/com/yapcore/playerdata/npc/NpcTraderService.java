package com.yapcore.playerdata.npc;

import com.yapcore.playerdata.NpcTraderAccess;
import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.NpcTraderRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.gui.YapMenuHolder;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Offer catalogs + buy/sell GUIs for YaPNpcs hub shops.
 * No standalone trader villagers — create/manage via {@code /npc shop}.
 */
public final class NpcTraderService implements NpcTraderAccess {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final NpcTraderRepository repo;
    private final BalanceStore balances;
    private final Map<UUID, Map<Integer, Long>> offerClicks = new HashMap<>();

    public NpcTraderService(JavaPlugin plugin, PlayerDataConfig config,
                            NpcTraderRepository repo, BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.balances = balances;
    }

    public void start() {
        YapSched.global(plugin, this::despawnLegacyEntities);
    }

    public void stop() {
        offerClicks.clear();
    }

    /** Remove leftover /trader villager entities from older installs; catalogs stay. */
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
        try {
            return repo.addOffer(traderId, mode, material, amount, price, stock);
        } catch (SQLException e) {
            throw new IllegalStateException("addOffer failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<OfferView> listOffers(long traderId) {
        try {
            return repo.offers(traderId).stream()
                    .map(o -> new OfferView(o.id(), o.mode(), o.material().name(),
                            o.amount(), o.price(), o.stock()))
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
    public boolean deleteCatalog(long traderId) {
        try {
            return repo.delete(traderId);
        } catch (SQLException e) {
            throw new IllegalStateException("deleteCatalog failed: " + e.getMessage(), e);
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
        try {
            var trader = repo.get(traderId);
            if (trader.isEmpty()) {
                player.sendMessage("§cShop gone.");
                return;
            }
            List<NpcTraderRepository.Offer> offers = repo.offers(traderId);
            YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.NPC_TRADER, traderId);
            Inventory inv = Bukkit.createInventory(holder, 54,
                    Component.text(trader.get().name(), NamedTextColor.GOLD));
            holder.bind(inv);
            YapMenuHolder.fillBorder(inv);
            Map<Integer, Long> meta = new HashMap<>();
            int slot = 10;
            for (var o : offers) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                boolean buy = o.mode().equalsIgnoreCase("BUY");
                ItemStack icon = new ItemStack(o.material(), Math.min(64, Math.max(1, o.amount())));
                String stock = o.stock() < 0 ? "∞" : String.valueOf(o.stock());
                icon.editMeta(m -> {
                    m.displayName(Component.text((buy ? "Buy " : "Sell ") + o.material().name())
                            .color(buy ? NamedTextColor.GREEN : NamedTextColor.AQUA));
                    m.lore(List.of(
                            Component.text("Amount: " + o.amount(), NamedTextColor.GRAY),
                            Component.text("Price: $" + String.format("%.2f", o.price()), NamedTextColor.YELLOW),
                            Component.text("Stock: " + stock, NamedTextColor.GRAY),
                            Component.text("Click to " + (buy ? "buy" : "sell"), NamedTextColor.WHITE)));
                });
                inv.setItem(slot, icon);
                meta.put(slot, o.id());
                slot++;
            }
            inv.setItem(49, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Close"));
            offerClicks.put(player.getUniqueId(), meta);
            player.openInventory(inv);
        } catch (SQLException e) {
            player.sendMessage("§cShop error.");
            plugin.getLogger().log(Level.WARNING, "openTradeGui", e);
        }
    }

    public boolean handleTradeClick(Player player, long traderId, int slot, String itemName) {
        if ("Close".equals(itemName)) {
            player.closeInventory();
            return true;
        }
        Map<Integer, Long> meta = offerClicks.getOrDefault(player.getUniqueId(), Map.of());
        Long offerId = meta.get(slot);
        if (offerId == null) {
            return true;
        }
        try {
            executeTrade(player, offerId);
            openTradeGui(player, traderId);
        } catch (Exception e) {
            player.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    private void executeTrade(Player player, long offerId) throws Exception {
        NpcTraderRepository.Offer offer = null;
        for (var t : repo.listForServer(config.serverId())) {
            for (var o : repo.offers(t.id())) {
                if (o.id() == offerId) {
                    offer = o;
                    break;
                }
            }
            if (offer != null) {
                break;
            }
        }
        if (offer == null) {
            throw new IllegalStateException("Offer gone");
        }
        if (offer.mode().equalsIgnoreCase("BUY")) {
            if (offer.stock() == 0) {
                throw new IllegalStateException("Out of stock");
            }
            double bal = balances.getBalance(player.getUniqueId());
            if (bal < offer.price()) {
                throw new IllegalStateException("Insufficient funds");
            }
            balances.setBalance(player.getUniqueId(), bal - offer.price());
            player.getInventory().addItem(new ItemStack(offer.material(), offer.amount()));
            if (offer.stock() > 0) {
                repo.setStock(offer.id(), offer.stock() - 1);
            }
            player.sendMessage("§aBought §f" + offer.amount() + "x " + offer.material()
                    + " §afor §f$" + String.format("%.2f", offer.price()));
        } else {
            ItemStack need = new ItemStack(offer.material(), offer.amount());
            if (!player.getInventory().containsAtLeast(need, offer.amount())) {
                throw new IllegalStateException("You don't have the items");
            }
            player.getInventory().removeItem(need);
            balances.setBalance(player.getUniqueId(),
                    balances.getBalance(player.getUniqueId()) + offer.price());
            if (offer.stock() >= 0) {
                repo.setStock(offer.id(), offer.stock() + 1);
            }
            player.sendMessage("§aSold §f" + offer.amount() + "x " + offer.material()
                    + " §afor §f$" + String.format("%.2f", offer.price()));
        }
    }

    public void clearClicks(Player player) {
        offerClicks.remove(player.getUniqueId());
    }
}
