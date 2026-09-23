package com.yapcore.regions;

import java.util.Locale;
import java.util.Optional;

/** WorldGuard-class region flags for claims and admin regions. */
public enum RegionFlag {
    PVP,
    MOB_DAMAGE,
    /**
     * All player combat + hazard damage. When {@link FlagValue#DENY}, players inside
     * take no damage and deal no damage (spawn / safe-zone god mode).
     * Aliases in {@link #parse}: {@code invincible}, {@code god}.
     */
    DAMAGE,
    BUILD,
    /**
     * Armor stands, item frames, paintings, flower pots — not doors/plates.
     * Doors / buttons / pressure plates use {@link #USE} (default allow for parkour).
     */
    INTERACT,
    /**
     * Doors, gates, buttons, levers, pressure plates. Default allow so parkour works
     * even when {@link #INTERACT} is deny.
     */
    USE,
    ENTRY,
    CHEST_ACCESS,
    FIRE_SPREAD,
    MOB_SPAWNING,
    /** Hostile mobs (Enemy) may not enter or remain in the region when denied. */
    MOB_ENTRY,
    ITEM_DROP,
    ITEM_PICKUP,
    TNT,
    CREEPER_EXPLOSION,
    /**
     * Nether / End / YaP End-door / YaP dungeon portal use. When {@link FlagValue#DENY}
     * (claim default), only the owner / ACCESS+ trust / staff may enter.
     * {@link FlagValue#ALLOW} = public use.
     * Aliases: {@code portal}, {@code nether-portal}, {@code portals}.
     */
    NETHER_PORTAL,
    /** Food bar drain / exhaustion. */
    HUNGER,
    /** Farmland trampling (player + mob). Aliases: {@code trampling}, {@code farmland}. */
    FARMLAND_TRAMPLE,
    /** Item frames, glow item frames, paintings. Alias: {@code frames}. */
    ITEM_FRAME,
    /** Armor stand place / break / manipulate. */
    ARMOR_STAND,
    /**
     * Damaging tagged YaP NPCs (villager / mannequin). When {@link FlagValue#DENY},
     * hits, fire, and zombie conversion are cancelled. Unset inherits {@link #DAMAGE}
     * (so existing hub {@code damage deny} also protects NPCs).
     * Aliases: {@code npc-protect}, {@code npc}, {@code npcs}.
     */
    NPC_DAMAGE,
    /** Natural leaf decay. */
    LEAF_DECAY,
    /** Piston extend / retract affecting the region. */
    PISTONS,
    /** Boat / minecart placement. */
    VEHICLE_PLACE,
    /** Boat / minecart destroy. */
    VEHICLE_DESTROY,
    /**
     * Client weather overlay. When {@link FlagValue#DENY}, players inside see clear skies
     * even if the world is raining; {@link FlagValue#ALLOW} (default) follows world weather.
     */
    WEATHER;

    public static Optional<RegionFlag> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (norm) {
            case "INVINCIBLE", "GOD", "INVULN" -> Optional.of(DAMAGE);
            case "TRAMPLING", "FARMLAND", "CROP_TRAMPLE" -> Optional.of(FARMLAND_TRAMPLE);
            case "FRAMES", "ITEM_FRAMES", "PAINTING", "PAINTINGS" -> Optional.of(ITEM_FRAME);
            case "ARMORSTANDS", "ARMOR_STANDS" -> Optional.of(ARMOR_STAND);
            case "NPC", "NPCS", "NPC_PROTECT", "NPCPROTECT" -> Optional.of(NPC_DAMAGE);
            case "LEAF", "LEAVES" -> Optional.of(LEAF_DECAY);
            case "PISTON", "PISTON_PROTECTION" -> Optional.of(PISTONS);
            case "VEHICLE", "VEHICLES" -> Optional.of(VEHICLE_PLACE);
            case "PORTAL", "PORTALS", "NETHERPORTAL" -> Optional.of(NETHER_PORTAL);
            default -> {
                try {
                    yield Optional.of(valueOf(norm));
                } catch (IllegalArgumentException e) {
                    yield Optional.empty();
                }
            }
        };
    }
}
