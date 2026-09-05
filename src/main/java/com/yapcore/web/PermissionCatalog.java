package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator-facing permission catalog for the web rank editor.
 * Covers first-party commands plus common vanilla / Paper server nodes.
 */
public final class PermissionCatalog {

    private PermissionCatalog() {
    }

    public static List<Map<String, Object>> categories() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(cat("player", "Player commands", "Everyday player commands (spawn, TPA, homes, kits).",
                n("yapessentials.spawn", "/spawn", "Teleport to spawn"),
                n("yapessentials.back", "/back", "Return to last location"),
                n("yapessentials.tpa", "/tpa /tpahere", "Request player teleports"),
                n("yapessentials.afk", "/afk", "Toggle away-from-keyboard"),
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
        out.add(cat("economy", "Economy & shops", "Money, shops, and auction house.",
                n("yapdata.balance", "/bal", "See your balance"),
                n("yapdata.balance.others", "/bal <player>", "See another player's balance"),
                n("yapdata.pay", "/pay", "Pay another player"),
                n("yapdata.eco", "/eco give", "Give / take / set money"),
                n("yapdata.shop", "/shop", "Chest shops"),
                n("yapdata.ah", "/ah", "Auction house"),
                n("yapdata.jobs", "/jobs", "Jobs GUI"),
                n("yapcraft.sell", "/sell", "Sell items (crafting)")));
        out.add(cat("chat", "Chat", "Talk, private messages, and staff chat.",
                n("yapchat.use", "Public chat", "Speak in public chat"),
                n("yapchat.msg", "/msg /reply", "Private messages"),
                n("yapchat.staff", "/staffchat", "Staff channel"),
                n("yapchat.socialspy", "/socialspy", "See private messages"),
                n("yapchat.admin", "Chat admin", "Clear chat / reload"),
                n("yapchat.bypass.filter", "Bypass filter", "Skip the word filter"),
                n("yapchat.bypass.slow", "Bypass slow mode", "Skip chat cooldown")));
        out.add(cat("kits", "Kits & extras", "Kit access and quality-of-life extras.",
                n("yapdata.kit.adventurer", "Adventurer kit", "Claim the adventurer kit"),
                n("yapdata.kit.vip", "VIP kit", "Claim the VIP kit"),
                n("yapdata.kit.*", "All kits", "Claim every kit"),
                n("yapdata.job.*", "All jobs", "Join any job"),
                n("yapessentials.hat", "/hat", "Wear the held item"),
                n("yapessentials.ptime", "/ptime", "Personal time of day"),
                n("yapessentials.pweather", "/pweather", "Personal weather"),
                n("yapvehicles.drive", "Drive vehicles", "Use vehicles"),
                n("yapvehicles.command", "Vehicle commands", "Spawn / vehicle admin"),
                n("yapvehicles.spawn", "Spawn vehicles", "Create a vehicle")));
        out.add(cat("claims", "Claims & land", "Land claims and claim admin.",
                n("yapdata.claim", "/claim", "Create and manage claims"),
                n("yapdata.claims.wilderness", "Build in wilderness", "When require-claim-to-build is on"),
                n("yapdata.claims.admin", "Bypass claims", "Build anywhere / admin claims"),
                n("yapregions.admin", "/region", "WorldGuard-class regions")));
        out.add(cat("staff-mod", "Staff moderation", "Warn, mute, kick, vanish — not full ban power.",
                n("yapmod.warn", "/warn", "Warn a player"),
                n("yapmod.mute", "/mute /unmute", "Mute players"),
                n("yapmod.kick", "/kick", "Kick players"),
                n("yapmod.history", "/modhistory", "See punishment history"),
                n("yapessentials.vanish", "/vanish", "Invisible staff mode"),
                n("yapessentials.invsee", "/invsee", "View inventories"),
                n("yapessentials.echest", "/echest", "Ender chest"),
                n("yapessentials.staff.socialspy", "Essentials socialspy", "Staff PM spy"),
                n("yapessentials.staff.freeze", "/freeze", "Freeze a player"),
                n("yapessentials.staff.check", "/check", "Staff inspect"),
                n("yapguard.alerts", "AC alerts", "See anti-cheat alerts"),
                n("yapadmin.menu", "/yapadmin", "Staff admin menu")));
        out.add(cat("staff-move", "Staff movement", "Teleport and heal tools.",
                n("yapessentials.teleport", "/tp /tphere", "Force teleport"),
                n("yapessentials.setspawn", "/setspawn", "Set world spawn"),
                n("yapdata.warp.admin", "/setwarp /delwarp", "Create warps"),
                n("yapessentials.gamemode", "/gm /gmc /gms", "Change game mode"),
                n("yapessentials.item", "/i /item", "Give items"),
                n("yapessentials.fly", "/fly", "Flight"),
                n("yapessentials.speed", "/speed", "Walk / fly speed"),
                n("yapessentials.heal", "/heal", "Restore health"),
                n("yapessentials.feed", "/feed", "Fill hunger"),
                n("yapessentials.repair", "/repair", "Repair items"),
                n("yapessentials.clear", "/clear", "Clear inventory"),
                n("yapessentials.broadcast", "/broadcast", "Server broadcast"),
                n("yapessentials.weather", "/weather", "Weather alias → YaPDisasters"),
                n("yapdisasters.use", "/yapdisaster", "Disasters GUI / extremes"),
                n("yapdisasters.admin", "Disasters admin", "Force / cancel disasters, reload")));
        out.add(cat("admin-mod", "Admin / bans", "Bans, OP-class YaP tools. Grant carefully.",
                n("yapmod.ban", "/ban /tempban", "Ban players"),
                n("yapmod.ipban", "/ipban", "IP ban"),
                n("yapmod.admin", "Moderation admin", "Reload moderation"),
                n("yapperm.admin", "Edit ranks", "YaPPerms admin (this editor)"),
                n("yapperm.promote", "/promote", "Promote on the track"),
                n("yapperm.demote", "/demote", "Demote on the track"),
                n("yapdata.admin", "Playerdata admin", "/yapdata reload / override"),
                n("yapdata.kit.give", "Give kits", "/kit give and grant"),
                n("yapdata.kit.create", "Create kits", "/createkit /delkit"),
                n("yapdata.kit.reset", "Reset kits", "/kitreset"),
                n("yapessentials.god", "/god", "Invulnerability"),
                n("yapessentials.nick", "/nick", "Change nickname"),
                n("yapessentials.nick.others", "/nick others", "Change others' nicknames"),
                n("yapessentials.admin", "Essentials admin", "Reload essentials"),
                n("yapguard.bypass", "Bypass anti-cheat", "Skip Guard checks"),
                n("yapguard.admin", "Guard admin", "Reload Guard"),
                n("yap.bypass", "Bypass all rules", "Skip land/chat/AC/MMO enforcement"),
                n("yap.bypass.mmo", "Bypass MMO", "Skip combat/skills/mechanics/ability bar")));
        out.add(cat("world", "World & build", "WorldEdit-class tools, protect, map.",
                n("yapworld.admin", "World admin", "/yapworld status"),
                n("yapworld.load", "/yapworld load", "Load a world"),
                n("yapworld.unload", "/yapworld unload", "Unload a world"),
                n("yapworld.teleport", "/yapworld tp", "Teleport to a world"),
                n("yapworld.selection", "Selection wand", "pos1 / pos2"),
                n("yapworld.schematic", "Schematics", "Save / paste schematics"),
                n("yapworld.brush", "Brushes", "Terraform brushes"),
                n("yapworld.pregen", "Pregen", "Chunk pre-generator"),
                n("yapprotect.lookup", "Protect lookup", "Inspect block history"),
                n("yapprotect.rollback", "Protect rollback", "Undo grief"),
                n("yapprotect.admin", "Protect admin", "Reload / prune"),
                n("yapmap.admin", "Map admin", "Web map render"),
                n("yapnpcs.admin", "/npc", "Create NPCs"),
                n("yapnpcs.quest", "/quests", "Player quests")));
        out.add(cat("vanilla", "Vanilla / server commands",
                "Mojang command nodes. OP bypasses these; ranks do not unless granted.",
                n("minecraft.command.gamemode", "/gamemode", "Change game mode"),
                n("minecraft.command.give", "/give", "Give items"),
                n("minecraft.command.teleport", "/tp (vanilla)", "Vanilla teleport"),
                n("minecraft.command.time", "/time", "Set world time"),
                n("minecraft.command.weather", "/minecraft:weather", "Vanilla weather (YaP uses /weather)"),
                n("minecraft.command.difficulty", "/difficulty", "Set difficulty"),
                n("minecraft.command.gamerule", "/gamerule", "Change gamerules"),
                n("minecraft.command.effect", "/effect", "Potion effects"),
                n("minecraft.command.enchant", "/enchant", "Enchant items"),
                n("minecraft.command.xp", "/xp /experience", "Give experience"),
                n("minecraft.command.clear", "/clear (vanilla)", "Clear inventory"),
                n("minecraft.command.kill", "/kill", "Kill entities"),
                n("minecraft.command.summon", "/summon", "Spawn entities"),
                n("minecraft.command.setblock", "/setblock", "Place a block"),
                n("minecraft.command.fill", "/fill", "Fill a region"),
                n("minecraft.command.clone", "/clone", "Clone a region"),
                n("minecraft.command.say", "/say", "Server say"),
                n("minecraft.command.kick", "/kick (vanilla)", "Vanilla kick"),
                n("minecraft.command.ban", "/ban (vanilla)", "Vanilla ban"),
                n("minecraft.command.pardon", "/pardon", "Unban"),
                n("minecraft.command.whitelist", "/whitelist", "Manage whitelist"),
                n("minecraft.command.op", "/op", "Grant operator", true),
                n("minecraft.command.deop", "/deop", "Remove operator", true),
                n("minecraft.command.stop", "/stop", "Stop the server", true),
                n("minecraft.command.*", "All vanilla commands", "Wildcard — admin only", true)));
        out.add(cat("paper", "Paper / Bukkit", "Server internals. Keep off default and VIP.",
                n("bukkit.command.plugins", "/plugins", "List plugins"),
                n("bukkit.command.version", "/version", "Server version"),
                n("bukkit.command.reload", "/reload", "Reload plugins", true),
                n("bukkit.command.timings", "/timings", "Timings report"),
                n("paper.command.paper", "/paper", "Paper admin command", true)));
        out.add(cat("mmo", "MMO & abilities", "Skills, combat, crafting, guilds, games, mechanics, abilities.",
                n("yapskills.use", "/skills", "Open skills"),
                n("yapskills.others", "Skills others", "View another player's skills"),
                n("yapskills.admin", "Skills admin", "Reload / grant skills"),
                n("yapcombat.use", "/combat", "Combat menu"),
                n("yapcombat.cast", "/cast /spells", "Cast combat spells"),
                n("yapcombat.prayer", "/prayer", "Prayer system"),
                n("yapcombat.admin", "Combat admin", "Reload combat"),
                n("yapcraft.use", "/recipe /ycraft", "Custom recipes"),
                n("yapcraft.admin", "Crafting admin", "Reload crafting"),
                n("yapguilds.use", "/g /guild", "Guild membership"),
                n("yapguilds.create", "Create guild", "Found a guild"),
                n("yapguilds.admin", "Guilds admin", "Reload / override guilds"),
                n("yapgames.use", "/queue /duel /game", "Join minigames"),
                n("yapgames.admin", "Games admin", "Arena / queue admin"),
                n("yapmechanics.admin", "Mechanics admin", "Reload stamina / mechanics"),
                n("yapmechanics.stamina.others", "Stamina others", "View others' stamina"),
                n("yapabilities.use", "/ability /spell", "Use abilities"),
                n("yapabilities.bar", "Ability bar", "Hotbar ability slots"),
                n("yapabilities.bypass.lock", "Bypass ability lock", "Cast locked abilities"),
                n("yapabilities.admin", "Abilities admin", "Reload abilities"),
                n("yapmmo.hiscores", "/hiscores", "MMO hiscores"),
                n("yapmmo.admin", "MMO admin", "Reload / give MMO content"),
                n("yapmmo.bedrock.use", "/mmoui", "Bedrock MMO UI")));
        out.add(cat("extras", "Tools & extras", "Stacker, packs, placeholders, and other first-party tools.",
                n("yapvehicles.destroy", "Destroy vehicles", "Remove spawned vehicles"),
                n("yapstacker.gui", "Stacker GUI", "Open mob stacker"),
                n("yapstacker.give", "Stacker give", "Give stacker items"),
                n("yapstacker.wand", "Stacker wand", "Stacker selection wand"),
                n("yapstacker.admin", "Stacker admin", "Reload stacker"),
                n("yapnpcs.admin", "NPC admin", "Hub NPCs, shops, warps, dialogue"),
                n("yappregen.admin", "Pregen admin", "Chunk pre-generator"),
                n("yapknobs.reload", "Knobs reload", "Reload performance knobs"),
                n("yapdb.admin", "Database admin", "YaPDB admin"),
                n("yappacks.admin", "Packs admin", "Resource pack admin"),
                n("yapcompat.status", "Compat status", "Plugin compatibility status"),
                n("placeholderapi.parse", "Parse placeholders", "Use PlaceholderAPI"),
                n("placeholderapi.admin", "Placeholder admin", "Reload PlaceholderAPI")));
        return out;
    }

    @SafeVarargs
    private static Map<String, Object> cat(String id, String title, String hint, Map<String, Object>... nodes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("hint", hint);
        row.put("nodes", List.of(nodes));
        return row;
    }

    private static Map<String, Object> n(String node, String label, String desc) {
        return n(node, label, desc, false);
    }

    private static Map<String, Object> n(String node, String label, String desc, boolean danger) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("node", node);
        row.put("label", label);
        row.put("desc", desc);
        if (danger) {
            row.put("danger", true);
        }
        return row;
    }

    /** Named packs used when creating a rank that already has permissions. */
    public static Map<String, List<String>> templates() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("blank", List.of());
        out.put("player", nodesInCategories("player", "economy", "chat", "kits", "claims"));
        out.put("staff", nodesInCategories("player", "economy", "chat", "kits", "claims",
                "staff-mod", "staff-move"));
        out.put("admin", nodesInCategories("player", "economy", "chat", "kits", "claims",
                "staff-mod", "staff-move", "admin-mod", "world", "extras"));
        return out;
    }

    public static List<Map<String, Object>> templateSummaries() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, String> labels = Map.of(
                "blank", "Empty — set permissions after create",
                "player", "Player — spawn, TPA, kits, chat, claims",
                "staff", "Staff — player plus mute/kick/vanish/tp",
                "admin", "Admin — staff plus bans, ranks, world tools");
        for (var e : templates().entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getKey());
            row.put("label", labels.getOrDefault(e.getKey(), e.getKey()));
            row.put("count", e.getValue().size());
            out.add(row);
        }
        return out;
    }

    public static List<String> nodesInCategories(String... ids) {
        java.util.Set<String> want = new java.util.LinkedHashSet<>(List.of(ids));
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Map<String, Object> cat : categories()) {
            if (!want.contains(String.valueOf(cat.get("id")))) {
                continue;
            }
            Object nodes = cat.get("nodes");
            if (!(nodes instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("node") != null) {
                    String node = String.valueOf(map.get("node"));
                    if (seen.add(node)) {
                        out.add(node);
                    }
                }
            }
        }
        return out;
    }

    /** Every catalog node id, in display order. */
    public static List<String> allNodes() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> cat : categories()) {
            Object nodes = cat.get("nodes");
            if (!(nodes instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("node") != null) {
                    out.add(String.valueOf(map.get("node")));
                }
            }
        }
        return out;
    }
}
