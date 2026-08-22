package com.yapcore.essentials.store;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {

    public record Request(UUID requester, UUID target, boolean here, long expiresAtMs) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Request> incoming = new ConcurrentHashMap<>();

    public TpaService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player requester, Player target, boolean here, int timeoutSeconds) {
        incoming.put(target.getUniqueId(), new Request(
                requester.getUniqueId(), target.getUniqueId(), here,
                System.currentTimeMillis() + timeoutSeconds * 1000L));
        target.sendMessage("§e" + requester.getName() + (here ? " wants you to teleport to them." : " wants to teleport to you.")
                + " §7/tpaccept or /tpdeny");
        requester.sendMessage("§aRequest sent to §f" + target.getName());
        YapSched.globalLater(plugin, () -> {
            Request req = incoming.get(target.getUniqueId());
            if (req != null && req.requester().equals(requester.getUniqueId()) && req.expired()) {
                incoming.remove(target.getUniqueId());
                Player r = Bukkit.getPlayer(requester.getUniqueId());
                if (r != null) {
                    r.sendMessage("§cTPA request to §f" + target.getName() + " §cexpired.");
                }
            }
        }, timeoutSeconds * 20L);
    }

    public Request pending(UUID target) {
        Request req = incoming.get(target);
        if (req != null && req.expired()) {
            incoming.remove(target);
            return null;
        }
        return req;
    }

    public void clear(UUID target) {
        incoming.remove(target);
    }
}
