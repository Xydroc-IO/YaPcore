package com.yapcore.yap420.cure;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.persist.RackStore;
import com.yapcore.yap420.plant.StrainId;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Place racks, deposit wet buds, collect cured buds, break/pickup. */
public final class RackListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final ItemBridge items;
    private final RackRegistry racks;
    private final RackDisplayService displays;
    private final RackStore store;

    public RackListener(
            Yap420Plugin plugin,
            Yap420Config config,
            ItemBridge items,
            RackRegistry racks,
            RackDisplayService displays,
            RackStore store
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.racks = racks;
        this.displays = displays;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceRack(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (items.idOf(hand).filter(Yap420ItemIds.DRYING_RACK::equals).isEmpty()) {
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
        if (racks.contains(placeAt.getWorld().getName(), placeAt.getX(), placeAt.getY(), placeAt.getZ())) {
            player.sendMessage(LEGACY.deserialize(config.messages().noSpace()));
            return;
        }
        YapSched.region(plugin, placeAt.getLocation(), () -> {
            if (!placeAt.getType().isAir() && placeAt.getType() != Material.BARRIER) {
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack again = player.getInventory().getItemInMainHand();
                if (items.idOf(again).filter(Yap420ItemIds.DRYING_RACK::equals).isEmpty()) {
                    return;
                }
                again.setAmount(again.getAmount() - 1);
            }
            placeAt.setType(Material.BARRIER, false);
            RackState rack = new RackState(
                    placeAt.getWorld().getName(),
                    placeAt.getX(),
                    placeAt.getY(),
                    placeAt.getZ(),
                    List.of(),
                    null);
            RackState spawned = displays.spawn(rack);
            racks.put(spawned);
            store.saveAsync();
            player.sendMessage(LEGACY.deserialize("&aPlaced drying rack. &7Punch to pick up."));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakBarrier(BlockBreakEvent event) {
        Block block = event.getBlock();
        racks.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).ifPresent(rack -> {
            event.setCancelled(true);
            breakRack(event.getPlayer(), rack, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        Entity looked = player.getTargetEntity(5);
        if (looked != null) {
            var rackOpt = findRack(looked);
            if (rackOpt.isPresent()) {
                event.setCancelled(true);
                breakRack(player, rackOpt.get(), true);
                return;
            }
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        racks.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())
                .ifPresent(rack -> {
                    event.setCancelled(true);
                    breakRack(player, rack, true);
                });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPunchDisplay(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof BlockDisplay) && !(event.getEntity() instanceof ItemDisplay)) {
            return;
        }
        findRack(event.getEntity()).ifPresent(rack -> {
            event.setCancelled(true);
            breakRack(player, rack, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRackEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        findRack(entity).ifPresent(rack -> {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                breakRack(player, rack, true);
            } else {
                handleUse(player, rack);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRackBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (items.idOf(event.getPlayer().getInventory().getItemInMainHand())
                .filter(Yap420ItemIds.DRYING_RACK::equals).isPresent()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        racks.get(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())
                .ifPresent(rack -> {
                    event.setCancelled(true);
                    Player player = event.getPlayer();
                    if (player.isSneaking()) {
                        breakRack(player, rack, true);
                    } else {
                        handleUse(player, rack);
                    }
                });
    }

    private java.util.Optional<RackState> findRack(Entity entity) {
        if (entity == null) {
            return java.util.Optional.empty();
        }
        String key = entity.getPersistentDataContainer()
                .get(plugin.keys().rackId(), org.bukkit.persistence.PersistentDataType.STRING);
        if (key != null) {
            // key is world|x|y|z
            for (RackState rack : racks.all()) {
                if (rack.key().equals(key)) {
                    return java.util.Optional.of(rack);
                }
            }
        }
        Location loc = entity.getLocation();
        return racks.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                .or(() -> racks.get(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()));
    }

    private void breakRack(Player player, RackState snapshot, boolean dropItem) {
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        YapSched.region(plugin, player.getWorld(), snapshot.x(), snapshot.z(), () -> {
            RackState rack = racks.remove(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()).orElse(null);
            if (rack == null) {
                return;
            }
            displays.remove(rack);
            Block block = player.getWorld().getBlockAt(rack.x(), rack.y(), rack.z());
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
            // Drop any wet/cured buds still hanging
            for (RackState.Slot slot : rack.slots()) {
                String id = slot.cured()
                        ? Yap420ItemIds.curedBud(slot.strain())
                        : Yap420ItemIds.wetBud(slot.strain());
                items.create(id, 1).ifPresent(stack ->
                        player.getWorld().dropItemNaturally(
                                new Location(player.getWorld(), rack.x() + 0.5, rack.y() + 0.5, rack.z() + 0.5),
                                stack));
            }
            if (dropItem && player.getGameMode() != GameMode.CREATIVE) {
                items.create(Yap420ItemIds.DRYING_RACK, 1).ifPresent(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
            store.saveAsync();
            player.sendMessage(LEGACY.deserialize("&aPicked up drying rack."));
        });
    }

    private void handleUse(Player player, RackState snapshot) {
        if (!player.hasPermission("yap420.use")) {
            return;
        }
        YapSched.region(plugin, player.getWorld(), snapshot.x(), snapshot.z(), () -> {
            RackState rack = racks.get(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()).orElse(null);
            if (rack == null) {
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            StrainId wet = items.idOf(hand).flatMap(Yap420ItemIds::strainFromWetBud).orElse(null);
            if (wet != null) {
                deposit(player, rack, wet, hand);
                return;
            }
            collectCured(player, rack);
        });
    }

    private void deposit(Player player, RackState rack, StrainId strain, ItemStack hand) {
        if (rack.occupied() >= config.rackMaxSlots()) {
            player.sendMessage(LEGACY.deserialize(config.messages().rackFull()));
            return;
        }
        int take = 1;
        if (player.isSneaking()) {
            take = Math.min(hand.getAmount(), config.rackMaxSlots() - rack.occupied());
        }
        if (take <= 0) {
            player.sendMessage(LEGACY.deserialize(config.messages().rackFull()));
            return;
        }
        List<RackState.Slot> slots = rack.mutableSlots();
        long now = System.currentTimeMillis();
        for (int i = 0; i < take; i++) {
            slots.add(new RackState.Slot(strain, now, false));
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - take);
        }
        racks.put(rack.withSlots(slots));
        store.saveAsync();
        player.sendMessage(LEGACY.deserialize(config.messages().deposited()
                .replace("{count}", String.valueOf(take))));
    }

    private void collectCured(Player player, RackState rack) {
        List<RackState.Slot> slots = rack.mutableSlots();
        List<RackState.Slot> remain = new ArrayList<>();
        int collected = 0;
        for (RackState.Slot slot : slots) {
            if (slot.cured()) {
                ItemStack cured = items.create(Yap420ItemIds.curedBud(slot.strain()), 1).orElse(null);
                if (cured != null) {
                    player.getInventory().addItem(cured).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    collected++;
                } else {
                    remain.add(slot);
                }
            } else {
                remain.add(slot);
            }
        }
        if (collected == 0) {
            if (items.idOf(player.getInventory().getItemInMainHand())
                    .flatMap(Yap420ItemIds::strainFromWetBud).isEmpty()) {
                player.sendMessage(LEGACY.deserialize(
                        "&7Empty rack — hold wet buds to deposit, or &fpunch&7 to pick up."));
            }
            return;
        }
        racks.put(rack.withSlots(remain));
        store.saveAsync();
        player.sendMessage(LEGACY.deserialize(config.messages().collected()
                .replace("{count}", String.valueOf(collected))));
    }
}
