package com.yapcore.bedrockblocks.cmd;

import com.yapcore.bedrockblocks.PortBlockDefinition;
import com.yapcore.bedrockblocks.PortBlockService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

public final class PortBlockCommand implements CommandExecutor, TabCompleter {

    private final PortBlockService service;

    public PortBlockCommand(PortBlockService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "list" -> {
                list(sender);
                yield true;
            }
            case "give" -> give(sender, args);
            case "set" -> set(sender, args);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Component.text("Bedrock catalog ports (" + service.list().size() + "):", NamedTextColor.GOLD));
        for (PortBlockDefinition def : service.list()) {
            sender.sendMessage(Component.text(
                    "  " + def.shortName() + "  [" + def.placement() + "]",
                    NamedTextColor.GRAY));
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /yapblock give <id> [player] [amount]", NamedTextColor.RED));
            return true;
        }
        String id = args[1];
        if (service.resolve(id).isEmpty()) {
            sender.sendMessage(Component.text("Unknown port: " + id, NamedTextColor.RED));
            return true;
        }
        Player target;
        int amount = 1;
        if (args.length >= 3) {
            Player named = Bukkit.getPlayerExact(args[2]);
            if (named != null) {
                target = named;
                if (args.length >= 4) {
                    amount = parseAmount(args[3]);
                }
            } else if (sender instanceof Player self) {
                target = self;
                amount = parseAmount(args[2]);
            } else {
                sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return true;
        }
        service.giveItem(target, id, amount);
        sender.sendMessage(Component.text(
                "Gave " + amount + "× " + id + " to " + target.getName(),
                NamedTextColor.GREEN));
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /yapblock set <id>", NamedTextColor.RED));
            return true;
        }
        Optional<PortBlockDefinition> def = service.resolve(args[1]);
        if (def.isEmpty()) {
            sender.sendMessage(Component.text("Unknown port: " + args[1], NamedTextColor.RED));
            return true;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            sender.sendMessage(Component.text("Look at a block within 6 meters.", NamedTextColor.RED));
            return true;
        }
        BlockFace face = player.getTargetBlockFace(6);
        if (face == null) {
            face = BlockFace.UP;
        }
        Block placeAt = target.getRelative(face);
        service.place(placeAt, def.get().jePortId(), face.getOppositeFace());
        sender.sendMessage(Component.text("Placed " + def.get().jePortId(), NamedTextColor.GREEN));
        return true;
    }

    private static int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " give <id> [player] [amount]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " set <id>", NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "list", "set"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set"))) {
            return filter(service.list().stream().map(PortBlockDefinition::shortName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
