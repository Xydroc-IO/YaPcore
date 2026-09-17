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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    /** Merged BUY+SELL lines so the catalog shows one icon per item. */
    private record ShopLine(
            Material material,
            int unitAmount,
            String metaJson,
            Long buyOfferId,
            double buyPrice,
            int buyStock,
            Long sellOfferId,
            double sellPrice
    ) {
        boolean canBuy() {
            return buyOfferId != null && buyPrice >= 0;
        }

        boolean canSell() {
            return sellOfferId != null && sellPrice > 0 && !OfferItemMeta.hasMeta(metaJson);
        }
    }

    private static final int PAGE_CAPACITY = 28;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    private static final int QTY_MINUS_10 = 11;
    private static final int QTY_MINUS_1 = 12;
    private static final int QTY_ITEM = 13;
    private static final int QTY_PLUS_1 = 14;
    private static final int QTY_PLUS_10 = 15;
    private static final int QTY_MAX = 20;
    private static final int QTY_CONFIRM = 22;
    private static final int QTY_BACK = 24;

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final NpcTraderRepository repo;
    private final BalanceStore balances;
    private final Map<UUID, Map<Integer, ShopLine>> lineClicks = new HashMap<>();
    private final Map<UUID, QtyGuiCtx> qtySessions = new HashMap<>();

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
        lineClicks.clear();
        qtySessions.clear();
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
        openTradeGui(player, traderId, 0);
    }

    public void openTradeGui(Player player, long traderId, int page) {
        qtySessions.remove(player.getUniqueId());
        try {
            var trader = repo.get(traderId);
            if (trader.isEmpty()) {
                player.sendMessage("§cShop gone.");
                return;
            }
            List<ShopLine> lines = mergeOffers(repo.offers(traderId));
            int pages = Math.max(1, (lines.size() + PAGE_CAPACITY - 1) / PAGE_CAPACITY);
            int safePage = Math.max(0, Math.min(page, pages - 1));
            TraderGuiCtx ctx = new TraderGuiCtx(traderId, safePage);
            YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.NPC_TRADER, ctx);
            String title = trader.get().name();
            if (pages > 1) {
                title = title + " (" + (safePage + 1) + "/" + pages + ")";
            }
            Inventory inv = Bukkit.createInventory(holder, 54,
                    Component.text(title, NamedTextColor.GOLD));
            holder.bind(inv);
            YapMenuHolder.fillBorder(inv);
            Map<Integer, ShopLine> meta = new HashMap<>();
            int start = safePage * PAGE_CAPACITY;
            int end = Math.min(lines.size(), start + PAGE_CAPACITY);
            List<Integer> contentSlots = contentSlots();
            int slotIdx = 0;
            for (int i = start; i < end && slotIdx < contentSlots.size(); i++, slotIdx++) {
                ShopLine line = lines.get(i);
                int slot = contentSlots.get(slotIdx);
                ItemStack icon = OfferItemMeta.buildStack(line.material(),
                        Math.min(64, Math.max(1, line.unitAmount())), line.metaJson());
                String label = prettyName(line.material());
                if (OfferItemMeta.hasMeta(line.metaJson())) {
                    label = "✦ " + label;
                }
                String finalLabel = label;
                icon.editMeta(m -> {
                    m.displayName(Component.text(finalLabel, NamedTextColor.YELLOW));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Per deal: " + line.unitAmount() + "x", NamedTextColor.GRAY));
                    if (line.canBuy()) {
                        String stock = line.buyStock() < 0 ? "∞" : String.valueOf(line.buyStock());
                        lore.add(Component.text("Buy  $" + money(line.buyPrice())
                                + "  (stock " + stock + ")", NamedTextColor.GREEN));
                    } else {
                        lore.add(Component.text("Buy  unavailable", NamedTextColor.DARK_GRAY));
                    }
                    if (line.canSell()) {
                        lore.add(Component.text("Sell $" + money(line.sellPrice()), NamedTextColor.AQUA));
                    } else {
                        lore.add(Component.text("Sell unavailable", NamedTextColor.DARK_GRAY));
                    }
                    lore.add(Component.empty());
                    lore.add(Component.text("Left-click  → Buy", NamedTextColor.WHITE));
                    lore.add(Component.text("Right-click → Sell", NamedTextColor.WHITE));
                    m.lore(lore);
                });
                inv.setItem(slot, icon);
                meta.put(slot, line);
            }
            if (safePage > 0) {
                inv.setItem(SLOT_PREV, YapMenuHolder.icon(Material.ARROW, NamedTextColor.YELLOW, "Previous"));
            }
            inv.setItem(SLOT_CLOSE, YapMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Close"));
            if (safePage < pages - 1) {
                inv.setItem(SLOT_NEXT, YapMenuHolder.icon(Material.ARROW, NamedTextColor.YELLOW, "Next"));
            }
            lineClicks.put(player.getUniqueId(), meta);
            player.openInventory(inv);
        } catch (SQLException e) {
            player.sendMessage("§cShop error.");
            plugin.getLogger().log(Level.WARNING, "openTradeGui", e);
        }
    }

    public boolean handleTradeClick(Player player, long traderId, int page, int slot,
                                    String itemName, ClickType click) {
        if ("Close".equals(itemName)) {
            player.closeInventory();
            return true;
        }
        if ("Previous".equals(itemName)) {
            openTradeGui(player, traderId, Math.max(0, page - 1));
            return true;
        }
        if ("Next".equals(itemName)) {
            openTradeGui(player, traderId, page + 1);
            return true;
        }
        ShopLine line = lineClicks.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
        if (line == null) {
            return true;
        }
        boolean buying = click.isLeftClick();
        if (click.isRightClick()) {
            buying = false;
        }
        if (buying) {
            if (!line.canBuy()) {
                player.sendMessage("§cThis item can't be bought here.");
                return true;
            }
            openQtyGui(player, traderId, page, line.buyOfferId(), true, 1);
        } else {
            if (!line.canSell()) {
                player.sendMessage("§cThis item can't be sold here.");
                return true;
            }
            openQtyGui(player, traderId, page, line.sellOfferId(), false, 1);
        }
        return true;
    }

    public boolean handleQtyClick(Player player, QtyGuiCtx ctx, int slot, String itemName) {
        if ("Back".equals(itemName) || "Cancel".equals(itemName)) {
            openTradeGui(player, ctx.traderId(), ctx.returnPage());
            return true;
        }
        int max = maxQty(player, ctx.offerId(), ctx.buying());
        int qty = ctx.qty();
        if (slot == QTY_MINUS_10 || itemName.startsWith("-10")) {
            qty = Math.max(1, qty - 10);
        } else if (slot == QTY_MINUS_1 || itemName.startsWith("-1")) {
            qty = Math.max(1, qty - 1);
        } else if (slot == QTY_PLUS_1 || itemName.startsWith("+1")) {
            qty = Math.min(max, qty + 1);
        } else if (slot == QTY_PLUS_10 || itemName.startsWith("+10")) {
            qty = Math.min(max, qty + 10);
        } else if (slot == QTY_MAX || "Max".equals(itemName)) {
            qty = Math.max(1, max);
        } else if (slot == QTY_CONFIRM || itemName.startsWith("Confirm")) {
            try {
                executeTrade(player, ctx.offerId(), qty);
                openTradeGui(player, ctx.traderId(), ctx.returnPage());
            } catch (Exception e) {
                player.sendMessage("§c" + e.getMessage());
                openQtyGui(player, ctx.traderId(), ctx.returnPage(), ctx.offerId(), ctx.buying(), qty);
            }
            return true;
        } else {
            return true;
        }
        openQtyGui(player, ctx.traderId(), ctx.returnPage(), ctx.offerId(), ctx.buying(), qty);
        return true;
    }

    private void openQtyGui(Player player, long traderId, int returnPage, long offerId,
                            boolean buying, int requestedQty) {
        try {
            var opt = repo.getOffer(offerId);
            if (opt.isEmpty()) {
                player.sendMessage("§cOffer gone.");
                openTradeGui(player, traderId, returnPage);
                return;
            }
            NpcTraderRepository.Offer offer = opt.get();
            int max = maxQty(player, offer, buying);
            if (max < 1) {
                player.sendMessage(buying
                        ? "§cCan't buy — check balance, stock, or inventory space."
                        : "§cYou don't have enough of that item to sell.");
                openTradeGui(player, traderId, returnPage);
                return;
            }
            int qty = Math.max(1, Math.min(requestedQty, max));
            QtyGuiCtx ctx = new QtyGuiCtx(traderId, returnPage, offerId, buying, qty);
            qtySessions.put(player.getUniqueId(), ctx);
            YapMenuHolder holder = new YapMenuHolder(YapMenuHolder.Kind.NPC_TRADER_QTY, ctx);
            String title = (buying ? "Buy " : "Sell ") + prettyName(offer.material());
            Inventory inv = Bukkit.createInventory(holder, 27,
                    Component.text(title, buying ? NamedTextColor.GREEN : NamedTextColor.AQUA));
            holder.bind(inv);
            YapMenuHolder.fillBorder(inv);

            int totalItems = offer.amount() * qty;
            double unitPrice = offer.price();
            double total = unitPrice * qty;

            ItemStack preview = OfferItemMeta.buildStack(offer.material(),
                    Math.min(64, Math.max(1, totalItems)), offer.metaJson());
            preview.editMeta(m -> {
                m.displayName(Component.text(prettyName(offer.material()), NamedTextColor.YELLOW));
                m.lore(List.of(
                        Component.text("Deals: " + qty + " × " + offer.amount()
                                + " = " + totalItems + " items", NamedTextColor.GRAY),
                        Component.text("Each deal: $" + money(unitPrice), NamedTextColor.YELLOW),
                        Component.text((buying ? "You pay: $" : "You get: $") + money(total),
                                buying ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                        Component.text("Max deals now: " + max, NamedTextColor.DARK_GRAY)));
            });
            inv.setItem(QTY_ITEM, preview);
            inv.setItem(QTY_MINUS_10, YapMenuHolder.icon(Material.RED_CONCRETE, NamedTextColor.RED, "-10"));
            inv.setItem(QTY_MINUS_1, YapMenuHolder.icon(Material.ORANGE_CONCRETE, NamedTextColor.GOLD, "-1"));
            inv.setItem(QTY_PLUS_1, YapMenuHolder.icon(Material.LIME_CONCRETE, NamedTextColor.GREEN, "+1"));
            inv.setItem(QTY_PLUS_10, YapMenuHolder.icon(Material.GREEN_CONCRETE, NamedTextColor.DARK_GREEN, "+10"));
            inv.setItem(QTY_MAX, YapMenuHolder.icon(Material.GOLD_INGOT, NamedTextColor.YELLOW, "Max"));
            inv.setItem(QTY_CONFIRM, YapMenuHolder.icon(
                    buying ? Material.EMERALD : Material.GOLD_NUGGET,
                    buying ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                    "Confirm " + (buying ? "Buy" : "Sell") + " · $" + money(total),
                    buying ? "Pay $" + money(total) + " for " + totalItems + "x"
                            : "Receive $" + money(total) + " for " + totalItems + "x"));
            inv.setItem(QTY_BACK, YapMenuHolder.icon(Material.ARROW, NamedTextColor.GRAY, "Back"));
            player.openInventory(inv);
        } catch (SQLException e) {
            player.sendMessage("§cShop error.");
            plugin.getLogger().log(Level.WARNING, "openQtyGui", e);
        }
    }

    private int maxQty(Player player, long offerId, boolean buying) {
        try {
            var opt = repo.getOffer(offerId);
            if (opt.isEmpty()) {
                return 0;
            }
            return maxQty(player, opt.get(), buying);
        } catch (SQLException e) {
            return 0;
        }
    }

    private int maxQty(Player player, NpcTraderRepository.Offer offer, boolean buying) {
        if (buying) {
            if (offer.stock() == 0) {
                return 0;
            }
            int byStock = offer.stock() < 0 ? 64 : offer.stock();
            double bal = balances.getBalance(player.getUniqueId());
            int byMoney = offer.price() <= 0 ? 64 : (int) Math.floor(bal / offer.price());
            int bySpace = emptySlots(player) * Math.max(1, 64 / Math.max(1, offer.amount()));
            return Math.max(0, Math.min(64, Math.min(byStock, Math.min(byMoney, Math.max(1, bySpace)))));
        }
        int have = countMaterial(player, offer.material());
        return Math.max(0, Math.min(64, have / Math.max(1, offer.amount())));
    }

    private void executeTrade(Player player, long offerId, int qty) throws Exception {
        if (qty < 1) {
            throw new IllegalStateException("Invalid quantity");
        }
        var opt = repo.getOffer(offerId);
        if (opt.isEmpty()) {
            throw new IllegalStateException("Offer gone");
        }
        NpcTraderRepository.Offer offer = opt.get();
        int max = maxQty(player, offer, offer.mode().equalsIgnoreCase("BUY"));
        if (qty > max) {
            throw new IllegalStateException("Max you can trade right now is " + max);
        }
        int totalItems = offer.amount() * qty;
        double totalPrice = offer.price() * qty;

        if (offer.mode().equalsIgnoreCase("BUY")) {
            if (offer.stock() == 0 || (offer.stock() > 0 && offer.stock() < qty)) {
                throw new IllegalStateException("Out of stock");
            }
            double bal = balances.getBalance(player.getUniqueId());
            if (bal < totalPrice) {
                throw new IllegalStateException("Insufficient funds (need $" + money(totalPrice) + ")");
            }
            balances.setBalance(player.getUniqueId(), bal - totalPrice);
            int remaining = totalItems;
            while (remaining > 0) {
                int chunk = Math.min(64, remaining);
                ItemStack stack = OfferItemMeta.buildStack(offer.material(), chunk, offer.metaJson());
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(s ->
                            player.getWorld().dropItemNaturally(player.getLocation(), s));
                }
                remaining -= chunk;
            }
            if (offer.stock() > 0) {
                repo.setStock(offer.id(), offer.stock() - qty);
            }
            player.sendMessage("§aBought §f" + totalItems + "x " + prettyName(offer.material())
                    + " §afor §f$" + money(totalPrice)
                    + " §7(" + qty + "×$" + money(offer.price()) + ")");
        } else {
            if (OfferItemMeta.hasMeta(offer.metaJson())) {
                throw new IllegalStateException("This offer can't be sold back");
            }
            ItemStack need = new ItemStack(offer.material(), totalItems);
            if (!player.getInventory().containsAtLeast(need, totalItems)) {
                throw new IllegalStateException("You don't have " + totalItems + "x "
                        + prettyName(offer.material()));
            }
            player.getInventory().removeItem(need);
            balances.setBalance(player.getUniqueId(),
                    balances.getBalance(player.getUniqueId()) + totalPrice);
            if (offer.stock() >= 0) {
                repo.setStock(offer.id(), offer.stock() + qty);
            }
            player.sendMessage("§aSold §f" + totalItems + "x " + prettyName(offer.material())
                    + " §afor §f$" + money(totalPrice)
                    + " §7(" + qty + "×$" + money(offer.price()) + ")");
        }
    }

    public void clearClicks(Player player) {
        lineClicks.remove(player.getUniqueId());
        qtySessions.remove(player.getUniqueId());
    }

    private static List<ShopLine> mergeOffers(List<NpcTraderRepository.Offer> offers) {
        Map<String, ShopLine> merged = new LinkedHashMap<>();
        for (NpcTraderRepository.Offer o : offers) {
            boolean buy = o.mode().equalsIgnoreCase("BUY");
            String metaKey = buy ? (o.metaJson() == null ? "" : o.metaJson()) : "";
            String key = o.material().name() + "|" + o.amount() + "|" + metaKey;
            ShopLine cur = merged.get(key);
            if (cur == null && !buy) {
                // SELL-only: key without meta so it can pair with a plain BUY
                key = o.material().name() + "|" + o.amount() + "|";
                cur = merged.get(key);
            }
            if (cur == null) {
                if (buy) {
                    merged.put(key, new ShopLine(o.material(), o.amount(), o.metaJson(),
                            o.id(), o.price(), o.stock(), null, 0));
                } else {
                    merged.put(key, new ShopLine(o.material(), o.amount(), null,
                            null, 0, -1, o.id(), o.price()));
                }
                continue;
            }
            if (buy) {
                merged.put(key, new ShopLine(cur.material(), cur.unitAmount(),
                        o.metaJson() != null ? o.metaJson() : cur.metaJson(),
                        o.id(), o.price(), o.stock(), cur.sellOfferId(), cur.sellPrice()));
            } else {
                merged.put(key, new ShopLine(cur.material(), cur.unitAmount(), cur.metaJson(),
                        cur.buyOfferId(), cur.buyPrice(), cur.buyStock(), o.id(), o.price()));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static int countMaterial(Player player, Material mat) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private static int emptySlots(Player player) {
        int n = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                n++;
            }
        }
        return n;
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>(PAGE_CAPACITY);
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private static String prettyName(Material mat) {
        String n = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (n.isEmpty()) {
            return mat.name();
        }
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
