package com.yapcore.npcs.service;

import com.yapcore.holo.Hologram;
import com.yapcore.holo.HologramAttach;
import com.yapcore.holo.HologramService;
import com.yapcore.holo.HologramServices;
import com.yapcore.npcs.NpcsConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Loaded only when YaPHolo is on the classpath. */
final class NpcHologramNametagApply {

    private NpcHologramNametagApply() {
    }

    static boolean sync(NpcsConfig config, String npcId, String displayName, Entity entity) {
        HologramService service = HologramServices.holograms();
        if (service == null || !service.enabled()) {
            return false;
        }
        String hid = hologramId(npcId);
        String line = colorName(displayName, npcId);
        // YaPHolo draws the name. Leaving the vanilla tag on stacks a second copy on Java.
        entity.customName(Component.empty());
        entity.setCustomNameVisible(false);
        double offset = effectiveOffset(config, entity);
        Location loc = HologramFollowLoc.at(entity, offset);
        Optional<Hologram> existing = service.get(hid);
        Hologram holo = existing.orElseGet(() -> service.create(hid, loc, List.of(line)));
        holo.setLines(List.of(line));
        holo.attach(HologramAttach.npc(npcId, offset));
        holo.teleport(loc);
        service.save();
        return true;
    }

    /**
     * Farmer / librarian hats sit above the hitbox and clip tight nametags.
     * Mannequins need a little extra too so the label clears the head.
     */
    static double effectiveOffset(NpcsConfig config, Entity entity) {
        double base = Math.max(0.35, config.nametagOffset());
        if (entity instanceof org.bukkit.entity.Villager villager) {
            org.bukkit.entity.Villager.Profession p = villager.getProfession();
            if (p == org.bukkit.entity.Villager.Profession.FARMER
                    || p == org.bukkit.entity.Villager.Profession.LIBRARIAN
                    || p == org.bukkit.entity.Villager.Profession.FISHERMAN
                    || p == org.bukkit.entity.Villager.Profession.WEAPONSMITH
                    || p == org.bukkit.entity.Villager.Profession.ARMORER
                    || p == org.bukkit.entity.Villager.Profession.TOOLSMITH) {
                return base + 0.45;
            }
        }
        if (entity instanceof org.bukkit.entity.Mannequin) {
            return base + 0.2;
        }
        return base;
    }

    static void delete(String npcId) {
        HologramService service = HologramServices.holograms();
        if (service != null) {
            service.delete(hologramId(npcId));
        }
    }

    static String hologramId(String npcId) {
        return ("npcntag_" + npcId.toLowerCase(Locale.ROOT)).replace(' ', '_');
    }

    private static String colorName(String displayName, String fallback) {
        String name = displayName == null || displayName.isBlank() ? fallback : displayName.trim();
        if (name.indexOf('&') >= 0 || name.indexOf('§') >= 0) {
            return name;
        }
        return "&6" + name;
    }

    /** Avoid importing holo impl from NPCs — height + offset only. */
    private static final class HologramFollowLoc {
        static Location at(Entity entity, double offsetY) {
            return entity.getLocation().add(0, entity.getHeight() + offsetY, 0);
        }
    }
}
