package com.yapcore.conquest.listener;

import com.yapcore.conquest.ConquestChunk;
import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.ConquestZoneType;
import com.yapcore.conquest.service.ConquestServiceImpl;
import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionServices;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConquestTerritoryListener implements Listener {

    private final ConquestConfig config;
    private final ConquestServiceImpl conquest;
    private final Map<UUID, String> lastKey = new ConcurrentHashMap<>();

    public ConquestTerritoryListener(ConquestConfig config, ConquestServiceImpl conquest) {
        this.config = config;
        this.conquest = conquest;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        denyBuild(event.getPlayer(), event.getBlock().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayerDamager(event);
        if (attacker == null) {
            return;
        }
        if (attacker.hasPermission("yapconquest.admin")) {
            return;
        }
        Optional<Boolean> allowed = conquest.evaluatePvp(attacker, victim, victim.getLocation());
        if (allowed.isEmpty()) {
            return;
        }
        if (allowed.get()) {
            event.setCancelled(false);
        } else {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP blocked in this conquest territory.");
        }
    }

    private static Player resolvePlayerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource src = projectile.getShooter();
            if (src instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplodeBlocks(event.blockList().iterator());
        if (event.getLocation() != null) {
            Optional<Boolean> allowed = conquest.evaluateExplode(event.getLocation());
            if (allowed.isPresent() && !allowed.get()) {
                event.setCancelled(true);
                event.blockList().clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplodeBlocks(event.blockList().iterator());
        Optional<Boolean> allowed = conquest.evaluateExplode(event.getBlock().getLocation());
        if (allowed.isPresent() && !allowed.get()) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if ((event.getFrom().getBlockX() >> 4) == (event.getTo().getBlockX() >> 4)
                && (event.getFrom().getBlockZ() >> 4) == (event.getTo().getBlockZ() >> 4)
                && event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        String nextKey = territoryKey(event.getTo());
        String prev = lastKey.get(player.getUniqueId());
        if (nextKey.equals(prev == null ? "" : prev)) {
            return;
        }
        lastKey.put(player.getUniqueId(), nextKey);
        announceEnter(player, event.getTo(), prev);
    }

    private void announceEnter(Player player, Location to, String prev) {
        Optional<ConquestChunk> nextClaim = conquest.chunkAt(to);
        if (nextClaim.isPresent()) {
            String name = FactionServices.find()
                    .flatMap(fs -> fs.getFaction(nextClaim.get().factionId()))
                    .map(Faction::name)
                    .orElse("#" + nextClaim.get().factionId());
            player.sendMessage(config.territoryEnterMessage().replace("%faction%", name));
            return;
        }
        if (config.zonesEnabled()) {
            ConquestZoneType zone = conquest.zoneAt(to).orElse(ConquestZoneType.WILDERNESS);
            String zoneKey = "z:" + zone.name();
            if (prev != null && prev.equals(zoneKey)) {
                return;
            }
            // Coming from a claim into a zone, or switching zone types.
            if (prev != null && prev.startsWith("c:")) {
                player.sendMessage(config.territoryLeaveMessage().replace("%faction%", leaveFactionName(prev)));
            }
            String msg = switch (zone) {
                case WARZONE -> config.zoneEnterWarzone();
                case SAFEZONE -> config.zoneEnterSafezone();
                case WILDERNESS -> config.zoneEnterWilderness();
            };
            player.sendMessage(msg);
            return;
        }
        if (prev != null && prev.startsWith("c:")) {
            player.sendMessage(config.territoryLeaveMessage().replace("%faction%", leaveFactionName(prev)));
        }
    }

    private static String leaveFactionName(String prev) {
        String[] parts = prev.split(":");
        if (parts.length >= 5) {
            try {
                long factionId = Long.parseLong(parts[4]);
                return FactionServices.find()
                        .flatMap(fs -> fs.getFaction(factionId))
                        .map(Faction::name)
                        .orElse("#" + factionId);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return "faction";
    }

    private String territoryKey(Location loc) {
        Optional<ConquestChunk> claim = conquest.chunkAt(loc);
        if (claim.isPresent()) {
            ConquestChunk c = claim.get();
            return "c:" + c.world() + ":" + c.chunkX() + ":" + c.chunkZ() + ":" + c.factionId();
        }
        if (config.zonesEnabled()) {
            ConquestZoneType z = conquest.zoneAt(loc).orElse(ConquestZoneType.WILDERNESS);
            return "z:" + z.name();
        }
        return "";
    }

    private void filterExplodeBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            Block block = it.next();
            Optional<Boolean> allowed = conquest.evaluateExplode(block.getLocation());
            if (allowed.isPresent() && !allowed.get()) {
                it.remove();
            }
        }
    }

    private void denyBuild(Player player, Location loc, Runnable cancel) {
        if (player.hasPermission("yapconquest.admin")) {
            return;
        }
        Optional<Boolean> allowed = conquest.evaluateBuild(player, loc);
        if (allowed.isPresent() && !allowed.get()) {
            cancel.run();
            player.sendMessage("§cConquest land — you cannot build here.");
        }
    }
}
