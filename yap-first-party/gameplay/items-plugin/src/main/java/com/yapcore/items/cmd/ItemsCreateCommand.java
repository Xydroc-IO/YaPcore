package com.yapcore.items.cmd;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemCreateRequest;
import com.yapcore.items.item.ItemWriter;
import com.yapcore.messages.YapMessages;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /yapitems create} subcommand body. */
final class ItemsCreateCommand {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemsPlugin plugin;

    ItemsCreateCommand(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean create(CommandSender sender, String[] args, ItemsConfig cfg) {
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
        Map<String, String> flags = ItemsCommandParsing.parseFlags(args, 2);
        boolean replace = ItemsCommandParsing.flagEnabled(flags, "replace")
                || ItemsCommandParsing.flagEnabled(flags, "force");
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
        int gearAttack = ItemsCommandParsing.parseIntFlag(
                flags, "gear-attack", flags.containsKey("attack") ? "attack" : null, 0);
        int gearStrength = ItemsCommandParsing.parseIntFlag(
                flags, "gear-strength", flags.containsKey("strength") ? "strength" : null, 0);
        boolean glow = ItemsCommandParsing.flagEnabled(flags, "glow")
                || ItemsCommandParsing.flagEnabled(flags, "glint")
                || ItemsCommandParsing.flagEnabled(flags, "shiny");
        if (flags.containsKey("no-glow") || flags.containsKey("noglow")) {
            glow = false;
        }
        boolean rainbow = ItemsCommandParsing.flagEnabled(flags, "rainbow")
                || ItemsCommandParsing.flagEnabled(flags, "rainbow-name")
                || ItemsCommandParsing.flagEnabled(flags, "name-rainbow");
        if (flags.containsKey("no-rainbow") || flags.containsKey("norainbow")
                || flags.containsKey("no-rainbow-name")) {
            rainbow = false;
        }
        boolean unbreakable = ItemsCommandParsing.flagEnabled(flags, "unbreakable")
                || ItemsCommandParsing.flagEnabled(flags, "unbreaking-inf")
                || ItemsCommandParsing.flagEnabled(flags, "infinite-durability");
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
                if (!flags.containsKey("rainbow") && !flags.containsKey("rainbow-name")
                        && !flags.containsKey("name-rainbow")
                        && !flags.containsKey("no-rainbow") && !flags.containsKey("norainbow")
                        && !flags.containsKey("no-rainbow-name")) {
                    rainbow = existing.get().rainbow();
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
        List<ItemCreateRequest.AbilityWrite> abilities = ItemsCommandParsing.parseAbilityWrites(args, 2, flags);
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>(
                ItemsCommandParsing.parseEnchants(args, 2, flags));
        if (replace && exists && enchants.isEmpty()
                && !flags.containsKey("enchant") && !flags.containsKey("enchants")
                && !flags.containsKey("no-enchants") && !flags.containsKey("clear-enchants")) {
            plugin.registry().get(id).ifPresent(def -> {
                for (var e : def.enchants().entrySet()) {
                    String key = e.getKey().getKey().getKey();
                    enchants.put(key, e.getValue());
                }
            });
        }
        if (ItemsCommandParsing.flagEnabled(flags, "no-enchants")
                || ItemsCommandParsing.flagEnabled(flags, "clear-enchants")) {
            enchants.clear();
        }
        ItemCreateRequest req = new ItemCreateRequest(
                id,
                base,
                name,
                lore,
                cmd,
                glow,
                rainbow,
                unbreakable,
                abilities,
                furniture,
                gearAttack,
                gearStrength,
                enchants);
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
}
