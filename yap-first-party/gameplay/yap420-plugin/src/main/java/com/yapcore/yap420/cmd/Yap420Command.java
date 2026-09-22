package com.yapcore.yap420.cmd;

import com.yapcore.yap420.Yap420Config;
import com.yapcore.yap420.Yap420Plugin;
import com.yapcore.yap420.cure.RackDisplayService;
import com.yapcore.yap420.cure.RackRegistry;
import com.yapcore.yap420.cure.RackState;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.item.Yap420ItemIds;
import com.yapcore.yap420.market.DealerGui;
import com.yapcore.yap420.market.DealerListener;
import com.yapcore.yap420.market.DealerService;
import com.yapcore.yap420.market.PackService;
import com.yapcore.yap420.market.PackUnit;
import com.yapcore.yap420.persist.PlotStore;
import com.yapcore.yap420.persist.RackStore;
import com.yapcore.yap420.plant.PlantDisplayService;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.PlotState;
import com.yapcore.yap420.plant.StrainId;
import com.yapcore.yap420.press.PressDisplayService;
import com.yapcore.yap420.press.PressRegistry;
import com.yapcore.yap420.press.PressState;
import com.yapcore.yap420.press.PressStore;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /yap420 reload|info|remove|give|sell|pack|unpack */
public final class Yap420Command implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> GIVE_IDS = List.of(
            Yap420ItemIds.SEED_SATIVA,
            Yap420ItemIds.SEED_INDICA,
            Yap420ItemIds.BUD_WET_SATIVA,
            Yap420ItemIds.BUD_WET_INDICA,
            Yap420ItemIds.BUD_CURED_SATIVA,
            Yap420ItemIds.BUD_CURED_INDICA,
            Yap420ItemIds.GRAM_SATIVA,
            Yap420ItemIds.GRAM_INDICA,
            Yap420ItemIds.OUNCE_SATIVA,
            Yap420ItemIds.OUNCE_INDICA,
            Yap420ItemIds.BRICK_SATIVA,
            Yap420ItemIds.BRICK_INDICA,
            Yap420ItemIds.ROLLING_PAPER,
            Yap420ItemIds.JOINT_SATIVA,
            Yap420ItemIds.JOINT_INDICA,
            Yap420ItemIds.BLUNT_SATIVA,
            Yap420ItemIds.BLUNT_INDICA,
            Yap420ItemIds.BROWNIE,
            Yap420ItemIds.DRYING_RACK,
            Yap420ItemIds.PACKAGING_PRESS,
            Yap420ItemIds.HEMP_FIBER
    );

    private final Yap420Plugin plugin;
    private final Yap420Config config;
    private final ItemBridge items;
    private final PlotRegistry plots;
    private final RackRegistry racks;
    private final PressRegistry presses;
    private final PlantDisplayService plantDisplays;
    private final RackDisplayService rackDisplays;
    private final PressDisplayService pressDisplays;
    private final PlotStore plotStore;
    private final RackStore rackStore;
    private final PressStore pressStore;
    private final PackService packService;
    private final DealerService dealerService;
    private final DealerGui dealerGui;
    private final DealerListener dealerListener;

    public Yap420Command(
            Yap420Plugin plugin,
            Yap420Config config,
            ItemBridge items,
            PlotRegistry plots,
            RackRegistry racks,
            PressRegistry presses,
            PlantDisplayService plantDisplays,
            RackDisplayService rackDisplays,
            PressDisplayService pressDisplays,
            PlotStore plotStore,
            RackStore rackStore,
            PressStore pressStore,
            PackService packService,
            DealerService dealerService,
            DealerGui dealerGui,
            DealerListener dealerListener
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.plots = plots;
        this.racks = racks;
        this.presses = presses;
        this.plantDisplays = plantDisplays;
        this.rackDisplays = rackDisplays;
        this.pressDisplays = pressDisplays;
        this.plotStore = plotStore;
        this.rackStore = rackStore;
        this.pressStore = pressStore;
        this.packService = packService;
        this.dealerService = dealerService;
        this.dealerGui = dealerGui;
        this.dealerListener = dealerListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(LEGACY.deserialize("&e/yap420 <reload|info|remove|give|sell|pack|unpack>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            case "remove" -> remove(sender);
            case "give" -> give(sender, args);
            case "sell", "dealer", "shop" -> sell(sender);
            case "pack" -> pack(sender, args, true);
            case "unpack" -> pack(sender, args, false);
            default -> {
                sender.sendMessage(LEGACY.deserialize(
                        "&e/yap420 <reload|info|remove|give|sell|pack|unpack>"));
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("yap420.admin")) {
            sender.sendMessage(LEGACY.deserialize("&cNo permission."));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(LEGACY.deserialize(plugin.yapConfig().messages().reloaded()));
        return true;
    }

    private boolean info(CommandSender sender) {
        var m = plugin.yapConfig().market();
        sender.sendMessage(LEGACY.deserialize("&aYaP420 plots=&f" + plots.size()
                + " &aracks=&f" + racks.size()
                + " &apresses=&f" + presses.size()
                + " &astages=&f" + config.stages()
                + " &aitems=&f" + (items.service().isPresent() ? "ok" : "missing YaPItems")
                + " &amarket=&f" + (m.enabled() ? "on" : "off")
                + " &a" + m.pack().gramsPerOunce() + "g/oz · "
                + m.pack().ouncesPerBrick() + "oz/lb"));
        return true;
    }

    private boolean remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&cPlayers only."));
            return true;
        }
        if (!player.hasPermission("yap420.admin")) {
            sender.sendMessage(LEGACY.deserialize("&cNo permission."));
            return true;
        }
        var target = player.getTargetBlockExact(6);
        if (target == null) {
            sender.sendMessage(LEGACY.deserialize(config.messages().nothing()));
            return true;
        }
        YapSched.region(plugin, target.getLocation(), () -> {
            boolean removed = false;
            PlotState plot = plots.remove(target.getWorld().getName(), target.getX(), target.getY(), target.getZ())
                    .orElse(null);
            if (plot != null) {
                plantDisplays.remove(plot);
                plotStore.saveAsync();
                removed = true;
            }
            RackState rack = racks.remove(target.getWorld().getName(), target.getX(), target.getY(), target.getZ())
                    .orElse(null);
            if (rack != null) {
                rackDisplays.remove(rack);
                rackStore.saveAsync();
                removed = true;
            }
            PressState press = presses.remove(target.getWorld().getName(), target.getX(), target.getY(), target.getZ())
                    .orElse(null);
            if (press != null) {
                pressDisplays.remove(press);
                pressStore.saveAsync();
                removed = true;
            }
            if (removed) {
                player.sendMessage(LEGACY.deserialize(config.messages().removed()));
            } else {
                player.sendMessage(LEGACY.deserialize(config.messages().nothing()));
            }
        });
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yap420.admin")) {
            sender.sendMessage(LEGACY.deserialize("&cNo permission."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&cPlayers only."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize("&e/yap420 give <id> [amount]"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("seed")) {
            id = Yap420ItemIds.seed(StrainId.SATIVA);
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }
        ItemStack stack = items.create(id, amount).orElse(null);
        if (stack == null) {
            sender.sendMessage(LEGACY.deserialize("&cUnknown or unloaded item &f" + id
                    + "&c — is YaPItems up with yap420.yml?"));
            return true;
        }
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        sender.sendMessage(LEGACY.deserialize("&aGave &f" + amount + "x " + id));
        return true;
    }

    private boolean sell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&cPlayers only."));
            return true;
        }
        if (!player.hasPermission("yap420.use")) {
            sender.sendMessage(LEGACY.deserialize("&cNo permission."));
            return true;
        }
        if (!plugin.yapConfig().market().enabled()) {
            player.sendMessage(LEGACY.deserialize(plugin.yapConfig().market().messages().disabled()));
            return true;
        }
        YapSched.entity(plugin, player, () -> dealerGui.open(player));
        return true;
    }

    private boolean pack(CommandSender sender, String[] args, boolean packing) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LEGACY.deserialize("&cPlayers only."));
            return true;
        }
        if (!player.hasPermission("yap420.use")) {
            sender.sendMessage(LEGACY.deserialize("&cNo permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(LEGACY.deserialize(
                    "&e/yap420 " + (packing ? "pack" : "unpack") + " <gram|ounce|pound> [sativa|indica|all] [amount]"));
            return true;
        }
        PackUnit unit = PackUnit.parse(args[1]).orElse(null);
        if (unit == null) {
            sender.sendMessage(LEGACY.deserialize("&cUnknown unit — use gram, ounce, or pound."));
            return true;
        }
        String strainRaw = args.length >= 3 ? args[2] : "all";
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                amount = 1;
            }
        } else if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                strainRaw = "all";
            } catch (NumberFormatException ignored) {
                // strain name
            }
        }
        List<StrainId> strains = new ArrayList<>();
        if ("all".equalsIgnoreCase(strainRaw)) {
            strains.add(StrainId.SATIVA);
            strains.add(StrainId.INDICA);
        } else {
            StrainId.parse(strainRaw).ifPresent(strains::add);
        }
        if (strains.isEmpty()) {
            sender.sendMessage(LEGACY.deserialize("&cStrain must be sativa, indica, or all."));
            return true;
        }
        int finalAmount = amount;
        YapSched.entity(plugin, player, () -> {
            for (StrainId strain : strains) {
                PackService.Outcome out = packing
                        ? packService.pack(player, unit, strain, finalAmount)
                        : packService.unpack(player, unit, strain, finalAmount);
                dealerListener.tellPack(player, out, packing);
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "info", "remove", "give", "sell", "pack", "unpack"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>(GIVE_IDS);
            for (StrainId strain : StrainId.values()) {
                for (int s = 0; s < 6; s++) {
                    ids.add(Yap420ItemIds.plantStage(strain, s));
                }
            }
            return filter(ids, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("pack") || args[0].equalsIgnoreCase("unpack"))) {
            return filter(List.of("gram", "ounce", "pound", "lb", "brick"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("pack") || args[0].equalsIgnoreCase("unpack"))) {
            return filter(List.of("sativa", "indica", "all", "1", "8", "16", "64"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "64"), args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("pack") || args[0].equalsIgnoreCase("unpack"))) {
            return filter(List.of("1", "8", "16", "64"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
