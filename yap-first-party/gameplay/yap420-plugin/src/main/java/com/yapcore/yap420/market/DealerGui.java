package com.yapcore.yap420.market;

import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.plant.StrainId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chest GUI for sell / buy / pack. */
public final class DealerGui {

    public static final String TITLE = "Blazed Boutique";

    public static final int SLOT_SELL_ALL = 49;
    public static final int SLOT_INFO = 4;
    public static final int SLOT_CLOSE = 53;

    // Pack row
    public static final int SLOT_PACK_GRAM_S = 37;
    public static final int SLOT_PACK_OZ_S = 38;
    public static final int SLOT_PACK_BRICK_S = 39;
    public static final int SLOT_PACK_GRAM_I = 41;
    public static final int SLOT_PACK_OZ_I = 42;
    public static final int SLOT_PACK_BRICK_I = 43;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int[] SELL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };
    private static final int[] BUY_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private final ItemBridge items;
    private final DealerService dealer;
    private final PackService packer;

    public DealerGui(ItemBridge items, DealerService dealer, PackService packer) {
        this.items = items;
        this.dealer = dealer;
        this.packer = packer;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.GREEN));
        holder.bind(inv);
        fill(player, inv);
        player.openInventory(inv);
    }

    public void refresh(Player player, Inventory inv) {
        if (!(inv.getHolder() instanceof Holder)) {
            return;
        }
        fill(player, inv);
    }

    private void fill(Player player, Inventory inv) {
        inv.clear();
        MarketSettings market = dealer.market();
        PackMath math = market.pack();
        String bal = dealer.economy().available()
                ? dealer.economy().format(dealer.economy().balance(player))
                : "n/a";

        inv.setItem(SLOT_INFO, glass(Material.LIME_STAINED_GLASS_PANE, "&aBlazed Boutique",
                "&7Balance: &f" + bal,
                "&7Click sell icons · shift = all of that item",
                "&7Pack: " + math.gramsPerOunce() + "g = 1oz · "
                        + math.ouncesPerBrick() + "oz = 1 pound",
                "&aPacking pays &7— ounces/pounds sell above loose grams"));

        Map<String, Integer> sellables = dealer.countSellables(player.getInventory());
        int si = 0;
        for (Map.Entry<String, Integer> e : sellables.entrySet()) {
            if (si >= SELL_SLOTS.length) {
                break;
            }
            double unit = market.sellPrice(e.getKey());
            ItemStack icon = items.create(e.getKey(), Math.min(64, e.getValue())).orElse(null);
            if (icon == null) {
                continue;
            }
            lore(icon,
                    "&7You have &f" + e.getValue(),
                    "&7Sell &f1 &7for &a" + dealer.economy().format(unit),
                    "&7Shift-click: sell all (&a"
                            + dealer.economy().format(unit * e.getValue()) + "&7)",
                    "&8id:" + e.getKey());
            inv.setItem(SELL_SLOTS[si++], icon);
        }

        if (market.buyEnabled()) {
            int bi = 0;
            for (Map.Entry<String, Double> e : market.buyPrices().entrySet()) {
                if (bi >= BUY_SLOTS.length) {
                    break;
                }
                ItemStack icon = items.create(e.getKey(), 1).orElse(null);
                if (icon == null) {
                    continue;
                }
                lore(icon,
                        "&eBuy &f1 &efor &c" + dealer.economy().format(e.getValue()),
                        "&7Shift-click: buy 8",
                        "&8buy:" + e.getKey());
                inv.setItem(BUY_SLOTS[bi++], icon);
            }
        }

        inv.setItem(SLOT_PACK_GRAM_S, packButton(StrainId.SATIVA, PackUnit.GRAM, math, market));
        inv.setItem(SLOT_PACK_OZ_S, packButton(StrainId.SATIVA, PackUnit.OUNCE, math, market));
        inv.setItem(SLOT_PACK_BRICK_S, packButton(StrainId.SATIVA, PackUnit.BRICK, math, market));
        inv.setItem(SLOT_PACK_GRAM_I, packButton(StrainId.INDICA, PackUnit.GRAM, math, market));
        inv.setItem(SLOT_PACK_OZ_I, packButton(StrainId.INDICA, PackUnit.OUNCE, math, market));
        inv.setItem(SLOT_PACK_BRICK_I, packButton(StrainId.INDICA, PackUnit.BRICK, math, market));

        inv.setItem(SLOT_SELL_ALL, glass(Material.GOLD_INGOT, "&6Sell everything",
                "&7Sell all priced YaP420 items",
                "&7in your inventory"));
        inv.setItem(SLOT_CLOSE, glass(Material.BARRIER, "&cClose"));
    }

    private ItemStack packButton(StrainId strain, PackUnit unit, PackMath math, MarketSettings market) {
        String id = Yap420ItemIds.packId(unit, strain);
        ItemStack icon = items.create(id, 1).orElse(glass(Material.CHEST, "&ePack " + unit.id()));
        double sell = market.sellPrice(id);
        String need = switch (unit) {
            case GRAM -> "1 cured bud → 1g bag";
            case OUNCE -> math.gramsPerOunce() + " cured/g → 1 oz";
            case BRICK -> math.ouncesPerBrick() + " oz → 1 pound";
        };
        List<String> lines = new ArrayList<>();
        lines.add("&e" + strain.id() + " · pack " + unit.id());
        lines.add("&7" + need);
        if (sell > 0) {
            lines.add("&7Sells for &a" + dealer.economy().format(sell));
            String premium = packagingPremiumHint(strain, unit, math, market, sell);
            if (premium != null) {
                lines.add(premium);
            }
        }
        lines.add("&7Click: pack 1 · Shift: pack as many as possible");
        lines.add("&7Q / drop-key style: use /yap420 unpack");
        lines.add("&8pack:" + unit.id() + ":" + strain.id());
        lore(icon, lines.toArray(String[]::new));
        return icon;
    }

    /** Lore showing why packing beats selling the same weight loose. */
    private String packagingPremiumHint(
            StrainId strain, PackUnit unit, PackMath math, MarketSettings market, double sell
    ) {
        return switch (unit) {
            case GRAM -> null;
            case OUNCE -> {
                double loose = market.sellPrice(Yap420ItemIds.gram(strain)) * math.gramsPerOunce();
                if (loose <= 0 || sell <= loose) {
                    yield null;
                }
                yield "&a+" + dealer.economy().format(sell - loose)
                        + " &7vs selling those grams loose";
            }
            case BRICK -> {
                double loose = market.sellPrice(Yap420ItemIds.ounce(strain)) * math.ouncesPerBrick();
                if (loose <= 0 || sell <= loose) {
                    yield null;
                }
                yield "&a+" + dealer.economy().format(sell - loose)
                        + " &7vs selling those ounces loose";
            }
        };
    }

    private static ItemStack glass(Material mat, String name, String... loreLines) {
        ItemStack stack = new ItemStack(mat);
        lore(stack, loreLines);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void lore(ItemStack stack, String... lines) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(LEGACY.deserialize(line));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
