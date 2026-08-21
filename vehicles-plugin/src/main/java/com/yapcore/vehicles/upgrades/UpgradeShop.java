package com.yapcore.vehicles.upgrades;

import com.yapcore.vehicles.api.VehicleType;
import com.yapcore.vehicles.api.VehicleUpgrade;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import com.yapcore.vehicles.engine.VehiclesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Map;

/**
 * Combined dealership: vehicles (top rows) + upgrade parts (bottom).
 */
public final class UpgradeShop implements Listener {

    public static final String TITLE = "YaP Garage Shop";

    private final UpgradeService upgrades;
    private final VehicleServiceImpl vehicles;
    private final VehiclesConfig config;

    public UpgradeShop(UpgradeService upgrades, VehicleServiceImpl vehicles, VehiclesConfig config) {
        this.upgrades = upgrades;
        this.vehicles = vehicles;
        this.config = config;
    }

    public void open(Player player) {
        if (!config.upgradesShopEnabled()) {
            player.sendMessage("Vehicle shop is disabled.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TITLE));

        for (DealershipListing listing : DealershipCatalog.listings()) {
            var typeOpt = vehicles.getType(listing.typeId());
            if (typeOpt.isEmpty() || listing.shopSlot() < 0 || listing.shopSlot() >= 18) {
                continue;
            }
            VehicleType type = typeOpt.get();
            ItemStack display = vehicles.createSpawnItem(type);
            display.editMeta(meta -> {
                var lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<Component>();
                lore.add(Component.empty());
                lore.add(Component.text("VEHICLE")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Price:")
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                for (ItemStack price : listing.price()) {
                    lore.add(Component.text("  " + price.getAmount() + "× " + price.getType().name())
                            .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.text("Click to buy spawn token")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                meta.getPersistentDataContainer()
                        .set(upgrades.dealershipKey(), PersistentDataType.STRING, listing.typeId());
            });
            inv.setItem(listing.shopSlot(), display);
        }

        for (VehicleUpgrade u : upgrades.getAll()) {
            if (u.shopPrice().isEmpty() || u.shopSlot() < 18 || u.shopSlot() >= 54) {
                continue;
            }
            ItemStack display = upgrades.createItem(u);
            display.editMeta(meta -> {
                var lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<Component>();
                lore.add(Component.empty());
                lore.add(Component.text("PART · " + u.slot().name())
                        .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Price:")
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                for (ItemStack price : u.shopPrice()) {
                    lore.add(Component.text("  " + price.getAmount() + "× " + price.getType().name())
                            .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                }
                lore.add(Component.text("Click to buy")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            inv.setItem(u.shopSlot(), display);
        }

        inv.setItem(17, filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Vehicles ↑"));
        inv.setItem(18, filler(Material.ORANGE_STAINED_GLASS_PANE, "Parts ↓"));

        player.openInventory(inv);
    }

    private static ItemStack filler(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> meta.displayName(Component.text(name)
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!TITLE.equals(title)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }

        String typeId = clicked.getItemMeta().getPersistentDataContainer()
                .get(upgrades.dealershipKey(), PersistentDataType.STRING);
        if (typeId != null) {
            buyVehicle(player, typeId);
            return;
        }

        var idOpt = upgrades.itemUpgradeId(clicked);
        if (idOpt.isEmpty()) {
            return;
        }
        VehicleUpgrade upgrade = upgrades.get(idOpt.get()).orElse(null);
        if (upgrade == null || upgrade.shopPrice().isEmpty()) {
            return;
        }
        if (!upgrades.takePrice(player, upgrade.shopPrice())) {
            player.sendMessage("Not enough materials for " + upgrade.displayName());
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(upgrades.createItem(upgrade));
        leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage("Purchased " + upgrade.displayName());
    }

    private void buyVehicle(Player player, String typeId) {
        DealershipListing listing = DealershipCatalog.byType(typeId);
        if (listing == null) {
            player.sendMessage("Unknown vehicle listing.");
            return;
        }
        var type = vehicles.getType(typeId);
        if (type.isEmpty()) {
            player.sendMessage("Vehicle type not available.");
            return;
        }
        if (!upgrades.takePrice(player, listing.price())) {
            player.sendMessage("Not enough materials for " + type.get().displayName());
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(vehicles.createSpawnItem(type.get()));
        leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage("Purchased " + type.get().displayName() + " spawn token");
    }
}
