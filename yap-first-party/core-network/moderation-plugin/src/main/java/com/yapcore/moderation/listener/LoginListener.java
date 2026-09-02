package com.yapcore.moderation.listener;

import com.yapcore.moderation.DurationParser;
import com.yapcore.moderation.ModerationConfig;
import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.StaffNotify;
import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.moderation.seen.SeenPlayerRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

public final class LoginListener implements Listener {

    private final JavaPlugin plugin;
    private final ModerationServiceImpl service;
    private final ModerationConfig config;
    private final AltRepository alts;
    private final SeenPlayerRepository seen;

    public LoginListener(JavaPlugin plugin, ModerationServiceImpl service, ModerationConfig config,
                         AltRepository alts, SeenPlayerRepository seen) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        this.alts = alts;
        this.seen = seen;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<Punishment> ipBan = service.activeIpBan(event.getAddress().getHostAddress());
        if (ipBan.isPresent()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    color(config.ipBanLoginMessage()
                            .replace("{reason}", ipBan.get().reason())));
            return;
        }
        Optional<Punishment> ban = service.activeBan(event.getUniqueId());
        if (ban.isPresent()) {
            Punishment p = ban.get();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    color(config.banLoginMessage()
                            .replace("{reason}", p.reason())
                            .replace("{expires}", DurationParser.formatExpiry(p.expiresAtEpochMs()))
                            .replace("{actor}", p.actorName())));
        }
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "";
        try {
            seen.record(event.getUniqueId(), event.getName(), "", ip);
            alts.record(event.getUniqueId(), ip);
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        Optional<Punishment> ban = service.activeBan(event.getPlayer().getUniqueId());
        if (ban.isPresent()) {
            Punishment p = ban.get();
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    color(config.banLoginMessage()
                            .replace("{reason}", p.reason())
                            .replace("{expires}", DurationParser.formatExpiry(p.expiresAtEpochMs()))
                            .replace("{actor}", p.actorName())));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "";
        String nick = SeenPlayerRepository.nicknameOrEmpty(player.getName(), player.getDisplayName());
        YapSched.async(plugin, () -> {
            try {
                seen.record(player.getUniqueId(), player.getName(), nick, ip);
                alts.record(player.getUniqueId(), ip);
                seen.writeSnapshot(plugin.getDataFolder().toPath().resolve("seen-players.json"));
                List<AltRepository.AltAccount> linked = alts.findAlts(player.getUniqueId(), ip);
                if (!linked.isEmpty() && config.altNotifyStaff()) {
                    String names = linked.stream()
                            .map(a -> a.name() != null ? a.name() : a.uuid().toString())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    YapSched.global(plugin, () ->
                            StaffNotify.broadcast("&e[Mod] Possible alts for &f"
                                    + player.getName() + "&e: &f" + names));
                }
            } catch (Exception ignored) {
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "";
        String nick = SeenPlayerRepository.nicknameOrEmpty(player.getName(), player.getDisplayName());
        YapSched.async(plugin, () -> {
            try {
                seen.record(player.getUniqueId(), player.getName(), nick, ip);
                alts.record(player.getUniqueId(), ip);
                seen.writeSnapshot(plugin.getDataFolder().toPath().resolve("seen-players.json"));
            } catch (Exception ignored) {
            }
        });
    }

    private static String color(String raw) {
        return raw.replace('&', '§');
    }
}
