package com.yapcore.yap420.press;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.market.PackService;
import com.yapcore.yap420.market.PackUnit;
import com.yapcore.yap420.plant.StrainId;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Place / use / break packaging presses + press GUI clicks. */
public final class PressListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final ItemBridge items;
    private final PressRegistry presses;
    private final PressDisplayService displays;
    private final PressStore store;
    private final PackService packer;
    private final PressGui gui;

    public PressListener(
            Yap420Plugin plugin,
            Yap420Config config,
            ItemBridge items,
            PressRegistry presses,
            PressDisplayService displays,
            PressStore store,
            PackService packer,
            PressGui gui
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.presses = presses;
        this.displays = displays;
        this.store = store;
        this.packer = packer;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (items.idOf(hand).filter(Yap420ItemIds.PACKAGING_PRESS::equals).isEmpty()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        event.setCancelled(true);
        Block placeAt = clicked.getRelative(event.getBlockFace() == null ? BlockFace.UP : event.getBlockFace());
        if (!placeAt.getType().isAir() && placeAt.getType() != Material.BARRIER) {
            player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
            return;
        }
        if (presses.contains(placeAt.getWorld().getName(), placeAt.getX(), placeAt.getY(), placeAt.getZ())) {
            player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
            return;
        }
        YapSched.region(plugin, placeAt.getLocation(), () -> {
            if (!placeAt.getType().isAir() && placeAt.getType() != Material.BARRIER) {
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack again = player.getInventory().getItemInMainHand();
                if (items.idOf(again).filter(Yap420ItemIds.PACKAGING_PRESS::equals).isEmpty()) {
                    return;
                }
                again.setAmount(again.getAmount() - 1);
            }
            placeAt.setType(Material.BARRIER, false);
            PressState press = new PressState(
                    placeAt.getWorld().getName(),
                    placeAt.getX(),
                    placeAt.getY(),
                    placeAt.getZ(),
                    null);
            PressState spawned = displays.spawn(press);
            presses.put(spawned);
            store.saveAsync();
            player.sendMessage(LEGACY.deserialize(
                    "&aPlaced packaging press. &7Right-click to pack · punch to pick up."));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Optional<PressState> opt = presses.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (opt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PressState press = opt.get();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            breakPress(player, press, true);
            return;
        }
        usePress(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        findPress(event.getRightClicked()).ifPresent(press -> {
            event.setCancelled(true);
            usePress(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunchEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        findPress(event.getEntity()).ifPresent(press -> {
            event.setCancelled(true);
            breakPress(player, press, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakBarrier(BlockBreakEvent event) {
        Block block = event.getBlock();
        presses.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).ifPresent(press -> {
            event.setCancelled(true);
            breakPress(event.getPlayer(), press, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PressGui.Holder)) {
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
        if (slot == PressGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        boolean unpack = PressGui.isUnpackSlot(slot);
        if (!unpack && !PressGui.isPackSlot(slot)) {
            return;
        }
        String tag = trailingTag(clicked);
        if (tag == null) {
            return;
        }
        String prefix = unpack ? "unpack:" : "pack:";
        if (!tag.startsWith(prefix)) {
            return;
        }
        String[] parts = tag.substring(prefix.length()).split(":");
        if (parts.length < 2) {
            return;
        }
        PackUnit unit = PackUnit.parse(parts[0]).orElse(null);
        StrainId strain = StrainId.parse(parts[1]).orElse(null);
        if (unit == null || strain == null) {
            return;
        }
        boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        int max = shift ? 64 : 1;
        PackService.Outcome out = unpack
                ? packer.unpack(player, unit, strain, max)
                : packer.pack(player, unit, strain, max);
        tell(player, out, !unpack);
        gui.refresh(player, event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PressGui.Holder) {
            event.setCancelled(true);
        }
    }

    private void usePress(Player player) {
        if (!player.hasPermission("yap420.use")) {
            player.sendMessage(LEGACY.deserialize("&cNo permission."));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<String> handId = items.idOf(hand);
        // Holding packable goods → quick one-step press; empty / other → GUI
        if (handId.isPresent() && tryQuickPack(player, handId.get(), player.isSneaking())) {
            return;
        }
        YapSched.entity(plugin, player, () -> gui.open(player));
    }

    private boolean tryQuickPack(Player player, String itemId, boolean unpack) {
        Optional<PackUnit> unitOpt = Yap420ItemIds.packUnitOf(itemId);
        Optional<StrainId> strainOpt = Yap420ItemIds.strainFromPack(itemId);
        if (unitOpt.isEmpty() || strainOpt.isEmpty()) {
            return false;
        }
        PackUnit unit = unitOpt.get();
        StrainId strain = strainOpt.get();
        PackService.Outcome out;
        if (unpack) {
            out = packer.unpack(player, unit, strain, 1);
        } else {
            // Holding cured/gram → try ounce if enough, else bag grams; holding ounce → brick
            if (unit == PackUnit.GRAM) {
                PackMathCheck check = canOunce(player, strain);
                out = check.enough
                        ? packer.pack(player, PackUnit.OUNCE, strain, 1)
                        : packer.pack(player, PackUnit.GRAM, strain, 1);
            } else if (unit == PackUnit.OUNCE) {
                out = packer.pack(player, PackUnit.BRICK, strain, 1);
            } else {
                out = packer.pack(player, PackUnit.BRICK, strain, 1);
            }
        }
        tell(player, out, !unpack);
        return out.result() == PackService.Result.OK;
    }

    private PackMathCheck canOunce(Player player, StrainId strain) {
        int need = packer.market().pack().gramsPerOunce();
        int have = items.count(player.getInventory(), Yap420ItemIds.curedBud(strain))
                + items.count(player.getInventory(), Yap420ItemIds.gram(strain));
        return new PackMathCheck(have >= need);
    }

    private record PackMathCheck(boolean enough) {
    }

    private void breakPress(Player player, PressState press, boolean dropItem) {
        YapSched.region(plugin, player.getLocation(), () -> {
            presses.remove(press.world(), press.x(), press.y(), press.z());
            displays.remove(press);
            store.saveAsync();
            if (dropItem && player.getGameMode() != GameMode.CREATIVE) {
                items.giveOrDrop(player, Yap420ItemIds.PACKAGING_PRESS, 1);
            }
            player.sendMessage(LEGACY.deserialize("&aPicked up packaging press."));
        });
    }

    private Optional<PressState> findPress(Entity entity) {
        if (!(entity instanceof BlockDisplay)) {
            return Optional.empty();
        }
        String key = entity.getPersistentDataContainer()
                .get(plugin.keys().pressId(), PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return Optional.empty();
        }
        try {
            return presses.get(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void tell(Player player, PackService.Outcome out, boolean packing) {
        var msg = packer.market().messages();
        switch (out.result()) {
            case DISABLED -> player.sendMessage(LEGACY.deserialize(msg.disabled()));
            case MISSING_CATALOG -> player.sendMessage(LEGACY.deserialize("&cYaPItems missing pack item."));
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
        last = last.replaceAll("&[0-9a-fk-or]", "");
        if (last.startsWith("pack:") || last.startsWith("unpack:")) {
            return last;
        }
        return null;
    }
}
