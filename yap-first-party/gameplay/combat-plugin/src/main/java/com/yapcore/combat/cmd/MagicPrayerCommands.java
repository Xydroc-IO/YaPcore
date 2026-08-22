package com.yapcore.combat.cmd;

import com.yapcore.combat.service.PrayerService;
import com.yapcore.combat.service.SpellBookService;
import com.yapcore.combat.prayer.PrayerDefinition;
import com.yapcore.combat.spell.SpellDefinition;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MagicPrayerCommands implements CommandExecutor, TabCompleter {

    private final SpellBookService spells;
    private final PrayerService prayers;

    public MagicPrayerCommands(SpellBookService spells, PrayerService prayers) {
        this.spells = spells;
        this.prayers = prayers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("spells".equals(name)) {
            return handleSpells(player);
        }
        if ("cast".equals(name)) {
            if (args.length == 0) {
                player.sendMessage("§e/cast <spell>");
                return true;
            }
            if (!player.hasPermission("yapcombat.cast")) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            spells.cast(player, args[0]);
            return true;
        }
        if ("prayer".equals(name)) {
            return handlePrayer(player, args);
        }
        return false;
    }

    private boolean handleSpells(Player player) {
        player.sendMessage("§6--- Spell book ---");
        for (SpellDefinition spell : spells.knownSpells(player)) {
            player.sendMessage("§f" + spell.id() + " §7(Lv " + spell.minMagicLevel()
                    + ", cost " + spell.prayerCost() + " pray, hit " + spell.baseMaxHit() + ")");
        }
        return true;
    }

    private boolean handlePrayer(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§e/prayer list|on <id>|off [id]|clear");
            return true;
        }
        if (!player.hasPermission("yapcombat.prayer")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                player.sendMessage("§6--- Prayers ---");
                for (PrayerDefinition def : prayers.available(player)) {
                    player.sendMessage("§f" + def.id() + " §7(Lv " + def.minPrayerLevel()
                            + ", drain " + def.drainPerTick() + "/tick)");
                }
            }
            case "on" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/prayer on <id>");
                    return true;
                }
                prayers.togglePrayer(player, args[1], true);
            }
            case "off" -> {
                if (args.length < 2) {
                    prayers.clearPrayers(player);
                } else {
                    prayers.togglePrayer(player, args[1], false);
                }
            }
            case "clear" -> prayers.clearPrayers(player);
            default -> player.sendMessage("§e/prayer list|on <id>|off [id]|clear");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("cast".equals(name) && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return spells.knownSpells(player).stream()
                    .map(SpellDefinition::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if ("prayer".equals(name)) {
            if (args.length == 1) {
                return prefix(List.of("list", "on", "off", "clear"), args[0]);
            }
            if (args.length == 2 && ("on".equalsIgnoreCase(args[0]) || "off".equalsIgnoreCase(args[0]))) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return prayers.available(player).stream()
                        .map(PrayerDefinition::id)
                        .filter(id -> id.startsWith(prefix))
                        .toList();
            }
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String partial) {
        String p = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
