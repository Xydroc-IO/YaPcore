package com.yapcore.dungeons.listener;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.portal.DungeonPortalRegistry;
import com.yapcore.dungeons.portal.DungeonPortalStore;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import com.yapcore.messages.YapMessages;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Optional;

final class DungeonStructureInteract implements Listener {

    private final DungeonsConfig config;
    private final PortalItems portalItems;
    private final PortalStructure structure;
    private final PortalStructureTags structureTags;
    private final DungeonPortalStore store;
    private final DungeonMenu menu;
    private final DungeonInstanceManager instances;

    DungeonStructureInteract(
            DungeonsConfig config,
            PortalItems portalItems,
            PortalStructure structure,
            PortalStructureTags structureTags,
            DungeonPortalStore store,
            DungeonMenu menu,
            DungeonInstanceManager instances) {
        this.config = config;
        this.portalItems = portalItems;
        this.structure = structure;
        this.structureTags = structureTags;
        this.store = store;
        this.menu = menu;
        this.instances = instances;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack used = player.getInventory().getItem(event.getHand());

        if (block.getState() instanceof org.bukkit.block.TileState tile
                && tile.getPersistentDataContainer().has(portalItems.blockKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            if (DungeonPortalHijack.denyClaimedPortal(player, block.getLocation())) {
                return;
            }
            menu.open(player, 0);
            return;
        }

        if (!config.structureEnabled()) {
            return;
        }

        Optional<Block> keystone = structureTags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isPresent()) {
            Optional<PortalStructure.Frame> frame = structureTags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && structure.contains(frame.get(), block, 1)) {
                event.setCancelled(true);
                structure.ensureWalkable(frame.get());
                DungeonPortalRegistry.register(frame.get());
                if (DungeonPortalHijack.denyClaimedPortal(player, block.getLocation())) {
                    return;
                }
                if (instances.byPlayer(player.getUniqueId()).isPresent()) {
                    player.sendMessage("§cYou are already in a dungeon. Use §e/dungeon leave §cfirst.");
                    return;
                }
                menu.open(player, 0);
                return;
            }
        }

        if (structure.isInteriorBlock(block) || block.getType().name().contains("STAINED_GLASS")) {
            Optional<Block> near = structureTags.findNearbyKeystone(
                    block, Math.max(structure.outerWidth(), structure.outerHeight()) + 1);
            if (near.isPresent()) {
                Optional<PortalStructure.Frame> fr = structureTags.frameFromKeystone(near.get());
                if (fr.isPresent() && structure.contains(fr.get(), block, 1)) {
                    event.setCancelled(true);
                    structure.ensureWalkable(fr.get());
                    DungeonPortalRegistry.register(fr.get());
                    if (DungeonPortalHijack.denyClaimedPortal(player, block.getLocation())) {
                        return;
                    }
                    if (instances.byPlayer(player.getUniqueId()).isPresent()) {
                        player.sendMessage("§cYou are already in a dungeon. Use §e/dungeon leave §cfirst.");
                        return;
                    }
                    menu.open(player, 0);
                    return;
                }
            }
        }

        if (used.getType() != config.structureActivateItem()) {
            return;
        }
        if (block.getType() != structure.frameMaterial() && block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        event.setCancelled(true);
        Optional<PortalStructure.Frame> complete = structure.findCompleteFrame(block);
        if (complete.isEmpty()) {
            String why = structure.explainIncomplete(block)
                    .orElse("need exact " + structure.outerWidth() + "×" + structure.outerHeight()
                            + " " + pretty(structure.frameMaterial()));
            player.sendMessage("§cDungeon portal not ready: §7" + why);
            return;
        }
        PortalStructure.Frame frame = complete.get();
        if (structureTags.isKeystone(frame.keystone())
                || structureTags.findNearbyKeystone(frame.keystone(), 1).isPresent()) {
            structure.ensureWalkable(frame);
            DungeonPortalRegistry.register(frame);
            store.upsert(frame);
            player.sendMessage("§7Dungeon portal is active — walk through to pick a level.");
            return;
        }
        if (!player.hasPermission("yapdungeons.portal.place")) {
            YapMessages.noPermission(player, "yapdungeons.portal.place");
            return;
        }
        if (DungeonPortalHijack.denyClaimedPortal(player, block.getLocation())) {
            return;
        }
        structure.fillInterior(frame);
        structureTags.installKeystone(frame, player.getUniqueId());
        structure.fillInterior(frame);
        DungeonPortalRegistry.register(frame);
        store.upsert(frame);
        if (player.getGameMode() != GameMode.CREATIVE) {
            used.setAmount(used.getAmount() - 1);
        }
        player.sendMessage("§aDungeon portal activated! §7Walk through (or right-click the frame) to pick a level.");
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
