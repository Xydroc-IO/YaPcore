package com.yapcore.dungeons.listener;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.gen.DungeonCarver;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import com.yapcore.dungeons.service.LiveRun;
import com.yapcore.messages.YapMessages;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final DungeonMenu menu;
    private final DungeonInstanceManager instances;
    private final NamespacedKey ownerKey;
    private final NamespacedKey bossKey;
    /** Debounce so move + portal events do not double-open the menu. */
    private final Map<UUID, Long> recentEnterMs = new ConcurrentHashMap<>();

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
            event.getPlayer().sendMessage("§aDungeon portal placed. Right-click to open the level picker.");
            return;
        }

        if (config.structureEnabled() && event.getBlockPlaced().getType() == structure.frameMaterial()) {
            structure.findCompleteFrame(event.getBlockPlaced()).ifPresent(frame ->
                    event.getPlayer().sendMessage(
                            "§aDungeon portal frame complete! §7Right-click with an §fEnder Eye §7to activate."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        // Craftable single-block portal (no walk-through) → menu
        if (block.getState() instanceof org.bukkit.block.TileState tile
                && tile.getPersistentDataContainer().has(portalItems.blockKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            if (denyClaimedPortal(player, block.getLocation())) {
                return;
            }
            menu.open(player, 0);
            return;
        }

        if (!config.structureEnabled()) {
            return;
        }

        // Already-activated structure → tip (walk-through opens the menu)
        Optional<Block> keystone = structureTags.findNearbyKeystone(
                block, Math.max(structure.outerWidth(), structure.outerHeight()));
        if (keystone.isPresent()) {
            Optional<PortalStructure.Frame> frame = structureTags.frameFromKeystone(keystone.get());
            if (frame.isPresent() && structure.contains(frame.get(), block)) {
                if (used.getType() == config.structureActivateItem()) {
                    event.setCancelled(true);
                    player.sendMessage("§7Dungeon portal is active — walk through to pick a level.");
                }
                return;
            }
        }

        // Activate incomplete→complete frame with ender eye (either hand)
        if (used.getType() != config.structureActivateItem()) {
            return;
        }
        if (block.getType() != structure.frameMaterial() && block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        // Always cancel eye-of-ender throw when clicking a dungeon frame block
        event.setCancelled(true);
        Optional<PortalStructure.Frame> complete = structure.findCompleteFrame(block);
        if (complete.isEmpty()) {
            player.sendMessage("§cDungeon portal frame incomplete. §7Need a §f"
                    + structure.outerWidth() + "×" + structure.outerHeight()
                    + " §7" + pretty(structure.frameMaterial())
                    + " frame with an empty "
                    + (structure.outerWidth() - 2) + "×" + (structure.outerHeight() - 2)
                    + " opening (not regular obsidian).");
            return;
        }
        PortalStructure.Frame frame = complete.get();
        if (structureTags.isKeystone(frame.keystone())
                || structureTags.findNearbyKeystone(frame.keystone(), 1).isPresent()) {
            player.sendMessage("§7Dungeon portal is active — walk through to pick a level.");
            return;
        }
        if (!player.hasPermission("yapdungeons.portal.place")) {
            YapMessages.noPermission(player, "yapdungeons.portal.place");
            return;
        }
        if (denyClaimedPortal(player, block.getLocation())) {
            return;
        }
        structure.fillInterior(frame);
        structureTags.installKeystone(frame, player.getUniqueId());
        if (player.getGameMode() != GameMode.CREATIVE) {
            used.setAmount(used.getAmount() - 1);
        }
        player.sendMessage("§aDungeon portal activated! §7Walk through to pick a level.");
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Same claim gate as nether / End / YaP End doors ({@code nether-portal} flag).
     * Default deny → owner + ACCESS+ trust + staff only.
     */
    private static boolean denyClaimedPortal(Player player, Location location) {
        if (ClaimLookups.canUsePortal(player, location)) {
            return false;
        }
        player.sendMessage("§cClaimed dungeon portal — only the owner and trusted players can use it.");
        return true;
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
        recentEnterMs.remove(event.getPlayer().getUniqueId());
        LiveRun run = instances.byPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (run == null) {
            return;
        }
        if (event.getPlayer().getWorld().getName().equals(run.worldName())) {
            instances.removePlayer(event.getPlayer().getUniqueId(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!hijackDungeonPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause =
                event.getPortalType() == org.bukkit.PortalType.NETHER
                        ? PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                        : PlayerTeleportEvent.TeleportCause.UNKNOWN;
        if (!hijackDungeonPortal(player, event.getFrom(), cause)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Walk-through hijack (Folia may fire teleport without PlayerPortalEvent)
        if (!(event instanceof PlayerPortalEvent)
                && hijackDungeonPortal(event.getPlayer(), event.getFrom(), event.getCause())) {
            event.setCancelled(true);
            return;
        }

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

    /**
     * @return true when this was an activated dungeon portal (event should cancel vanilla nether hop)
     */
    private boolean hijackDungeonPortal(
            Player player, Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (!config.enabled() || !config.structureEnabled()) {
            return false;
        }
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return false;
        }
        Location probe = from != null ? from : player.getLocation();
        if (!isInsideDungeonPortal(probe)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(player.getUniqueId());
        if (prev != null && now - prev < 1500L) {
            return true;
        }
        recentEnterMs.put(player.getUniqueId(), now);
        if (denyClaimedPortal(player, probe)) {
            return true;
        }
        if (instances.byPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage("§cYou are already in a dungeon. Use §e/dungeon leave §cfirst.");
            return true;
        }
        menu.open(player, 0);
        return true;
    }

    private boolean isInsideDungeonPortal(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Block block = loc.getBlock();
        int radius = Math.max(structure.outerWidth(), structure.outerHeight());
        Optional<Block> keystone = structureTags.findNearbyKeystone(block, radius);
        if (keystone.isEmpty()) {
            keystone = structureTags.findNearbyKeystone(block.getRelative(0, -1, 0), radius);
        }
        if (keystone.isEmpty()) {
            return false;
        }
        Optional<PortalStructure.Frame> frame = structureTags.frameFromKeystone(keystone.get());
        if (frame.isEmpty()) {
            return false;
        }
        return structure.contains(frame.get(), block)
                || structure.contains(frame.get(), block.getRelative(0, -1, 0));
    }
}
