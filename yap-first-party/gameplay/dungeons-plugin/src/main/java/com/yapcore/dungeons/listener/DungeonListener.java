package com.yapcore.dungeons.listener;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.gen.DungeonCarver;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.loot.LootService;
import com.yapcore.dungeons.portal.DungeonPortalRegistry;
import com.yapcore.dungeons.portal.DungeonPortalRehydrate;
import com.yapcore.dungeons.portal.DungeonPortalStore;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import com.yapcore.dungeons.service.LiveRun;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Dungeon portals: craftable block (click) + crying-obsidian frame (walk-through).
 * Cancels vanilla nether hops only when the portal belongs to an activated dungeon frame.
 * Regular obsidian nether portals and End portals are untouched.
 */
public final class DungeonListener implements Listener {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final PortalItems portalItems;
    private final PortalStructure structure;
    private final PortalStructureTags structureTags;
    private final DungeonPortalStore store;
    private final DungeonMenu menu;
    private final DungeonInstanceManager instances;
    private final LootService loot;
    private final NamespacedKey ownerKey;
    private final NamespacedKey bossKey;
    private final DungeonPortalHijack portalHijack;

    public DungeonListener(
            JavaPlugin plugin,
            DungeonsConfig config,
            PortalItems portalItems,
            PortalStructure structure,
            PortalStructureTags structureTags,
            DungeonPortalStore store,
            DungeonMenu menu,
            DungeonInstanceManager instances,
            LootService loot) {
        this.plugin = plugin;
        this.config = config;
        this.portalItems = portalItems;
        this.structure = structure;
        this.structureTags = structureTags;
        this.store = store;
        this.menu = menu;
        this.instances = instances;
        this.loot = loot;
        this.ownerKey = new NamespacedKey(plugin, "yap_dungeon_portal_owner");
        this.bossKey = new NamespacedKey(plugin, DungeonCarver.BOSS_PDC_KEY);
        this.portalHijack = new DungeonPortalHijack(
                plugin, config, structure, structureTags, menu, instances);
        plugin.getServer().getPluginManager().registerEvents(
                new DungeonStructureInteract(
                        config, portalItems, structure, structureTags, store, menu, instances),
                plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDungeonChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Chest chest)) {
            return;
        }
        Block block = chest.getBlock();
        if (!chest.getPersistentDataContainer().has(loot.chestKey(), PersistentDataType.STRING)
                && !chest.getPersistentDataContainer().has(
                        new NamespacedKey(plugin, "yap_dungeon_chest"), PersistentDataType.STRING)) {
            return;
        }
        LiveRun run = instances.byWorld(block.getWorld().getName()).orElse(null);
        int level = run != null ? run.dungeonLevel() : 1;
        long seed = run != null ? run.seed() : block.getX() * 31L ^ block.getZ();
        int filled = loot.refillIfEmpty(block, level, seed);
        if (filled > 0) {
            plugin.getLogger().info("Refilled empty dungeon chest at "
                    + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " stacks=" + filled);
        }
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
            event.getPlayer().sendMessage("§aDungeon portal placed. Right-click to open the level picker.");
            return;
        }

        if (config.structureEnabled() && event.getBlockPlaced().getType() == structure.frameMaterial()) {
            structure.findCompleteFrame(event.getBlockPlaced()).ifPresent(frame ->
                    event.getPlayer().sendMessage(
                            "§aDungeon portal frame complete! §7Right-click with an §fEnder Eye §7to activate."));
        }
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
        frame.ifPresent(f -> {
            DungeonPortalRegistry.unregister(f);
            store.remove(f);
        });
        player.sendMessage("§7Dungeon portal deactivated.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!config.enabled() || !config.structureEnabled()) {
            return;
        }
        DungeonPortalRehydrate.rehydrateChunk(plugin, structure, structureTags, store, event.getChunk());
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
        portalHijack.recentEnterMs().remove(event.getPlayer().getUniqueId());
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null) {
            return;
        }
        if (event.getPlayer().getWorld().getName().equals(run.worldName())) {
            instances.removePlayer(event.getPlayer().getUniqueId(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.enabled()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())) {
            return;
        }

        // Lime return portal after boss clear
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run != null
                && run.state() == com.yapcore.dungeons.DungeonRunState.CLEARED
                && com.yapcore.dungeons.portal.DungeonExitPortal.near(to, run.exitPortal(), 2.25)) {
            event.getPlayer().sendMessage("§aReturning from dungeon…");
            instances.removePlayer(event.getPlayer().getUniqueId(), true);
            return;
        }

        if (!config.structureEnabled()) {
            return;
        }
        if (portalHijack.dungeonFrameAt(to).isEmpty()) {
            return;
        }
        portalHijack.hijackDungeonPortal(event.getPlayer(), to, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortal(PlayerPortalEvent event) {
        if (!portalHijack.hijackDungeonPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause =
                event.getPortalType() == org.bukkit.PortalType.NETHER
                        ? PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                        : PlayerTeleportEvent.TeleportCause.UNKNOWN;
        if (!portalHijack.hijackDungeonPortal(player, event.getFrom(), cause)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        boolean toNether = to != null && to.getWorld() != null
                && to.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        if (!(event instanceof PlayerPortalEvent)
                && (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || toNether)
                && portalHijack.hijackDungeonPortal(event.getPlayer(), event.getFrom(),
                event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                        ? event.getCause()
                        : PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)) {
            event.setCancelled(true);
            return;
        }

        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null || run.isTerminal() || event.getTo() == null) {
            return;
        }
        String from = event.getFrom().getWorld() != null ? event.getFrom().getWorld().getName() : "";
        String toName = event.getTo().getWorld() != null ? event.getTo().getWorld().getName() : "";
        if (from.equals(run.worldName()) && !toName.equals(run.worldName())) {
            instances.removePlayer(event.getPlayer().getUniqueId(), false);
            event.getPlayer().sendMessage("§7Left dungeon (teleported out).");
        }
    }

}
