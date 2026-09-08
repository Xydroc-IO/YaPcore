package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateDraft;
import com.yapcore.messages.YapMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Builds admin super-menu inventories. */
public final class AdminMenus {

    public static final int SLOT_BACK = 45;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_INFO = 4;

    // Hub
    public static final int HUB_PLAYERS = 10;
    public static final int HUB_SELF = 12;
    public static final int HUB_GIVE = 14;
    public static final int HUB_ITEMS = 15;
    public static final int HUB_MOD = 16;
    public static final int HUB_TROLLS = 19;
    public static final int HUB_SERVER = 28;
    public static final int HUB_ECONOMY = 30;
    public static final int HUB_LINKS = 32;
    public static final int HUB_COMBAT = 34;

    // Give hub
    public static final int GIVE_PRESETS = 20;
    public static final int GIVE_KITS = 22;
    public static final int GIVE_MATS = 24;
    public static final int GIVE_AMOUNT = 31;
    public static final int GIVE_TARGET = 40;

    // Materials nav
    public static final int MAT_PREV = 45;
    public static final int MAT_NEXT = 53;
    public static final int MAT_AMOUNT = 49;
    public static final int MAT_BACK = 48;
    public static final int CAT_ALL = 0;
    public static final int CAT_BLOCKS = 1;
    public static final int CAT_TOOLS = 2;
    public static final int CAT_COMBAT = 3;
    public static final int CAT_FOOD = 4;
    public static final int CAT_MISC = 5;

    private static final int PAGE_SIZE = 28;

    private final AdminPlugin plugin;

    public AdminMenus(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        if (!player.hasPermission("yapadmin.menu")) {
            YapMessages.noPermission(player, "yapadmin.menu");
            return;
        }
        if (AdminBedrockForms.tryOpenHub(plugin, player)) {
            return;
        }
        openHubInventory(player);
    }

    /** JE chest hub (also used as fallback from Bedrock form callbacks). */
    public void openHubInventory(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP Admin", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        AdminSession session = plugin.session(player.getUniqueId());
        String targetLore = session.hasTarget()
                ? "Player: " + session.targetName()
                : "No player selected";

        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.NETHER_STAR, NamedTextColor.AQUA, "YaP Admin",
                "Network admin tools",
                targetLore,
                "Online: " + Bukkit.getOnlinePlayers().size()));

        inv.setItem(HUB_PLAYERS, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Players",
                "Online list — manage one player"));
        inv.setItem(HUB_SELF, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
                "God, fly, vanish, gamemode, speed"));
        if (player.hasPermission("yapadmin.give")) {
            inv.setItem(HUB_GIVE, AdminMenuHolder.icon(Material.CHEST, "Give",
                    "Items, kits, and materials"));
        }
        if (plugin.actions().pluginEnabled("YaPItems") && player.hasPermission("yapadmin.give")) {
            inv.setItem(HUB_ITEMS, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Custom items",
                    "Browse, give, and create YaPItems"));
        }
        inv.setItem(HUB_MOD, AdminMenuHolder.icon(Material.IRON_SWORD, NamedTextColor.RED, "Moderation",
                "Kick, warn, mute, tempban"));
        if (player.hasPermission("yapadmin.troll")) {
            inv.setItem(HUB_TROLLS, AdminMenuHolder.icon(Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Trolls",
                    "Smite, launch, burn, and more"));
        }
        if (player.hasPermission("yapadmin.server")) {
            inv.setItem(HUB_SERVER, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server",
                    "Broadcast, weather, status"));
        }
        if (player.hasPermission("yapadmin.economy") && plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(HUB_ECONOMY, AdminMenuHolder.icon(Material.GOLD_INGOT, "Economy",
                    "Grant money to a player"));
        }
        inv.setItem(HUB_LINKS, AdminMenuHolder.icon(Material.COMPASS, "More…",
                "Ranks, world edit, stacker, menu"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(HUB_COMBAT, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills",
                    "Open the skills menu"));
        }
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openPlayers(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.PLAYERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Online players", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Online players",
                "Click a head to manage"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        int slot = 10;
        for (Player other : online) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(other);
                meta.displayName(Component.text(other.getName()).color(NamedTextColor.GREEN)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("World: " + other.getWorld().getName())
                                .color(NamedTextColor.GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text("Click to manage")
                                .color(NamedTextColor.DARK_GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            });
            inv.setItem(slot, head);
            slot++;
        }
        player.openInventory(inv);
    }

    public void openPlayerActions(Player player, Player target) {
        plugin.session(player.getUniqueId()).setTarget(target.getUniqueId(), target.getName());
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.PLAYER_ACTIONS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Player: " + target.getName(), NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(SLOT_INFO, playerHead(target, "Managing " + target.getName()));
        inv.setItem(10, AdminMenuHolder.icon(Material.ENDER_PEARL, "TP to player",
                "Teleport yourself to them"));
        inv.setItem(11, AdminMenuHolder.icon(Material.LEAD, "TP here",
                "Bring them to you"));
        inv.setItem(12, AdminMenuHolder.icon(Material.OAK_DOOR, "TP to spawn",
                "Send them to world spawn"));
        inv.setItem(14, AdminMenuHolder.icon(Material.PACKED_ICE, "Freeze",
                "Toggle freeze (Essentials)"));
        inv.setItem(15, AdminMenuHolder.icon(Material.CHEST, "Invsee",
                "Open their inventory"));
        inv.setItem(16, AdminMenuHolder.icon(Material.ENDER_CHEST, "Ender chest",
                "Open their ender chest"));

        inv.setItem(19, AdminMenuHolder.icon(Material.GOLDEN_APPLE, NamedTextColor.GREEN, "Heal",
                "Full health + hunger"));
        inv.setItem(20, AdminMenuHolder.icon(Material.COOKED_BEEF, "Feed",
                "Restore hunger"));
        inv.setItem(21, AdminMenuHolder.icon(Material.LAVA_BUCKET, NamedTextColor.RED, "Clear inv",
                "Requires confirm click twice"));
        inv.setItem(23, AdminMenuHolder.icon(Material.EMERALD, "Promote",
                "/promote " + target.getName()));
        inv.setItem(24, AdminMenuHolder.icon(Material.REDSTONE, "Demote",
                "/demote " + target.getName()));
        if (player.hasPermission("yapadmin.give")) {
            inv.setItem(25, AdminMenuHolder.icon(Material.SHULKER_BOX, "Give items",
                    "Open give menu for this player"));
        }

        inv.setItem(28, AdminMenuHolder.icon(Material.IRON_BOOTS, NamedTextColor.RED, "Kick",
                "Immediate kick"));
        inv.setItem(29, AdminMenuHolder.icon(Material.PAPER, "Warn",
                "Staff warning"));
        inv.setItem(30, AdminMenuHolder.icon(Material.WRITABLE_BOOK, "Mute 1h",
                "Temp mute one hour"));
        inv.setItem(31, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.DARK_RED, "Tempban 1d",
                "Ban for one day"));

        if (player.hasPermission("yapadmin.troll")) {
            inv.setItem(33, AdminMenuHolder.icon(Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Smite",
                    "Strike with lightning"));
            inv.setItem(34, AdminMenuHolder.icon(Material.FIREWORK_ROCKET, "Launch",
                    "Yeet upward"));
            inv.setItem(35, AdminMenuHolder.icon(Material.FLINT_AND_STEEL, "Burn",
                    "Set on fire 8s"));
            inv.setItem(36, AdminMenuHolder.icon(Material.ENDER_EYE, "Blind",
                    "Blindness 12s"));
            inv.setItem(37, AdminMenuHolder.icon(Material.SLIME_BALL, "Slap",
                    "Knock away"));
            inv.setItem(38, AdminMenuHolder.icon(Material.TNT, "Rocket",
                    "High launch + bolt FX"));
            inv.setItem(39, AdminMenuHolder.icon(Material.ANVIL, "Squash",
                    "Drop from height"));
            inv.setItem(40, AdminMenuHolder.icon(Material.POISONOUS_POTATO, "Confuse",
                    "Nausea 15s"));
            inv.setItem(41, AdminMenuHolder.icon(Material.DROPPER, "Drop hand",
                    "Force-drop held item"));
        }

        inv.setItem(42, AdminMenuHolder.icon(Material.SPYGLASS, "Check",
                "/check " + target.getName()));
        inv.setItem(43, AdminMenuHolder.icon(Material.BOOK, "Mod history",
                "/modhistory " + target.getName()));

        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openTrolls(Player player, Player target) {
        if (!player.hasPermission("yapadmin.troll")) {
            YapMessages.noPermission(player, "yapadmin.troll");
            return;
        }
        plugin.session(player.getUniqueId()).setTarget(target.getUniqueId(), target.getName());
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.TROLLS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Troll: " + target.getName(), NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, playerHead(target, "Trolling " + target.getName()));
        inv.setItem(19, AdminMenuHolder.icon(Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Smite", "Lightning"));
        inv.setItem(20, AdminMenuHolder.icon(Material.FIREWORK_ROCKET, "Launch", "Yeet up"));
        inv.setItem(21, AdminMenuHolder.icon(Material.FLINT_AND_STEEL, "Burn", "Fire 8s"));
        inv.setItem(22, AdminMenuHolder.icon(Material.TNT, "Rocket", "Launch + bolt FX"));
        inv.setItem(23, AdminMenuHolder.icon(Material.ANVIL, "Squash", "Drop from height"));
        inv.setItem(24, AdminMenuHolder.icon(Material.ENDER_EYE, "Blind", "12s"));
        inv.setItem(25, AdminMenuHolder.icon(Material.POISONOUS_POTATO, "Confuse", "Nausea"));
        inv.setItem(28, AdminMenuHolder.icon(Material.SLIME_BALL, "Slap", "Knockback"));
        inv.setItem(29, AdminMenuHolder.icon(Material.DROPPER, "Drop hand", "Drop held item"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openSelfTools(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SELF_TOOLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Self tools", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
                "Actions apply to you"));
        inv.setItem(19, AdminMenuHolder.icon(Material.ELYTRA, "Fly", "Toggle flight"));
        inv.setItem(20, AdminMenuHolder.icon(Material.TOTEM_OF_UNDYING, "God", "Toggle invulnerability"));
        inv.setItem(21, AdminMenuHolder.icon(Material.GLASS, "Vanish", "Hide from players"));
        inv.setItem(22, AdminMenuHolder.icon(Material.GOLDEN_APPLE, "Heal", "Restore health and hunger"));
        inv.setItem(23, AdminMenuHolder.icon(Material.COOKED_BEEF, "Feed", "Fill hunger"));
        inv.setItem(24, AdminMenuHolder.icon(Material.ENDER_EYE, "Night vision", "Toggle for 5 minutes"));
        inv.setItem(28, AdminMenuHolder.icon(Material.GRASS_BLOCK, "Survival", "Set gamemode survival"));
        inv.setItem(29, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Creative", "Set gamemode creative"));
        inv.setItem(30, AdminMenuHolder.icon(Material.STONE, "Adventure", "Set gamemode adventure"));
        inv.setItem(31, AdminMenuHolder.icon(Material.ENDER_PEARL, "Spectator", "Set gamemode spectator"));
        inv.setItem(33, AdminMenuHolder.icon(Material.ANVIL, "Repair", "Repair held item"));
        inv.setItem(34, AdminMenuHolder.icon(Material.SUGAR, "Walk speed 5", "Set walk speed to 5/10"));
        inv.setItem(35, AdminMenuHolder.icon(Material.FEATHER, "Fly speed 5", "Set fly speed to 5/10"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openGiveHub(Player player) {
        if (!player.hasPermission("yapadmin.give")) {
            YapMessages.noPermission(player, "yapadmin.give");
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.CHEST, "Give hub",
                "Target: " + target,
                "Amount chip: " + session.giveAmount()));
        inv.setItem(GIVE_PRESETS, AdminMenuHolder.icon(Material.DIAMOND, "Curated presets",
                "Common admin items"));
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(GIVE_KITS, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                    "starter · adventurer · vip"));
        }
        inv.setItem(GIVE_MATS, AdminMenuHolder.icon(Material.COMPASS, "Material browser",
                "Paginated vanilla items"));
        inv.setItem(GIVE_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(GIVE_TARGET, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Change target",
                "Pick online player"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openGivePresets(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_PRESETS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give presets", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.DIAMOND, "Presets",
                "Click to give · shift = stack×4"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

        int slot = 9;
        for (AdminConfig.ItemPreset preset : plugin.adminConfig().presets()) {
            if (slot >= 44) {
                break;
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            ItemStack icon = AdminMenuHolder.icon(preset.material(), preset.displayName(),
                    "Default ×" + preset.amount(),
                    "Id: " + preset.id());
            icon.setAmount(Math.min(64, Math.max(1, preset.amount())));
            inv.setItem(slot, icon);
            slot++;
        }
        player.openInventory(inv);
    }

    public void openGiveKits(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_KITS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give kits", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                "Dispatches /kit give"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        int slot = 19;
        for (String kit : plugin.adminConfig().kits()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.CHEST, kit,
                    "/kit give <player> " + kit));
        }
        player.openInventory(inv);
    }

    public void openGiveMaterials(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_MATERIALS);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Materials p" + (session.materialPage() + 1), NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(CAT_ALL, AdminMenuHolder.icon(Material.NETHER_STAR, catLabel(session, AdminSession.MaterialCategory.ALL)));
        inv.setItem(CAT_BLOCKS, AdminMenuHolder.icon(Material.BRICKS, catLabel(session, AdminSession.MaterialCategory.BLOCKS)));
        inv.setItem(CAT_TOOLS, AdminMenuHolder.icon(Material.IRON_PICKAXE, catLabel(session, AdminSession.MaterialCategory.TOOLS)));
        inv.setItem(CAT_COMBAT, AdminMenuHolder.icon(Material.IRON_SWORD, catLabel(session, AdminSession.MaterialCategory.COMBAT)));
        inv.setItem(CAT_FOOD, AdminMenuHolder.icon(Material.BREAD, catLabel(session, AdminSession.MaterialCategory.FOOD)));
        inv.setItem(CAT_MISC, AdminMenuHolder.icon(Material.CHEST, catLabel(session, AdminSession.MaterialCategory.MISC)));

        List<Material> mats = filteredMaterials(session.category());
        int page = session.materialPage();
        int maxPage = Math.max(0, (mats.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            session.setMaterialPage(maxPage);
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(mats.size(), start + PAGE_SIZE);
        int slot = 9;
        for (int i = start; i < end; i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            Material mat = mats.get(i);
            inv.setItem(slot, AdminMenuHolder.icon(mat, AdminActions.pretty(mat),
                    "Give ×" + session.giveAmount(),
                    mat.name()));
            slot++;
        }

        inv.setItem(MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        inv.setItem(MAT_BACK, AdminMenuHolder.icon(Material.OAK_DOOR, "Back to Give"));
        inv.setItem(MAT_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        player.openInventory(inv);
    }

    public void openServerOps(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SERVER_OPS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Server ops", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        double[] tps;
        try {
            tps = Bukkit.getTPS();
        } catch (Throwable t) {
            tps = new double[0];
        }
        String tpsLine = tps.length > 0 ? String.format(Locale.ROOT, "%.2f", tps[0]) : "n/a";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server status",
                "Online: " + Bukkit.getOnlinePlayers().size(),
                "Worlds: " + Bukkit.getWorlds().size(),
                "TPS (1m): " + tpsLine));

        List<String> presets = plugin.adminConfig().broadcastPresets();
        int slot = 19;
        for (int i = 0; i < presets.size() && slot < 26; i++) {
            String msg = presets.get(i);
            String shortMsg = msg.length() > 40 ? msg.substring(0, 37) + "…" : msg;
            inv.setItem(slot++, AdminMenuHolder.icon(Material.NOTE_BLOCK, "Broadcast #" + (i + 1),
                    shortMsg));
        }
        inv.setItem(28, AdminMenuHolder.icon(Material.BOOKSHELF, "Open Ranks GUI",
                "YaPPerms reload lives there"));
        if (plugin.actions().pluginEnabled("YaPEssentials") || plugin.actions().pluginEnabled("YaPDisasters")) {
            inv.setItem(30, AdminMenuHolder.icon(Material.WATER_BUCKET, "Weather / Disasters",
                    "Clear · storms · disasters", "/yapdisaster"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openEconomy(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.ECONOMY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Economy", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        AdminSession session = plugin.session(player.getUniqueId());
        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.GOLD_INGOT, "Grant money",
                "Target: " + target,
                "Uses /eco give"));
        int slot = 19;
        for (int amount : plugin.adminConfig().moneyAmounts()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.EMERALD, "+" + amount,
                    "Give " + amount + " to " + target));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openDeepLinks(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.DEEP_LINKS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("More", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.COMPASS, "More",
                "Opens another in-game menu"));
        if (plugin.actions().pluginEnabled("YaPPerms")) {
            inv.setItem(19, AdminMenuHolder.icon(Material.NAME_TAG, "Ranks", "Open YaPPerms"));
        }
        if (plugin.actions().pluginEnabled("YaPWorld")) {
            inv.setItem(20, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit", "Open YaPWorld panel"));
            inv.setItem(23, AdminMenuHolder.icon(Material.MAP, "Schematics", "Browse saved schematics"));
        }
        if (plugin.actions().pluginEnabled("YaPStacker")) {
            inv.setItem(21, AdminMenuHolder.icon(Material.SPAWNER, "Stacker", "Open stacker GUI"));
        }
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.COMPASS, "Player menu", "Open /menu"));
        }
        if (plugin.actions().pluginEnabled("YaPPregen")) {
            inv.setItem(24, AdminMenuHolder.icon(Material.RECOVERY_COMPASS, "Pregen", "Show pregen status"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsHub(Player player) {
        if (!plugin.actions().pluginEnabled("YaPItems")) {
            player.sendMessage("§cYaPItems is not installed.");
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Custom items", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        AdminSession session = plugin.session(player.getUniqueId());
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Custom items",
                session.hasTarget() ? "Player: " + session.targetName() : "Giving to yourself",
                "Amount ×" + session.giveAmount()));
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Browse & give", "Paged list of registered items"));
        inv.setItem(22, AdminMenuHolder.icon(Material.ANVIL, "Create item", "Wizard presets → items/custom/"));
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldowns", "Set ability cooldown per item"));
        inv.setItem(29, AdminMenuHolder.icon(Material.EMERALD, "Reload", "Reload YaPItems registry"));
        inv.setItem(31, AdminMenuHolder.icon(Material.GOLD_INGOT, "Amount ×" + session.giveAmount(), "Cycle give amount"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCooldownBrowse(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_COOLDOWN);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Ability cooldowns", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldowns",
                "Click an item → pick a time",
                "Writes YAML + reloads (takes effect immediately)"));
        List<String> ids = listYapItemIds();
        int page = Math.max(0, session.materialPage());
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
            session.setMaterialPage(page);
        }
        int start = page * PAGE_SIZE;
        int slot = 10;
        for (int i = start; i < Math.min(start + PAGE_SIZE, ids.size()); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            String id = ids.get(i);
            String cd = AdminActions.itemAbilityCooldownLabel(id);
            ItemStack icon = createYapItemIcon(id);
            if (icon == null) {
                icon = AdminMenuHolder.icon(Material.CLOCK, id, "Current CD: " + cd, "Click to change");
            } else {
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(id).color(NamedTextColor.YELLOW)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text("Current CD: " + cd).color(NamedTextColor.AQUA)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                            Component.text("Click to change").color(NamedTextColor.GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                });
            }
            inv.setItem(slot++, icon);
        }
        if (page > 0) {
            inv.setItem(MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous"));
        }
        if (page < maxPage) {
            inv.setItem(MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next"));
        }
        inv.setItem(MAT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCooldownEdit(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (id.isBlank()) {
            openCustomItemsCooldownBrowse(player);
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_COOLDOWN_EDIT);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Cooldown: " + id, NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        String current = AdminActions.itemAbilityCooldownLabel(id);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.CLOCK, id,
                "Current: " + current,
                "Click a preset to apply now"));
        String[] presets = {"0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"};
        int slot = 19;
        for (String preset : presets) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 40) {
                break;
            }
            boolean selected = preset.equalsIgnoreCase(current);
            inv.setItem(slot++, AdminMenuHolder.icon(
                    selected ? Material.LIME_DYE : Material.CLOCK,
                    (selected ? "▶ " : "") + preset,
                    selected ? "Already set to " + preset : "Set " + id + " → " + preset));
        }
        inv.setItem(40, AdminMenuHolder.icon(Material.NAME_TAG, "Custom…",
                "Type a duration in chat",
                "Examples: 4s · 2.5s · 500ms · cancel"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsBrowse(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_BROWSE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Browse custom items", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Browse items",
                "Click an item to manage",
                "Give · Edit · Delete · Cooldown"));
        List<String> ids = listYapItemIds();
        int page = Math.max(0, session.materialPage());
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
            session.setMaterialPage(page);
        }
        int start = page * PAGE_SIZE;
        int slot = 10;
        for (int i = start; i < Math.min(start + PAGE_SIZE, ids.size()); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            String id = ids.get(i);
            ItemStack icon = createYapItemIcon(id);
            if (icon == null) {
                icon = AdminMenuHolder.icon(Material.PAPER, id, "Click to manage");
            } else {
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(id).color(NamedTextColor.AQUA)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text("Click to manage").color(NamedTextColor.GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                            Component.text("Give / Edit / Delete / CD").color(NamedTextColor.DARK_GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                });
            }
            inv.setItem(slot++, icon);
        }
        if (page > 0) {
            inv.setItem(MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous"));
        }
        if (page < maxPage) {
            inv.setItem(MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next"));
        }
        inv.setItem(MAT_AMOUNT, AdminMenuHolder.icon(Material.GOLD_INGOT, "Amount ×" + session.giveAmount()));
        inv.setItem(MAT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsManage(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (id.isBlank()) {
            openCustomItemsBrowse(player);
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_MANAGE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Manage: " + id, NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        ItemStack icon = createYapItemIcon(id);
        if (icon == null) {
            inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, id, "Custom / registered item"));
        } else {
            icon.editMeta(meta -> meta.displayName(Component.text(id).color(NamedTextColor.YELLOW)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            inv.setItem(SLOT_INFO, icon);
        }
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Give ×" + session.giveAmount(),
                session.hasTarget() ? "To " + session.targetName() : "To yourself"));
        inv.setItem(22, AdminMenuHolder.icon(Material.ANVIL, "Edit item",
                "Opens the builder with this item loaded",
                "Only items/custom/ can be overwritten"));
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldown",
                "Current: " + AdminActions.itemAbilityCooldownLabel(id)));
        inv.setItem(31, AdminMenuHolder.icon(Material.BARRIER, "Delete item",
                "Removes items/custom/" + id + ".yml",
                "Builtins cannot be deleted — cannot undo"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCreate(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Create — category", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.ANVIL, "Pick a category",
                "Then choose the exact base item"));
        inv.setItem(19, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Weapons",
                "Sword · axe · spear · mace · bow…"));
        inv.setItem(21, AdminMenuHolder.icon(Material.NETHERITE_PICKAXE, "Tools",
                "Pickaxe · shovel · hoe · shears · rod"));
        inv.setItem(23, AdminMenuHolder.icon(Material.AMETHYST_SHARD, "Gems / charms",
                "Amethyst · emerald · diamond · totem…"));
        inv.setItem(25, AdminMenuHolder.icon(Material.CHEST, "Props (placeable)",
                "Pick the look: oak · stone · lantern…"));
        inv.setItem(31, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Other", "Key"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCreateBase(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String group = session.createTemplateGroup();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_BASE);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Base: " + ItemTemplateCatalog.groupTitle(group), NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, ItemTemplateCatalog.groupTitle(group),
                "Click the exact base item",
                group.equals("prop") ? "These are placeable furniture looks" : "Then configure name / abilities"));
        int slot = 10;
        for (ItemTemplateCatalog.Entry e : ItemTemplateCatalog.byGroup(group)) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot++, AdminMenuHolder.icon(e.icon(), e.label(),
                    e.furniture() ? "Placeable prop" : "Held / use item",
                    "id: " + e.id()));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCreateBuild(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_BUILD);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Build item", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Draft",
                "Name: " + plain(d.displayName()),
                "Id: " + d.id(),
                "Type: " + ItemTemplateCatalog.label(d.template()) + " · Abilities: " + d.abilitiesLabel(),
                "Dmg " + d.damageLabel() + " · Range " + trim(d.range()) + " · CD " + d.cooldown(),
                "Gear ATK " + d.gearAttack() + " · STR " + d.gearStrength(),
                (d.glow() ? "Glow " : "No glow ") + "· " + (d.unbreakable() ? "Unbreakable" : "Breakable")));
        inv.setItem(12, AdminMenuHolder.icon(
                d.glow() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                d.glow() ? "Glow: ON" : "Glow: OFF",
                "Enchantment shine on the item",
                "Click to toggle"));
        inv.setItem(13, AdminMenuHolder.icon(
                d.unbreakable() ? Material.BEDROCK : Material.IRON_INGOT,
                d.unbreakable() ? "Unbreakable: ON" : "Unbreakable: OFF",
                "Item never loses durability",
                "Click to toggle"));
        inv.setItem(19, AdminMenuHolder.icon(Material.NAME_TAG, "Set display name…",
                "Type in chat (supports & color codes)",
                "Current: " + plain(d.displayName())));
        inv.setItem(20, AdminMenuHolder.icon(Material.PAPER, "Set id…",
                "Internal id [a-z0-9_]",
                "Current: " + d.id()));
        inv.setItem(21, AdminMenuHolder.icon(Material.NETHER_STAR, "Abilities: " + d.abilities().size(),
                d.abilitiesLabel(),
                "Click to add/remove abilities"));
        inv.setItem(25, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Keybinds / Together",
                "Per-ability: Together (same key) or separate key",
                "Together = all fire at once on primary key"));
        if (d.usesDamage()) {
            inv.setItem(22, AdminMenuHolder.icon(Material.IRON_SWORD, "Damage: " + d.damageLabel(),
                    "Click to cycle — includes INSTAKILL"));
        }
        if (d.usesRange() && !d.usesBreakVolume()) {
            inv.setItem(23, AdminMenuHolder.icon(Material.ENDER_PEARL, "Range: " + trim(d.range()),
                    "Click to cycle (up to 100 blocks)"));
        }
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Cooldown: " + d.cooldown(),
                "Click to cycle"));
        if (d.usesBreakVolume()) {
            inv.setItem(23, AdminMenuHolder.icon(Material.ENDER_PEARL, "Break reach: " + trim(d.range()),
                    "How far you can look to break"));
            inv.setItem(28, AdminMenuHolder.icon(Material.COBBLESTONE, "Break blast: " + d.breakRadius(),
                    "0 = single block · 1–3 = cube around target"));
            inv.setItem(34, AdminMenuHolder.icon(Material.DIAMOND_PICKAXE, "Max blocks: " + d.breakCount(),
                    "Cap how many blocks one use breaks"));
        }
        if (d.usesRadius() && !d.usesBreakVolume()) {
            inv.setItem(28, AdminMenuHolder.icon(Material.TARGET, "AoE radius: " + trim(d.radius()),
                    "Enemy potion / stomp / pull / push"));
        }
        if (d.usesPotion()) {
            inv.setItem(31, AdminMenuHolder.icon(Material.POTION, "Potion: " + d.potionEffect(),
                    d.abilities().contains("effect") && !d.abilities().contains("area_effect")
                            ? "Self potion only"
                            : d.abilities().contains("area_effect") && !d.abilities().contains("effect")
                            ? "Enemy AoE potion"
                            : "Self and/or enemy AoE"));
        }
        if (d.usesPotionPower()) {
            inv.setItem(37, AdminMenuHolder.icon(Material.CLOCK, "Potion time: " + d.potionDurationSec() + "s",
                    "Duration of potion effects"));
            inv.setItem(38, AdminMenuHolder.icon(Material.GLOWSTONE_DUST, "Potion level: " + (d.potionAmplifier() + 1),
                    "Amplifier 0 = I · 1 = II · 2 = III"));
        }
        if (d.usesProjectile()) {
            inv.setItem(32, AdminMenuHolder.icon(Material.SNOWBALL, "Projectile: " + d.projectileKind(),
                    "snowball / arrow / egg / ender_pearl / fireball"));
        }
        if (d.usesHeal() && !d.usesBreakVolume()) {
            inv.setItem(39, AdminMenuHolder.icon(Material.GOLDEN_APPLE, "Heal HP: " + trim(d.healAmount()),
                    "Hearts restored by Heal self"));
        }
        inv.setItem(29, AdminMenuHolder.icon(Material.DIAMOND_SWORD, "Gear attack: +" + d.gearAttack(),
                "Click to cycle (melee bonus)"));
        inv.setItem(30, AdminMenuHolder.icon(Material.BLAZE_POWDER, "Gear strength: +" + d.gearStrength(),
                "Click to cycle"));
        inv.setItem(33, AdminMenuHolder.icon(Material.LIME_CONCRETE,
                d.replaceExisting() ? "SAVE CHANGES" : "CREATE ITEM",
                d.replaceExisting() ? "Overwrites items/custom/" + d.id() + ".yml" : "Writes the item and gives you one",
                plain(d.displayName()) + " (" + d.id() + ")"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCreateAbility(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_ABILITY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Abilities", NamedTextColor.LIGHT_PURPLE));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        String group = d.itemGroup();
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.NETHER_STAR,
                d.showAllAbilities() ? "All abilities" : ("Suited to " + ItemTemplateCatalog.groupTitle(group)),
                "Selected: " + d.abilitiesLabel(),
                "Click filter button to toggle"));
        inv.setItem(8, AdminMenuHolder.icon(
                d.showAllAbilities() ? Material.ENDER_EYE : Material.SPYGLASS,
                d.showAllAbilities() ? "Show suited only" : "Show all abilities",
                "Currently: " + (d.showAllAbilities() ? "ALL" : ItemTemplateCatalog.groupTitle(group))));
        inv.setItem(10, AdminMenuHolder.icon(Material.BARRIER,
                d.abilities().isEmpty() ? "▶ No ability" : "Clear abilities",
                "Remove all selected abilities"));
        int slot = 11;
        String lastCat = "";
        java.util.List<AbilityCatalog.Info> ordered = new java.util.ArrayList<>();
        for (String cat : AbilityCatalog.categoryOrder()) {
            for (AbilityCatalog.Info info : (d.showAllAbilities()
                    ? AbilityCatalog.all()
                    : AbilityCatalog.forItemGroup(group))) {
                if (info.category().equals(cat)) {
                    ordered.add(info);
                }
            }
        }
        for (AbilityCatalog.Info info : ordered) {
            if (!info.category().equals(lastCat)) {
                lastCat = info.category();
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot++, AdminMenuHolder.icon(Material.GRAY_STAINED_GLASS_PANE,
                        AbilityCatalog.categoryTitle(info.category()), "Category"));
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            boolean sel = d.hasAbility(info.id());
            inv.setItem(slot++, AdminMenuHolder.icon(
                    sel ? Material.LIME_DYE : Material.LIGHT_GRAY_DYE,
                    (sel ? "▶ " : "") + info.label(),
                    info.description(),
                    sel ? "Selected — click to remove" : "Click to add"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCustomItemsCreateTriggers(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_TRIGGERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Ability keybinds", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Keybinds",
                "Click an ability to cycle its key",
                "Together = fires with the primary key at once"));
        List<ItemCreateDraft.AbilitySlot> slots = d.abilitySlots();
        int slot = 10;
        for (int i = 0; i < slots.size(); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemCreateDraft.AbilitySlot ab = slots.get(i);
            inv.setItem(slot++, AdminMenuHolder.icon(Material.NAME_TAG,
                    AbilityCatalog.label(ab.type()) + " → " + ab.triggerLabel(),
                    "Click to cycle keybind",
                    "Together / RMB / Sneak+RMB / LMB / Q / F / Attack"));
        }
        if (slots.isEmpty()) {
            inv.setItem(22, AdminMenuHolder.icon(Material.BARRIER, "No abilities yet",
                    "Add abilities first"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static String plain(String legacyName) {
        if (legacyName == null) {
            return "";
        }
        return legacyName.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    private static String trim(double v) {
        if (Math.rint(v) == v) {
            return Integer.toString((int) v);
        }
        return Double.toString(v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> listYapItemIds() {
        try {
            Class<?> services = Class.forName("com.yapcore.items.api.ItemServices");
            Object opt = services.getMethod("find").invoke(null);
            if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return List.of();
            }
            Object service = opt.getClass().getMethod("get").invoke(opt);
            Object ids = service.getClass().getMethod("ids").invoke(service);
            if (ids instanceof Collection<?> col) {
                List<String> out = new ArrayList<>();
                for (Object o : col) {
                    out.add(String.valueOf(o));
                }
                out.sort(String::compareToIgnoreCase);
                return out;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    private static ItemStack createYapItemIcon(String id) {
        try {
            Class<?> services = Class.forName("com.yapcore.items.api.ItemServices");
            Object opt = services.getMethod("find").invoke(null);
            if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) {
                return null;
            }
            Object service = opt.getClass().getMethod("get").invoke(opt);
            Object created = service.getClass().getMethod("create", String.class, int.class).invoke(service, id, 1);
            if (!(boolean) created.getClass().getMethod("isPresent").invoke(created)) {
                return null;
            }
            return ((ItemStack) created.getClass().getMethod("get").invoke(created)).clone();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public void openCombatSkills(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.COMBAT_SKILLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Skills", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills",
                "Mining · woodcutting · strength"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills menu", "/skills"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static String catLabel(AdminSession session, AdminSession.MaterialCategory cat) {
        String base = cat.name().charAt(0) + cat.name().substring(1).toLowerCase(Locale.ROOT);
        return session.category() == cat ? "▶ " + base : base;
    }

    private static List<Material> filteredMaterials(AdminSession.MaterialCategory category) {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .filter(m -> !m.name().startsWith("LEGACY_"))
                .filter(m -> matchesCategory(m, category))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private static boolean matchesCategory(Material m, AdminSession.MaterialCategory category) {
        return switch (category) {
            case ALL -> true;
            case BLOCKS -> m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m);
            case TOOLS -> AdminActions.isTool(m);
            case COMBAT -> AdminActions.isCombat(m);
            case FOOD -> m.isEdible();
            case MISC -> !m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m) && !m.isEdible();
        };
    }

    private static ItemStack playerHead(Player player, String title) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            meta.displayName(Component.text(title).color(NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        return head;
    }
}
