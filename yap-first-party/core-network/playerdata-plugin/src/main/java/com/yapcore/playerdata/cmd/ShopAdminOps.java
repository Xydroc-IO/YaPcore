package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.ShopRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Coord-based chest shop admin (dashboard / console). */
final class ShopAdminOps {

    static final UUID CONSOLE_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final ShopRepository shops;

    ShopAdminOps(JavaPlugin plugin, PlayerDataConfig config, ShopRepository shops) {
        this.plugin = plugin;
        this.config = config;
        this.shops = shops;
    }

    boolean list(CommandSender sender, String[] args) throws SQLException {
        boolean json = false;
        boolean all = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            if ("json".equals(a)) {
                json = true;
            } else if ("all".equals(a)) {
                all = true;
            }
        }
        if (all && sender instanceof Player && !sender.hasPermission("yapdata.admin")) {
            sender.sendMessage("§cNeed yapdata.admin to list every backend.");
            return true;
        }
        List<ShopRepository.Shop> rows = all ? shops.listAll() : shops.list(config.serverId());
        if (json) {
            sender.sendMessage(ShopJson.PREFIX + ShopJson.list(rows));
            return true;
        }
        if (rows.isEmpty()) {
            sender.sendMessage("§7No chest shops" + (all ? "." : " on this server."));
            return true;
        }
        sender.sendMessage("§6Chest shops (" + rows.size() + ")");
        for (ShopRepository.Shop s : rows) {
            sender.sendMessage("§7 " + s.serverId() + " " + s.world() + " "
                    + s.x() + "," + s.y() + "," + s.z()
                    + " §f" + s.amount() + "x " + s.material().name()
                    + " §a$" + String.format(Locale.ROOT, "%.2f", s.price())
                    + " §8" + ShopJson.ownerName(s.owner()));
        }
        return true;
    }

    boolean createAt(CommandSender sender, String[] args) throws Exception {
        Parsed p = parseCoords(sender, args, 1);
        if (p == null) {
            return true;
        }
        String err = requireChest(p.world, p.x, p.y, p.z);
        if (err != null) {
            sender.sendMessage("§c" + err);
            return true;
        }
        shops.upsert(new ShopRepository.Shop(
                0, p.owner, config.serverId(), p.world.getName(),
                p.x, p.y, p.z, p.material, p.amount, p.price));
        sender.sendMessage("§aChest shop §f" + p.amount + "x " + p.material.name()
                + " §a@ §f$" + String.format(Locale.ROOT, "%.2f", p.price)
                + " §7" + p.world.getName() + " " + p.x + "," + p.y + "," + p.z);
        return true;
    }

    boolean setAt(CommandSender sender, String[] args) throws Exception {
        return createAt(sender, args);
    }

    boolean removeAt(CommandSender sender, String[] args) throws Exception {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /shop remove <world> <x> <y> <z>");
            return true;
        }
        String worldName = args[1];
        Integer x = parseInt(sender, args[2]);
        Integer y = parseInt(sender, args[3]);
        Integer z = parseInt(sender, args[4]);
        if (x == null || y == null || z == null) {
            return true;
        }
        boolean ok = shops.delete(config.serverId(), worldName, x, y, z);
        sender.sendMessage(ok ? "§aChest shop removed." : "§cNo chest shop at that block.");
        return true;
    }

    boolean infoAt(CommandSender sender, String[] args) throws Exception {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /shop info <world> <x> <y> <z> [json]");
            return true;
        }
        boolean json = args.length >= 6 && "json".equalsIgnoreCase(args[5]);
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage("§cUnknown world: " + args[1]);
            return true;
        }
        Integer x = parseInt(sender, args[2]);
        Integer y = parseInt(sender, args[3]);
        Integer z = parseInt(sender, args[4]);
        if (x == null || y == null || z == null) {
            return true;
        }
        var opt = shops.findAt(config.serverId(), world.getName(), x, y, z);
        if (opt.isEmpty()) {
            sender.sendMessage("§cNo chest shop there.");
            return true;
        }
        ShopRepository.Shop s = opt.get();
        int stock = probeStock(world, x, y, z, s);
        if (json) {
            sender.sendMessage(ShopJson.PREFIX + ShopJson.one(s, Math.max(stock, 0)));
            return true;
        }
        sender.sendMessage("§aShop: §f" + s.amount() + "x " + s.material().name()
                + " §a@ §f$" + String.format(Locale.ROOT, "%.2f", s.price())
                + " §7stock=" + (stock < 0 ? "?" : stock)
                + " §8" + ShopJson.ownerName(s.owner()));
        return true;
    }

    private String requireChest(World world, int x, int y, int z) {
        return onChest(world, x, y, z, chest -> null, "No chest at " + world.getName()
                + " " + x + "," + y + "," + z);
    }

    private int probeStock(World world, int x, int y, int z, ShopRepository.Shop shop) {
        String raw = onChest(world, x, y, z, chest -> {
            int n = 0;
            for (ItemStack stack : chest.getInventory().getContents()) {
                if (stack != null && stack.getType() == shop.material()) {
                    n += stack.getAmount();
                }
            }
            return Integer.toString(n);
        }, null);
        if (raw == null) {
            return -1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String onChest(World world, int x, int y, int z,
                           java.util.function.Function<Chest, String> fn, String missing) {
        CompletableFuture<String> done = new CompletableFuture<>();
        YapSched.region(plugin, world, x, z, () -> {
            try {
                Block block = world.getBlockAt(x, y, z);
                if (!(block.getState() instanceof Chest chest)) {
                    done.complete(missing);
                    return;
                }
                done.complete(fn.apply(chest));
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            return done.get(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            return missing != null ? missing : null;
        }
    }

    private Parsed parseCoords(CommandSender sender, String[] args, int from) {
        if (args.length < from + 7) {
            sender.sendMessage("§cUsage: /shop create <world> <x> <y> <z> <material> <amount> <price> [owner]");
            return null;
        }
        World world = Bukkit.getWorld(args[from]);
        if (world == null) {
            sender.sendMessage("§cUnknown world: " + args[from]);
            return null;
        }
        Integer x = parseInt(sender, args[from + 1]);
        Integer y = parseInt(sender, args[from + 2]);
        Integer z = parseInt(sender, args[from + 3]);
        if (x == null || y == null || z == null) {
            return null;
        }
        Material material = Material.matchMaterial(args[from + 4]);
        if (material == null || material.isAir() || !material.isItem()) {
            sender.sendMessage("§cUnknown item: " + args[from + 4]);
            return null;
        }
        Integer amount = parseInt(sender, args[from + 5]);
        if (amount == null || amount < 1) {
            sender.sendMessage("§cAmount must be ≥ 1.");
            return null;
        }
        double price;
        try {
            price = Double.parseDouble(args[from + 6]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid price.");
            return null;
        }
        if (price <= 0) {
            sender.sendMessage("§cPrice must be positive.");
            return null;
        }
        UUID owner = resolveOwner(sender, args.length > from + 7 ? args[from + 7] : null);
        return new Parsed(world, x, y, z, material, amount, price, owner);
    }

    private UUID resolveOwner(CommandSender sender, String raw) {
        if (raw == null || raw.isBlank()) {
            if (sender instanceof Player player) {
                return player.getUniqueId();
            }
            return CONSOLE_OWNER;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(raw);
        return off.getUniqueId();
    }

    private static Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid integer: " + raw);
            return null;
        }
    }

    private record Parsed(World world, int x, int y, int z, Material material, int amount,
                          double price, UUID owner) {
    }
}
