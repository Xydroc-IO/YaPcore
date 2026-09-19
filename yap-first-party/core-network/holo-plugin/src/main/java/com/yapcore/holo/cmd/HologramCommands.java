package com.yapcore.holo.cmd;

import com.yapcore.holo.HoloPlugin;
import com.yapcore.holo.Hologram;
import com.yapcore.holo.impl.HologramServiceImpl;
import com.yapcore.messages.YapHelp;
import com.yapcore.messages.YapMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HologramCommands implements CommandExecutor, TabCompleter {

    private final HoloPlugin plugin;
    private final HologramServiceImpl holograms;
    private final HologramCommandOps ops;
    private final HologramCommandFeatures features;

    public HologramCommands(HoloPlugin plugin, HologramServiceImpl holograms) {
        this.plugin = plugin;
        this.holograms = holograms;
        this.ops = new HologramCommandOps(holograms);
        this.features = new HologramCommandFeatures(holograms);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapholo.admin")) {
            YapMessages.noPermission(sender, "yapholo.admin");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "create" -> requireEnabled(sender) ? true : ops.create(sender, args);
            case "delete", "remove" -> requireEnabled(sender) ? true : ops.delete(sender, args);
            case "list" -> ops.list(sender, args);
            case "near" -> requireEnabled(sender) ? true : ops.near(sender, args);
            case "info" -> requireEnabled(sender) ? true : ops.info(sender, args);
            case "move" -> requireEnabled(sender) ? true : ops.move(sender, args);
            case "movehere", "tphere" -> requireEnabled(sender) ? true : ops.moveHere(sender, args);
            case "addline" -> requireEnabled(sender) ? true : ops.addLine(sender, args);
            case "setline" -> requireEnabled(sender) ? true : ops.setLine(sender, args);
            case "insertline" -> requireEnabled(sender) ? true : ops.insertLine(sender, args);
            case "removeline" -> requireEnabled(sender) ? true : ops.removeLine(sender, args);
            case "setlines" -> requireEnabled(sender) ? true : ops.setLines(sender, args);
            case "view" -> requireEnabled(sender) ? true : ops.view(sender, args);
            case "attach" -> requireEnabled(sender) ? true : features.attach(sender, args);
            case "click", "clicks" -> requireEnabled(sender) ? true : features.click(sender, args);
            case "see", "permission" -> requireEnabled(sender) ? true : features.see(sender, args);
            case "setpages" -> requireEnabled(sender) ? true : features.setPages(sender, args);
            case "addpage" -> requireEnabled(sender) ? true : features.addPage(sender, args);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean requireEnabled(CommandSender sender) {
        if (holograms.enabled()) {
            return false;
        }
        sender.sendMessage("§cHolograms disabled or NMS packets unavailable.");
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage("§aYaPHolo §7— enabled=" + holograms.enabled()
                + " count=" + holograms.all().size()
                + " nms=" + (holograms.nmsReady()
                ? (holograms.textDisplay() ? "text_display" : "armor_stand")
                : "unavailable"));
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadHolo();
        YapMessages.reloaded(sender, "YaPHolo");
        return true;
    }

    private void help(CommandSender sender) {
        YapHelp.header(sender, "YaP holograms");
        YapHelp.usage(sender, "/yapholo create <id> [text]");
        YapHelp.usage(sender, "/yapholo create <id> at <world> <x> <y> <z> [text]");
        YapHelp.usage(sender, "/yapholo delete|info|movehere <id>");
        YapHelp.usage(sender, "/yapholo move <id> at <world> <x> <y> <z>");
        YapHelp.usage(sender, "/yapholo addline|setline|setlines|setpages|addpage|view …");
        YapHelp.usage(sender, "/yapholo attach|click|see <id> …");
        YapHelp.usage(sender, "/yapholo list [json]|near [radius]|status|reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapholo.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("create", "delete", "list", "near", "info", "move", "movehere",
                    "addline", "setline", "insertline", "removeline", "setlines", "setpages", "addpage",
                    "view", "attach", "click", "see", "status", "reload");
        }
        if (args.length == 2 && needsId(args[0])) {
            List<String> ids = new ArrayList<>();
            for (Hologram holo : holograms.all()) {
                ids.add(holo.id());
            }
            return ids;
        }
        return List.of();
    }

    private static boolean needsId(String sub) {
        String s = sub.toLowerCase(Locale.ROOT);
        return s.equals("delete") || s.equals("remove") || s.equals("info") || s.equals("move")
                || s.equals("movehere") || s.equals("tphere") || s.equals("addline")
                || s.equals("setline") || s.equals("insertline") || s.equals("removeline")
                || s.equals("setlines") || s.equals("view") || s.equals("attach") || s.equals("click")
                || s.equals("clicks") || s.equals("see") || s.equals("permission")
                || s.equals("setpages") || s.equals("addpage");
    }

    static boolean playersOnly(CommandSender sender) {
        if (sender instanceof Player) {
            return false;
        }
        YapMessages.playersOnly(sender);
        return true;
    }
}
