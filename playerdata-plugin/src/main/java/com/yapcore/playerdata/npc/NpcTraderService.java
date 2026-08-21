package com.yapcore.playerdata.npc;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.NpcTraderRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.gui.YapMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persistent villager NPC traders with buy/sell offer GUIs.
 */
public final class NpcTraderService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final NpcTraderRepository repo;
    private final BalanceStore balances;
    private final NamespacedKey traderKey;
    private final Map<UUID, Map<Integer, Long>> offerClicks = new HashMap<>();

    public NpcTraderService(JavaPlugin plugin, PlayerDataConfig config,
                            NpcTraderRepository repo, BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.balances = balances;
        this.traderKey = new NamespacedKey(plugin, "npc_trader_id");
    }

    public NamespacedKey traderKey() {
        return traderKey;
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::respawnAll);
    }

    public void stop() {
        // leave entities; they persist in world — entity_uuid updated on next start
        offerClicks.clear();
    }

    public void respawnAll() {
        try {
            for (var t : repo.listForServer(config.serverId())) {
                spawnOrRefresh(t);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn NPC traders", e);
        }
    }

    public long createAt(Player player, String name) throws SQLException {
        Location loc = player.getLocation();
        var draft = new NpcTraderRepository.Trader(
                0, config.serverId(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(),
                name, null);
        long id = repo.create(draft);
        var saved = repo.get(id).orElseThrow();
        spawnOrRefresh(saved);
        return id;
    }

    public boolean removeNearest(Player player, double radius) throws SQLException {
        Optional<Long> id = findTraderIdNear(player.getLocation(), radius);
        if (id.isEmpty()) {
            return false;
        }
        despawn(id.get());
        return repo.delete(id.get());
    }

    public Optional<Long> findTraderIdNear(Location loc, double radius) throws SQLException {
        double best = radius * radius;
        Long found = null;
        for (var t : repo.listForServer(config.serverId())) {
            if (!t.world().equals(loc.getWorld().getName())) {
                continue;
            }
            double dx = t.x() - loc.getX();
            double dy = t.y() - loc.getY();
            double dz = t.z() - loc.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= best) {
                best = d;
                found = t.id();
            }
        }
        return Optional.ofNullable(found);
    }

    public Optional<Long> traderIdFromEntity(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Byte ignored = entity.getPersistentDataContainer().get(traderKey, PersistentDataType.BYTE);
        String raw = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "npc_trader_id_long"), PersistentDataType.STRING);
        if (raw != null) {
            try {
                return Optional.of(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        // also check long stored as string key we set
        return Optional.empty();
    }

    public Optional<Long> readTraderId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "trader_id"), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void tag(Entity entity, long id) {
        entity.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "trader_id"), PersistentDataType.STRING, Long.toString(id));
        entity.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void spawnOrRefresh(NpcTraderRepository.Trader t) throws SQLException {
        World world = Bukkit.getWorld(t.world());
        if (world == null) {
            return;
        }
        if (t.entityUuid() != null) {
            Entity existing = Bukkit.getEntity(t.entityUuid());
            if (existing instanceof Villager v && !existing.isDead()) {
                tag(v, t.id());
                v.customName(Component.text(t.name(), NamedTextColor.GOLD));
                v.setCustomNameVisible(true);
                return;
            }
        }
        Location loc = new Location(world, t.x(), t.y(), t.z(), t.yaw(), 0);
        Villager v = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setRemoveWhenFarAway(false);
        v.setProfession(Villager.Profession.NITWIT);
        v.customName(Component.text(t.name(), NamedTextColor.GOLD));
        v.setCustomNameVisible(true);
        tag(v, t.id());
        repo.setEntityUuid(t.id(), v.getUniqueId());
    }

    private void despawn(long id) throws SQLException {
        var opt = repo.get(id);
        if (opt.isEmpty() || opt.get().entityUuid() == null) {
            return;
        }
        Entity e = Bukkit.getEntity(opt.get().entityUuid());
        if (e != null) {
            e.remove();
        }
    }

    public void openTradeGui(Player player, long traderId) {
        try {
            var trader = repo.get(traderId);
            if (trader.isEmpty()) {
                player.sendMessage("§cTrader gone.");
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
            player.sendMessage("§cTrader error.");
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
        // find offer among all — cheap enough
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
            // player buys from NPC
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
            // player sells to NPC
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

    public NpcTraderRepository repo() {
        return repo;
    }

    public void clearClicks(Player player) {
        offerClicks.remove(player.getUniqueId());
    }
}
