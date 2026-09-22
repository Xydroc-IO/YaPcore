package com.yapcore.yap420.press;

import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.market.PackMath;
import com.yapcore.yap420.market.PackService;
import com.yapcore.yap420.market.PackUnit;
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

/** Chest GUI on a packaging press — pack / unpack grams, ounces, pounds. */
public final class PressGui {

    public static final String TITLE = "Packaging Press";
    public static final int SLOT_INFO = 4;
    public static final int SLOT_CLOSE = 49;

    public static final int SLOT_BAG_S = 19;
    public static final int SLOT_OZ_S = 20;
    public static final int SLOT_BRICK_S = 21;
    public static final int SLOT_BAG_I = 23;
    public static final int SLOT_OZ_I = 24;
    public static final int SLOT_BRICK_I = 25;

    public static final int SLOT_UNBAG_S = 28;
    public static final int SLOT_UNOZ_S = 29;
    public static final int SLOT_UNBRICK_S = 30;
    public static final int SLOT_UNBAG_I = 32;
    public static final int SLOT_UNOZ_I = 33;
    public static final int SLOT_UNBRICK_I = 34;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemBridge items;
    private final PackService packer;

    public PressGui(ItemBridge items, PackService packer) {
        this.items = items;
        this.packer = packer;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(TITLE, NamedTextColor.GOLD));
        holder.bind(inv);
        fill(player, inv);
        player.openInventory(inv);
    }

    public void refresh(Player player, Inventory inv) {
        if (inv.getHolder() instanceof Holder) {
            fill(player, inv);
        }
    }

    private void fill(Player player, Inventory inv) {
        inv.clear();
        PackMath math = packer.market().pack();
        inv.setItem(SLOT_INFO, pane(Material.ANVIL, "&6Packaging Press",
                "&7Uses items in your inventory",
                "&7" + math.gramsPerOunce() + "g → 1 ounce",
                "&7" + math.ouncesPerBrick() + " ounces → 1 pound",
                "&eClick &7pack · &eShift &7= pack as many as you can",
                "&7Bottom row unpacks one step"));

        inv.setItem(SLOT_BAG_S, actionIcon(StrainId.SATIVA, PackUnit.GRAM, false, math));
        inv.setItem(SLOT_OZ_S, actionIcon(StrainId.SATIVA, PackUnit.OUNCE, false, math));
        inv.setItem(SLOT_BRICK_S, actionIcon(StrainId.SATIVA, PackUnit.BRICK, false, math));
        inv.setItem(SLOT_BAG_I, actionIcon(StrainId.INDICA, PackUnit.GRAM, false, math));
        inv.setItem(SLOT_OZ_I, actionIcon(StrainId.INDICA, PackUnit.OUNCE, false, math));
        inv.setItem(SLOT_BRICK_I, actionIcon(StrainId.INDICA, PackUnit.BRICK, false, math));

        inv.setItem(SLOT_UNBAG_S, actionIcon(StrainId.SATIVA, PackUnit.GRAM, true, math));
        inv.setItem(SLOT_UNOZ_S, actionIcon(StrainId.SATIVA, PackUnit.OUNCE, true, math));
        inv.setItem(SLOT_UNBRICK_S, actionIcon(StrainId.SATIVA, PackUnit.BRICK, true, math));
        inv.setItem(SLOT_UNBAG_I, actionIcon(StrainId.INDICA, PackUnit.GRAM, true, math));
        inv.setItem(SLOT_UNOZ_I, actionIcon(StrainId.INDICA, PackUnit.OUNCE, true, math));
        inv.setItem(SLOT_UNBRICK_I, actionIcon(StrainId.INDICA, PackUnit.BRICK, true, math));

        inv.setItem(SLOT_CLOSE, pane(Material.BARRIER, "&cClose"));
    }

    private ItemStack actionIcon(StrainId strain, PackUnit unit, boolean unpack, PackMath math) {
        String id = Yap420ItemIds.packId(unit, strain);
        ItemStack icon = items.create(id, 1).orElseGet(() -> new ItemStack(Material.HAY_BLOCK));
        String need = switch (unit) {
            case GRAM -> unpack ? "1g bag → cured bud" : "1 cured → 1g bag";
            case OUNCE -> unpack
                    ? "1 ounce → " + math.gramsPerOunce() + "g"
                    : math.gramsPerOunce() + " cured/g → 1 ounce";
            case BRICK -> unpack
                    ? "1 pound → " + math.ouncesPerBrick() + " ounces"
                    : math.ouncesPerBrick() + " ounces → 1 pound";
        };
        String verb = unpack ? "Unpack" : "Press / pack";
        lore(icon,
                "&e" + verb + " &f" + strain.id() + " " + label(unit),
                "&7" + need,
                "&7Click: 1 · Shift: max",
                "&8" + (unpack ? "unpack:" : "pack:") + unit.id() + ":" + strain.id());
        return icon;
    }

    private static String label(PackUnit unit) {
        return switch (unit) {
            case GRAM -> "gram";
            case OUNCE -> "ounce";
            case BRICK -> "pound";
        };
    }

    private static ItemStack pane(Material mat, String name, String... loreLines) {
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

    public static boolean isPackSlot(int slot) {
        return slot == SLOT_BAG_S || slot == SLOT_OZ_S || slot == SLOT_BRICK_S
                || slot == SLOT_BAG_I || slot == SLOT_OZ_I || slot == SLOT_BRICK_I;
    }

    public static boolean isUnpackSlot(int slot) {
        return slot == SLOT_UNBAG_S || slot == SLOT_UNOZ_S || slot == SLOT_UNBRICK_S
                || slot == SLOT_UNBAG_I || slot == SLOT_UNOZ_I || slot == SLOT_UNBRICK_I;
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
