package com.yapcore.tailor.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.tailor.ActiveSkin;
import com.yapcore.tailor.SkinImageService;
import com.yapcore.tailor.SkinModel;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorPlugin;
import com.yapcore.tailor.TailorServiceImpl;
import com.yapcore.tailor.gui.BedrockWardrobeForms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SkinCommand implements CommandExecutor, TabCompleter {

    private final TailorPlugin plugin;
    private final TailorServiceImpl service;

    public SkinCommand(TailorPlugin plugin, TailorServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player
                    && BedrockWardrobeForms.tryOpenSkinHub(plugin, service, player)) {
                return true;
            }
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender, label);
            case "reload" -> {
                if (!sender.hasPermission("yap.tailor.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadTailor();
                sender.sendMessage(Component.text("YaPTailor reloaded.", NamedTextColor.GREEN));
            }
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender);
            case "update", "refresh" -> handleUpdate(sender);
            case "slim" -> handleModel(sender, SkinModel.SLIM);
            case "wide", "classic" -> handleModel(sender, SkinModel.WIDE);
            case "cape" -> handleCape(sender, args);
            case "setplayer" -> handleSetPlayer(sender, args);
            default -> {
                // /skin <url> shorthand
                if (SkinImageService.looksLikeUrl(args[0])) {
                    handleSetUrl(sender, args[0]);
                } else if (sender instanceof Player player) {
                    // /skin <playerName> — copy
                    handleCopy(player, args[0]);
                } else {
                    sendHelp(sender, label);
                }
            }
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only (use /skin setplayer).", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("yap.tailor.skin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /skin set <url|player>", NamedTextColor.YELLOW));
            return;
        }
        String arg = args[1];
        if (SkinImageService.looksLikeUrl(arg)) {
            handleSetUrl(player, arg);
        } else {
            handleCopy(player, arg);
        }
    }

    private void handleSetUrl(CommandSender sender, String url) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Downloading skin…", NamedTextColor.GRAY));
        YapSched.async(plugin, () -> {
            try {
                ActiveSkin skin = service.applySkin(player, url);
                YapSched.entity(plugin, player, () -> player.sendMessage(Component.text(
                        "Skin applied (" + skin.model().name().toLowerCase(Locale.ROOT) + ").",
                        NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleCopy(Player player, String targetName) {
        player.sendMessage(Component.text("Looking up skin for " + targetName + "…", NamedTextColor.GRAY));
        YapSched.async(plugin, () -> {
            try {
                service.applyFromPlayerName(player, targetName);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Copied skin from " + targetName + ".", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                service.clearSkin(player);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Skin cleared.", NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleUpdate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        try {
            service.refreshPlayer(player);
            player.sendMessage(Component.text("Skin refreshed.", NamedTextColor.GREEN));
        } catch (TailorException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleModel(CommandSender sender, SkinModel model) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                service.setModel(player, model);
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Model set to " + model.name().toLowerCase(Locale.ROOT) + ".",
                                NamedTextColor.GREEN)));
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleCape(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("yap.tailor.cape")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /skin cape <url|clear>", NamedTextColor.YELLOW));
            return;
        }
        String arg = args[1];
        YapSched.async(plugin, () -> {
            try {
                if ("clear".equalsIgnoreCase(arg) || "none".equalsIgnoreCase(arg) || "off".equalsIgnoreCase(arg)) {
                    service.setCape(player, null);
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text("Cape cleared.", NamedTextColor.GREEN)));
                } else {
                    service.setCape(player, arg);
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text("Cape applied.", NamedTextColor.GREEN)));
                }
            } catch (TailorException e) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void handleSetPlayer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yap.tailor.setother") && !sender.hasPermission("yap.tailor.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /skin setplayer <player> <url>", NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
            return;
        }
        String url = args[2];
        YapSched.async(plugin, () -> {
            try {
                service.applySkin(target, url);
                YapSched.global(plugin, () -> {
                    sender.sendMessage(Component.text("Set skin for " + target.getName() + ".", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("Your skin was updated by staff.", NamedTextColor.YELLOW));
                });
            } catch (TailorException e) {
                YapSched.global(plugin, () ->
                        sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("—" + " YaP Tailor " + "—", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " set <url|player>", NamedTextColor.YELLOW)
                .append(Component.text(" — apply skin", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " clear", NamedTextColor.YELLOW)
                .append(Component.text(" — restore default", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " update", NamedTextColor.YELLOW)
                .append(Component.text(" — re-apply active skin", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " slim|wide", NamedTextColor.YELLOW)
                .append(Component.text(" — arm model", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " cape <url|clear>", NamedTextColor.YELLOW)
                .append(Component.text(" — cape", NamedTextColor.GRAY)));
        if (sender.hasPermission("yap.tailor.setother") || sender.hasPermission("yap.tailor.admin")) {
            sender.sendMessage(Component.text("/" + label + " setplayer <player> <url>", NamedTextColor.YELLOW));
        }
        if (sender.hasPermission("yap.tailor.admin")) {
            sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList(
                    "set", "clear", "update", "slim", "wide", "cape", "help"));
            if (sender.hasPermission("yap.tailor.setother") || sender.hasPermission("yap.tailor.admin")) {
                base.add("setplayer");
            }
            if (sender.hasPermission("yap.tailor.admin")) {
                base.add("reload");
            }
            return filter(base, args[0]);
        }
        if (args.length == 2 && "cape".equalsIgnoreCase(args[0])) {
            return filter(List.of("clear", "https://"), args[1]);
        }
        if (args.length == 2 && ("set".equalsIgnoreCase(args[0]) || "setplayer".equalsIgnoreCase(args[0]))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && "setplayer".equalsIgnoreCase(args[0])) {
            return filter(List.of("https://"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
