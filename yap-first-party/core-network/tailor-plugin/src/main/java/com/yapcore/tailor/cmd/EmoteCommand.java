package com.yapcore.tailor.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.tailor.ChassisEmotePush;
import com.yapcore.tailor.PresenceChannel;
import com.yapcore.tailor.TailorEmoteCatalog;
import com.yapcore.tailor.TailorPlugin;
import com.yapcore.tailor.gui.BedrockWardrobeForms;
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
import java.util.Optional;
import java.util.stream.Collectors;

public final class EmoteCommand implements CommandExecutor, TabCompleter {

    private final TailorPlugin plugin;
    private final TailorEmoteCatalog catalog = TailorEmoteCatalog.get();

    public EmoteCommand(TailorPlugin plugin) {
        this.plugin = plugin;
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
        if (args.length == 0) {
            if (BedrockWardrobeForms.tryOpenEmotePicker(plugin, player)) {
                return true;
            }
            list(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub) || "help".equals(sub)) {
            list(player);
            return true;
        }
        String query = String.join(" ", args);
        Optional<TailorEmoteCatalog.Entry> entry = catalog.resolve(query);
        if (entry.isEmpty() && args.length >= 1) {
            entry = catalog.resolve(args[0]);
        }
        if (entry.isEmpty()) {
            player.sendMessage(Component.text(
                    "Unknown emote. Use /" + label + " list", NamedTextColor.RED));
            return true;
        }
        play(player, entry.get());
        return true;
    }

    private void list(Player player) {
        player.sendMessage(Component.text("Catalog emotes (" + catalog.size() + "):", NamedTextColor.GOLD));
        for (TailorEmoteCatalog.Entry e : catalog.entries()) {
            player.sendMessage(Component.text("  " + e.name() + "  (" + e.slug() + ")", NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Play: /emote <name>", NamedTextColor.YELLOW));
    }

    private void play(Player player, TailorEmoteCatalog.Entry entry) {
        PresenceChannel channel = plugin.presenceChannel();
        if (channel != null) {
            channel.playEmote(player, entry.id());
        } else {
            YapSched.async(plugin, () -> {
                boolean ok = ChassisEmotePush.push(
                        plugin, plugin.tailorConfig(), player.getName(), player.getUniqueId(), entry.id());
                YapSched.entity(plugin, player, () -> {
                    if (ok) {
                        player.sendMessage(Component.text("Playing " + entry.name(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text(
                                "Emote rejected (cooldown or chassis offline).", NamedTextColor.RED));
                    }
                });
            });
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            out.add("list");
            for (String name : catalog.names()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)
                        || TailorEmoteCatalog.slugify(name).startsWith(prefix)) {
                    out.add(name.contains(" ") ? TailorEmoteCatalog.slugify(name) : name);
                }
            }
            return out.stream().distinct().collect(Collectors.toList());
        }
        return List.of();
    }
}
