package com.yapcore.dungeons.listener;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.gen.DungeonCarver;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import com.yapcore.dungeons.service.LiveRun;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class DungeonListener implements Listener {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final PortalItems portalItems;
    private final PortalStructure structure;
    private final PortalStructureTags structureTags;
    private final DungeonMenu menu;
    private final DungeonInstanceManager instances;
    private final NamespacedKey ownerKey;
    private final NamespacedKey bossKey;

    public DungeonListener(
            JavaPlugin plugin,
            DungeonsConfig config,
            PortalItems portalItems,
            PortalStructure structure,
            PortalStructureTags structureTags,
            DungeonMenu menu,
            DungeonInstanceManager instances) {
        this.plugin = plugin;
        this.config = config;
        this.portalItems = portalItems;
        this.structure = structure;
        this.structureTags = structureTags;
        this.menu = menu;
        this.instances = instances;
        this.ownerKey = new NamespacedKey(plugin, "yap_dungeon_portal_owner");
        this.bossKey = new NamespacedKey(plugin, DungeonCarver.BOSS_PDC_KEY);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        LiveRun worldRun = instances.byWorld(event.getBlock().getWorld().getName()).orElse(null);
        if (worldRun != null
                && !worldRun.isMember(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("yapdungeons.admin")) {
            event.setCancelled(true);
            return;
        }

        ItemStack stack = event.getItemInHand();
        if (portalItems.isPortalItem(stack)) {
            if (!event.getPlayer().hasPermission("yapdungeons.portal.place")) {
                event.setCancelled(true);
                return;
            }
            Block block = event.getBlockPlaced();
            block.setType(config.portalBlock(), false);
            if (block.getState() instanceof org.bukkit.block.TileState tile) {
                tile.getPersistentDataContainer().set(portalItems.blockKey(), PersistentDataType.BYTE, (byte) 1);
                tile.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING,
                        event.getPlayer().getUniqueId().toString());
                tile.update();
            }
            event.getPlayer().sendMessage("§aDungeon portal placed. Interact to open the menu.");
            return;
        }

        if (config.structureEnabled() && event.getBlockPlaced().getType() == structure.frameMaterial()) {
            structure.findCompleteFrame(event.getBlockPlaced()).ifPresent(frame ->
                    event.getPlayer().sendMessage(
                            "§aDungeon portal frame complete! §7Right-click with an §fEnder Eye §7to activate."));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
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

        // Craftable single-block portal
        if (block.getState() instanceof org.bukkit.block.TileState tile
                && tile.getPersistentDataContainer().has(portalItems.blockKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            menu.open(player, 0);
            return;
        }

        if (!config.structureEnabled()) {
            return;
        }

        // Already-activated structure → open menu
        Optional<Block> keystone = structureTags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isPresent()) {
            Optional<PortalStructure.Frame> frame = structureTags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && structure.contains(frame.get(), block)) {
                event.setCancelled(true);
                menu.open(player, 0);
                return;
            }
        }

        // Activate incomplete→complete frame with ender eye
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != config.structureActivateItem()) {
            return;
        }
        if (block.getType() != structure.frameMaterial() && block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        Optional<PortalStructure.Frame> complete = structure.findCompleteFrame(block);
        if (complete.isEmpty()) {
            return;
        }
        PortalStructure.Frame frame = complete.get();
        // Skip if already activated
        if (structureTags.isKeystone(frame.keystone())
                || structureTags.findNearbyKeystone(frame.keystone(), 1).isPresent()) {
            event.setCancelled(true);
            menu.open(player, 0);
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("yapdungeons.portal.place")) {
            player.sendMessage("§cNo permission to activate dungeon portals.");
            return;
        }
        structure.fillInterior(frame);
        structureTags.installKeystone(frame, player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.sendMessage("§aDungeon portal activated! §7Right-click it to open dungeons.");
        menu.open(player, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LiveRun worldRun = instances.byWorld(event.getBlock().getWorld().getName()).orElse(null);
        if (worldRun != null
                && !worldRun.isMember(event.getPlayer().getUniqueId())
                && !event.getPlayer().hasPermission("yapdungeons.admin")) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Craftable portal break
        if (block.getState() instanceof org.bukkit.block.TileState tile
                && tile.getPersistentDataContainer().has(portalItems.blockKey(), PersistentDataType.BYTE)) {
            String owner = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (owner != null && !owner.equals(player.getUniqueId().toString())
                    && !player.hasPermission("yapdungeons.admin")
                    && player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
                player.sendMessage("§cNot your dungeon portal.");
                return;
            }
            event.setDropItems(false);
            player.getWorld().dropItemNaturally(block.getLocation(), portalItems.createPortalItem());
            return;
        }

        if (!config.structureEnabled()) {
            return;
        }
        // Breaking keystone or any block of an activated frame deactivates
        Optional<Block> keystone = structureTags.findNearbyKeystone(block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isEmpty()) {
            return;
        }
        Optional<PortalStructure.Frame> frame = structureTags.frameFromKeystone(keystone.get());
        if (frame.isEmpty() || !structure.contains(frame.get(), block)) {
            return;
        }
        Optional<String> owner = structureTags.owner(keystone.get());
        if (owner.isPresent() && !owner.get().equals(player.getUniqueId().toString())
                && !player.hasPermission("yapdungeons.admin")
                && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage("§cNot your dungeon portal.");
            return;
        }
        structureTags.deactivate(keystone.get(), structure);
        player.sendMessage("§7Dungeon portal deactivated.");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        LiveRun run = instances.byPlayer(player.getUniqueId()).orElse(null);
        if (run == null) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        instances.onPlayerDeath(run, player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null || run.isTerminal()) {
            return;
        }
        if (run.entrance() != null) {
            event.setRespawnLocation(run.entrance());
        }
        instances.onPlayerRespawn(run, event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!living.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)) {
            return;
        }
        String runId = living.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        if (runId == null) {
            return;
        }
        instances.byId(runId).ifPresent(instances::onBossKilled);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null) {
            return;
        }
        if (event.getPlayer().getWorld().getName().equals(run.worldName())) {
            instances.removePlayer(event.getPlayer().getUniqueId(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null || run.isTerminal() || event.getTo() == null) {
            return;
        }
        String from = event.getFrom().getWorld() != null ? event.getFrom().getWorld().getName() : "";
        String to = event.getTo().getWorld() != null ? event.getTo().getWorld().getName() : "";
        if (from.equals(run.worldName()) && !to.equals(run.worldName())) {
            instances.removePlayer(event.getPlayer().getUniqueId(), false);
            event.getPlayer().sendMessage("§7Left dungeon (teleported out).");
        }
    }
}
