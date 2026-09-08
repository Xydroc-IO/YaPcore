package com.yapcore.items.cmd;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemCreateRequest;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemWriter;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ItemsCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> SUBS = List.of(
            "give", "take", "list", "info", "gui", "reload", "create", "delete", "cooldown", "furniture");
    public static final List<String> COOLDOWN_PRESETS = List.of(
            "0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s");

    private final ItemsPlugin plugin;

    public ItemsCommand(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(LEGACY.deserialize("&e/yapitems <give|take|list|info|gui|reload|create|delete|cooldown|furniture>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        ItemsConfig cfg = plugin.config();
        return switch (sub) {
            case "give" -> give(sender, args, cfg);
            case "take" -> take(sender, args, cfg);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args, cfg);
            case "gui" -> gui(sender, args);
            case "reload" -> reload(sender, cfg);
            case "create" -> create(sender, args, cfg);
            case "delete" -> delete(sender, args, cfg);
            case "cooldown", "cd" -> cooldown(sender, args, cfg);
            case "furniture" -> furniture(sender, args);
            default -> {
                sender.sendMessage(LEGACY.deserialize("&cUnknown subcommand."));
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.give")) {
            YapMessages.noPermission(sender, "yapitems.give");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems give <id> [amount] [player]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        Player target = sender instanceof Player p ? p : null;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                    return true;
                }
            }
        }
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                return true;
            }
        }
        if (target == null) {
            YapMessages.playersOnly(sender);
            return true;
        }
        Player finalTarget = target;
        int finalAmount = Math.max(1, Math.min(64, amount));
        plugin.factory().create(id, finalAmount).ifPresentOrElse(stack -> YapSched.entity(plugin, finalTarget, () -> {
            finalTarget.getInventory().addItem(stack);
            String msg = cfg.msgGiven()
                    .replace("{amount}", String.valueOf(finalAmount))
                    .replace("{id}", id)
                    .replace("{player}", finalTarget.getName());
            sender.sendMessage(LEGACY.deserialize(msg));
        }), () -> sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id))));
        return true;
    }

    private boolean take(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.take") && !sender.hasPermission("yapitems.give")) {
            YapMessages.noPermission(sender, "yapitems.take");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems take <id> [amount] [player]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        Player target = sender instanceof Player p ? p : null;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                target = Bukkit.getPlayerExact(args[2]);
            }
        }
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
        }
        if (target == null) {
            YapMessages.playersOnly(sender);
            return true;
        }
        Player finalTarget = target;
        int finalAmount = Math.max(1, amount);
        YapSched.entity(plugin, finalTarget, () -> {
            int removed = plugin.factory().takeMatching(finalTarget.getInventory(), id, finalAmount);
            String msg = cfg.msgTaken()
                    .replace("{amount}", String.valueOf(removed))
                    .replace("{id}", id)
                    .replace("{player}", finalTarget.getName());
            sender.sendMessage(LEGACY.deserialize(msg));
        });
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.use")) {
            YapMessages.noPermission(sender, "yapitems.use");
            return true;
        }
        boolean rawIds = args.length >= 2 && ("--ids".equalsIgnoreCase(args[1]) || "ids".equalsIgnoreCase(args[1]));
        final String filter;
        if (!rawIds && args.length >= 2) {
            filter = args[1].toLowerCase(Locale.ROOT);
        } else if (rawIds && args.length >= 3) {
            filter = args[2].toLowerCase(Locale.ROOT);
        } else {
            filter = "";
        }
        List<String> ids = plugin.registry().all().keySet().stream()
                .filter(id -> filter.isEmpty() || id.contains(filter))
                .sorted()
                .toList();
        if (rawIds) {
            // Machine-readable lines for yap-staff client sync (hidden from normal list UX).
            for (String id : ids) {
                sender.sendMessage("yapitems:id=" + id);
            }
            return true;
        }
        sender.sendMessage(LEGACY.deserialize("&aItems (&f" + ids.size() + "&a): &f" + String.join(", ", ids)));
        return true;
    }

    private boolean info(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems info <id>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        var defOpt = plugin.registry().get(id);
        if (defOpt.isEmpty()) {
            sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id)));
            return true;
        }
        ItemDefinition def = defOpt.get();
        if (def.abilities().isEmpty()) {
            sender.sendMessage(LEGACY.deserialize("&b" + def.id() + " &7— base &f" + def.base()
                    + " &7CMD &f" + def.customModelData()
                    + " &7abilities &fnone"
                    + (def.isFurniture() ? " &7[furniture]" : "")));
            return true;
        }
        sender.sendMessage(LEGACY.deserialize("&b" + def.id() + " &7— base &f" + def.base()
                + " &7CMD &f" + def.customModelData()
                + " &7abilities &f" + def.abilities().size()
                + (def.isFurniture() ? " &7[furniture]" : "")));
        for (var ab : def.abilities()) {
            sender.sendMessage(LEGACY.deserialize("  &8• &f" + ab.type().name().toLowerCase(Locale.ROOT)
                    + " &7(" + ab.trigger().name().toLowerCase(Locale.ROOT)
                    + ", cd &f" + formatCooldown(ab.cooldownMs()) + "&7)"));
        }
        return true;
    }

    private static String formatCooldown(long ms) {
        if (ms <= 0L) {
            return "0s";
        }
        if (ms % 1000L == 0L) {
            return (ms / 1000L) + "s";
        }
        return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    private boolean gui(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.gui")) {
            YapMessages.noPermission(sender, "yapitems.gui");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(LEGACY.deserialize("&cPlayer not found."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            YapMessages.playersOnly(sender);
            return true;
        }
        plugin.gui().open(target, 0);
        return true;
    }

    private boolean reload(CommandSender sender, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(LEGACY.deserialize(cfg.msgReloaded().replace("{count}", String.valueOf(plugin.registry().size()))));
        return true;
    }

    private boolean create(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.create") && !sender.hasPermission("yapitems.admin")) {
            YapMessages.noPermission(sender, "yapitems.create");
            return true;
        }
        // /yapitems create <id> --base <mat> --name <text> [--cmd n] [--ability type] [--template sword|tool|gem|prop]
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize(
                    "&cUsage: /yapitems create <id> --name <text> [--ability <type> ...] [--abilities a,b,c] [--damage <n>] [--range <n>] [--cooldown <t>]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_]+")) {
            sender.sendMessage(LEGACY.deserialize("&cId must be [a-z0-9_]+"));
            return true;
        }
        Map<String, String> flags = parseFlags(args, 2);
        boolean replace = flagEnabled(flags, "replace") || flagEnabled(flags, "force");
        boolean exists = plugin.registry().get(id).isPresent();
        if (exists && !replace) {
            sender.sendMessage(LEGACY.deserialize("&cItem already exists: &f" + id
                    + "&c. Use &f--replace&c to overwrite a custom item."));
            return true;
        }
        if (replace && exists && !plugin.writer().isCustom(id)) {
            sender.sendMessage(LEGACY.deserialize("&cCannot replace builtin/example items — only items/custom/."));
            return true;
        }
        String template = flags.get("template");
        Material base = flags.containsKey("base")
                ? Material.matchMaterial(flags.get("base"))
                : ItemWriter.templateBase(template);
        if (base == null || !base.isItem()) {
            sender.sendMessage(LEGACY.deserialize("&cInvalid base material."));
            return true;
        }
        String name = flags.getOrDefault("name", "&f" + id);
        if (flags.containsKey("nameb64")) {
            try {
                name = new String(java.util.Base64.getUrlDecoder().decode(flags.get("nameb64")),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(LEGACY.deserialize("&cInvalid --nameb64"));
                return true;
            }
        }
        int cmd;
        if (flags.containsKey("cmd")) {
            try {
                cmd = Integer.parseInt(flags.get("cmd"));
            } catch (NumberFormatException e) {
                sender.sendMessage(LEGACY.deserialize("&cInvalid --cmd"));
                return true;
            }
        } else if (replace && exists) {
            cmd = plugin.registry().get(id).map(d -> d.customModelData()).orElse(0);
            if (cmd <= 0) {
                cmd = template != null
                        ? ItemWriter.templateCmd(template, plugin.registry().nextFreeCmd())
                        : plugin.registry().nextFreeCmd();
            }
        } else if (template != null) {
            cmd = ItemWriter.templateCmd(template, plugin.registry().nextFreeCmd());
        } else {
            cmd = plugin.registry().nextFreeCmd();
        }
        boolean furniture = ItemWriter.templateIsFurniture(template)
                || "true".equalsIgnoreCase(flags.get("furniture"));
        int gearAttack = parseIntFlag(flags, "gear-attack", flags.containsKey("attack") ? "attack" : null, 0);
        int gearStrength = parseIntFlag(flags, "gear-strength", flags.containsKey("strength") ? "strength" : null, 0);
        boolean glow = flagEnabled(flags, "glow") || flagEnabled(flags, "glint") || flagEnabled(flags, "shiny");
        if (flags.containsKey("no-glow") || flags.containsKey("noglow")) {
            glow = false;
        }
        boolean unbreakable = flagEnabled(flags, "unbreakable") || flagEnabled(flags, "unbreaking-inf")
                || flagEnabled(flags, "infinite-durability");
        if (flags.containsKey("no-unbreakable") || flags.containsKey("breakable")) {
            unbreakable = false;
        }
        // Edit without explicit flags: keep existing look flags
        if (replace && exists) {
            var existing = plugin.registry().get(id);
            if (existing.isPresent()) {
                if (!flags.containsKey("glow") && !flags.containsKey("glint") && !flags.containsKey("shiny")
                        && !flags.containsKey("no-glow") && !flags.containsKey("noglow")) {
                    glow = existing.get().glow();
                }
                if (!flags.containsKey("unbreakable") && !flags.containsKey("unbreaking-inf")
                        && !flags.containsKey("infinite-durability")
                        && !flags.containsKey("no-unbreakable") && !flags.containsKey("breakable")) {
                    unbreakable = existing.get().unbreakable();
                }
            }
        }
        List<String> lore = new ArrayList<>();
        if (flags.containsKey("lore")) {
            lore.add(flags.get("lore"));
        } else {
            lore.add(replace ? "&7Custom YaP item (edited)" : "&7Custom YaP item");
        }
        List<ItemCreateRequest.AbilityWrite> abilities = parseAbilityWrites(args, 2, flags);
        ItemCreateRequest req = new ItemCreateRequest(
                id,
                base,
                name,
                lore,
                cmd,
                glow,
                unbreakable,
                abilities,
                furniture,
                gearAttack,
                gearStrength);
        try {
            plugin.writer().writeCustom(req);
            plugin.reloadAll();
            if (replace && exists) {
                sender.sendMessage(LEGACY.deserialize("&aUpdated item &f" + id));
                sender.sendMessage("yapitems:updated=" + id);
            } else {
                sender.sendMessage(LEGACY.deserialize(cfg.msgCreated().replace("{id}", id)));
                sender.sendMessage("yapitems:created=" + id);
            }
            if (sender instanceof Player player) {
                plugin.factory().create(id, 1).ifPresent(stack ->
                        YapSched.entity(plugin, player, () -> player.getInventory().addItem(stack)));
            }
        } catch (Exception e) {
            sender.sendMessage(LEGACY.deserialize("&cCreate failed: &f" + e.getMessage()));
        }
        return true;
    }

    /**
     * Supports {@code --abilities dash,heal,effect} and/or repeated
     * {@code --ability lightning_dash --damage 40 --ability heal --amount 8}.
     */
    private static List<ItemCreateRequest.AbilityWrite> parseAbilityWrites(
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

    private static boolean flagEnabled(Map<String, String> flags, String key) {
        if (!flags.containsKey(key)) {
            return false;
        }
        String v = flags.get(key);
        return v == null || v.isBlank()
                || "true".equalsIgnoreCase(v)
                || "yes".equalsIgnoreCase(v)
                || "1".equals(v);
    }

    private static int parseIntFlag(Map<String, String> flags, String primary, String alt, int def) {
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

    private static Map<String, Object> abilityParamsFromFlags(String ability, Map<String, String> flags) {
        Map<String, Object> params = new java.util.LinkedHashMap<>(abilityPresets(ability));
        putDoubleParam(params, flags, "damage");
        putDoubleParam(params, flags, "range");
        putDoubleParam(params, flags, "power");
        putDoubleParam(params, flags, "amount");
        putDoubleParam(params, flags, "y");
        putDoubleParam(params, flags, "radius");
        if (flags.containsKey("effect")) {
            params.put("effect", flags.get("effect"));
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

    private boolean cooldown(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin") && !sender.hasPermission("yapitems.create")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems cooldown <id> <duration>"));
            sender.sendMessage(LEGACY.deserialize("&7Examples: &f0s &78s &f12s &730s &f2.5s"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.registry().get(id).isEmpty()) {
            sender.sendMessage(LEGACY.deserialize(cfg.msgUnknown().replace("{id}", id)));
            return true;
        }
        String duration = ItemWriter.normalizeCooldown(args[2]);
        try {
            var written = plugin.writer().setAbilityCooldown(id, duration);
            if (written.isEmpty()) {
                sender.sendMessage(LEGACY.deserialize(
                        "&cCould not update cooldown for &f" + id + "&c."));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(LEGACY.deserialize(
                    "&aSet &f" + id + "&a ability cooldown to &f" + duration + "&a."));
        } catch (Exception e) {
            sender.sendMessage(LEGACY.deserialize("&cCooldown update failed: &f" + e.getMessage()));
        }
        return true;
    }

    private boolean delete(CommandSender sender, String[] args, ItemsConfig cfg) {
        if (!sender.hasPermission("yapitems.admin") && !sender.hasPermission("yapitems.create")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems delete <id>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.writer().isCustom(id)) {
            sender.sendMessage(LEGACY.deserialize("&cOnly items under items/custom/ can be deleted (or file missing)."));
            return true;
        }
        if (!plugin.writer().deleteCustom(id)) {
            sender.sendMessage(LEGACY.deserialize("&cDelete failed for &f" + id));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(LEGACY.deserialize(cfg.msgDeleted().replace("{id}", id)));
        sender.sendMessage("yapitems:deleted=" + id);
        return true;
    }

    private boolean furniture(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapitems.admin")) {
            YapMessages.noPermission(sender, "yapitems.admin");
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (args.length < 2 || !"remove".equalsIgnoreCase(args[1])) {
            sender.sendMessage(LEGACY.deserialize("&cUsage: /yapitems furniture remove"));
            return true;
        }
        plugin.furniture().removeLookingAt(player);
        return true;
    }

    private static Map<String, String> parseFlags(String[] args, int start) {
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give", "take", "info", "delete", "cooldown", "cd" -> filter(plugin.registry().all().keySet().stream().sorted().toList(), args[1]);
                case "furniture" -> filter(List.of("remove"), args[1]);
                case "gui" -> filter(onlineNames(), args[1]);
                case "create" -> List.of("<id>");
                default -> List.of();
            };
        }
        if (args.length == 3 && ("cooldown".equals(sub) || "cd".equals(sub))) {
            return filter(COOLDOWN_PRESETS, args[2]);
        }
        if (args.length == 3 && ("give".equals(sub) || "take".equals(sub))) {
            List<String> opts = new ArrayList<>(List.of("1", "16", "64"));
            opts.addAll(onlineNames());
            return filter(opts, args[2]);
        }
        if (args.length == 4 && ("give".equals(sub) || "take".equals(sub))) {
            return filter(onlineNames(), args[3]);
        }
        if ("create".equals(sub) && args[args.length - 1].startsWith("--")) {
            return filter(List.of(
                    "--base", "--name", "--nameb64", "--cmd", "--ability", "--abilities", "--cooldown", "--cd",
                    "--template", "--furniture", "--damage", "--range", "--power", "--radius",
                    "--effect", "--projectile", "--kind", "--amplifier", "--duration",
                    "--amount", "--gear-attack", "--gear-strength", "--lore", "--trigger",
                    "--glow", "--glint", "--no-glow", "--unbreakable", "--no-unbreakable", "--breakable",
                    "--replace", "--force"), args[args.length - 1]);
        }
        if ("create".equals(sub) && args.length >= 3) {
            String prev = args[args.length - 2];
            if ("--template".equals(prev)) {
                return filter(List.of(
                        "sword", "axe", "spear", "mace", "bow", "crossbow", "trident",
                        "shield", "wand", "pickaxe", "shovel", "hoe", "shears", "fishing_rod",
                        "amethyst", "emerald", "diamond", "nether_star", "echo_shard", "totem",
                        "golden_apple", "heart_of_the_sea", "prismarine",
                        "prop_paper", "prop_oak", "prop_stone", "prop_chest", "prop_ender_chest",
                        "prop_lantern", "prop_soul_lantern", "prop_beacon", "prop_pot", "prop_bell",
                        "prop_armor_stand", "key"
                ), args[args.length - 1]);
            }
            if ("--ability".equals(prev)) {
                return filter(Arrays.stream(com.yapcore.items.ability.AbilityType.values())
                        .map(e -> e.name().toLowerCase(Locale.ROOT))
                        .toList(), args[args.length - 1]);
            }
            if ("--effect".equals(prev)) {
                return filter(List.of(
                        "SPEED", "STRENGTH", "REGENERATION", "RESISTANCE", "JUMP_BOOST",
                        "INVISIBILITY", "FIRE_RESISTANCE", "HASTE", "NIGHT_VISION",
                        "SLOWNESS", "WEAKNESS", "POISON", "WITHER", "BLINDNESS", "NAUSEA", "LEVITATION"
                ), args[args.length - 1]);
            }
            if ("--projectile".equals(prev) || "--kind".equals(prev)) {
                return filter(List.of("snowball", "arrow", "egg", "ender_pearl", "fireball"), args[args.length - 1]);
            }
            if ("--radius".equals(prev)) {
                return filter(List.of("2", "3", "4", "5", "6", "8", "12"), args[args.length - 1]);
            }
            if ("--cooldown".equals(prev) || "--cd".equals(prev)) {
                return filter(COOLDOWN_PRESETS, args[args.length - 1]);
            }
            if ("--damage".equals(prev)) {
                return filter(List.of("2", "4", "8", "12", "20", "40", "100", "200", "-1", "kill"), args[args.length - 1]);
            }
            if ("--range".equals(prev)) {
                return filter(List.of("4", "6", "8", "12", "16", "24", "32", "48", "64", "80", "100"), args[args.length - 1]);
            }
            if ("--gear-attack".equals(prev) || "--gear-strength".equals(prev)) {
                return filter(List.of("0", "2", "5", "10", "20", "50"), args[args.length - 1]);
            }
            if ("--base".equals(prev)) {
                return filter(Arrays.stream(Material.values())
                        .filter(Material::isItem)
                        .map(m -> m.name().toLowerCase(Locale.ROOT))
                        .limit(40)
                        .toList(), args[args.length - 1]);
            }
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
