package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.schem.SpongeSchematicImporter;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;

/** Schematic save/paste/import command handlers for {@link WorldCommands}. */
final class WorldCommandsSchematics {

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final SelectionServiceImpl selection;
    private final SchematicPaster paster;
    private final WorldEditOps editOps;

    WorldCommandsSchematics(WorldPlugin plugin, WorldConfig config, SelectionServiceImpl selection,
                            SchematicPaster paster, WorldEditOps editOps) {
        this.plugin = plugin;
        this.config = config;
        this.selection = selection;
        this.paster = paster;
        this.editOps = editOps;
    }

    boolean schem(CommandSender sender, String[] args) {
        if (!config.schematicsEnabled()) {
            sender.sendMessage("§cSchematics disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!player.hasPermission("yapworld.schematic")) {
            YapMessages.noPermission(sender, "yapworld.schematic");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/yapworld schem save|paste|import|list|load|delete|formats|browse|confirm|cancel|here|undo");
            return true;
        }
        if ("browse".equalsIgnoreCase(args[1]) || "gui".equalsIgnoreCase(args[1])
                || "menu".equalsIgnoreCase(args[1])) {
            plugin.openSchematicsGui(player);
            return true;
        }
        if ("confirm".equalsIgnoreCase(args[1]) || "yes".equalsIgnoreCase(args[1])
                || "apply".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"confirm"});
        }
        if ("cancel".equalsIgnoreCase(args[1]) || "abort".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"cancel"});
        }
        if ("here".equalsIgnoreCase(args[1]) || "move".equalsIgnoreCase(args[1])
                || "reposition".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"here"});
        }
        if ("undo".equalsIgnoreCase(args[1])) {
            return editOps.dispatch(player, "schem", new String[]{"undo"});
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
        sender.sendMessage("§e/yapworld schem save|paste|import|list|load|delete|formats|browse|confirm|cancel|here|undo");
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
            player.sendMessage("§e/yapworld schem paste <name> [-y]");
            return true;
        }
        String name = args[2];
        boolean skipPreview = false;
        for (int i = 3; i < args.length; i++) {
            if ("-y".equalsIgnoreCase(args[i]) || "--yes".equalsIgnoreCase(args[i])) {
                skipPreview = true;
            }
        }
        Path file = com.yapcore.world.schem.SchematicCatalog.resolve(plugin.schematicsDir(), name);
        if (file == null) {
            player.sendMessage("§cSchematic not found.");
            return true;
        }
        var loc = player.getLocation();
        String label = file.getFileName().toString();
        boolean skip = skipPreview;
        YapSched.async(plugin, () -> {
            try {
                Schematic schematic = com.yapcore.world.schem.SchematicCatalog.load(file);
                YapSched.global(plugin, () -> {
                    if (skip) {
                        paster.paste(player, schematic, player.getWorld(),
                                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                                .thenAccept(count -> YapSched.global(plugin, () -> {
                                    player.sendMessage("§aPasted §f" + count + " §ablocks.");
                                    player.sendMessage("§7Wrong place? §f//undo §7to remove.");
                                }));
                        return;
                    }
                    plugin.pastePreview().begin(player, schematic, label,
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false);
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> player.sendMessage("§cLoad failed: " + e.getMessage()));
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
}
