package com.yapcore.tab;

import com.yapcore.perms.YaPPerms;
import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.sched.YapSched;
import com.yapcore.tab.util.LegacyColors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tab list + sidebar using Adventure header/footer and Folia-safe Bukkit scoreboards.
 * Packet scoreboard libraries are avoided — NMS constructors break on YaP-Folia 26.2.
 */
public final class TabServiceImpl implements com.yapcore.tab.TabService {

    private static final String SIDEBAR_OBJECTIVE = "yaptab";

    private final JavaPlugin plugin;
    private final TabConfig config;
    private final TabNetworkState networkState;
    private final java.util.concurrent.atomic.AtomicBoolean sidebarUnsupportedLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private List<String> runtimeHeader;
    private List<String> runtimeFooter;
    private List<String> runtimeSidebar;

    public TabServiceImpl(JavaPlugin plugin, TabConfig config, TabNetworkState networkState) {
        this.plugin = plugin;
        this.config = config;
        this.networkState = networkState;
    }

    @Override
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        YapSched.entity(plugin, player, () -> refreshOnEntity(player));
    }

    @Override
    public void refreshAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            refresh(online);
        }
    }

    private void refreshOnEntity(Player player) {
        if (!player.isOnline()) {
            return;
        }
        try {
            applyHeaderFooter(player);
            applyTabSort(player);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "YaPTab header/list failed for " + player.getName() + ": " + t.getMessage(), t);
        }
        if (effectiveSidebarEnabled()) {
            try {
                applySidebar(player);
            } catch (UnsupportedOperationException e) {
                // Folia disables Bukkit scoreboards unless -Dyap.folia.scoreboard-swmr=true
                if (sidebarUnsupportedLogged.compareAndSet(false, true)) {
                    plugin.getLogger().warning(
                            "Sidebar unavailable: enable folia-scoreboard-swmr=true in config/server.properties "
                                    + "and restart (YaP-Folia). Rank/balance still show in the tab footer.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "YaPTab sidebar failed for " + player.getName() + ": " + t.getMessage(), t);
            }
        }
        if (effectiveNametagTeams()) {
            try {
                applyNametag(player);
            } catch (Throwable ignored) {
                // nametags are best-effort on Folia
            }
        }
    }

    @Override
    public void setHeaderFooter(List<String> header, List<String> footer) {
        runtimeHeader = header == null ? null : List.copyOf(header);
        runtimeFooter = footer == null ? null : List.copyOf(footer);
    }

    @Override
    public void setSidebarLines(List<String> lines) {
        runtimeSidebar = lines == null ? null : List.copyOf(lines);
    }

    @Override
    public void clearOverrides() {
        runtimeHeader = null;
        runtimeFooter = null;
        runtimeSidebar = null;
        networkState.clear();
    }

    private List<String> effectiveHeader() {
        if (runtimeHeader != null) {
            return runtimeHeader;
        }
        if (networkState.active()) {
            return networkState.header();
        }
        return config.header();
    }

    private List<String> effectiveFooter() {
        if (runtimeFooter != null) {
            return runtimeFooter;
        }
        if (networkState.active()) {
            return networkState.footer();
        }
        return config.footer();
    }

    private List<String> effectiveSidebar() {
        if (runtimeSidebar != null) {
            return runtimeSidebar;
        }
        if (networkState.active()) {
            return networkState.sidebar();
        }
        return config.sidebar();
    }

    private boolean effectiveSidebarEnabled() {
        if (networkState.active()) {
            return networkState.sidebarEnabled();
        }
        return config.sidebarEnabled();
    }

    private boolean effectiveNametagTeams() {
        if (networkState.active()) {
            return networkState.nametagTeams();
        }
        return config.nametagTeams();
    }

    private void applyHeaderFooter(Player player) {
        Component header = joinLines(effectiveHeader(), player);
        Component footer = joinLines(effectiveFooter(), player);
        player.sendPlayerListHeader(header);
        player.sendPlayerListFooter(footer);
    }

    private void applyTabSort(Player viewer) {
        YaPPerms perms = Bukkit.getServicesManager().load(YaPPerms.class);
        for (Player target : Bukkit.getOnlinePlayers()) {
            Component name = buildListName(target, perms);
            target.playerListName(name);
        }
    }

    private Component buildListName(Player target, YaPPerms perms) {
        String prefix = "";
        String suffix = "";
        if (perms != null) {
            prefix = perms.getPrefix(target.getUniqueId()).orElse("");
            suffix = perms.getSuffix(target.getUniqueId()).orElse("");
        }
        if (isVanished(target)) {
            suffix = suffix + " &8[V]";
        }
        if (isAfk(target)) {
            suffix = suffix + " &7[AFK]";
        }
        String raw = prefix + target.getName() + suffix;
        return LegacyColors.component(applyPlaceholders(target, raw));
    }

    private void applySidebar(Player player) {
        ScoreboardManagerSafe boardMgr = ScoreboardManagerSafe.get();
        if (boardMgr == null) {
            return;
        }
        Scoreboard board = boardMgr.newScoreboard();
        Objective obj = board.registerNewObjective(
                SIDEBAR_OBJECTIVE, Criteria.DUMMY,
                LegacyColors.component("&6&lYaP"), RenderType.INTEGER);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>(effectiveSidebar());
        int score = Math.min(15, lines.size());
        int index = 0;
        for (String line : lines) {
            if (index >= 15) {
                break;
            }
            if (line == null || line.isBlank()) {
                score--;
                continue;
            }
            String entry = uniqueEntry(score);
            Team team = board.registerNewTeam("line" + score);
            team.addEntry(entry);
            team.prefix(LegacyColors.component(applyPlaceholders(player, trim(line, 64))));
            obj.getScore(entry).setScore(score);
            score--;
            index++;
        }
        player.setScoreboard(board);
    }

    private void applyNametag(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            return;
        }
        YaPPerms perms = Bukkit.getServicesManager().load(YaPPerms.class);
        for (Player target : Bukkit.getOnlinePlayers()) {
            String id = teamId(target.getUniqueId());
            Team team = board.getTeam(id);
            if (team == null) {
                team = board.registerNewTeam(id);
            }
            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }
            String prefix = perms != null ? perms.getPrefix(target.getUniqueId()).orElse("") : "";
            String suffix = perms != null ? perms.getSuffix(target.getUniqueId()).orElse("") : "";
            team.prefix(LegacyColors.component(applyPlaceholders(player, prefix)));
            team.suffix(LegacyColors.component(applyPlaceholders(player, suffix)));
        }
    }

    private Component joinLines(List<String> lines, Player player) {
        Component out = Component.empty();
        boolean first = true;
        for (String line : lines) {
            if (!first) {
                out = out.append(Component.newline());
            }
            first = false;
            out = out.append(LegacyColors.component(applyPlaceholders(player, line)));
        }
        return out;
    }

    private String applyPlaceholders(Player player, String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw
                .replace("{player}", player.getName())
                .replace("{displayname}", player.getDisplayName())
                .replace("{world}", player.getWorld().getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        YaPPerms perms = Bukkit.getServicesManager().load(YaPPerms.class);
        if (perms != null) {
            out = out.replace("{prefix}", LegacyColors.plain(perms.getPrefix(player.getUniqueId()).orElse("")))
                    .replace("{suffix}", LegacyColors.plain(perms.getSuffix(player.getUniqueId()).orElse("")))
                    .replace("{rank}", perms.displayGroup(player.getUniqueId()));
        }
        out = out.replace("{balance}", formatBalance(player));
        out = applyPapi(player, out);
        return out;
    }

    private static String formatBalance(Player player) {
        try {
            PlayerDataService data = Bukkit.getServicesManager().load(PlayerDataService.class);
            if (data != null && data.economyEnabled()) {
                return String.format("%.2f", data.balance(player.getUniqueId()));
            }
        } catch (Exception ignored) {
        }
        return "—";
    }

    private static String applyPapi(Player player, String text) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            var method = papi.getMethod("setPlaceholders", Player.class, String.class);
            return (String) method.invoke(null, player, text);
        } catch (Exception ignored) {
            return text;
        }
    }

    private static boolean isVanished(Player player) {
        return player.isInvisible() && player.hasPermission("yapessentials.vanish");
    }

    private static boolean isAfk(Player player) {
        return player.hasMetadata("yap_afk") || LegacyColors.plain(player.getDisplayName()).contains("[AFK]");
    }

    private static String teamId(UUID uuid) {
        return "yt_" + uuid.toString().substring(0, 8);
    }

    private static String uniqueEntry(int score) {
        return "§" + Integer.toHexString(Math.max(0, Math.min(15, score % 16))) + "§r";
    }

    private static String trim(String line, int max) {
        String plain = LegacyColors.plain(line);
        if (plain.length() <= max) {
            return line;
        }
        return plain.substring(0, max);
    }

    /** Tiny wrapper so missing ScoreboardManager never NPEs. */
    private static final class ScoreboardManagerSafe {
        private final org.bukkit.scoreboard.ScoreboardManager mgr;

        private ScoreboardManagerSafe(org.bukkit.scoreboard.ScoreboardManager mgr) {
            this.mgr = mgr;
        }

        static ScoreboardManagerSafe get() {
            try {
                var mgr = Bukkit.getScoreboardManager();
                return mgr == null ? null : new ScoreboardManagerSafe(mgr);
            } catch (Throwable t) {
                return null;
            }
        }

        Scoreboard newScoreboard() {
            return mgr.getNewScoreboard();
        }
    }
}
