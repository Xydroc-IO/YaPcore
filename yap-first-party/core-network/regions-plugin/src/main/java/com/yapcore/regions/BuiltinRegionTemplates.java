package com.yapcore.regions;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flag, message, and gamemode presets shipped with YaPRegions.
 * A template saved in the database with the same name overrides these.
 */
public final class BuiltinRegionTemplates {

    public record Preset(
            String name,
            String gameMode,
            Map<RegionFlag, FlagValue> flags,
            Map<RegionMessageKind, String> messages
    ) {
    }

    private static final Map<String, Preset> BY_NAME = build();

    private BuiltinRegionTemplates() {
    }

    public static List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    public static Preset get(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static Map<String, Preset> build() {
        Map<String, Preset> out = new LinkedHashMap<>();
        put(out, spawn());
        put(out, hub());
        put(out, wilderness());
        put(out, arena());
        put(out, creative());
        put(out, market());
        return Map.copyOf(out);
    }

    private static void put(Map<String, Preset> out, Preset preset) {
        out.put(preset.name(), preset);
    }

    /** Safe spawn: no combat, building, hunger, or mobs. Doors still work. */
    private static Preset spawn() {
        return new Preset("spawn", "adventure", deny(
                RegionFlag.PVP, RegionFlag.DAMAGE, RegionFlag.MOB_DAMAGE, RegionFlag.NPC_DAMAGE,
                RegionFlag.BUILD, RegionFlag.INTERACT, RegionFlag.CHEST_ACCESS,
                RegionFlag.FIRE_SPREAD, RegionFlag.MOB_SPAWNING, RegionFlag.MOB_ENTRY,
                RegionFlag.ITEM_DROP, RegionFlag.ITEM_PICKUP,
                RegionFlag.TNT, RegionFlag.CREEPER_EXPLOSION,
                RegionFlag.HUNGER, RegionFlag.FARMLAND_TRAMPLE,
                RegionFlag.ITEM_FRAME, RegionFlag.ARMOR_STAND,
                RegionFlag.LEAF_DECAY, RegionFlag.PISTONS,
                RegionFlag.VEHICLE_PLACE, RegionFlag.VEHICLE_DESTROY,
                RegionFlag.WEATHER),
                greet("Welcome to spawn.", "You are leaving spawn."));
    }

    /** Same protection as a saved hub pad: no PvP or building, hunger off, weather clear. */
    private static Preset hub() {
        return new Preset("hub", "adventure", deny(
                RegionFlag.PVP, RegionFlag.MOB_DAMAGE, RegionFlag.NPC_DAMAGE,
                RegionFlag.BUILD,
                RegionFlag.FIRE_SPREAD, RegionFlag.MOB_SPAWNING, RegionFlag.MOB_ENTRY,
                RegionFlag.TNT, RegionFlag.CREEPER_EXPLOSION,
                RegionFlag.HUNGER, RegionFlag.FARMLAND_TRAMPLE,
                RegionFlag.ITEM_FRAME, RegionFlag.ARMOR_STAND,
                RegionFlag.LEAF_DECAY, RegionFlag.PISTONS,
                RegionFlag.WEATHER),
                greet("Welcome to the hub.", "You are leaving the hub."));
    }

    /** Open world. Applying this clears spawn-style denies. */
    private static Preset wilderness() {
        return new Preset("wilderness", "survival", Map.of(),
                greet("You enter the wilderness.", "You leave the wilderness."));
    }

    /** PvP on, building and mobs off. */
    private static Preset arena() {
        Map<RegionFlag, FlagValue> flags = deny(
                RegionFlag.BUILD, RegionFlag.INTERACT, RegionFlag.CHEST_ACCESS,
                RegionFlag.MOB_SPAWNING, RegionFlag.MOB_ENTRY,
                RegionFlag.FIRE_SPREAD, RegionFlag.TNT, RegionFlag.CREEPER_EXPLOSION,
                RegionFlag.HUNGER);
        allow(flags, RegionFlag.PVP, RegionFlag.DAMAGE);
        return new Preset("arena", "survival", flags,
                greet("You entered the arena.", "You left the arena."));
    }

    /** Build freely. No PvP or mobs. */
    private static Preset creative() {
        Map<RegionFlag, FlagValue> flags = deny(
                RegionFlag.PVP, RegionFlag.DAMAGE, RegionFlag.MOB_DAMAGE, RegionFlag.NPC_DAMAGE,
                RegionFlag.MOB_SPAWNING, RegionFlag.MOB_ENTRY,
                RegionFlag.TNT, RegionFlag.CREEPER_EXPLOSION, RegionFlag.FIRE_SPREAD);
        allow(flags, RegionFlag.BUILD, RegionFlag.INTERACT, RegionFlag.CHEST_ACCESS);
        return new Preset("creative", "creative", flags,
                greet("Welcome to creative.", "You are leaving creative."));
    }

    /** Shop plot: chests and doors work, no building or combat. */
    private static Preset market() {
        Map<RegionFlag, FlagValue> flags = deny(
                RegionFlag.PVP, RegionFlag.DAMAGE, RegionFlag.NPC_DAMAGE, RegionFlag.BUILD,
                RegionFlag.MOB_SPAWNING, RegionFlag.MOB_ENTRY,
                RegionFlag.TNT, RegionFlag.CREEPER_EXPLOSION);
        allow(flags, RegionFlag.CHEST_ACCESS, RegionFlag.USE, RegionFlag.INTERACT);
        return new Preset("market", "adventure", flags,
                greet("Welcome to the market.", "You are leaving the market."));
    }

    private static Map<RegionFlag, FlagValue> deny(RegionFlag... flags) {
        Map<RegionFlag, FlagValue> out = new EnumMap<>(RegionFlag.class);
        for (RegionFlag flag : flags) {
            out.put(flag, FlagValue.DENY);
        }
        return out;
    }

    private static void allow(Map<RegionFlag, FlagValue> flags, RegionFlag... allow) {
        for (RegionFlag flag : allow) {
            flags.put(flag, FlagValue.ALLOW);
        }
    }

    private static Map<RegionMessageKind, String> greet(String greeting, String farewell) {
        Map<RegionMessageKind, String> out = new EnumMap<>(RegionMessageKind.class);
        out.put(RegionMessageKind.GREETING, greeting);
        out.put(RegionMessageKind.FAREWELL, farewell);
        return out;
    }
}
