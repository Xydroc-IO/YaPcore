package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.schem.LegacySchematicImporter;
import com.yapcore.world.schem.LitematicImporter;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.schem.SpongeSchematicExporter;
import com.yapcore.world.schem.SpongeSchematicImporter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Schematic and clipboard-slot operations for WorldEdit commands.
 */
final class WorldEditSchematicOps {

    private final WorldPlugin plugin;
    private final ClipboardService clipboard;
    private final WorldEditOpsSupport support;

    WorldEditSchematicOps(WorldPlugin plugin, ClipboardService clipboard, WorldEditOpsSupport support) {
        this.plugin = plugin;
        this.clipboard = clipboard;
        this.support = support;
    }

    void applyClipboardSlotFlag(Player player, String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-m".equalsIgnoreCase(args[i]) || "-slot".equalsIgnoreCase(args[i])) {
                clipboard.setSlot(player.getUniqueId(), WorldEditOpsSupport.parseInt(args[i + 1], 0));
                return;
            }
        }
    }

    void clipboardSlot(Player player, String[] args) {
        if (args.length >= 1) {
            clipboard.setSlot(player.getUniqueId(), WorldEditOpsSupport.parseInt(args[0], 0));
        }
        player.sendMessage("§aClipboard §f" + clipboard.statusLine(player.getUniqueId()));
    }

    boolean copy(Player player, String[] args) {
        applyClipboardSlotFlag(player, args);
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        clipboard.copy(player, sel.get(), false).thenAccept(n ->
                YapSched.global(plugin, () -> {
                    var clip = clipboard.clipboard(player.getUniqueId());
                    int ents = clip == null ? 0 : clip.entities().size();
                    player.sendMessage("§aCopied §f" + n + " §ablocks"
                            + (ents > 0 ? " §7+ §f" + ents + " §7entities" : "")
                            + " §7(slot " + clipboard.slot(player.getUniqueId()) + ")");
                }));
        return true;
    }

    boolean cut(Player player, String[] args) {
        applyClipboardSlotFlag(player, args);
        Optional<CuboidSelection> sel = support.requireSel(player);
        if (sel.isEmpty()) {
            return true;
        }
        clipboard.copy(player, sel.get(), true).thenAccept(n ->
                YapSched.global(plugin, () -> {
                    var clip = clipboard.clipboard(player.getUniqueId());
                    int ents = clip == null ? 0 : clip.entities().size();
                    player.sendMessage("§aCut §f" + n + " §ablocks"
                            + (ents > 0 ? " §7+ §f" + ents + " §7entities" : "")
                            + " §7(slot " + clipboard.slot(player.getUniqueId()) + ")");
                }));
        return true;
    }

    boolean paste(Player player, String[] args) {
        ClipboardService.PasteOptions opts = ClipboardService.PasteOptions.parse(args);
        var clip = clipboard.clipboard(player.getUniqueId());
        boolean large = clip != null && clipboard.isLargePaste(clip.blocks().size());
        if (large) {
            player.sendMessage("§eLarge paste §7(§f" + clip.blocks().size() + " §7blocks) — "
                    + (plugin.worldConfig().autoFastLarge() ? "§efast/no-undo §7· " : "")
                    + "§eprogress on.");
        }
        clipboard.paste(player, opts).thenAccept(n ->
                YapSched.global(plugin, () -> {
                    player.sendMessage("§aPasted §f" + n + " §ablocks"
                            + (opts.entities() ? " §7(+entities)" : "")
                            + (opts.biomes() ? " §7(+biomes)" : "")
                            + (opts.atOrigin() ? " §7@origin" : "")
                            + ".");
                    if (large && plugin.worldConfig().deferRelightLarge()) {
                        player.sendMessage("§7Relighting…");
                        support.maybeAutoRelight(player);
                    } else {
                        support.maybeAutoRelight(player);
                    }
                }));
        return true;
    }

    boolean rotate(Player player, String[] args) {
        int deg = args.length >= 1 ? WorldEditOpsSupport.parseInt(args[0], 90) : 90;
        if (clipboard.rotateY(player.getUniqueId(), deg)) {
            player.sendMessage("§aClipboard rotated §f" + deg + "°");
        } else {
            player.sendMessage("§cClipboard empty.");
        }
        return true;
    }

    boolean flip(Player player, String[] args) {
        char axis = args.length >= 1 ? Character.toLowerCase(args[0].charAt(0)) : 'x';
        if (clipboard.flip(player.getUniqueId(), axis)) {
            player.sendMessage("§aClipboard flipped on §f" + axis);
        } else {
            player.sendMessage("§cClipboard empty or bad axis (x/y/z).");
        }
        return true;
    }

    void schemCmd(Player player, String[] args) {
        if (!plugin.worldConfig().schematicsEnabled()) {
            player.sendMessage("§cSchematics disabled.");
            return;
        }
        if (!player.hasPermission("yapworld.schematic")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 1) {
            player.sendMessage("§e//schem list|load|save|delete|formats|paste <name>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "ls" -> schemList(player);
            case "formats", "format" -> player.sendMessage(
                    "§aFormats: §f.yschem §7(native) §f.schem §7(Sponge import/export) §f.schematic §7(legacy import) §f.litematic §7(import)");
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage("§e//schem save <name> [.yschem|.schem]");
                    return;
                }
                schemSave(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "load", "paste" -> {
                if (args.length < 2) {
                    player.sendMessage("§e//schem " + sub + " <name>");
                    return;
                }
                if ("load".equals(sub)) {
                    schemLoadClipboard(player, args[1]);
                } else {
                    schemPasteAtFeet(player, args[1]);
                }
            }
            case "delete", "rm", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§e//schem delete <name>");
                    return;
                }
                schemDelete(player, args[1]);
            }
            default -> player.sendMessage("§e//schem list|load|save|delete|formats|paste <name>");
        }
    }

    void schemList(Player player) {
        Path dir = plugin.schematicsDir();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String n = p.getFileName().toString();
                if (n.endsWith(".yschem") || n.endsWith(".schem")
                        || n.endsWith(".schematic") || n.endsWith(".litematic")) {
                    names.add(n);
                }
            }
        } catch (Exception e) {
            player.sendMessage("§cList failed: " + e.getMessage());
            return;
        }
        if (names.isEmpty()) {
            player.sendMessage("§eNo schematics in §f" + dir.getFileName());
            return;
        }
        player.sendMessage("§aSchematics (§f" + names.size() + "§a):");
        names.stream().sorted().limit(40).forEach(n -> player.sendMessage("  §7" + n));
        if (names.size() > 40) {
            player.sendMessage("  §8… +" + (names.size() - 40) + " more");
        }
    }

    void schemSave(Player player, String name, String formatHint) {
        Optional<CuboidSelection> opt = support.requireSel(player);
        if (opt.isEmpty()) {
            return;
        }
        CuboidSelection sel = opt.get();
        World world = org.bukkit.Bukkit.getWorld(sel.world());
        if (world == null) {
            player.sendMessage("§cWorld not loaded.");
            return;
        }
        String fmt = formatHint == null ? ".yschem" : formatHint.toLowerCase(Locale.ROOT);
        if (!fmt.startsWith(".")) {
            fmt = "." + fmt;
        }
        if (name.endsWith(".schem") || name.endsWith(".yschem") || name.endsWith(".schematic")) {
            int dot = name.lastIndexOf('.');
            fmt = name.substring(dot);
            name = name.substring(0, dot);
        }
        final String base = name;
        final String format = fmt;
        YapSched.global(plugin, () -> {
            try {
                Schematic schem = SchematicIO.capture(sel, world);
                if (".schem".equals(format)) {
                    Path file = plugin.schematicsDir().resolve(base + ".schem");
                    SpongeSchematicExporter.exportFile(file, schem);
                    player.sendMessage("§aSaved §f" + base + ".schem §a(" + schem.blocks().size() + " blocks).");
                } else {
                    Path file = plugin.schematicsDir().resolve(base + ".yschem");
                    SchematicIO.save(file, schem);
                    player.sendMessage("§aSaved §f" + base + ".yschem §a(" + schem.blocks().size() + " blocks).");
                }
            } catch (Exception e) {
                player.sendMessage("§cSave failed: " + e.getMessage());
            }
        });
    }

    void schemLoadClipboard(Player player, String name) {
        Path file = resolveSchemFile(name);
        if (file == null) {
            player.sendMessage("§cSchematic not found.");
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                Schematic schematic = loadAnySchematic(file);
                YapSched.global(plugin, () -> {
                    clipboard.loadSchematic(player.getUniqueId(), schematic, 0, 0, 0);
                    player.sendMessage("§aLoaded §f" + file.getFileName() + " §ainto clipboard slot "
                            + clipboard.slot(player.getUniqueId()) + " (§f" + schematic.blocks().size() + "§a"
                            + (schematic.entities().isEmpty() ? "" : ", §f" + schematic.entities().size() + " §aents")
                            + ").");
                });
            } catch (Exception e) {
                YapSched.global(plugin, () -> player.sendMessage("§cLoad failed: " + e.getMessage()));
            }
        });
    }

    void schemPasteAtFeet(Player player, String name) {
        Path file = resolveSchemFile(name);
        if (file == null) {
            player.sendMessage("§cSchematic not found.");
            return;
        }
        Location loc = player.getLocation();
        YapSched.async(plugin, () -> {
            try {
                Schematic schematic = loadAnySchematic(file);
                SchematicPaster paster = new SchematicPaster(plugin);
                paster.paste(schematic, player.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                        .thenAccept(count -> YapSched.global(plugin,
                                () -> player.sendMessage("§aPasted §f" + count + " §ablocks.")));
            } catch (Exception e) {
                YapSched.global(plugin, () -> player.sendMessage("§cPaste failed: " + e.getMessage()));
            }
        });
    }

    static Schematic loadAnySchematic(Path file) throws Exception {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".schem")) {
            return SpongeSchematicImporter.importFile(file);
        }
        if (n.endsWith(".schematic")) {
            return LegacySchematicImporter.importFile(file);
        }
        if (n.endsWith(".litematic")) {
            return LitematicImporter.importFile(file);
        }
        return SchematicIO.load(file);
    }

    void schemDelete(Player player, String name) {
        Path file = resolveSchemFile(name);
        if (file == null) {
            player.sendMessage("§cSchematic not found.");
            return;
        }
        try {
            Files.deleteIfExists(file);
            player.sendMessage("§aDeleted §f" + file.getFileName());
        } catch (Exception e) {
            player.sendMessage("§cDelete failed: " + e.getMessage());
        }
    }

    Path resolveSchemFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yschem") || lower.endsWith(".schem")
                || lower.endsWith(".schematic") || lower.endsWith(".litematic")) {
            Path raw = plugin.schematicsDir().resolve(name);
            return Files.isRegularFile(raw) ? raw : null;
        }
        String base = name;
        Path yschem = plugin.schematicsDir().resolve(base + ".yschem");
        Path schem = plugin.schematicsDir().resolve(base + ".schem");
        Path schematic = plugin.schematicsDir().resolve(base + ".schematic");
        Path litematic = plugin.schematicsDir().resolve(base + ".litematic");
        if (Files.isRegularFile(yschem)) {
            return yschem;
        }
        if (Files.isRegularFile(schem)) {
            return schem;
        }
        if (Files.isRegularFile(schematic)) {
            return schematic;
        }
        if (Files.isRegularFile(litematic)) {
            return litematic;
        }
        Path raw = plugin.schematicsDir().resolve(name);
        return Files.isRegularFile(raw) ? raw : null;
    }
}
