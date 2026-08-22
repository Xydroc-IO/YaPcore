package com.yapcore.moderation.listener;

import com.yapcore.moderation.DurationParser;
import com.yapcore.moderation.ModerationConfig;
import com.yapcore.moderation.ModerationServiceImpl;
import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.StaffNotify;
import com.yapcore.moderation.alt.AltRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.List;
import java.util.Optional;

public final class LoginListener implements Listener {

    private final ModerationServiceImpl service;
    private final ModerationConfig config;
    private final AltRepository alts;

    public LoginListener(ModerationServiceImpl service, ModerationConfig config, AltRepository alts) {
        this.service = service;
        this.config = config;
        this.alts = alts;
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
        String ip = event.getPlayer().getAddress() != null
                ? event.getPlayer().getAddress().getAddress().getHostAddress() : "";
        YapSched.async(Bukkit.getPluginManager().getPlugin("YaPModeration"), () -> {
            try {
                alts.record(event.getPlayer().getUniqueId(), ip);
                List<AltRepository.AltAccount> linked = alts.findAlts(event.getPlayer().getUniqueId(), ip);
                if (!linked.isEmpty() && config.altNotifyStaff()) {
                    String names = linked.stream()
                            .map(a -> a.name() != null ? a.name() : a.uuid().toString())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    YapSched.global(Bukkit.getPluginManager().getPlugin("YaPModeration"), () ->
                            StaffNotify.broadcast("&e[Mod] Possible alts for &f"
                                    + event.getPlayer().getName() + "&e: &f" + names));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static String color(String raw) {
        return raw.replace('&', '§');
    }
}
