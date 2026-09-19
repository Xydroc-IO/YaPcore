package com.yapcore.holo.impl;

import com.yapcore.holo.HologramAttach;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Resolve follow targets on the correct entity (Folia-safe when called on that entity's scheduler). */
public final class HologramFollow {

    private HologramFollow() {
    }

    public static void schedule(JavaPlugin plugin, HologramImpl holo) {
        HologramAttach attach = holo.attachment();
        if (attach == null || attach.kind() == HologramAttach.Kind.NONE) {
            return;
        }
        switch (attach.kind()) {
            case PLAYER -> {
                UUID id = attach.uuidKey();
                Player player = id == null ? null : Bukkit.getPlayer(id);
                if (player != null) {
                    YapSched.entity(plugin, player, holo::followTick);
                }
            }
            case ENTITY -> {
                UUID id = attach.uuidKey();
                Entity entity = id == null ? null : Bukkit.getEntity(id);
                if (entity != null) {
                    YapSched.entity(plugin, entity, holo::followTick);
                }
            }
            case NPC -> {
                Location loc = holo.location();
                if (loc.getWorld() == null) {
                    return;
                }
                YapSched.region(plugin, loc, () -> {
                    Entity found = target(plugin, attach, loc);
                    if (found != null) {
                        YapSched.entity(plugin, found, holo::followTick);
                    }
                });
            }
            case NONE -> {
            }
        }
    }

    public static Entity target(Plugin plugin, HologramAttach attach, Location fallback) {
        if (attach == null || attach.kind() == HologramAttach.Kind.NONE) {
            return null;
        }
        return switch (attach.kind()) {
            case PLAYER -> {
                UUID id = attach.uuidKey();
                yield id == null ? null : Bukkit.getPlayer(id);
            }
            case ENTITY -> {
                UUID id = attach.uuidKey();
                yield id == null ? null : Bukkit.getEntity(id);
            }
            case NPC -> findNpc(plugin, attach.key(), fallback);
            case NONE -> null;
        };
    }

    public static Location followLocation(Entity entity, double offsetY) {
        if (entity == null) {
            return null;
        }
        Location loc = entity.getLocation();
        return loc.add(0, entity.getHeight() + offsetY, 0);
    }

    static Entity findNpc(Plugin holo, String npcId, Location near) {
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        Plugin npcs = Bukkit.getPluginManager().getPlugin("YaPNpcs");
        if (npcs == null || !npcs.isEnabled()) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(npcs, "npc_id");
        if (near != null && near.getWorld() != null) {
            for (Entity e : near.getWorld().getNearbyEntities(near, 48, 24, 48)) {
                String tagged = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (npcId.equalsIgnoreCase(tagged)) {
                    return e;
                }
            }
        }
        return null;
    }
}
