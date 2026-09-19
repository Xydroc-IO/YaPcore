package com.yapcore.holo.cmd;

import com.yapcore.holo.Hologram;
import com.yapcore.holo.impl.HologramImpl;
import com.yapcore.holo.impl.HologramPages;
import com.yapcore.holo.impl.HologramServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HologramCommandOps {

    private final HologramServiceImpl holograms;
    private final HologramCommandFeatures features;

    public HologramCommandOps(HologramServiceImpl holograms) {
        this.holograms = holograms;
        this.features = new HologramCommandFeatures(holograms);
    }

    public boolean create(CommandSender sender, String[] args) {
        if (args.length < 2 || !HologramCommandParse.validId(args[1])) {
            sender.sendMessage("§e/yapholo create <id> [text]");
            sender.sendMessage("§e/yapholo create <id> at <world> <x> <y> <z> [text]");
            return true;
        }
        int at = HologramCommandParse.indexOf(args, "at", 2);
        Location loc;
        String worldName;
        List<String> lines;
        if (at >= 0) {
            if (args.length < at + 5) {
                sender.sendMessage("§e/yapholo create <id> at <world> <x> <y> <z> [text]");
                return true;
            }
            worldName = args[at + 1];
            double x = HologramCommandParse.parseDouble(args[at + 2], sender);
            double y = HologramCommandParse.parseDouble(args[at + 3], sender);
            double z = HologramCommandParse.parseDouble(args[at + 4], sender);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                return true;
            }
            World world = Bukkit.getWorld(worldName);
            loc = new Location(world, x, y, z);
            lines = args.length == at + 5
                    ? List.of("&f" + args[1])
                    : List.of(HologramCommandParse.join(args, at + 5));
        } else {
            if (HologramCommands.playersOnly(sender)) {
                return true;
            }
            Player player = (Player) sender;
            loc = player.getLocation().add(0, 1.8, 0);
            worldName = player.getWorld().getName();
            lines = args.length == 2
                    ? List.of("&f" + args[1])
                    : List.of(HologramCommandParse.join(args, 2));
        }
        holograms.create(args[1], loc, lines, worldName);
        sender.sendMessage("§aCreated hologram §f" + args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    public boolean delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/yapholo delete <id>");
            return true;
        }
        if (holograms.delete(args[1])) {
            sender.sendMessage("§aDeleted hologram §f" + args[1]);
        } else {
            sender.sendMessage("§cUnknown hologram.");
        }
        return true;
    }

    public boolean list(CommandSender sender, String[] args) {
        if (args.length >= 2 && "json".equalsIgnoreCase(args[1])) {
            sender.sendMessage(HologramCommandParse.JSON_PREFIX + HologramCommandParse.toJson(rows()));
            return true;
        }
        if (holograms.all().isEmpty()) {
            sender.sendMessage("§7No holograms.");
            return true;
        }
        sender.sendMessage("§aHolograms §7(" + holograms.all().size() + ")");
        for (Hologram holo : holograms.all()) {
            sender.sendMessage("§f- " + holo.id() + " §7" + holo.lines().size() + " lines");
        }
        return true;
    }

    public boolean near(CommandSender sender, String[] args) {
        if (HologramCommands.playersOnly(sender)) {
            return true;
        }
        Player player = (Player) sender;
        double radius = 32;
        if (args.length >= 2) {
            try {
                radius = Math.max(1, Double.parseDouble(args[1]));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cRadius must be a number.");
                return true;
            }
        }
        double r2 = radius * radius;
        List<Hologram> found = new ArrayList<>();
        for (Hologram holo : holograms.all()) {
            if (holo.location().getWorld() != null && holo.location().getWorld().equals(player.getWorld())
                    && holo.location().distanceSquared(player.getLocation()) <= r2) {
                found.add(holo);
            }
        }
        if (found.isEmpty()) {
            sender.sendMessage("§7No holograms within " + (int) radius + " blocks.");
            return true;
        }
        sender.sendMessage("§aNearby holograms §7(" + found.size() + ")");
        for (Hologram holo : found) {
            sender.sendMessage("§f- " + holo.id() + " §7" + (int) holo.location().distance(player.getLocation()) + "m");
        }
        return true;
    }

    public boolean info(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        Hologram h = holo.get();
        sender.sendMessage("§a" + h.id() + " §7at " + format(h) + " §f" + h.lines().size() + " lines");
        int i = 1;
        for (String line : h.lines()) {
            sender.sendMessage("§7" + i++ + ". §f" + line);
        }
        return features.infoExtra(sender, h);
    }

    public boolean move(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        int at = HologramCommandParse.indexOf(args, "at", 2);
        if (at < 0) {
            return moveHere(sender, args);
        }
        if (args.length < at + 5) {
            sender.sendMessage("§e/yapholo move <id> at <world> <x> <y> <z>");
            return true;
        }
        String worldName = args[at + 1];
        double x = HologramCommandParse.parseDouble(args[at + 2], sender);
        double y = HologramCommandParse.parseDouble(args[at + 3], sender);
        double z = HologramCommandParse.parseDouble(args[at + 4], sender);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        holo.get().teleport(new Location(world, x, y, z));
        if (holo.get() instanceof HologramImpl impl) {
            impl.applyWorldName(worldName);
        }
        holograms.save();
        sender.sendMessage("§aMoved §f" + holo.get().id());
        return true;
    }

    public boolean moveHere(CommandSender sender, String[] args) {
        if (HologramCommands.playersOnly(sender)) {
            return true;
        }
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        Player player = (Player) sender;
        holo.get().teleport(player.getLocation().add(0, 1.8, 0));
        holograms.save();
        sender.sendMessage("§aMoved §f" + holo.get().id());
        return true;
    }

    public boolean addLine(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 3) {
            if (args.length < 3) {
                sender.sendMessage("§e/yapholo addline <id> <text>");
            }
            return true;
        }
        holo.get().addLine(HologramCommandParse.join(args, 2));
        holograms.save();
        sender.sendMessage("§aLine added.");
        return true;
    }

    public boolean setLine(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 4) {
            sender.sendMessage("§e/yapholo setline <id> <n> <text>");
            return true;
        }
        int index = lineIndex(sender, args[2], holo.get());
        if (index < 0) {
            return true;
        }
        holo.get().setLine(index, HologramCommandParse.join(args, 3));
        holograms.save();
        sender.sendMessage("§aLine updated.");
        return true;
    }

    public boolean insertLine(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 4) {
            sender.sendMessage("§e/yapholo insertline <id> <n> <text>");
            return true;
        }
        int index = lineIndex(sender, args[2], holo.get());
        if (index < 0) {
            return true;
        }
        holo.get().insertLine(index, HologramCommandParse.join(args, 3));
        holograms.save();
        sender.sendMessage("§aLine inserted.");
        return true;
    }

    public boolean removeLine(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 3) {
            sender.sendMessage("§e/yapholo removeline <id> <n>");
            return true;
        }
        int index = lineIndex(sender, args[2], holo.get());
        if (index < 0) {
            return true;
        }
        holo.get().removeLine(index);
        holograms.save();
        sender.sendMessage("§aLine removed.");
        return true;
    }

    public boolean setLines(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 3) {
            sender.sendMessage("§e/yapholo setlines <id> line1|line2");
            return true;
        }
        String raw = HologramCommandParse.join(args, 2);
        if (raw.contains(";;")) {
            holo.get().setPages(HologramPages.splitPages(raw));
        } else {
            holo.get().setLines(HologramCommandParse.splitLines(raw));
        }
        holograms.save();
        sender.sendMessage("§aLines updated.");
        return true;
    }

    public boolean view(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 3) {
            sender.sendMessage("§e/yapholo view <id> <blocks>");
            return true;
        }
        double blocks = HologramCommandParse.parseDouble(args[2], sender);
        if (Double.isNaN(blocks)) {
            return true;
        }
        holo.get().setViewDistance(blocks);
        holograms.save();
        sender.sendMessage("§aView distance §f" + holo.get().id() + " §7→ " + (int) holo.get().viewDistance());
        return true;
    }

    private List<HologramCommandParse.Row> rows() {
        List<HologramCommandParse.Row> out = new ArrayList<>();
        for (Hologram holo : holograms.all()) {
            Location loc = holo.location();
            String world = holo instanceof HologramImpl impl ? impl.worldName()
                    : (loc.getWorld() == null ? "?" : loc.getWorld().getName());
            String lines = holo instanceof HologramImpl impl ? impl.pagesSerialized()
                    : String.join("|", holo.lines());
            String attach = holo.attachment() == null ? "" : holo.attachment().serialize();
            String clicks = holo instanceof HologramImpl impl ? impl.clicksSerialized()
                    : "";
            out.add(new HologramCommandParse.Row(holo.id(), world, loc.getX(), loc.getY(), loc.getZ(),
                    holo.viewDistance(), lines, attach, clicks, holo.seePermission(), holo.pageCount()));
        }
        return out;
    }

    private Optional<Hologram> require(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eNeed hologram id.");
            return Optional.empty();
        }
        Optional<Hologram> holo = holograms.get(args[1]);
        if (holo.isEmpty()) {
            sender.sendMessage("§cUnknown hologram.");
        }
        return holo;
    }

    private static int lineIndex(CommandSender sender, String raw, Hologram holo) {
        try {
            int n = Integer.parseInt(raw);
            int index = n - 1;
            if (index < 0 || index >= holo.lines().size()) {
                sender.sendMessage("§cLine out of range (1-" + holo.lines().size() + ").");
                return -1;
            }
            return index;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLine must be a number.");
            return -1;
        }
    }

    private static String format(Hologram holo) {
        var loc = holo.location();
        String world = loc.getWorld() == null
                ? (holo instanceof HologramImpl impl ? impl.worldName() : "?")
                : loc.getWorld().getName();
        return world + " " + (int) loc.getX() + " " + (int) loc.getY() + " " + (int) loc.getZ();
    }
}
