package com.yapcore.mmocontent.cmd;

import com.yapcore.mmo.HiscoreEntry;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmocontent.MmoContentConfig;
import com.yapcore.mmocontent.MmoContentPlugin;
import com.yapcore.mmocontent.db.HiscoreRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HiscoresCommand implements CommandExecutor, TabCompleter {

    private final MmoContentPlugin plugin;
    private final MmoContentConfig config;
    private final HiscoreRepository hiscores;

    public HiscoresCommand(MmoContentPlugin plugin, MmoContentConfig config, HiscoreRepository hiscores) {
        this.plugin = plugin;
        this.config = config;
        this.hiscores = hiscores;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapmmo.hiscores")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/hiscores <skill> [page]");
            return true;
        }
        SkillId skillId = SkillId.of(args[0]);
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        int finalPage = page;
        YapSched.async(plugin, () -> {
            try {
                int offset = (finalPage - 1) * config.hiscorePageSize();
                List<HiscoreEntry> rows = hiscores.top(skillId, config.hiscorePageSize(), offset);
                YapSched.global(plugin, () -> render(sender, skillId, finalPage, rows));
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cHiscores unavailable."));
                plugin.getLogger().warning("hiscores query failed: " + e.getMessage());
            }
        });
        return true;
    }

    private void render(CommandSender sender, SkillId skillId, int page, List<HiscoreEntry> rows) {
        sender.sendMessage("§6Hiscores — §f" + skillId.id() + " §7(page " + page + ")");
        if (rows.isEmpty()) {
            sender.sendMessage("§7No entries yet.");
            return;
        }
        for (HiscoreEntry row : rows) {
            String name = resolveName(row.playerId(), row.playerName());
            sender.sendMessage("§e#" + row.rank() + " §f" + name
                    + " §7— lvl §a" + row.level() + " §7(" + (int) row.xp() + " xp)");
        }
    }

    private static String resolveName(java.util.UUID uuid, String fallback) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : fallback;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> skills = new ArrayList<>();
            SkillServices.find().ifPresent(svc -> svc.definitions().forEach(def -> skills.add(def.id().id())));
            if (skills.isEmpty()) {
                skills.addAll(List.of("mining", "fishing", "smithing", "attack", "strength", "defence", "hitpoints"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return skills.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
