package com.yapcore.tailor.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorPlugin;
import com.yapcore.tailor.TailorServiceImpl;
import com.yapcore.tailor.gui.BedrockWardrobeForms;
import com.yapcore.tailor.gui.WardrobeGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WardrobeCommand implements CommandExecutor, TabCompleter {

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;
    private final WardrobeGui gui;

    public WardrobeCommand(TailorPlugin plugin, TailorServiceImpl service, WardrobeGui gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("yap.tailor.wardrobe")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            if (BedrockWardrobeForms.tryOpenWardrobe(plugin, service, player)) {
                return true;
            }
            gui.open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /wardrobe save <name>", NamedTextColor.YELLOW));
                    return true;
                }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                YapSched.async(plugin, () -> {
                    try {
                        var slot = service.saveWardrobeSlot(player.getUniqueId(), name);
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text(
                                        "Saved wardrobe slot #" + slot.id() + " as '" + slot.name() + "'.",
                                        NamedTextColor.GREEN)));
                    } catch (TailorException e) {
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                    }
                });
            }
            case "delete", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /wardrobe delete <id>", NamedTextColor.YELLOW));
                    return true;
                }
                long id;
                try {
                    id = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid slot id.", NamedTextColor.RED));
                    return true;
                }
                YapSched.async(plugin, () -> {
                    try {
                        service.deleteWardrobeSlot(player.getUniqueId(), id);
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text("Deleted slot #" + id + ".", NamedTextColor.GREEN)));
                    } catch (TailorException e) {
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                    }
                });
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /wardrobe rename <id> <name>", NamedTextColor.YELLOW));
                    return true;
                }
                long id;
                try {
                    id = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid slot id.", NamedTextColor.RED));
                    return true;
                }
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                YapSched.async(plugin, () -> {
                    try {
                        var slot = service.renameSlot(player.getUniqueId(), id, name);
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text(
                                        "Renamed slot #" + slot.id() + " to '" + slot.name() + "'.",
                                        NamedTextColor.GREEN)));
                    } catch (TailorException e) {
                        YapSched.entity(plugin, player, () ->
                                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
                    }
                });
            }
            case "gui", "open" -> {
                if (!BedrockWardrobeForms.tryOpenWardrobe(plugin, service, player)) {
                    gui.open(player);
                }
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /wardrobe | /wardrobe save <name> | /wardrobe rename <id> <name> | /wardrobe delete <id>",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("save", "rename", "delete", "gui"), args[0]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
