package com.yapcore.world.gui;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.tool.WorldEditSession;
import com.yapcore.world.tool.WorldEditTool;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** In-game world edit control panel. */
public final class WorldEditGui {

    static final Material[] PALETTE = {
            Material.STONE, Material.DIRT, Material.GRASS_BLOCK, Material.OAK_PLANKS,
            Material.GLASS, Material.SAND, Material.COBBLESTONE, Material.DEEPSLATE, Material.AIR
    };

    // Main menu slot map
    static final int SLOT_INFO = 4;
    static final int SLOT_TOOL = 10;
    static final int SLOT_POS1 = 11;
    static final int SLOT_POS2 = 12;
    static final int SLOT_CLEAR = 13;
    static final int SLOT_MODE = 14;
    static final int SLOT_FILL = 15;
    static final int SLOT_UNDO = 16;
    static final int SLOT_REDO = 17;
    static final int SLOT_RADIUS_DOWN = 28;
    static final int SLOT_RADIUS = 29;
    static final int SLOT_RADIUS_UP = 30;
    static final int SLOT_SAVE = 31;
    static final int SLOT_PASTE_LIST = 32;
    static final int SLOT_WALLS = 34;
    static final int SLOT_HOLLOW = 35;
    static final int SLOT_COPY = 42;
    static final int SLOT_CLIP_PASTE = 43;
    static final int SLOT_EXPAND = 44;
    static final int SLOT_SPHERE = 45;
    static final int SLOT_CYL = 46;
    static final int SLOT_BROWSER = 37;
    static final int SLOT_PREGEN = 40;
    static final int SLOT_CLOSE = 49;
    static final int PALETTE_START = 19;

    private final WorldPlugin plugin;
    private final WorldConfig config;
    private final SelectionServiceImpl selection;
    private final WorldEditTool tool;

    public WorldEditGui(WorldPlugin plugin, WorldConfig config, SelectionServiceImpl selection, WorldEditTool tool) {
        this.plugin = plugin;
        this.config = config;
        this.selection = selection;
        this.tool = tool;
    }

    public void openMain(Player player) {
        if (!config.selectionEnabled()) {
            player.sendMessage("§cWorld edit selection is disabled.");
            return;
        }
        WorldEditGuiHolder holder = new WorldEditGuiHolder(WorldEditGuiHolder.Kind.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP World Edit"));
        holder.bind(inv);
        WorldEditGuiHolder.fillBorder(inv);
        populateMain(player, inv);
        player.openInventory(inv);
    }

    public void refreshMain(Player player, Inventory inv) {
        populateMain(player, inv);
    }

    private void populateMain(Player player, Inventory inv) {
        WorldEditSession session = WorldEditSession.of(player.getUniqueId());
        Optional<CuboidSelection> sel = selection.selection(player.getUniqueId());
        String pos1 = selection.pos1Label(player.getUniqueId()).orElse("not set");
        String pos2 = selection.pos2Label(player.getUniqueId()).orElse("not set");
        long volume = sel.map(CuboidSelection::volume).orElse(0L);

        inv.setItem(SLOT_INFO, WorldEditGuiHolder.icon(Material.MAP, "Selection",
                "Pos1: " + pos1,
                "Pos2: " + pos2,
                "Volume: " + volume + " / " + config.maxVolume(),
                "Mode: " + session.mode().name().toLowerCase(Locale.ROOT)));

        inv.setItem(SLOT_TOOL, WorldEditGuiHolder.icon(Material.GOLDEN_AXE, "Get edit tool",
                "Left = pos1 · Right = pos2/brush",
                "Shift + right-click = this menu"));

        inv.setItem(SLOT_POS1, WorldEditGuiHolder.icon(Material.LIME_WOOL, "Set pos1 here",
                "Uses your feet position"));
        inv.setItem(SLOT_POS2, WorldEditGuiHolder.icon(Material.RED_WOOL, "Set pos2 here",
                "Uses your feet position"));
        inv.setItem(SLOT_CLEAR, WorldEditGuiHolder.icon(Material.BARRIER, "Clear selection"));

        Material modeIcon = session.mode() == WorldEditSession.Mode.BRUSH ? Material.BLAZE_ROD : Material.WOODEN_AXE;
        inv.setItem(SLOT_MODE, WorldEditGuiHolder.icon(modeIcon, "Mode: " + session.mode().name(),
                "Click to toggle Select / Brush"));

        inv.setItem(SLOT_FILL, WorldEditGuiHolder.icon(Material.BUCKET, "Fill selection",
                "Material: " + session.material().name(),
                sel.isEmpty() ? "Set pos1 + pos2 first" : "Ready"));
        inv.setItem(SLOT_WALLS, WorldEditGuiHolder.icon(Material.BRICKS, "Walls",
                "Four sides with " + session.material().name()));
        inv.setItem(SLOT_HOLLOW, WorldEditGuiHolder.icon(Material.SPONGE, "Hollow",
                "Clear interior, keep shell"));

        inv.setItem(SLOT_COPY, WorldEditGuiHolder.icon(Material.SHEARS, "Copy selection",
                "Clipboard relative to your feet"));
        inv.setItem(SLOT_CLIP_PASTE, WorldEditGuiHolder.icon(Material.SLIME_BALL, "Paste clipboard",
                "At your feet · ignores air? use //paste -a"));
        inv.setItem(SLOT_EXPAND, WorldEditGuiHolder.icon(Material.PISTON, "Expand +1 all",
                "Grow selection · //expand <n> [dir]"));
        inv.setItem(SLOT_SPHERE, WorldEditGuiHolder.icon(Material.FIRE_CHARGE, "Sphere here",
                "Radius = brush · material from palette"));
        inv.setItem(SLOT_CYL, WorldEditGuiHolder.icon(Material.END_ROD, "Cylinder here",
                "Radius/height = brush · material from palette"));

        inv.setItem(SLOT_UNDO, WorldEditGuiHolder.icon(Material.ORANGE_STAINED_GLASS, "Undo"));
        inv.setItem(SLOT_REDO, WorldEditGuiHolder.icon(Material.CYAN_STAINED_GLASS, "Redo"));

        for (int i = 0; i < PALETTE.length && i < 9; i++) {
            Material mat = PALETTE[i];
            if (mat == session.material()) {
                inv.setItem(PALETTE_START + i, WorldEditGuiHolder.selected(mat, pretty(mat)));
            } else {
                inv.setItem(PALETTE_START + i, WorldEditGuiHolder.icon(mat, pretty(mat), "Click to select"));
            }
        }

        inv.setItem(SLOT_RADIUS_DOWN, WorldEditGuiHolder.icon(Material.REDSTONE, "Radius −"));
        inv.setItem(SLOT_RADIUS, WorldEditGuiHolder.icon(Material.PAPER, "Brush radius: " + session.brushRadius(),
                "Max " + config.maxBrushRadius()));
        inv.setItem(SLOT_RADIUS_UP, WorldEditGuiHolder.icon(Material.GLOWSTONE_DUST, "Radius +"));

        inv.setItem(SLOT_SAVE, WorldEditGuiHolder.icon(Material.WRITABLE_BOOK, "Quick save schematic",
                "Saves as quick-<name>"));
        inv.setItem(SLOT_PASTE_LIST, WorldEditGuiHolder.icon(Material.CHEST, "Paste schematic",
                "Browse saved schematics"));
        inv.setItem(SLOT_BROWSER, WorldEditGuiHolder.icon(Material.COMPASS, "Browser studio",
                "Optional web editor (advanced)"));

        inv.setItem(SLOT_PREGEN, WorldEditGuiHolder.icon(Material.RECOVERY_COMPASS, "Pregen selection",
                "Needs YaPPregen + selection"));

        inv.setItem(SLOT_CLOSE, WorldEditGuiHolder.icon(Material.DARK_OAK_DOOR, "Close"));
    }

    public void openSchematics(Player player) {
        WorldEditGuiHolder holder = new WorldEditGuiHolder(WorldEditGuiHolder.Kind.SCHEMATICS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Schematics"));
        holder.bind(inv);
        WorldEditGuiHolder.fillBorder(inv);
        inv.setItem(4, WorldEditGuiHolder.icon(Material.BOOKSHELF, "Saved schematics",
                "Click a file to paste at your feet",
                "Back arrow returns to editor"));
        inv.setItem(45, WorldEditGuiHolder.icon(Material.ARROW, "Back"));

        List<String> names = listSchematics();
        int slot = 9;
        for (String name : names) {
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot++, WorldEditGuiHolder.icon(Material.PAPER, name, "Click to paste"));
        }
        if (names.isEmpty()) {
            inv.setItem(22, WorldEditGuiHolder.icon(Material.BARRIER, "No schematics yet",
                    "Use Quick save in the editor"));
        }
        player.openInventory(inv);
    }

    List<String> listSchematics() {
        Path dir = plugin.schematicsDir();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".yschem") || n.endsWith(".schem");
            }).sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(p -> {
                String file = p.getFileName().toString();
                out.add(file.replace(".yschem", "").replace(".schem", ""));
            });
        } catch (IOException ignored) {
            // empty list
        }
        return out;
    }

    public void giveTool(Player player) {
        player.getInventory().addItem(tool.create());
        player.sendMessage("§aWorld edit tool equipped — shift + right-click to open the editor.");
    }

    private static String pretty(Material mat) {
        return mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
