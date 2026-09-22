package com.yapcore.yap420.market;

import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.plant.StrainId;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/** Handles YaP420 dealer chest clicks. */
public final class DealerListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemBridge items;
    private final DealerService dealer;
    private final PackService packer;
    private final DealerGui gui;

    public DealerListener(ItemBridge items, DealerService dealer, PackService packer, DealerGui gui) {
        this.items = items;
        this.dealer = dealer;
        this.packer = packer;
        this.gui = gui;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DealerGui.Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        boolean shift = event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT;

        if (slot == DealerGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == DealerGui.SLOT_SELL_ALL) {
            handleSellAll(player);
            gui.refresh(player, event.getInventory());
            return;
        }
        if (isPackSlot(slot)) {
            handlePack(player, clicked, shift);
            gui.refresh(player, event.getInventory());
            return;
        }

        String tag = trailingTag(clicked);
        if (tag != null && tag.startsWith("buy:")) {
            handleBuy(player, tag.substring(4), shift ? 8 : 1);
            gui.refresh(player, event.getInventory());
            return;
        }
        if (tag != null && tag.startsWith("id:")) {
            String id = tag.substring(3);
            int amount = shift ? Math.max(1, items.count(player.getInventory(), id)) : 1;
            handleSellOne(player, id, amount);
            gui.refresh(player, event.getInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DealerGui.Holder) {
            event.setCancelled(true);
        }
    }

    private void handleSellAll(Player player) {
        DealerService.TradeOutcome out = dealer.sellAll(player);
        MarketSettings.Messages msg = dealer.market().messages();
        switch (out.result()) {
            case DISABLED -> player.sendMessage(LEGACY.deserialize(msg.disabled()));
            case NO_ECONOMY -> player.sendMessage(LEGACY.deserialize(msg.noEconomy()));
            case NOTHING -> player.sendMessage(LEGACY.deserialize(msg.nothingToSell()));
            case OK -> player.sendMessage(LEGACY.deserialize(msg.soldAll()
                    .replace("{count}", String.valueOf(out.sales().size()))
                    .replace("{money}", dealer.economy().format(out.total()))));
            default -> {
            }
        }
    }

    private void handleSellOne(Player player, String id, int amount) {
        DealerService.TradeOutcome out = dealer.sellOne(player, id, amount);
        MarketSettings.Messages msg = dealer.market().messages();
        switch (out.result()) {
            case DISABLED -> player.sendMessage(LEGACY.deserialize(msg.disabled()));
            case NO_ECONOMY -> player.sendMessage(LEGACY.deserialize(msg.noEconomy()));
            case NOTHING -> player.sendMessage(LEGACY.deserialize(msg.nothingToSell()));
            case OK -> player.sendMessage(LEGACY.deserialize(msg.sold()
                    .replace("{amount}", String.valueOf(out.amount()))
                    .replace("{item}", id)
                    .replace("{money}", dealer.economy().format(out.total()))));
            default -> {
            }
        }
    }

    private void handleBuy(Player player, String id, int amount) {
        DealerService.TradeOutcome out = dealer.buy(player, id, amount);
        MarketSettings.Messages msg = dealer.market().messages();
        switch (out.result()) {
            case DISABLED -> player.sendMessage(LEGACY.deserialize(msg.disabled()));
            case NO_ECONOMY -> player.sendMessage(LEGACY.deserialize(msg.noEconomy()));
            case CANNOT_AFFORD -> player.sendMessage(LEGACY.deserialize(msg.cannotAfford()
                    .replace("{money}", dealer.economy().format(out.total()))
                    .replace("{balance}", dealer.economy().format(dealer.economy().balance(player)))));
            case MISSING_CATALOG, INVENTORY_FULL ->
                    player.sendMessage(LEGACY.deserialize(msg.inventoryFull()));
            case OK -> player.sendMessage(LEGACY.deserialize(msg.bought()
                    .replace("{amount}", String.valueOf(out.amount()))
                    .replace("{item}", id)
                    .replace("{money}", dealer.economy().format(out.total()))));
            default -> {
            }
        }
    }

    private void handlePack(Player player, ItemStack clicked, boolean shift) {
        String tag = trailingTag(clicked);
        if (tag == null || !tag.startsWith("pack:")) {
            return;
        }
        String[] parts = tag.substring(5).split(":");
        if (parts.length < 2) {
            return;
        }
        PackUnit unit = PackUnit.parse(parts[0]).orElse(null);
        StrainId strain = StrainId.parse(parts[1]).orElse(null);
        if (unit == null || strain == null) {
            return;
        }
        int max = shift ? 64 : 1;
        PackService.Outcome out = packer.pack(player, unit, strain, max);
        tellPack(player, out, true);
    }

    public void tellPack(Player player, PackService.Outcome out, boolean packing) {
        MarketSettings.Messages msg = dealer.market().messages();
        switch (out.result()) {
            case DISABLED -> player.sendMessage(LEGACY.deserialize(msg.disabled()));
            case MISSING_CATALOG -> player.sendMessage(LEGACY.deserialize("&cYaPItems catalog missing pack item."));
            case NEED_ITEMS -> player.sendMessage(LEGACY.deserialize(msg.needItems()
                    .replace("{need}", String.valueOf(out.need()))
                    .replace("{have}", String.valueOf(out.have()))
                    .replace("{item}", out.needId() == null ? "?" : out.needId())));
            case OK -> {
                String template = packing ? msg.packed() : msg.unpacked();
                player.sendMessage(LEGACY.deserialize(template
                        .replace("{amount}", String.valueOf(out.produced()))
                        .replace("{item}", out.productId() == null ? "?" : out.productId())));
            }
        }
    }

    private static boolean isPackSlot(int slot) {
        return slot == DealerGui.SLOT_PACK_GRAM_S
                || slot == DealerGui.SLOT_PACK_OZ_S
                || slot == DealerGui.SLOT_PACK_BRICK_S
                || slot == DealerGui.SLOT_PACK_GRAM_I
                || slot == DealerGui.SLOT_PACK_OZ_I
                || slot == DealerGui.SLOT_PACK_BRICK_I;
    }

    private static String trailingTag(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        String last = LEGACY.serialize(lore.get(lore.size() - 1)).toLowerCase(Locale.ROOT);
        // strip color codes from legacy
        last = last.replaceAll("&[0-9a-fk-or]", "");
        if (last.startsWith("id:") || last.startsWith("buy:") || last.startsWith("pack:")) {
            return last;
        }
        return null;
    }
}
