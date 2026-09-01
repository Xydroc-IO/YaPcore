package com.yapcore.world.gui;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.schem.Schematic;
import com.yapcore.world.schem.SchematicIO;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.schem.SpongeSchematicImporter;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.tool.WorldEditSession;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WorldEditGuiListener implements Listener {

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final WorldEditGui gui;
    private final SelectionServiceImpl selection;
    private final BrushService brushService;
    private final SelectionEditService selectionEdit;
    private final ClipboardService clipboard;
    private final GenerationService generation;
    private final UndoService undoService;
    private final SchematicPaster paster;

    public WorldEditGuiListener(WorldPlugin plugin, WorldConfig config, WorldEditGui gui,
                                SelectionServiceImpl selection, BrushService brushService,
                                SelectionEditService selectionEdit, ClipboardService clipboard,
                                GenerationService generation, UndoService undoService,
                                SchematicPaster paster) {
        this.plugin = plugin;
        this.config = config;
        this.gui = gui;
        this.selection = selection;
        this.brushService = brushService;
        this.selectionEdit = selectionEdit;
        this.clipboard = clipboard;
        this.generation = generation;
        this.undoService = undoService;
        this.paster = paster;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof WorldEditGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (holder.kind() == WorldEditGuiHolder.Kind.SCHEMATICS) {
            handleSchemClick(player, slot, event.getCurrentItem());
            return;
        }
        handleMainClick(player, event.getInventory(), slot);
    }

    private void handleMainClick(Player player, Inventory inv, int slot) {
        WorldEditSession session = WorldEditSession.of(player.getUniqueId());
        if (slot == WorldEditGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == WorldEditGui.SLOT_TOOL) {
            gui.giveTool(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_POS1) {
            var loc = player.getLocation();
            selection.setPos1(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos1 set.");
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_POS2) {
            var loc = player.getLocation();
            selection.setPos2(player.getUniqueId(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            player.sendMessage("§aPos2 set.");
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_CLEAR) {
            selection.clearSelection(player.getUniqueId());
            player.sendMessage("§eSelection cleared.");
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_MODE) {
            session.toggleMode();
            brushService.setBrush(player.getUniqueId(), session.brushRadius(), session.material());
            player.sendMessage("§aMode: §f" + session.mode().name().toLowerCase(Locale.ROOT));
            gui.refreshMain(player, inv);
            return;
        }
        if (slot >= WorldEditGui.PALETTE_START && slot < WorldEditGui.PALETTE_START + WorldEditGui.PALETTE.length) {
            Material mat = WorldEditGui.PALETTE[slot - WorldEditGui.PALETTE_START];
            session.setMaterial(mat);
            brushService.setBrush(player.getUniqueId(), session.brushRadius(), mat);
            player.sendMessage("§aMaterial: §f" + mat.name());
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_RADIUS_DOWN) {
            session.adjustRadius(-1, config.maxBrushRadius());
            brushService.setBrush(player.getUniqueId(), session.brushRadius(), session.material());
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_RADIUS_UP) {
            session.adjustRadius(1, config.maxBrushRadius());
            brushService.setBrush(player.getUniqueId(), session.brushRadius(), session.material());
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_FILL) {
            fillSelection(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_WALLS) {
            wallsSelection(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_HOLLOW) {
            hollowSelection(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_COPY) {
            copySelection(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_CLIP_PASTE) {
            pasteClipboard(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_EXPAND) {
            selection.expand(player.getUniqueId(), 1, "all");
            player.sendMessage("§aExpanded selection.");
            gui.refreshMain(player, inv);
            return;
        }
        if (slot == WorldEditGui.SLOT_SPHERE) {
            sphereHere(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_CYL) {
            cylHere(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_UNDO) {
            undoService.undo(player.getUniqueId()).thenAccept(count ->
                    YapSched.global(plugin, () -> player.sendMessage("§aUndid §f" + count + " §ablocks.")));
            return;
        }
        if (slot == WorldEditGui.SLOT_REDO) {
            undoService.redo(player.getUniqueId()).thenAccept(count ->
                    YapSched.global(plugin, () -> player.sendMessage("§aRedid §f" + count + " §ablocks.")));
            return;
        }
        if (slot == WorldEditGui.SLOT_SAVE) {
            quickSave(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_PASTE_LIST) {
            gui.openSchematics(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_BROWSER) {
            player.closeInventory();
            plugin.openBrowserEditor(player);
            return;
        }
        if (slot == WorldEditGui.SLOT_PREGEN) {
            startPregen(player);
        }
    }

    private void handleSchemClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            gui.openMain(player);
            return;
        }
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = plainName(clicked);
        if (name.isBlank() || "No schematics yet".equals(name) || "Back".equals(name)
                || "Saved schematics".equals(name)) {
            return;
        }
        pasteSchematic(player, name);
    }

    private void fillSelection(Player player) {
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first (max volume " + config.maxVolume() + ").");
            return;
        }
        Material mat = WorldEditSession.of(player.getUniqueId()).material();
        player.sendMessage("§7Filling selection with §f" + mat.name() + "§7…");
        selectionEdit.fill(player, opt.get(), mat).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aFilled §f" + count + " §ablocks.")));
    }

    private void wallsSelection(Player player) {
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return;
        }
        Material mat = WorldEditSession.of(player.getUniqueId()).material();
        player.sendMessage("§7Building walls with §f" + mat.name() + "§7…");
        selectionEdit.walls(player, opt.get(), mat).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aWalls §f" + count + " §ablocks.")));
    }

    private void hollowSelection(Player player) {
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return;
        }
        player.sendMessage("§7Hollowing selection…");
        selectionEdit.hollow(player, opt.get()).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aHollowed §f" + count + " §ablocks.")));
    }

    private void copySelection(Player player) {
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return;
        }
        clipboard.copy(player, opt.get(), false).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aCopied §f" + count + " §ablocks.")));
    }

    private void pasteClipboard(Player player) {
        clipboard.paste(player, false).thenAccept(count ->
                YapSched.global(plugin, () -> {
                    if (count == 0) {
                        player.sendMessage("§cClipboard empty — copy a selection first.");
                    } else {
                        player.sendMessage("§aPasted §f" + count + " §ablocks.");
                    }
                }));
    }

    private void sphereHere(Player player) {
        WorldEditSession session = WorldEditSession.of(player.getUniqueId());
        String pattern = session.material().name().toLowerCase(Locale.ROOT);
        generation.sphere(player, player.getLocation(), pattern, session.brushRadius(), false)
                .thenAccept(count -> YapSched.global(plugin, () ->
                        player.sendMessage("§aSphere §f" + count + " §ablocks.")));
    }

    private void cylHere(Player player) {
        WorldEditSession session = WorldEditSession.of(player.getUniqueId());
        String pattern = session.material().name().toLowerCase(Locale.ROOT);
        int r = session.brushRadius();
        generation.cylinder(player, player.getLocation(), pattern, r, Math.max(1, r), false)
                .thenAccept(count -> YapSched.global(plugin, () ->
                        player.sendMessage("§aCylinder §f" + count + " §ablocks.")));
    }

    private void quickSave(Player player) {
        if (!config.schematicsEnabled()) {
            player.sendMessage("§cSchematics disabled.");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return;
        }
        String name = "quick-" + player.getName().toLowerCase(Locale.ROOT);
        World world = Bukkit.getWorld(opt.get().world());
        if (world == null) {
            player.sendMessage("§cWorld not loaded.");
            return;
        }
        YapSched.global(plugin, () -> {
            try {
                Schematic schem = SchematicIO.capture(opt.get(), world);
                Path file = plugin.schematicsDir().resolve(name + ".yschem");
                SchematicIO.save(file, schem);
                player.sendMessage("§aSaved §f" + name + ".yschem §a(" + schem.blocks().size() + " blocks).");
            } catch (Exception e) {
                player.sendMessage("§cSave failed: " + e.getMessage());
            }
        });
    }

    private void pasteSchematic(Player player, String name) {
        if (!config.schematicsEnabled()) {
            player.sendMessage("§cSchematics disabled.");
            return;
        }
        Path yschem = plugin.schematicsDir().resolve(name + ".yschem");
        Path schem = plugin.schematicsDir().resolve(name + ".schem");
        Path file = Files.isRegularFile(yschem) ? yschem : schem;
        if (!Files.isRegularFile(file)) {
            player.sendMessage("§cSchematic not found.");
            return;
        }
        var loc = player.getLocation();
        player.closeInventory();
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
    }

    private void startPregen(Player player) {
        if (!player.hasPermission("yapworld.pregen")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (!PregenBridge.available()) {
            player.sendMessage("§cYaPPregen is not loaded.");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return;
        }
        String msg = PregenBridge.startSelection(player.getWorld(), opt.get());
        player.sendMessage("§a" + msg);
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WorldEditGuiHolder) {
            event.setCancelled(true);
        }
    }

    private static String plainName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null || stack.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }
}
