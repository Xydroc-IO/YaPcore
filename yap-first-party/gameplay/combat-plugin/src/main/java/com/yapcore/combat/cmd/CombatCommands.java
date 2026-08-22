package com.yapcore.combat.cmd;

import com.yapcore.combat.CombatPlugin;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.combat.combo.ComboService;
import com.yapcore.mmo.CombatStats;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatCommands implements CommandExecutor, TabCompleter {

    private final CombatPlugin plugin;
    private final CombatServiceImpl combat;
    private final StatusEffectService status;
    private final ComboService combo;

    public CombatCommands(
            CombatPlugin plugin,
            CombatServiceImpl combat,
            StatusEffectService status,
            ComboService combo) {
        this.plugin = plugin;
        this.combat = combat;
        this.status = status;
        this.combo = combo;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("yapcombat".equals(name)) {
            return handleAdmin(sender, args);
        }
        return handleCombat(sender, args);
    }

    private boolean handleCombat(CommandSender sender, String[] args) {
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("yapcombat.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadCombat();
            sender.sendMessage("§aYaPCombat reloaded.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!player.hasPermission("yapcombat.use")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        showStats(player, player);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapcombat.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 4 && "admin".equalsIgnoreCase(args[0]) && "sethp".equalsIgnoreCase(args[1])) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            int hp;
            try {
                hp = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid HP.");
                return true;
            }
            combat.setHp(target.getUniqueId(), hp).thenRun(() ->
                    sender.sendMessage("§aSet §f" + target.getName() + "§a HP to §f" + hp));
            return true;
        }
        sender.sendMessage("§eUsage: /yapcombat admin sethp <player> <hp>");
        return true;
    }

    private void showStats(Player viewer, Player target) {
        CombatStats stats = combat.stats(target);
        viewer.sendMessage("§6--- Combat stats: §f" + target.getName() + " §6---");
        viewer.sendMessage(String.format(
                "§7Atk §f%d §7Str §f%d §7Def §f%d §7HP lvl §f%d",
                stats.attack(), stats.strength(), stats.defence(), stats.hitpoints()));
        viewer.sendMessage(String.format(
                "§7Rng §f%d §7Mag §f%d §7Pray §f%d §7(§f%d§7/§f%d§7 pray pts)",
                stats.ranged(), stats.magic(), stats.prayer(), stats.currentPrayer(), stats.maxPrayer()));
        viewer.sendMessage(String.format(
                "§7Gear +atk §f%d §7+str §f%d §7+def §f%d §7+rng §f%d §7+mag §f%d §7+pray §f%d",
                stats.gear().attackBonus(), stats.gear().strengthBonus(), stats.gear().defenceBonus(),
                stats.gear().rangedBonus(), stats.gear().magicBonus(), stats.gear().prayerBonus()));
        var prayers = combat.prayerModifiers(target);
        viewer.sendMessage(String.format(
                "§7Prayer +atk §f%d §7+str §f%d §7+def §f%d §7+rng §f%d §7+mag §f%d",
                prayers.attackBoost(), prayers.strengthBoost(), prayers.defenceBoost(),
                prayers.rangedBoost(), prayers.magicBoost()));
        if (prayers.protectMelee() > 0 || prayers.protectMissiles() > 0 || prayers.protectMagic() > 0) {
            viewer.sendMessage(String.format(
                    "§7Protect melee §f%.0f%% §7missiles §f%.0f%% §7magic §f%.0f%%",
                    prayers.protectMelee() * 100, prayers.protectMissiles() * 100, prayers.protectMagic() * 100));
        }
        java.util.Set<String> active = combat.activePrayers(target);
        if (!active.isEmpty()) {
            viewer.sendMessage("§7Active prayers: §f" + String.join(", ", active));
        }
        viewer.sendMessage(String.format(
                "§7Buffs +atk §f%d §7+str §f%d §7+def §f%d",
                stats.buffs().attackBoost(), stats.buffs().strengthBoost(), stats.buffs().defenceBoost()));
        viewer.sendMessage(String.format(
                "§7Health §f%d§7/§f%d §7(custom)",
                stats.currentHp(), stats.maxHp()));
        int comboCount = combo.currentCombo(target);
        if (comboCount > 0) {
            viewer.sendMessage(String.format(
                    "§7Combo §f%dx §7(§f+%d%%§7 damage)",
                    comboCount,
                    (int) Math.round((combo.currentMultiplier(target) - 1.0) * 100)));
        }
        java.util.List<String> effects = status.describe(target);
        if (!effects.isEmpty()) {
            viewer.sendMessage("§7Status: §f" + String.join("§7, §f", effects));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if ("combat".equalsIgnoreCase(command.getName())) {
            if (args.length == 1 && sender.hasPermission("yapcombat.admin")) {
                if ("reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("reload");
                }
                if ("stats".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("stats");
                }
            }
        } else if ("yapcombat".equalsIgnoreCase(command.getName()) && sender.hasPermission("yapcombat.admin")) {
            if (args.length == 1 && "admin".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("admin");
            } else if (args.length == 2 && "admin".equalsIgnoreCase(args[0])
                    && "sethp".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("sethp");
            } else if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "sethp".equalsIgnoreCase(args[1])) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                }
            }
        }
        return out;
    }
}
