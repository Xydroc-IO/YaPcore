package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Hub, players, player actions, trolls, and self tools menus. */
final class AdminMenusCore {

    private final AdminPlugin plugin;

    AdminMenusCore(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openHub(Player player) {
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
    void openHubInventory(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP Admin", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        AdminSession session = plugin.session(player.getUniqueId());
        String targetLore = session.hasTarget()
                ? "Player: " + session.targetName()
                : "No player selected";

        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.NETHER_STAR, NamedTextColor.AQUA, "YaP Admin",
                "Network admin tools",
                targetLore,
                "Online: " + Bukkit.getOnlinePlayers().size()));

        inv.setItem(AdminMenuSlots.HUB_PLAYERS, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Players",
                "Online list — manage one player"));
        inv.setItem(AdminMenuSlots.HUB_SELF, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
                "God, fly, vanish, gamemode, speed"));
        if (player.hasPermission("yapadmin.give")) {
            inv.setItem(AdminMenuSlots.HUB_GIVE, AdminMenuHolder.icon(Material.CHEST, "Give",
                    "Items, kits, and materials"));
        }
        if (plugin.actions().pluginEnabled("YaPItems") && player.hasPermission("yapadmin.give")) {
            inv.setItem(AdminMenuSlots.HUB_ITEMS, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Custom items",
                    "Browse, give, and create YaPItems"));
        }
        inv.setItem(AdminMenuSlots.HUB_MOD, AdminMenuHolder.icon(Material.IRON_SWORD, NamedTextColor.RED, "Moderation",
                "Kick, warn, mute, tempban"));
        if (player.hasPermission("yapadmin.troll")) {
            inv.setItem(AdminMenuSlots.HUB_TROLLS, AdminMenuHolder.icon(Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Trolls",
                    "Smite, launch, burn, and more"));
        }
        if (player.hasPermission("yapadmin.server")) {
            inv.setItem(AdminMenuSlots.HUB_SERVER, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server",
                    "Broadcast, weather, status"));
        }
        if (player.hasPermission("yapadmin.economy") && plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(AdminMenuSlots.HUB_ECONOMY, AdminMenuHolder.icon(Material.GOLD_INGOT, "Economy",
                    "Grant money to a player"));
        }
        inv.setItem(AdminMenuSlots.HUB_LINKS, AdminMenuHolder.icon(Material.COMPASS, "More…",
                "Ranks, world edit, stacker, menu"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(AdminMenuSlots.HUB_COMBAT, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills",
                    "Open the skills menu"));
        }
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openPlayers(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.PLAYERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Online players", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Online players",
                "Click a head to manage"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

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

    void openPlayerActions(Player player, Player target) {
        plugin.session(player.getUniqueId()).setTarget(target.getUniqueId(), target.getName());
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuKind.PLAYER_ACTIONS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Player: " + target.getName(), NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuItemSupport.playerHead(target, "Managing " + target.getName()));
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

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openTrolls(Player player, Player target) {
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
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuItemSupport.playerHead(target, "Trolling " + target.getName()));
        inv.setItem(19, AdminMenuHolder.icon(Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Smite", "Lightning"));
        inv.setItem(20, AdminMenuHolder.icon(Material.FIREWORK_ROCKET, "Launch", "Yeet up"));
        inv.setItem(21, AdminMenuHolder.icon(Material.FLINT_AND_STEEL, "Burn", "Fire 8s"));
        inv.setItem(22, AdminMenuHolder.icon(Material.TNT, "Rocket", "Launch + bolt FX"));
        inv.setItem(23, AdminMenuHolder.icon(Material.ANVIL, "Squash", "Drop from height"));
        inv.setItem(24, AdminMenuHolder.icon(Material.ENDER_EYE, "Blind", "12s"));
        inv.setItem(25, AdminMenuHolder.icon(Material.POISONOUS_POTATO, "Confuse", "Nausea"));
        inv.setItem(28, AdminMenuHolder.icon(Material.SLIME_BALL, "Slap", "Knockback"));
        inv.setItem(29, AdminMenuHolder.icon(Material.DROPPER, "Drop hand", "Drop held item"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openSelfTools(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.SELF_TOOLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Self tools", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
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
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
