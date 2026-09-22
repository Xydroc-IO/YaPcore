package com.yapcore.admin.action;

import com.yapcore.admin.AdminGearKits;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.moderation.ModerationService;
import com.yapcore.sched.YapSched;
import com.yapcore.messages.YapMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Optional;

/** Staff actions for the admin super menu (Folia-safe). */
public final class AdminActions {

    private final AdminPlugin plugin;
    private final AdminTrollActions trolls;
    private final AdminItemActions items;
    private final AdminModerationActions moderationActions;
    private final AdminNightVision nightVision;

    public AdminActions(AdminPlugin plugin) {
        this.plugin = plugin;
        this.trolls = new AdminTrollActions(plugin);
        this.items = new AdminItemActions(this);
        this.moderationActions = new AdminModerationActions(this);
        this.nightVision = new AdminNightVision(plugin);
    }

    public AdminNightVision nightVision() {
        return nightVision;
    }

    public boolean pluginEnabled(String name) {
        var p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /** Economy UI requires YaPPlayerData. */
    public boolean economyPluginEnabled() {
        return pluginEnabled("YaPPlayerData");
    }

    public Optional<ModerationService> moderation() {
        RegisteredServiceProvider<ModerationService> rsp =
                Bukkit.getServicesManager().getRegistration(ModerationService.class);
        return rsp == null ? Optional.empty() : Optional.of(rsp.getProvider());
    }

    public Player resolveTarget(Player admin) {
        var session = plugin.session(admin.getUniqueId());
        if (!session.hasTarget()) {
            return null;
        }
        return Bukkit.getPlayer(session.targetUuid());
    }

    public Player resolveGiveTarget(Player admin) {
        Player target = resolveTarget(admin);
        return target != null ? target : admin;
    }

    public void teleport(Player who, Location dest) {
        if (who == null || dest == null || dest.getWorld() == null) {
            return;
        }
        Location copy = dest.clone();
        // Folia: sync teleport() throws UnsupportedOperationException under region threading.
        who.teleportAsync(copy);
    }

    public void teleportToPlayer(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.teleport") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapessentials.teleport");
            return;
        }
        teleport(admin, target.getLocation());
        admin.sendMessage("§aTeleported to §f" + target.getName() + "§a.");
    }

    public void teleportHere(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.teleport") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapessentials.teleport");
            return;
        }
        teleport(target, admin.getLocation());
        admin.sendMessage("§aBrought §f" + target.getName() + " §ato you.");
        target.sendMessage("§eYou were teleported by staff.");
    }

    public void teleportSpawn(Player admin, Player target) {
        Location spawn = target.getWorld().getSpawnLocation();
        teleport(target, spawn);
        admin.sendMessage("§aSent §f" + target.getName() + " §ato spawn.");
    }

    public void giveItem(Player admin, Player target, Material material, int amount) {
        if (!admin.hasPermission("yapadmin.give")) {
            YapMessages.noPermission(admin, "yapadmin.give");
            return;
        }
        if (material == null || !material.isItem()) {
            admin.sendMessage("§cInvalid item.");
            return;
        }
        int qty = Math.max(1, Math.min(material.getMaxStackSize() * 36, amount));
        YapSched.entity(plugin, target, () -> {
            int remaining = qty;
            while (remaining > 0) {
                int give = Math.min(remaining, material.getMaxStackSize());
                ItemStack piece = new ItemStack(material, give);
                var leftover = target.getInventory().addItem(piece);
                leftover.values().forEach(left ->
                        target.getWorld().dropItemNaturally(target.getLocation(), left));
                remaining -= give;
            }
            target.sendMessage("§aReceived §f" + qty + "× " + pretty(material) + "§a.");
        });
        if (!target.equals(admin)) {
            admin.sendMessage("§aGave §f" + qty + "× " + pretty(material) + " §ato §f" + target.getName() + "§a.");
        }
    }

    /** Give a full armor / weapon / tool gear kit in one Folia entity pass. */
    public void giveGearKit(Player admin, Player target, AdminGearKits.GearKit kit) {
        if (!admin.hasPermission("yapadmin.give")) {
            YapMessages.noPermission(admin, "yapadmin.give");
            return;
        }
        if (kit == null || kit.items().isEmpty()) {
            admin.sendMessage("§cEmpty gear kit.");
            return;
        }
        YapSched.entity(plugin, target, () -> {
            int pieces = 0;
            for (AdminGearKits.GearItem item : kit.items()) {
                if (item.material() == null || !item.material().isItem()) {
                    continue;
                }
                int remaining = Math.max(1, item.amount());
                while (remaining > 0) {
                    int give = Math.min(remaining, item.material().getMaxStackSize());
                    ItemStack piece = new ItemStack(item.material(), give);
                    var leftover = target.getInventory().addItem(piece);
                    leftover.values().forEach(left ->
                            target.getWorld().dropItemNaturally(target.getLocation(), left));
                    remaining -= give;
                }
                pieces++;
            }
            target.sendMessage("§aReceived gear kit §f" + kit.displayName()
                    + " §7(" + pieces + " stacks)§a.");
        });
        if (!target.equals(admin)) {
            admin.sendMessage("§aGave gear kit §f" + kit.displayName() + " §ato §f" + target.getName() + "§a.");
        }
    }

    /**
     * Spawn living / spawnable entities at a player's feet (Folia entity thread).
     * {@code at} null → spawn at admin. {@code level} null → leave leveling to YaPLeveledMobs.
     */
    public void spawnMobs(Player admin, Player at, org.bukkit.entity.EntityType type, int amount) {
        spawnMobs(admin, at, type, amount, null);
    }

    public void spawnMobs(
            Player admin,
            Player at,
            org.bukkit.entity.EntityType type,
            int amount,
            Integer level) {
        if (!admin.hasPermission("yapadmin.spawnmob")) {
            YapMessages.noPermission(admin, "yapadmin.spawnmob");
            return;
        }
        if (type == null || type == org.bukkit.entity.EntityType.PLAYER
                || !type.isSpawnable() || type == org.bukkit.entity.EntityType.UNKNOWN) {
            admin.sendMessage("§cCannot spawn that entity type.");
            return;
        }
        Player host = at != null ? at : admin;
        int qty = Math.max(1, Math.min(64, amount));
        YapSched.entity(plugin, host, () -> {
            Location loc = host.getLocation();
            int spawned = 0;
            String lastFail = null;
            for (int i = 0; i < qty; i++) {
                try {
                    var entity = host.getWorld().spawnEntity(loc, type,
                            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND);
                    if (entity != null && entity.isValid() && !entity.isDead()) {
                        if (level != null && entity instanceof org.bukkit.entity.LivingEntity living) {
                            if (!LeveledMobBridge.setLevel(living, level, plugin.getLogger())) {
                                lastFail = "spawned but level " + level
                                        + " not applied (YaPMobs / YaPLeveledMobs missing?)";
                            }
                        }
                        spawned++;
                    } else {
                        lastFail = "spawn cancelled (region mob-entry/mob-spawning, LagGuard, or knobs)";
                        break;
                    }
                } catch (Exception e) {
                    lastFail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    plugin.getLogger().warning("spawnmob " + type + ": " + lastFail);
                    break;
                }
            }
            int n = spawned;
            if (n <= 0) {
                admin.sendMessage("§cSpawn failed for §f" + type.name().toLowerCase(Locale.ROOT)
                        + (lastFail != null ? "§c: " + lastFail : "§c."));
                return;
            }
            String lvlBit = level != null ? " §7(lv §f" + level + "§7)" : "";
            admin.sendMessage("§aSpawned §f" + n + "× " + type.name().toLowerCase(Locale.ROOT)
                    + lvlBit + " §aat §f" + host.getName() + "§a.");
            if (!host.equals(admin)) {
                host.sendMessage("§e" + admin.getName() + " §7spawned §f" + n + "× "
                        + type.name().toLowerCase(Locale.ROOT) + " §7on you.");
            }
            if (lastFail != null && level != null) {
                admin.sendMessage("§eNote: §7" + lastFail);
            }
        });
    }

    public void giveKit(Player admin, Player target, String kitId) {
        if (!admin.hasPermission("yapadmin.give") && !admin.hasPermission("yapdata.kit.give")
                && !admin.hasPermission("yapdata.admin")) {
            YapMessages.noPermission(admin, "yapadmin.give");
            return;
        }
        if (!pluginEnabled("YaPPlayerData")) {
            admin.sendMessage("§cYaPPlayerData is not loaded.");
            return;
        }
        String cmd = "kit give " + target.getName() + " " + kitId;
        runAs(admin, cmd);
    }

    public void giveMoney(Player admin, Player target, int amount) {
        if (!admin.hasPermission("yapadmin.economy")) {
            YapMessages.noPermission(admin, "yapadmin.economy");
            return;
        }
        if (!economyPluginEnabled()) {
            admin.sendMessage("§cYaPPlayerData is not loaded.");
            return;
        }
        if (amount <= 0) {
            admin.sendMessage("§cAmount must be positive.");
            return;
        }
        var data = Bukkit.getServicesManager().load(com.yapcore.playerdata.PlayerDataService.class);
        if (data == null || !data.economyEnabled()) {
            admin.sendMessage("§cEconomy is disabled.");
            return;
        }
        // Deposit on the target's region thread — never dispatch /eco via global scheduler (Folia).
        YapSched.entity(plugin, target, () -> {
            var next = data.deposit(target.getUniqueId(), amount);
            YapSched.entity(plugin, admin, () -> {
                if (next.isEmpty()) {
                    admin.sendMessage("§cCould not deposit for §f" + target.getName() + "§c.");
                    return;
                }
                admin.sendMessage("§aGave §f$" + amount + " §ato §f" + target.getName()
                        + " §7(bal $" + String.format("%.2f", next.get()) + "§7).");
                if (!target.equals(admin)) {
                    target.sendMessage("§aYou received §f$" + amount + " §afrom staff."
                            + " §7Balance: §f$" + String.format("%.2f", next.get()));
                }
            });
        });
    }

    public void runAs(Player admin, String command) {
        // Folia: Bukkit.dispatchCommand requires the global tick thread.
        YapSched.global(plugin, () -> Bukkit.dispatchCommand(admin, command));
    }

    public void kick(Player admin, Player target, String reason) {
        moderationActions.kick(admin, target, reason);
    }

    public void warn(Player admin, Player target, String reason) {
        moderationActions.warn(admin, target, reason);
    }

    public void muteHour(Player admin, Player target, String reason) {
        moderationActions.muteHour(admin, target, reason);
    }

    public void tempbanDay(Player admin, Player target, String reason) {
        moderationActions.tempbanDay(admin, target, reason);
    }

    public void heal(Player admin, Player target) {
        YapSched.entity(plugin, target, () -> {
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
        });
        admin.sendMessage("§aHealed §f" + target.getName() + "§a.");
    }

    public void feed(Player admin, Player target) {
        YapSched.entity(plugin, target, () -> {
            target.setFoodLevel(20);
            target.setSaturation(20f);
        });
        admin.sendMessage("§aFed §f" + target.getName() + "§a.");
    }

    @Deprecated
    public void toggleNightVision(Player admin) {
        if (admin.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            nightVision.apply(admin, admin, AdminNightVision.Mode.OFF);
        } else {
            nightVision.apply(admin, admin, AdminNightVision.Mode.UNLIMITED);
        }
    }

    public void setNightVision(Player admin, Player target, AdminNightVision.Mode mode) {
        nightVision.apply(admin, target, mode);
    }

    public boolean requireTroll(Player admin) {
        return trolls.requireTroll(admin);
    }

    public void trollSmite(Player admin, Player target) {
        trolls.trollSmite(admin, target);
    }

    public void trollLaunch(Player admin, Player target) {
        trolls.trollLaunch(admin, target);
    }

    public void trollBurn(Player admin, Player target) {
        trolls.trollBurn(admin, target);
    }

    public void trollRocket(Player admin, Player target) {
        trolls.trollRocket(admin, target);
    }

    public void trollSquash(Player admin, Player target) {
        trolls.trollSquash(admin, target);
    }

    public void trollBlind(Player admin, Player target) {
        trolls.trollBlind(admin, target);
    }

    public void trollConfuse(Player admin, Player target) {
        trolls.trollConfuse(admin, target);
    }

    public void trollSlap(Player admin, Player target) {
        trolls.trollSlap(admin, target);
    }

    public void trollDropHand(Player admin, Player target) {
        trolls.trollDropHand(admin, target);
    }

    public void runTroll(Player admin, Player target, String type) {
        trolls.runTroll(admin, target, type);
    }

    public void clearInventory(Player admin, Player target) {
        if (!admin.hasPermission("yapessentials.clear") && !admin.isOp()) {
            YapMessages.noPermission(admin, "yapessentials.clear");
            return;
        }
        YapSched.entity(plugin, target, () -> target.getInventory().clear());
        admin.sendMessage("§aCleared inventory of §f" + target.getName() + "§a.");
        if (!target.equals(admin)) {
            target.sendMessage("§eYour inventory was cleared by staff.");
        }
    }

    public void broadcast(Player admin, String message) {
        if (!admin.hasPermission("yapadmin.server") && !admin.hasPermission("yapessentials.broadcast")) {
            YapMessages.noPermission(admin, "yapadmin.server");
            return;
        }
        if (pluginEnabled("YaPEssentials") && admin.hasPermission("yapessentials.broadcast")) {
            runAs(admin, "broadcast " + message);
            return;
        }
        Bukkit.broadcast(Component.text("[Broadcast] " + message, NamedTextColor.GOLD));
    }

    public void closeAndRun(Player admin, String command) {
        admin.closeInventory();
        YapSched.globalLater(plugin, () -> Bukkit.dispatchCommand(admin, command), 1L);
    }

    public boolean giveYapItem(Player admin, String targetName, String itemId, int amount) {
        return items.giveYapItem(admin, targetName, itemId, amount);
    }

    AdminPlugin plugin() {
        return plugin;
    }

    public boolean setItemAbilityCooldown(Player admin, String itemId, String duration) {
        return items.setItemAbilityCooldown(admin, itemId, duration);
    }

    public static String itemAbilityCooldownLabel(String itemId) {
        return AdminItemActions.itemAbilityCooldownLabel(itemId);
    }

    public static String pretty(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.equals("SHEARS") || n.equals("FISHING_ROD")
                || n.equals("FLINT_AND_STEEL") || n.equals("BRUSH");
    }

    public static boolean isCombat(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.equals("BOW")
                || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("SHIELD")
                || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") || n.equals("TIPPED_ARROW")
                || n.equals("MACE");
    }
}
