package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.gui.WorldEditGui;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditTool;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WorldCommands implements CommandExecutor, TabCompleter {

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldManagerServiceImpl worlds;
    private final SelectionServiceImpl selection;
    private final BrushService brushService;
    private final UndoService undoService;
    private final WorldEditOps editOps;
    private final WorldEditTool worldEditTool;
    private final WorldEditGui gui;
    private final WorldCommandsWorldOps worldOps;
    private final WorldCommandsSchematics schematics;
    private final WorldCommandsSelectionEdit selectionEditCmds;

    public WorldCommands(WorldPlugin plugin, WorldConfig config, WorldManagerServiceImpl worlds,
                         SelectionServiceImpl selection, SchematicPaster paster,
                         BrushService brushService, UndoService undoService,
                         SelectionEditService selectionEdit, WorldEditOps editOps,
                         WorldEditTool worldEditTool, WorldEditGui gui) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
        this.selection = selection;
        this.brushService = brushService;
        this.undoService = undoService;
        this.editOps = editOps;
        this.worldEditTool = worldEditTool;
        this.gui = gui;
        this.worldOps = new WorldCommandsWorldOps(plugin, worlds, selection);
        this.schematics = new WorldCommandsSchematics(plugin, config, selection, paster, editOps);
        this.selectionEditCmds = new WorldCommandsSelectionEdit(plugin, selection, selectionEdit);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.openInGameGui(player);
                return true;
            }
            return worldOps.status(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "reload" -> worldOps.reload(sender);
            case "status" -> worldOps.status(sender);
            case "load" -> worldOps.load(sender, args);
            case "create", "new" -> worldOps.create(sender, args);
            case "unload" -> worldOps.unload(sender, args);
            case "tp", "teleport" -> worldOps.teleport(sender, args);
            case "wand", "tool" -> tool(sender);
            case "gui", "menu" -> openGui(sender);
            case "editor", "studio", "web" -> openEditor(sender);
            case "pos1" -> setPos(sender, true);
            case "pos2" -> setPos(sender, false);
            case "clear", "desel" -> clearSel(sender);
            case "fill", "set", "walls", "shell", "faces", "hollow", "outline", "edges",
                 "replace", "copy", "cut", "paste", "rotate", "flip", "stack", "move",
                 "expand", "contract", "shift", "outset", "inset", "chunk", "size", "distr",
                 "cyl", "hcyl", "sphere", "hsphere", "pyramid", "line", "drain", "smooth",
                 "overlay", "naturalize", "replacenear", "thru", "jumpto", "up", "ascend",
                 "descend" -> weOp(sender, args);
            case "schem", "schematic" -> schematics.schem(sender, args);
            case "brush" -> brush(sender, args);
            case "undo" -> undo(sender);
            case "redo" -> redo(sender);
            case "pregen" -> worldOps.pregen(sender, args);
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean weOp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection") && !player.hasPermission("yapworld.brush")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String name = args[0];
        String[] rest = args.length > 1
                ? java.util.Arrays.copyOfRange(args, 1, args.length)
                : new String[0];
        if (!editOps.dispatch(player, name, rest)) {
            help(sender);
        }
        return true;
    }

    private boolean tool(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!config.selectionEnabled()) {
            sender.sendMessage("§cWorld edit is disabled.");
            return true;
        }
        player.getInventory().addItem(worldEditTool.create());
        player.sendMessage("§aWorld edit tool equipped.");
        player.sendMessage("§7Left-click = pos1 · Right-click = pos2 · Shift+right-click = §fGUI");
        return true;
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only — use /yapworld status from console.");
            return true;
        }
        plugin.openInGameGui(player);
        return true;
    }

    private boolean openEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        plugin.openBrowserEditor(player);
        return true;
    }

    private boolean clearSel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        selection.clearSelection(player.getUniqueId());
        player.sendMessage("§eSelection cleared.");
        return true;
    }

    private boolean setPos(CommandSender sender, boolean pos1) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var loc = player.getLocation();
        if (pos1) {
            selection.setPos1(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos1 set.");
        } else {
            selection.setPos2(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos2 set.");
        }
        return true;
    }

    private boolean brush(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.brush")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld brush <radius> [material]");
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid radius.");
            return true;
        }
        if (radius > config.maxBrushRadius()) {
            sender.sendMessage("§cRadius capped at §f" + config.maxBrushRadius());
            radius = config.maxBrushRadius();
        }
        Material material = Material.STONE;
        if (args.length >= 3) {
            Material parsed = Material.matchMaterial(args[2]);
            if (parsed == null || !parsed.isBlock()) {
                sender.sendMessage("§cUnknown block material.");
                return true;
            }
            material = parsed;
        }
        brushService.setBrush(player.getUniqueId(), radius, material);
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(BrushService.BRUSH_TOOL));
        player.sendMessage("§aBrush §f" + radius + " §a→ §f" + material.name()
                + " §7(right-click with blaze rod)");
        return true;
    }

    private boolean undo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.brush") && !player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        undoService.undo(player.getUniqueId()).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aUndid §f" + count + " §ablocks.")));
        return true;
    }

    private boolean redo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.brush") && !player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        undoService.redo(player.getUniqueId()).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aRedid §f" + count + " §ablocks.")));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6YaPWorld §7— Folia-safe FAWE-class");
        sender.sendMessage("§e/yapworld §7or §e// §7— GUI / help · §e/yapworld tool §7— wand");
        sender.sendMessage("§e//set //replace //mask //gmask //sel //copy //paste //brush //fast");
        sender.sendMessage("§e//regen //forest //setbiome //deform //undo //redo");
        sender.sendMessage("§e/yapworld schem|brush|editor|create|load|unload|tp|pregen|status|reload");
        sender.sendMessage("§e/yapworld create <name> --type flat|normal|large_biomes|amplified --env overworld|nether|end [--seed n] [--generator id]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "gui", "menu", "tool", "wand", "editor", "pos1", "pos2", "clear",
                    "set", "fill", "walls", "shell", "hollow", "outline", "replace",
                    "copy", "cut", "paste", "rotate", "flip", "stack", "move",
                    "expand", "contract", "shift", "cyl", "sphere", "pyramid", "smooth",
                    "sel", "mask", "gmask", "fast", "regen", "forest", "setbiome",
                    "schem", "brush", "undo", "redo", "pregen", "create", "load", "unload", "tp", "status", "reload"), args[0]);
        }
        if (args.length == 2 && "brush".equalsIgnoreCase(args[0])) {
            return filter(List.of("sphere", "cyl", "smooth", "gravity", "clipboard", "butcher"), args[1]);
        }
        if (args.length == 2 && "sel".equalsIgnoreCase(args[0])) {
            return filter(List.of("cuboid", "sphere", "cyl", "poly"), args[1]);
        }
        if (args.length >= 2 && ("create".equalsIgnoreCase(args[0]) || "new".equalsIgnoreCase(args[0]))) {
            if (args.length == 2) {
                return List.of();
            }
            String prev = args[args.length - 2].toLowerCase(Locale.ROOT);
            String cur = args[args.length - 1];
            if (prev.equals("--type") || prev.equals("-t")) {
                return filter(List.of("normal", "flat", "large_biomes", "amplified"), cur);
            }
            if (prev.equals("--env") || prev.equals("--environment") || prev.equals("-e")) {
                return filter(List.of("overworld", "nether", "end"), cur);
            }
            if (prev.equals("--structures")) {
                return filter(List.of("true", "false"), cur);
            }
            if (cur.startsWith("-")) {
                return filter(List.of("--type", "--env", "--seed", "--generator", "--no-structures"), cur);
            }
            return List.of();
        }
        if (args.length == 2 && ("load".equalsIgnoreCase(args[0]) || "unload".equalsIgnoreCase(args[0])
                || "tp".equalsIgnoreCase(args[0]))) {
            return filter(worlds.loadedWorlds().stream().toList(), args[1]);
        }
        if (args.length == 2 && "schem".equalsIgnoreCase(args[0])) {
            return filter(List.of("save", "paste", "import", "list", "load", "delete", "formats"), args[1]);
        }
        if (args.length == 2 && "pregen".equalsIgnoreCase(args[0])) {
            return filter(List.of("start", "status", "pause", "resume", "cancel"), args[1]);
        }
        if (args.length == 2 && List.of("fill", "walls", "shell", "outline").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("stone", "dirt", "glass", "oak_planks", "air", "cobblestone"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
