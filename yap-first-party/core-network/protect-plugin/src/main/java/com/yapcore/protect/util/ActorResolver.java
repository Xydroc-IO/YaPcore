package com.yapcore.protect.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.UUID;

/** Attributes natural / explosion / fire actors for protect logs. */
public final class ActorResolver {

    public static final String NATURE = "#Nature";

    public record Actor(UUID uuid, String name) {
    }

    private ActorResolver() {
    }

    public static Actor natural() {
        return new Actor(null, NATURE);
    }

    public static Actor fromExplosion(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return new Actor(player.getUniqueId(), player.getName());
            }
            return new Actor(null, "#TNT");
        }
        if (entity instanceof Player player) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        if (entity != null) {
            return new Actor(null, "#" + entity.getType().name());
        }
        return natural();
    }

    public static Actor fromBlockExplosion(BlockExplodeEvent event) {
        String mat = event.getBlock().getType().name();
        return new Actor(null, "#" + mat);
    }

    public static Actor fromIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            return new Actor(player.getUniqueId(), player.getName());
        }
        return switch (event.getCause()) {
            case LIGHTNING -> new Actor(null, "#Lightning");
            case LAVA -> new Actor(null, "#Lava");
            case EXPLOSION -> new Actor(null, "#Explosion");
            case FIREBALL -> new Actor(null, "#Fireball");
            default -> natural();
        };
    }
}
