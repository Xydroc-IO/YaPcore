package com.yapcore.conquest.cmd;

import com.yapcore.conquest.ConquestChunk;
import com.yapcore.conquest.ConquestConfig;
import com.yapcore.conquest.ConquestPlugin;
import com.yapcore.conquest.ConquestZoneType;
import com.yapcore.conquest.map.ConquestMapRenderer;
import com.yapcore.conquest.service.ConquestServiceImpl;
import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionServices;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ConquestCommands implements CommandExecutor, TabCompleter {

    private final ConquestPlugin plugin;
    private final ConquestConfig config;
    private final ConquestServiceImpl conquest;

    public ConquestCommands(ConquestPlugin plugin, ConquestConfig config, ConquestServiceImpl conquest) {
        this.plugin = plugin;
        this.config = config;
        this.conquest = conquest;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!player.hasPermission("yapconquest.use")) {
            YapMessages.noPermission(player, "yapconquest.use");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "claim" -> claim(player);
            case "unclaim" -> unclaim(player);
            case "map" -> map(player);
            case "info" -> info(player);
            case "power" -> power(player);
            case "help", "?" -> help(player, label);
            default -> {
                player.sendMessage("§cUnknown subcommand. Try §f/" + label + " help§c.");
                yield true;
            }
        };
    }

    private boolean claim(Player player) {
        boolean wasClaimed = conquest.chunkAt(player.getLocation()).isPresent();
        conquest.claim(player, player.getLocation())
                .thenAccept(chunk -> YapSched.entity(plugin, player, () -> {
                    if (wasClaimed) {
                        player.sendMessage("§aOverclaimed chunk §f" + chunk.chunkX() + "," + chunk.chunkZ()
                                + " §a(cost " + chunk.powerCost() + ").");
                    } else {
                        player.sendMessage("§aClaimed chunk §f" + chunk.chunkX() + "," + chunk.chunkZ()
                                + " §a(cost " + chunk.powerCost() + ").");
                    }
                }))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean unclaim(Player player) {
        conquest.unclaim(player, player.getLocation())
                .thenRun(() -> YapSched.entity(plugin, player, () ->
                        player.sendMessage("§aChunk unclaimed.")))
                .exceptionally(ex -> {
                    YapSched.entity(plugin, player, () -> player.sendMessage("§c" + rootMessage(ex)));
                    return null;
                });
        return true;
    }

    private boolean map(Player player) {
        for (String line : ConquestMapRenderer.render(player, conquest, config)) {
            player.sendMessage(line);
        }
        return true;
    }

    private boolean info(Player player) {
        Optional<ConquestChunk> chunk = conquest.chunkAt(player.getLocation());
        if (chunk.isPresent()) {
            ConquestChunk c = chunk.get();
            String name = FactionServices.find()
                    .flatMap(fs -> fs.getFaction(c.factionId()))
                    .map(Faction::name)
                    .orElse("#" + c.factionId());
            player.sendMessage("§6Conquest chunk §f" + c.chunkX() + "," + c.chunkZ());
            player.sendMessage("§7Owner §f" + name + " §7cost §f" + c.powerCost()
                    + (c.frozen() ? " §c(frozen)" : ""));
        } else {
            player.sendMessage("§7No faction claim here.");
        }
        if (config.zonesEnabled()) {
            ConquestZoneType zone = conquest.zoneAt(player.getLocation()).orElse(ConquestZoneType.WILDERNESS);
            player.sendMessage("§7Zone §f" + zone.name().toLowerCase(Locale.ROOT)
                    + (ConquestZoneType.WILDERNESS == zone ? " §8(claimable)" : ""));
        }
        if (chunk.isPresent() && config.overclaimEnabled()) {
            FactionServices.find().flatMap(fs -> fs.getFaction(chunk.get().factionId())).ifPresent(f -> {
                int land = conquest.totalPowerUsed(f.id());
                boolean vulnerable = com.yapcore.conquest.ConquestOverclaimRules.isVulnerable(f.power(), land);
                player.sendMessage("§7Overclaimable: §f" + (vulnerable && !f.isShielded() && !chunk.get().frozen()
                        ? "yes" : "no")
                        + " §7(power §f" + f.power() + "§7 / land §f" + land + "§7)");
            });
        }
        if (config.combatTagEnabled() && conquest.isCombatTagged(player.getUniqueId())) {
            player.sendMessage("§cYou are combat tagged.");
        }
        return true;
    }

    private boolean power(Player player) {
        var factions = FactionServices.find();
        if (factions.isEmpty()) {
            player.sendMessage("§cYaPFactions is required for conquest power.");
            return true;
        }
        Optional<Faction> faction = factions.get().findByPlayer(player.getUniqueId());
        if (faction.isEmpty()) {
            player.sendMessage("§cJoin a faction first.");
            return true;
        }
        Faction f = faction.get();
        int used = conquest.totalPowerUsed(f.id());
        int land = conquest.chunksForFaction(f.id()).size();
        player.sendMessage("§6Conquest power §f" + f.power() + "§7/§f" + f.maxPower());
        player.sendMessage("§7Land §f" + land + " §7chunks · used §f" + used
                + "§7/§f" + f.maxPower() + " §7(claim cost " + config.claimCost() + ")");
        return true;
    }

    private boolean help(Player player, String label) {
        player.sendMessage("§6/" + label + " §7— conquest chunk land");
        player.sendMessage("§e/" + label + " claim§7 — claim / overclaim this chunk (officer+)");
        player.sendMessage("§e/" + label + " unclaim§7 — unclaim this chunk");
        player.sendMessage("§e/" + label + " map§7 — ASCII chunk map");
        player.sendMessage("§e/" + label + " info§7 — claim + zone here");
        player.sendMessage("§e/" + label + " power§7 — faction power & land");
        if (config.overclaimEnabled()) {
            player.sendMessage("§7Overclaim: enemies can take land when defender power < land cost");
        }
        if (config.combatTagEnabled()) {
            player.sendMessage("§7Combat tag: " + config.combatTagSeconds() + "s (blocks teleport"
                    + (config.combatTagBlockFly() ? "/fly" : "") + ")");
        }
        if (player.hasPermission("yapconquest.admin")) {
            player.sendMessage("§e/yapconquest setzone|clearzone§7 — admin zone tools");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("claim", "unclaim", "map", "info", "power", "help")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }

    static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? "Failed." : msg;
    }
}
