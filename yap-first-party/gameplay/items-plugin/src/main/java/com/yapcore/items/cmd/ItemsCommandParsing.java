package com.yapcore.items.cmd;

import com.yapcore.items.item.ItemCreateRequest;
import com.yapcore.items.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Flag / ability / enchant parsing helpers for {@link ItemsCommand} / {@link ItemsCreateCommand}. */
final class ItemsCommandParsing {

    private ItemsCommandParsing() {
    }

    static String formatCooldown(long ms) {
        if (ms <= 0L) {
            return "0s";
        }
        if (ms % 1000L == 0L) {
            return (ms / 1000L) + "s";
        }
        return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    /**
     * Supports {@code --enchants sharpness:5,unbreaking:3} and repeated
     * {@code --enchant sharpness:5}.
     */
    static Map<String, Integer> parseEnchants(String[] args, int start, Map<String, String> flags) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (flags.containsKey("enchants")) {
            putEnchantPieces(out, flags.get("enchants"));
        }
        for (int i = start; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || a.length() < 3) {
                continue;
            }
            String key = a.substring(2).toLowerCase(Locale.ROOT);
            if (!"enchant".equals(key) && !"ench".equals(key)) {
                continue;
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                continue;
            }
            putEnchantPieces(out, args[++i]);
        }
        return out;
    }

    private static void putEnchantPieces(Map<String, Integer> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String piece = part.trim().toLowerCase(Locale.ROOT);
            if (piece.isBlank()) {
                continue;
            }
            String name = piece;
            int level = 1;
            int colon = piece.indexOf(':');
            if (colon > 0) {
                name = piece.substring(0, colon).trim();
                try {
                    level = Integer.parseInt(piece.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    level = 1;
                }
            }
            name = name.replace('-', '_').replace(' ', '_');
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            if (name.isBlank()) {
                continue;
            }
            out.put(name, Math.max(1, Math.min(10, level)));
        }
    }

    /**
     * Supports {@code --abilities dash,heal,effect} and/or repeated
     * {@code --ability lightning_dash --damage 40 --ability heal --amount 8}.
     */
    static List<ItemCreateRequest.AbilityWrite> parseAbilityWrites(
            String[] args, int start, Map<String, String> flags) {
        List<ItemCreateRequest.AbilityWrite> out = new ArrayList<>();
        String sharedCd = ItemWriter.normalizeCooldown(
                flags.getOrDefault("cooldown", flags.getOrDefault("cd", "5s")));

        if (flags.containsKey("abilities")) {
            for (String part : flags.get("abilities").split(",")) {
                String piece = part.trim().toLowerCase(Locale.ROOT);
                if (piece.isBlank() || "none".equals(piece)) {
                    continue;
                }
                String type = piece;
                String trigger = "RIGHT_CLICK";
                int colon = piece.indexOf(':');
                if (colon > 0) {
                    type = piece.substring(0, colon).trim();
                    trigger = piece.substring(colon + 1).trim();
                }
                if (type.isBlank()) {
                    continue;
                }
                out.add(new ItemCreateRequest.AbilityWrite(
                        type,
                        trigger,
                        sharedCd,
                        abilityParamsFromFlags(type, flags)));
            }
        }

        // Repeated --ability blocks (params apply until the next --ability).
        String currentType = null;
        Map<String, String> currentFlags = new java.util.LinkedHashMap<>();
        for (int i = start; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || a.length() < 3) {
                continue;
            }
            String key = a.substring(2).toLowerCase(Locale.ROOT);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                if ("name".equals(key) || "lore".equals(key) || "text".equals(key)) {
                    StringBuilder sb = new StringBuilder(args[++i]);
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        sb.append(' ').append(args[++i]);
                    }
                    value = sb.toString();
                } else {
                    value = args[++i];
                }
            }
            if ("ability".equals(key)) {
                if (currentType != null) {
                    out.add(writeAbility(currentType, currentFlags, flags, sharedCd));
                }
                currentType = value.toLowerCase(Locale.ROOT);
                currentFlags = new java.util.LinkedHashMap<>();
                continue;
            }
            if (currentType != null) {
                currentFlags.put(key, value);
            }
        }
        if (currentType != null && !"none".equals(currentType)) {
            out.add(writeAbility(currentType, currentFlags, flags, sharedCd));
        }

        // Fallback: single --ability without block parsing already handled above;
        // if still empty and flags has ability once via parseFlags last-wins:
        if (out.isEmpty() && flags.containsKey("ability") && !"none".equalsIgnoreCase(flags.get("ability"))) {
            String type = flags.get("ability").toLowerCase(Locale.ROOT);
            out.add(new ItemCreateRequest.AbilityWrite(
                    type,
                    flags.getOrDefault("trigger", "RIGHT_CLICK"),
                    sharedCd,
                    abilityParamsFromFlags(type, flags)));
        }

        // Deduplicate accidental double-parse when both --abilities and repeated --ability used? keep all.
        // But repeated --ability also ends up in parseFlags as last ability — avoid duplicating that path:
        // If we already collected from repeated blocks, don't also add from flags.ability alone —
        // the block above only runs when out.isEmpty().

        return out;
    }

    /** Per-ability flags override shared create flags (so trailing --damage/--effect apply to every ability). */
    private static ItemCreateRequest.AbilityWrite writeAbility(
            String type,
            Map<String, String> currentFlags,
            Map<String, String> sharedFlags,
            String sharedCd) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(sharedFlags);
        merged.putAll(currentFlags);
        return new ItemCreateRequest.AbilityWrite(
                type,
                currentFlags.getOrDefault("trigger", sharedFlags.getOrDefault("trigger", "RIGHT_CLICK")),
                ItemWriter.normalizeCooldown(currentFlags.getOrDefault("cooldown",
                        currentFlags.getOrDefault("cd", sharedCd))),
                abilityParamsFromFlags(type, merged));
    }

    static boolean flagEnabled(Map<String, String> flags, String key) {
        if (!flags.containsKey(key)) {
            return false;
        }
        String v = flags.get(key);
        return v == null || v.isBlank()
                || "true".equalsIgnoreCase(v)
                || "yes".equalsIgnoreCase(v)
                || "1".equals(v);
    }

    static int parseIntFlag(Map<String, String> flags, String primary, String alt, int def) {
        String raw = flags.get(primary);
        if ((raw == null || raw.isBlank()) && alt != null) {
            raw = flags.get(alt);
        }
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static Map<String, Object> abilityParamsFromFlags(String ability, Map<String, String> flags) {
        Map<String, Object> params = new java.util.LinkedHashMap<>(abilityPresets(ability));
        putDoubleParam(params, flags, "damage");
        putDoubleParam(params, flags, "range");
        putDoubleParam(params, flags, "power");
        putDoubleParam(params, flags, "amount");
        putDoubleParam(params, flags, "y");
        putDoubleParam(params, flags, "radius");
        if (flags.containsKey("effect") || flags.containsKey("effects")) {
            String raw = flags.getOrDefault("effects", flags.get("effect"));
            if (raw != null && !raw.isBlank()) {
                if (raw.indexOf(',') >= 0 || raw.indexOf(';') >= 0 || raw.indexOf('+') >= 0 || raw.indexOf('|') >= 0) {
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (String part : raw.split("[,;+/|]+")) {
                        String e = part.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                        if (!e.isEmpty() && !list.contains(e)) {
                            list.add(e);
                        }
                        if (list.size() >= 6) {
                            break;
                        }
                    }
                    if (!list.isEmpty()) {
                        params.put("effects", list);
                        params.put("effect", list.get(0));
                    }
                } else {
                    params.put("effect", raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
                }
            }
        }
        if (flags.containsKey("instant-kill") || flags.containsKey("instakill") || flags.containsKey("kill")) {
            String raw = flags.getOrDefault("instant-kill", flags.getOrDefault("instakill", flags.get("kill")));
            if (raw == null || raw.isBlank() || "true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw) || "1".equals(raw)) {
                params.put("damage", -1);
            }
        }
        String dmgRaw = flags.get("damage");
        if (dmgRaw != null && ("kill".equalsIgnoreCase(dmgRaw) || "instakill".equalsIgnoreCase(dmgRaw)
                || "instant_kill".equalsIgnoreCase(dmgRaw) || "instant-kill".equalsIgnoreCase(dmgRaw))) {
            params.put("damage", -1);
        }
        if (flags.containsKey("duration")) {
            putDoubleParam(params, flags, "duration");
        }
        if (flags.containsKey("amplifier")) {
            putDoubleParam(params, flags, "amplifier");
            Object amp = params.get("amplifier");
            if (amp instanceof Number n) {
                params.put("amplifier", Math.max(0, Math.min(99, n.intValue())));
            }
        }
        if (flags.containsKey("text")) {
            params.put("text", flags.get("text"));
        }
        if (flags.containsKey("projectile") || flags.containsKey("kind")) {
            String kind = flags.getOrDefault("projectile", flags.get("kind"));
            if (kind != null && !kind.isBlank()) {
                params.put("projectile", kind.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (flags.containsKey("sound")) {
            String sound = flags.get("sound");
            if (sound != null && !sound.isBlank() && !"default".equalsIgnoreCase(sound)) {
                params.put("sound", sound.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            }
        }
        if (flags.containsKey("particle")) {
            String particle = flags.get("particle");
            if (particle != null && !particle.isBlank() && !"default".equalsIgnoreCase(particle)) {
                params.put("particle", particle.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            }
        }
        if (flags.containsKey("count")) {
            putDoubleParam(params, flags, "count");
        }
        if (flags.containsKey("no-fx") || flags.containsKey("nofx")) {
            params.put("fx", false);
        } else if (flags.containsKey("fx")) {
            String v = flags.get("fx");
            if (v == null || v.isBlank()
                    || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v)) {
                params.put("fx", true);
            } else {
                params.put("fx", false);
            }
        }
        return params;
    }

    private static void putDoubleParam(Map<String, Object> params, Map<String, String> flags, String key) {
        String raw = flags.get(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            if (v == Math.rint(v) && Math.abs(v) < Integer.MAX_VALUE) {
                params.put(key, (int) Math.rint(v));
            } else {
                params.put(key, v);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static Map<String, Object> abilityPresets(String ability) {
        if (ability == null) {
            return Map.of();
        }
        return switch (ability.toLowerCase(Locale.ROOT)) {
            case "heal" -> Map.of("amount", 6);
            case "launch" -> Map.of("power", 1.2, "y", 0.5);
            case "lightning_dash", "dash", "blink" -> Map.of("range", 8, "damage", 4);
            case "smite_target" -> Map.of("range", 16, "damage", 8);
            case "explode" -> Map.of("power", 2.0, "fire", false);
            case "effect" -> Map.of("effect", "SPEED", "duration", 100, "amplifier", 0);
            case "area_effect" -> Map.of("effect", "SLOWNESS", "duration", 60, "amplifier", 0, "radius", 4);
            case "absorb" -> Map.of("amplifier", 1, "duration", 200);
            case "ground_slam" -> Map.of("radius", 4, "damage", 6, "hop", 0.35);
            case "pull", "push" -> Map.of("radius", 5, "strength", 1.2);
            case "break_block" -> Map.of("range", 5, "radius", 0, "amount", 1);
            case "projectile" -> Map.of("projectile", "snowball", "speed", 1.5);
            case "fireball" -> Map.of("speed", 1.2, "power", 1.0, "fire", true);
            case "message" -> Map.of("text", "&aAbility!");
            default -> Map.of();
        };
    }

    static Map<String, String> parseFlags(String[] args, int start) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (int i = start; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || a.length() < 3) {
                continue;
            }
            String key = a.substring(2).toLowerCase(Locale.ROOT);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                // join until next flag for --name
                if ("name".equals(key)) {
                    StringBuilder sb = new StringBuilder(args[++i]);
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        sb.append(' ').append(args[++i]);
                    }
                    out.put(key, sb.toString());
                } else {
                    out.put(key, args[++i]);
                }
            } else {
                out.put(key, "true");
            }
        }
        return out;
    }
}
