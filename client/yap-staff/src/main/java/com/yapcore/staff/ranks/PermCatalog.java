package com.yapcore.staff.ranks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operator permission catalog for the staff ranks editor.
 * Mirrors the web dashboard catalog so JE staff can grant the same nodes in-game.
 */
public final class PermCatalog {

    public record Node(String node, String label, String desc, boolean danger) {
        public Node(String node, String label, String desc) {
            this(node, label, desc, false);
        }
    }

    public record Category(String id, String title, String hint, List<Node> nodes) {
    }

    private static final List<Category> CATEGORIES = build();

    private PermCatalog() {
    }

    public static List<Category> categories() {
        return CATEGORIES;
    }

    public static List<Node> allNodes() {
        List<Node> out = new ArrayList<>();
        for (Category cat : CATEGORIES) {
            out.addAll(cat.nodes());
        }
        return out;
    }

    public static List<Node> search(String filter, String categoryId) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        List<Node> out = new ArrayList<>();
        for (Category cat : CATEGORIES) {
            if (categoryId != null && !categoryId.isBlank() && !"all".equals(categoryId)
                    && !cat.id().equals(categoryId)) {
                continue;
            }
            for (Node node : cat.nodes()) {
                if (needle.isEmpty()
                        || node.node().toLowerCase(Locale.ROOT).contains(needle)
                        || node.label().toLowerCase(Locale.ROOT).contains(needle)
                        || node.desc().toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(node);
                }
            }
        }
        return out;
    }

    private static List<Category> build() {
        List<Category> out = new ArrayList<>();
        out.add(cat("player", "Player", "Everyday player commands",
                n("yapessentials.spawn", "/spawn", "Teleport to spawn"),
                n("yapessentials.back", "/back", "Return to last location"),
                n("yapessentials.tpa", "/tpa /tpahere", "Request player teleports"),
                n("yapessentials.afk", "/afk", "Toggle AFK"),
                n("yapessentials.list", "/list", "See who is online"),
                n("yapessentials.rules", "/rules", "Read server rules"),
                n("yapessentials.motd", "/motd", "Message of the day"),
                n("yapessentials.suicide", "/suicide", "Kill yourself"),
                n("yapdata.menu", "/menu", "Open the player hub"),
                n("yapdata.home", "/home /sethome", "Personal homes"),
                n("yapdata.warp", "/warp", "Use warps"),
                n("yapdata.kit", "/kit /kits", "Open and claim kits"),
                n("yapdata.kit.starter", "Starter kit", "Claim the starter kit"),
                n("yapdata.mail", "/mail", "Player mail")));
        out.add(cat("economy", "Economy", "Money, shops, AH",
                n("yapdata.balance", "/bal", "See your balance"),
                n("yapdata.balance.others", "/bal <player>", "See another player's balance"),
                n("yapdata.pay", "/pay", "Pay another player"),
                n("yapdata.eco", "/eco", "Give / take / set money"),
                n("yapdata.shop", "/shop", "Chest shops"),
                n("yapdata.ah", "/ah", "Auction house"),
                n("yapdata.jobs", "/jobs", "Jobs GUI")));
        out.add(cat("chat", "Chat", "Talk and staff chat",
                n("yapchat.use", "Public chat", "Speak in public chat"),
                n("yapchat.msg", "/msg /reply", "Private messages"),
                n("yapchat.staff", "/staffchat", "Staff channel"),
                n("yapchat.socialspy", "Social spy", "See private messages"),
                n("yapchat.admin", "Chat admin", "Clear chat / reload"),
                n("yapchat.bypass.filter", "Bypass filter", "Skip the word filter"),
                n("yapchat.bypass.slow", "Bypass slow", "Skip chat cooldown")));
        out.add(cat("kits", "Kits & extras", "Kits and QoL",
                n("yapdata.kit.adventurer", "Adventurer kit", "Claim adventurer"),
                n("yapdata.kit.vip", "VIP kit", "Claim VIP"),
                n("yapdata.kit.*", "All kits", "Claim every kit"),
                n("yapessentials.hat", "/hat", "Wear held item"),
                n("yapessentials.ptime", "/ptime", "Personal time"),
                n("yapessentials.pweather", "/pweather", "Personal weather")));
        out.add(cat("claims", "Claims", "Land claims",
                n("yapdata.claim", "/claim", "Create and manage claims"),
                n("yapdata.claims.wilderness", "Wilderness build", "When claim-to-build is on"),
                n("yapdata.claims.admin", "Bypass claims", "Build anywhere"),
                n("yapregions.admin", "/region", "WorldGuard-class regions")));
        out.add(cat("staff-mod", "Staff mod", "Warn / mute / kick",
                n("yapmod.warn", "/warn", "Warn a player"),
                n("yapmod.mute", "/mute /unmute", "Mute players"),
                n("yapmod.kick", "/kick", "Kick players"),
                n("yapmod.history", "/modhistory", "Punishment history"),
                n("yapessentials.vanish", "/vanish", "Invisible staff"),
                n("yapessentials.invsee", "/invsee", "View inventories"),
                n("yapessentials.echest", "/echest", "Ender chest"),
                n("yapessentials.staff.freeze", "/freeze", "Freeze a player"),
                n("yapessentials.staff.check", "/check", "Staff inspect"),
                n("yapguard.alerts", "AC alerts", "See anti-cheat alerts"),
                n("yapadmin.menu", "/yapadmin", "Staff admin menu"),
                n("yapadmin.troll", "Trolls", "Staff troll actions"),
                n("yapadmin.give", "Give items", "yapadmin give"),
                n("yapadmin.spawnmob", "Spawn mobs", "yapadmin spawnmob")));
        out.add(cat("staff-move", "Staff tools", "TP / heal / fly",
                n("yapessentials.teleport", "/tp /tphere", "Force teleport"),
                n("yapessentials.setspawn", "/setspawn", "Set world spawn"),
                n("yapdata.warp.admin", "/setwarp", "Create warps"),
                n("yapessentials.gamemode", "/gm", "Change game mode"),
                n("yapessentials.item", "/i /item", "Give items"),
                n("yapessentials.fly", "/fly", "Flight"),
                n("yapessentials.speed", "/speed", "Walk / fly speed"),
                n("yapessentials.heal", "/heal", "Restore health"),
                n("yapessentials.feed", "/feed", "Fill hunger"),
                n("yapessentials.repair", "/repair", "Repair items"),
                n("yapessentials.clear", "/clear", "Clear inventory"),
                n("yapessentials.broadcast", "/broadcast", "Server broadcast"),
                n("yapdisasters.use", "/yapdisaster", "Disasters GUI"),
                n("yapdisasters.admin", "Disasters admin", "Force / cancel")));
        out.add(cat("admin-mod", "Admin", "Bans & ranks — grant carefully",
                n("yapmod.ban", "/ban /tempban", "Ban players"),
                n("yapmod.ipban", "/ipban", "IP ban"),
                n("yapmod.admin", "Moderation admin", "Reload moderation"),
                n("yapperm.admin", "Edit ranks", "YaPPerms admin"),
                n("yapperm.promote", "/promote", "Promote on track"),
                n("yapperm.demote", "/demote", "Demote on track"),
                n("yapdata.admin", "Playerdata admin", "Override / reload"),
                n("yapdata.kit.give", "Give kits", "/kit give"),
                n("yapdata.kit.create", "Create kits", "/createkit"),
                n("yapessentials.god", "/god", "Invulnerability"),
                n("yapessentials.nick", "/nick", "Nickname"),
                n("yapessentials.admin", "Essentials admin", "Reload essentials"),
                n("yapguard.bypass", "Bypass AC", "Skip Guard checks"),
                n("yapguard.admin", "Guard admin", "Reload Guard"),
                n("yap.bypass", "Bypass all", "Skip land/chat/AC/skills", true),
                n("*", "Wildcard *", "Everything — owner only", true)));
        out.add(cat("world", "World", "WorldEdit-class tools",
                n("yapworld.admin", "World admin", "/yapworld status"),
                n("yapworld.load", "/yapworld load", "Load a world"),
                n("yapworld.unload", "/yapworld unload", "Unload a world"),
                n("yapworld.teleport", "/yapworld tp", "Teleport to a world"),
                n("yapworld.selection", "Selection wand", "pos1 / pos2"),
                n("yapworld.schematic", "Schematics", "Save / paste"),
                n("yapworld.brush", "Brushes", "Terraform brushes"),
                n("yapworld.pregen", "Pregen", "Chunk pre-generator"),
                n("yapprotect.lookup", "Protect lookup", "Inspect history"),
                n("yapprotect.rollback", "Protect rollback", "Undo grief"),
                n("yapprotect.admin", "Protect admin", "Reload / prune"),
                n("yapmap.admin", "Map admin", "Web map"),
                n("yapnpcs.admin", "/npc", "Hub NPCs"),
                n("yapnpcs.quest", "/quests", "Player quests")));
        out.add(cat("vanilla", "Vanilla", "Mojang command nodes",
                n("minecraft.command.gamemode", "/gamemode", "Change game mode"),
                n("minecraft.command.give", "/give", "Give items"),
                n("minecraft.command.teleport", "/tp (vanilla)", "Vanilla teleport"),
                n("minecraft.command.time", "/time", "Set world time"),
                n("minecraft.command.weather", "/minecraft:weather", "Vanilla weather"),
                n("minecraft.command.effect", "/effect", "Potion effects"),
                n("minecraft.command.enchant", "/enchant", "Enchant items"),
                n("minecraft.command.xp", "/xp", "Give experience"),
                n("minecraft.command.kick", "/kick (vanilla)", "Vanilla kick"),
                n("minecraft.command.ban", "/ban (vanilla)", "Vanilla ban"),
                n("minecraft.command.op", "/op", "Grant operator", true),
                n("minecraft.command.deop", "/deop", "Remove operator", true),
                n("minecraft.command.stop", "/stop", "Stop the server", true),
                n("minecraft.command.*", "All vanilla", "Wildcard — admin only", true)));
        out.add(cat("paper", "Paper", "Server internals",
                n("bukkit.command.plugins", "/plugins", "List plugins"),
                n("bukkit.command.version", "/version", "Server version"),
                n("bukkit.command.reload", "/reload", "Reload plugins", true),
                n("paper.command.paper", "/paper", "Paper admin", true)));
        out.add(cat("skills", "Skills", "Thin skills",
                n("yapskills.use", "/skills", "Open skills"),
                n("yapskills.others", "Skills others", "View others"),
                n("yapskills.admin", "Skills admin", "Reload / grant"),
                n("yapskills.bypass", "Bypass XP", "Skip skill XP")));
        out.add(cat("extras", "Extras", "Stacker, packs, factions",
                n("yapstacker.gui", "Stacker GUI", "Open mob stacker"),
                n("yapstacker.admin", "Stacker admin", "Reload stacker"),
                n("yappregen.admin", "Pregen admin", "Chunk pregen"),
                n("yapknobs.reload", "Knobs reload", "Performance knobs"),
                n("yapdb.admin", "Database admin", "YaPDB admin"),
                n("yappacks.admin", "Packs admin", "Resource packs"),
                n("yapfactions.use", "/f", "Factions"),
                n("yapfactions.admin", "Factions admin", "Override factions")));
        return List.copyOf(out);
    }

    private static Category cat(String id, String title, String hint, Node... nodes) {
        return new Category(id, title, hint, List.of(nodes));
    }

    private static Node n(String node, String label, String desc) {
        return new Node(node, label, desc, false);
    }

    private static Node n(String node, String label, String desc, boolean danger) {
        return new Node(node, label, desc, danger);
    }
}
