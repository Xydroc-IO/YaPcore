package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.gui.WorldEditGui;
import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.schem.SpongeSchematicImporter;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditTool;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WorldCommands implements CommandExecutor, TabCompleter {

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldManagerServiceImpl worlds;
    private final SelectionServiceImpl selection;
    private final SchematicPaster paster;
    private final BrushService brushService;
    private final UndoService undoService;
    private final SelectionEditService selectionEdit;
    private final WorldEditOps editOps;
    private final WorldEditTool worldEditTool;
    private final WorldEditGui gui;

    public WorldCommands(WorldPlugin plugin, WorldConfig config, WorldManagerServiceImpl worlds,
                         SelectionServiceImpl selection, SchematicPaster paster,
                         BrushService brushService, UndoService undoService,
                         SelectionEditService selectionEdit, WorldEditOps editOps,
                         WorldEditTool worldEditTool, WorldEditGui gui) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
        this.selection = selection;
        this.paster = paster;
        this.brushService = brushService;
        this.undoService = undoService;
        this.selectionEdit = selectionEdit;
        this.editOps = editOps;
        this.worldEditTool = worldEditTool;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.openInGameGui(player);
                return true;
            }
            return status(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "load" -> load(sender, args);
            case "create", "new" -> create(sender, args);
            case "unload" -> unload(sender, args);
            case "tp", "teleport" -> teleport(sender, args);
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
            case "schem", "schematic" -> schem(sender, args);
            case "brush" -> brush(sender, args);
            case "undo" -> undo(sender);
            case "redo" -> redo(sender);
            case "pregen" -> pregen(sender, args);
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

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yapworld.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        plugin.reloadWorld();
        sender.sendMessage("§aYaPWorld reloaded.");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("yapworld.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        sender.sendMessage("§aYaPWorld §7— worlds: §f" + String.join(", ", worlds.loadedWorlds())
                + " §7pregen: §f" + (PregenBridge.available() ? "ready" : "missing"));
        return true;
    }

    private boolean load(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.load") && !sender.hasPermission("yapworld.create")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld load <world>");
            return true;
        }
        worlds.loadWorld(args[1]).thenAccept(ok ->
                YapSched.global(plugin, () -> sender.sendMessage(ok ? "§aWorld loaded." : "§cLoad failed.")));
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.create") && !sender.hasPermission("yapworld.load")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld create <name> [--type flat|normal|large_biomes|amplified]");
            sender.sendMessage("§7  [--env overworld|nether|end] [--seed <long>] [--generator <id>] [--no-structures]");
            return true;
        }
        String name = args[1];
        if (WorldManagerServiceImpl.sanitizeName(name) == null) {
            sender.sendMessage("§cInvalid world name (use letters, digits, _ or -).");
            return true;
        }
        WorldCreateOptions.Builder b = WorldCreateOptions.builder();
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("--no-structures") || a.equalsIgnoreCase("-S")) {
                b.generateStructures(false);
                continue;
            }
            String key;
            String val;
            if (a.startsWith("--") && a.contains("=")) {
                int eq = a.indexOf('=');
                key = a.substring(2, eq).toLowerCase(Locale.ROOT);
                val = a.substring(eq + 1);
            } else if (a.startsWith("--") && i + 1 < args.length) {
                key = a.substring(2).toLowerCase(Locale.ROOT);
                val = args[++i];
            } else if (a.startsWith("-") && a.length() == 2 && i + 1 < args.length) {
                key = switch (a.charAt(1)) {
                    case 't' -> "type";
                    case 'e' -> "env";
                    case 's' -> "seed";
                    case 'g' -> "generator";
                    default -> "";
                };
                val = args[++i];
            } else {
                sender.sendMessage("§cUnknown option: §f" + a);
                return true;
            }
            switch (key) {
                case "type", "t", "worldtype" -> b.type(val);
                case "env", "environment", "dim", "dimension" -> b.environment(val);
                case "seed" -> {
                    try {
                        b.seed(Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid seed: §f" + val);
                        return true;
                    }
                }
                case "generator", "gen", "g" -> b.generator(val);
                case "structures" -> b.generateStructures(!"false".equalsIgnoreCase(val)
                        && !"no".equalsIgnoreCase(val) && !"0".equals(val));
                default -> {
                    sender.sendMessage("§cUnknown option: §f--" + key);
                    return true;
                }
            }
        }
        WorldCreateOptions opts = b.build();
        worlds.createWorld(name, opts).thenAccept(ok ->
                YapSched.global(plugin, () -> {
                    if (ok) {
                        sender.sendMessage("§aWorld §f" + name + " §aready §7(" + opts.type()
                                + " / " + opts.environment()
                                + (opts.seed() != null ? " seed=" + opts.seed() : "")
                                + (opts.generator() != null ? " gen=" + opts.generator() : "")
                                + ").");
                    } else {
                        sender.sendMessage("§cCreate/load failed. Check console (name taken? generator missing?).");
                    }
                }));
        return true;
    }

    private boolean unload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.unload")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld unload <world>");
            return true;
        }
        worlds.unloadWorld(args[1]).thenAccept(ok ->
                YapSched.global(plugin, () -> sender.sendMessage(ok ? "§aWorld unloaded." : "§cUnload failed.")));
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.teleport")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld tp <world> [player]");
            return true;
        }
        Player target;
        String worldName;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            worldName = args[1];
        } else if (sender instanceof Player player) {
            target = player;
            worldName = args[1];
        } else {
            sender.sendMessage("Console must specify a player.");
            return true;
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        worlds.teleportToWorldSpawn(target.getUniqueId(), worldName).thenAccept(ok ->
                YapSched.global(plugin, () -> {
                    if (sender != target) {
                        sender.sendMessage(ok ? "§aTeleported." : "§cTeleport failed.");
                    }
                }));
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

    private boolean fill(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "fill", (player, sel, mat) ->
                selectionEdit.fill(player, sel, mat));
    }

    private boolean walls(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "walls", (player, sel, mat) ->
                selectionEdit.walls(player, sel, mat));
    }

    private boolean shell(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "shell", (player, sel, mat) ->
                selectionEdit.shell(player, sel, mat));
    }

    private boolean outline(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "outline", (player, sel, mat) ->
                selectionEdit.outline(player, sel, mat));
    }

    private boolean hollow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first — §f/yapworld tool §cor §f/yapworld gui");
            return true;
        }
        player.sendMessage("§7Hollowing selection…");
        selectionEdit.hollow(player, opt.get()).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aHollowed §f" + count + " §ablocks.")));
        return true;
    }

    private boolean replace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§e/yapworld replace <from> <to>");
            return true;
        }
        Material from = Material.matchMaterial(args[1]);
        Material to = Material.matchMaterial(args[2]);
        if (from == null || to == null || !from.isBlock() || !to.isBlock()) {
            player.sendMessage("§cUnknown block material.");
            return true;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return true;
        }
        player.sendMessage("§7Replacing §f" + from.name() + " §7→ §f" + to.name() + "§7…");
        selectionEdit.replace(player, opt.get(), from, to).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced §f" + count + " §ablocks.")));
        return true;
    }

    @FunctionalInterface
    private interface EditOp {
        java.util.concurrent.CompletableFuture<Integer> run(Player player, CuboidSelection sel, Material mat);
    }

    private boolean runSelectionEdit(CommandSender sender, String[] args, String label, EditOp op) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Material mat = Material.STONE;
        if (args.length >= 2) {
            Material parsed = Material.matchMaterial(args[1]);
            if (parsed == null || !parsed.isBlock()) {
                player.sendMessage("§cUnknown block material.");
                return true;
            }
            mat = parsed;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first — §f/yapworld tool §cor open §f/yapworld gui");
            return true;
        }
        player.sendMessage("§7Running §f" + label + " §7with §f" + mat.name() + "§7…");
        Material finalMat = mat;
        op.run(player, opt.get(), finalMat).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§a" + label + " §f" + count + " §ablocks.")));
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

    private boolean pregen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yapworld.pregen")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld pregen start [radius] | status | pause | resume | cancel");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("start".equals(sub)) {
            return pregenStart(sender, args);
        }
        String target = args.length >= 3 ? args[2] : "all";
        String msg = switch (sub) {
            case "status" -> PregenBridge.status(target);
            case "pause" -> PregenBridge.pause(target);
            case "resume" -> PregenBridge.resume(target);
            case "cancel" -> PregenBridge.cancel(target);
            default -> "Unknown: " + sub;
        };
        sender.sendMessage("§7" + msg);
        return true;
    }

    private boolean pregenStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only for pregen start.");
            return true;
        }
        if (!PregenBridge.available()) {
            sender.sendMessage("§cYaPPregen is not loaded.");
            return true;
        }
        World world = player.getWorld();
        var selOpt = selection.selection(player.getUniqueId());
        String msg;
        if (selOpt.isPresent()) {
            msg = PregenBridge.startSelection(world, selOpt.get());
        } else {
            int radius = 128;
            if (args.length >= 3) {
                try {
                    radius = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid radius.");
                    return true;
                }
            }
            var loc = player.getLocation();
            msg = PregenBridge.startRadius(world, loc.getBlockX(), loc.getBlockZ(), radius);
        }
        sender.sendMessage("§a" + msg);
        return true;
    }

    private boolean schem(CommandSender sender, String[] args) {
        if (!config.schematicsEnabled()) {
            sender.sendMessage("§cSchematics disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.schematic")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld schem save|paste|import|list|load|delete|formats <name>");
            return true;
        }
        if ("save".equalsIgnoreCase(args[1])) {
            return schemSave(player, args);
        }
        if ("paste".equalsIgnoreCase(args[1])) {
            return schemPaste(player, args);
        }
        if ("import".equalsIgnoreCase(args[1])) {
            return schemImport(player, args);
        }
        if ("list".equalsIgnoreCase(args[1]) || "ls".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"list"});
        }
        if ("load".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage("§e/yapworld schem load <name>");
                return true;
            }
            return editOps.dispatch(player, "schem", new String[]{"load", args[2]});
        }
        if ("delete".equalsIgnoreCase(args[1]) || "rm".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage("§e/yapworld schem delete <name>");
                return true;
            }
            return editOps.dispatch(player, "schem", new String[]{"delete", args[2]});
        }
        if ("formats".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"formats"});
        }
        sender.sendMessage("§e/yapworld schem save|paste|import|list|load|delete|formats <name>");
        return true;
    }

    private boolean schemSave(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e/yapworld schem save <name>");
            return true;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1/pos2 first (volume limit " + config.maxVolume() + ").");
            return true;
        }
        CuboidSelection sel = opt.get();
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            player.sendMessage("§cWorld not loaded.");
            return true;
        }
        YapSched.global(plugin, () -> {
            try {
                Schematic schem = SchematicIO.capture(sel, world);
                Path file = plugin.schematicsDir().resolve(args[2] + ".yschem");
                SchematicIO.save(file, schem);
                player.sendMessage("§aSaved §f" + args[2] + ".yschem §a(" + schem.blocks().size() + " blocks).");
            } catch (Exception e) {
                player.sendMessage("§cSave failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean schemPaste(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e/yapworld schem paste <name>");
            return true;
        }
        String name = args[2];
        Path yschem = plugin.schematicsDir().resolve(name + ".yschem");
        Path schem = plugin.schematicsDir().resolve(name + ".schem");
        Path file = Files.isRegularFile(yschem) ? yschem : schem;
        if (!Files.isRegularFile(file)) {
            player.sendMessage("§cSchematic not found (.yschem or .schem).");
            return true;
        }
        var loc = player.getLocation();
        YapSched.async(plugin, () -> {
            try {
                Schematic schematic = file.toString().endsWith(".schem")
                        ? SpongeSchematicImporter.importFile(file)
                        : SchematicIO.load(file);
                World target = player.getWorld();
                paster.paste(schematic, target, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                        .thenAccept(count -> YapSched.global(plugin,
                                () -> player.sendMessage("§aPasted §f" + count + " §ablocks.")));
            } catch (Exception e) {
                YapSched.global(plugin, () -> player.sendMessage("§cPaste failed: " + e.getMessage()));
            }
        });
        return true;
    }

    private boolean schemImport(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§e/yapworld schem import <file.schem> [save-as]");
            return true;
        }
        String inputName = args[2];
        Path source = inputName.contains("/") || inputName.contains("\\")
                ? Path.of(inputName)
                : plugin.schematicsDir().resolve(inputName.endsWith(".schem") ? inputName : inputName + ".schem");
        if (!Files.isRegularFile(source)) {
            player.sendMessage("§cFile not found: " + source.getFileName());
            return true;
        }
        String saveAs = args.length >= 4 ? args[3] : source.getFileName().toString().replace(".schem", "");
        YapSched.async(plugin, () -> {
            try {
                Schematic schematic = SpongeSchematicImporter.importFile(source);
                Path out = plugin.schematicsDir().resolve(saveAs + ".yschem");
                SchematicIO.save(out, schematic);
                YapSched.global(plugin, () -> player.sendMessage("§aImported §f" + saveAs + ".yschem §a("
                        + schematic.blocks().size() + " blocks)."));
            } catch (Exception e) {
                YapSched.global(plugin, () -> player.sendMessage("§cImport failed: " + e.getMessage()));
            }
        });
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
