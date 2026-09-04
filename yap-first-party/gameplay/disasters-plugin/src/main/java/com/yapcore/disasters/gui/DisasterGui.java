package com.yapcore.disasters.gui;

import com.yapcore.disasters.DisasterType;
import com.yapcore.disasters.DisastersPlugin;
import com.yapcore.disasters.SkyWeather;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisasterGui implements Listener {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_CLEAR = 10;
    private static final int SLOT_RAIN = 11;
    private static final int SLOT_THUNDER = 12;
    private static final int SLOT_HURRICANE = 13;
    private static final int SLOT_TORNADO = 14;
    private static final int SLOT_QUAKE = 15;
    private static final int SLOT_VOLCANO = 16;
    private static final int SLOT_BLIZZARD = 19;
    private static final int SLOT_DROUGHT = 21;
    private static final int SLOT_METEOR = 23;
    private static final int SLOT_TSUNAMI = 24;
    private static final int SLOT_RANDOM = 25;
    private static final int SLOT_SITE_ADD = 28;
    private static final int SLOT_SITE_ERUPT = 30;
    private static final int SLOT_DUR_1M = 37;
    private static final int SLOT_DUR_5M = 38;
    private static final int SLOT_DUR_15M = 39;
    private static final int SLOT_CYCLE = 42;
    private static final int SLOT_STOP = 43;
    private static final int SLOT_CLOSE = 49;

    private final DisastersPlugin plugin;
    private final Map<UUID, Integer> durationSeconds = new ConcurrentHashMap<>();

    public DisasterGui(DisastersPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!player.hasPermission("yapdisasters.use")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        World world = player.getWorld();
        DisasterGuiHolder holder = new DisasterGuiHolder(world.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("YaP Disasters — " + world.getName(), NamedTextColor.GOLD));
        holder.bind(inv);
        fill(inv);

        int dur = durationSeconds.getOrDefault(player.getUniqueId(), plugin.config().defaultDurationSeconds());
        boolean cycle = Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE));
        boolean grief = plugin.config().grief();
        boolean protectClaims = plugin.config().protectClaims();
        boolean protectRegions = plugin.config().protectRegions();

        inv.setItem(SLOT_INFO, icon(Material.NETHER_STAR, NamedTextColor.AQUA, "Disasters (Phase 3–5a)",
                "Sky: " + SkyWeather.describe(world),
                "Active FX: " + plugin.manager().describeActive(world),
                "Duration: " + formatDuration(dur),
                "Grief: " + (grief ? "ON" : "off"),
                "Random: " + plugin.randomEvents().statusLine(),
                "Volcano sites: " + plugin.volcanoSites().all().size(),
                "Protect claims: " + (protectClaims ? "yes" : "no"),
                "Protect regions: " + (protectRegions ? "yes" : "no"),
                "Cycle: " + (cycle ? "on" : "locked")));

        inv.setItem(SLOT_CLEAR, icon(Material.SUNFLOWER, NamedTextColor.YELLOW, "Clear", "Clear skies"));
        inv.setItem(SLOT_RAIN, icon(Material.WATER_BUCKET, NamedTextColor.AQUA, "Rain", "Start rain"));
        inv.setItem(SLOT_THUNDER, icon(Material.LIGHTNING_ROD, NamedTextColor.LIGHT_PURPLE, "Thunderstorm",
                "Storm + lightning FX"));
        inv.setItem(SLOT_HURRICANE, icon(Material.WHITE_BANNER, NamedTextColor.WHITE, "Hurricane",
                "Thunder + wind"));
        inv.setItem(SLOT_TORNADO, icon(Material.FEATHER, NamedTextColor.GRAY, "Tornado",
                "Swirl near you"));
        inv.setItem(SLOT_QUAKE, icon(Material.COBBLESTONE, NamedTextColor.DARK_GRAY, "Earthquake",
                "Shake players"));
        inv.setItem(SLOT_VOLCANO, icon(Material.MAGMA_BLOCK, NamedTextColor.RED, "Volcano",
                "Eruption FX (snaps to nearby site)",
                grief ? "Grief lava (unprotected only)" : "Particles only (grief off)"));
        inv.setItem(SLOT_BLIZZARD, icon(Material.SNOW_BLOCK, NamedTextColor.WHITE, "Blizzard",
                "Snow + slow",
                grief ? "Temp snow on wilderness" : "Particles only (grief off)"));
        inv.setItem(SLOT_DROUGHT, icon(Material.DEAD_BUSH, NamedTextColor.GOLD, "Drought",
                "Heat haze / dry FX",
                grief ? "Temp dry unprotected water/grass" : "Particles only (grief off)"));
        inv.setItem(SLOT_METEOR, icon(Material.FIRE_CHARGE, NamedTextColor.DARK_RED, "Meteor shower",
                "Sky impacts near you",
                grief ? "Temp fire on wilderness" : "Particles only (grief off)"));
        inv.setItem(SLOT_TSUNAMI, icon(Material.PRISMARINE, NamedTextColor.AQUA, "Tsunami / flood",
                "Wave surge near you",
                grief ? "Temp water on wilderness" : "Particles only (grief off)"));

        boolean randomOn = plugin.randomEvents().runtimeEnabled();
        inv.setItem(SLOT_RANDOM, icon(
                randomOn ? Material.CLOCK : Material.GRAY_DYE,
                randomOn ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                randomOn ? "Random events: ON" : "Random events: OFF",
                plugin.randomEvents().statusLine(),
                "Click: toggle runtime",
                "Shift-click: force now (with warning)"));
        inv.setItem(SLOT_SITE_ADD, icon(Material.LODESTONE, NamedTextColor.GOLD, "Add volcano site here",
                "Tags this spot as a soft volcano",
                "Ambient smoke when idle"));
        inv.setItem(SLOT_SITE_ERUPT, icon(Material.FIRE_CORAL, NamedTextColor.RED, "Erupt nearest site",
                "Or random site in this world",
                "Sites: " + plugin.volcanoSites().all().size()));

        inv.setItem(SLOT_DUR_1M, durationIcon(dur, 60, "1 minute"));
        inv.setItem(SLOT_DUR_5M, durationIcon(dur, 300, "5 minutes"));
        inv.setItem(SLOT_DUR_15M, durationIcon(dur, 900, "15 minutes"));

        inv.setItem(SLOT_CYCLE, icon(
                cycle ? Material.LIME_DYE : Material.GRAY_DYE,
                cycle ? NamedTextColor.GREEN : NamedTextColor.RED,
                cycle ? "Weather cycle: ON" : "Weather cycle: LOCKED",
                "Toggle natural weather cycle"));
        inv.setItem(SLOT_STOP, icon(Material.BARRIER, NamedTextColor.RED, "Stop FX",
                "Cancel active disaster / warning"));
        inv.setItem(SLOT_CLOSE, icon(Material.DARK_OAK_DOOR, NamedTextColor.GRAY, "Close"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof DisasterGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!player.hasPermission("yapdisasters.use")) {
            player.closeInventory();
            return;
        }
        World world = player.getWorld();
        int slot = event.getSlot();
        switch (slot) {
            case SLOT_CLOSE -> player.closeInventory();
            case SLOT_CLEAR -> start(player, world, DisasterType.CLEAR);
            case SLOT_RAIN -> start(player, world, DisasterType.RAIN);
            case SLOT_THUNDER -> start(player, world, DisasterType.THUNDER);
            case SLOT_HURRICANE -> start(player, world, DisasterType.HURRICANE);
            case SLOT_TORNADO -> start(player, world, DisasterType.TORNADO);
            case SLOT_QUAKE -> start(player, world, DisasterType.EARTHQUAKE);
            case SLOT_VOLCANO -> start(player, world, DisasterType.VOLCANO);
            case SLOT_BLIZZARD -> start(player, world, DisasterType.BLIZZARD);
            case SLOT_DROUGHT -> start(player, world, DisasterType.DROUGHT);
            case SLOT_METEOR -> start(player, world, DisasterType.METEOR);
            case SLOT_TSUNAMI -> start(player, world, DisasterType.TSUNAMI);
            case SLOT_RANDOM -> {
                if (event.isShiftClick()) {
                    boolean ok = plugin.randomEvents().triggerNow(world, null);
                    player.sendMessage(ok
                            ? "§aRandom event warning started."
                            : "§cCould not trigger random (busy / empty pool).");
                } else if (player.hasPermission("yapdisasters.admin")) {
                    boolean next = !plugin.randomEvents().runtimeEnabled();
                    plugin.randomEvents().setRuntimeEnabled(next);
                    player.sendMessage(next ? "§aRandom disasters ON." : "§eRandom disasters OFF.");
                } else {
                    player.sendMessage("§e" + plugin.randomEvents().statusLine());
                }
                open(player);
            }
            case SLOT_SITE_ADD -> {
                String id = "site_" + (plugin.volcanoSites().all().size() + 1);
                boolean ok = plugin.volcanoSites().add(id, player.getLocation(), false);
                player.sendMessage(ok ? "§aAdded volcano site §f" + id : "§cCould not add site.");
                open(player);
            }
            case SLOT_SITE_ERUPT -> {
                var opt = plugin.volcanoSites().randomActiveInWorld(world);
                if (opt.isEmpty()) {
                    // Try snap from player position after adding none — resolve via start volcano
                    player.sendMessage("§eNo sites in this world — starting volcano at your location.");
                    start(player, world, DisasterType.VOLCANO);
                    return;
                }
                int dur = durationSeconds.getOrDefault(player.getUniqueId(), plugin.config().defaultDurationSeconds());
                var loc = opt.get().toLocation();
                boolean ok = loc != null && plugin.manager().start(world, DisasterType.VOLCANO, dur, loc);
                player.sendMessage(ok
                        ? "§aErupting §f" + opt.get().id()
                        : "§cCould not erupt site.");
                YapSched.entityLater(plugin, player, () -> open(player), 2L);
            }
            case SLOT_DUR_1M -> {
                durationSeconds.put(player.getUniqueId(), 60);
                open(player);
            }
            case SLOT_DUR_5M -> {
                durationSeconds.put(player.getUniqueId(), 300);
                open(player);
            }
            case SLOT_DUR_15M -> {
                durationSeconds.put(player.getUniqueId(), 900);
                open(player);
            }
            case SLOT_CYCLE -> {
                boolean cycle = Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE));
                SkyWeather.setCycle(plugin, world, !cycle);
                player.sendMessage(!cycle ? "§aWeather cycle unlocked." : "§eWeather cycle locked.");
                YapSched.entityLater(plugin, player, () -> open(player), 2L);
            }
            case SLOT_STOP -> {
                plugin.warnings().cancel(world);
                plugin.manager().stop(world);
                player.sendMessage("§eDisaster FX / warning stopped.");
                open(player);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DisasterGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void start(Player player, World world, DisasterType type) {
        int dur = durationSeconds.getOrDefault(player.getUniqueId(), plugin.config().defaultDurationSeconds());
        boolean ok = plugin.manager().start(world, type, dur, player.getLocation());
        if (!ok) {
            player.sendMessage("§cCould not start — disabled in config or world not allowed.");
            return;
        }
        player.sendMessage("§aStarted §f" + type.configKey() + " §afor §f" + formatDuration(dur) + "§a.");
        YapSched.entityLater(plugin, player, () -> open(player), 2L);
    }

    private ItemStack durationIcon(int selected, int value, String label) {
        boolean on = selected == value;
        return icon(on ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                on ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                (on ? "▶ " : "") + label, "Select duration");
    }

    private static void fill(Inventory inv) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.DARK_GRAY, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private static ItemStack icon(Material mat, NamedTextColor color, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lines);
            }
        });
        return stack;
    }

    private static String formatDuration(int seconds) {
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }
}
