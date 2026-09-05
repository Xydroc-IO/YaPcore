package com.yapcore.protect.listener;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.protect.util.ActorResolver;
import com.yapcore.protect.util.BlockCodec;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Logs explosion, liquid flow, and fire block changes for rollback. */
public final class NaturalChangeListener implements Listener {

    private final ProtectServiceImpl service;

    public NaturalChangeListener(ProtectServiceImpl service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!service.isLogging() || !service.config().logExplosion()) {
            return;
        }
        ActorResolver.Actor actor = ActorResolver.fromExplosion(event);
        for (Block block : event.blockList()) {
            logBlock(ChangeType.EXPLOSION, actor, block, "AIR");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!service.isLogging() || !service.config().logExplosion()) {
            return;
        }
        ActorResolver.Actor actor = ActorResolver.fromBlockExplosion(event);
        for (Block block : event.blockList()) {
            logBlock(ChangeType.EXPLOSION, actor, block, "AIR");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!service.isLogging() || !service.config().logLiquid()) {
            return;
        }
        Block from = event.getBlock();
        Material type = from.getType();
        if (type != Material.WATER && type != Material.LAVA
                && type != Material.WATER_CAULDRON && type != Material.LAVA_CAULDRON) {
            // Still allow flowing water/lava legacy names via isLiquid when available
            if (!from.isLiquid()) {
                return;
            }
        }
        Block to = event.getToBlock();
        ActorResolver.Actor actor = ActorResolver.natural();
        service.logAsync(
                ChangeType.LIQUID_FLOW,
                actor.uuid(),
                actor.name(),
                to.getWorld().getName(),
                to.getX(),
                to.getY(),
                to.getZ(),
                BlockCodec.encode(to),
                BlockCodec.encode(from));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!service.isLogging() || !service.config().logFire()) {
            return;
        }
        ActorResolver.Actor actor = ActorResolver.natural();
        logBlock(ChangeType.FIRE, actor, event.getBlock(), "AIR");
    }

    private void logBlock(ChangeType type, ActorResolver.Actor actor, Block block, String after) {
        service.logAsync(
                type,
                actor.uuid(),
                actor.name(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                BlockCodec.encode(block),
                after);
    }
}
