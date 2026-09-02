package com.yapcore.moderation.cmd;

import com.yapcore.moderation.ModerationPlugin;
import com.yapcore.moderation.seen.SeenPlayerRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.command.CommandSender;

final class SeenCommands {
    private final ModerationPlugin plugin;
    private final SeenPlayerRepository seen;

    SeenCommands(ModerationPlugin plugin, SeenPlayerRepository seen) {
        this.plugin = plugin;
        this.seen = seen;
    }

    boolean dump(CommandSender sender, String[] args) {
        String mode = args.length >= 2 ? args[1].toLowerCase() : "";
        if ("snapshot".equals(mode)) {
            try {
                var file = plugin.getDataFolder().toPath().resolve("seen-players.json");
                seen.writeSnapshot(file);
                sender.sendMessage("§aPlayer directory snapshot written (" + seen.list(20000).size() + ").");
            } catch (Exception e) {
                sender.sendMessage("§cSnapshot failed: " + e.getMessage());
            }
            return true;
        }
        boolean json = "json".equals(mode);
        YapSched.async(plugin, () -> {
            try {
                if (json) {
                    String payload = seen.toJsonArray(20000);
                    YapSched.global(plugin, () -> sender.sendMessage("YAPSEEN_JSON:" + payload));
                    return;
                }
                var rows = seen.list(50);
                YapSched.global(plugin, () -> {
                    sender.sendMessage("§6Seen players (" + rows.size() + " shown):");
                    for (var row : rows) {
                        sender.sendMessage("§f" + row.username()
                                + (row.nickname().isBlank() ? "" : " §7(" + row.nickname() + ")")
                                + " §8" + row.uuid()
                                + " §7" + (row.lastIp().isBlank() ? "—" : row.lastIp()));
                    }
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> sender.sendMessage("§cSeen list failed: " + e.getMessage()));
            }
        });
        return true;
    }
}
