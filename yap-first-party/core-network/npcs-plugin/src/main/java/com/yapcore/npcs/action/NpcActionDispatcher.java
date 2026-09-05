package com.yapcore.npcs.action;

import com.yapcore.playerdata.NpcTraderAccess;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Dispatches hub NPC click actions (shop / warp / spawn / command). */
public final class NpcActionDispatcher {

    private final JavaPlugin plugin;

    public NpcActionDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void dispatch(Player player, String actionRaw) {
        List<NpcActions.Action> actions = NpcActions.parse(actionRaw);
        if (actions.isEmpty()) {
            return;
        }
        for (NpcActions.Action action : actions) {
            switch (action.kind()) {
                case SHOP -> openShop(player, action.value());
                case SPAWN -> YapSched.entity(plugin, player, () -> player.performCommand("spawn"));
                case WARP -> {
                    if ("spawn".equalsIgnoreCase(action.value().trim())) {
                        plugin.getLogger().fine("legacy warp:spawn on NPC — treating as /spawn");
                        YapSched.entity(plugin, player, () -> player.performCommand("spawn"));
                    } else {
                        YapSched.entity(plugin, player, () ->
                                player.performCommand("warp " + action.value()));
                    }
                }
                case COMMAND -> {
                    String cmd = action.value().replace("{player}", player.getName());
                    YapSched.global(plugin, () ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
                }
                case PLAYER -> {
                    String cmd = action.value().replace("{player}", player.getName());
                    YapSched.entity(plugin, player, () -> player.performCommand(cmd));
                }
            }
        }
    }

    private void openShop(Player player, String rawId) {
        long traderId;
        try {
            String id = rawId.startsWith("#") ? rawId.substring(1) : rawId;
            traderId = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cShop NPC misconfigured (bad trader id).");
            return;
        }
        RegisteredServiceProvider<NpcTraderAccess> reg =
                Bukkit.getServicesManager().getRegistration(NpcTraderAccess.class);
        if (reg == null) {
            player.sendMessage("§cShops unavailable — enable YaPPlayerData features.traders.");
            plugin.getLogger().fine("shop action skipped — NpcTraderAccess not registered");
            return;
        }
        NpcTraderAccess access = reg.getProvider();
        if (!access.traderExists(traderId)) {
            player.sendMessage("§cShop catalog #" + traderId + " missing.");
            return;
        }
        try {
            YapSched.entity(plugin, player, () -> access.openTradeGui(player, traderId));
        } catch (Exception e) {
            player.sendMessage("§cCould not open shop.");
            plugin.getLogger().log(Level.WARNING, "open shop " + traderId, e);
        }
    }

    /** True when raw action tries to use warp name {@code spawn} for server spawn. */
    public static boolean isReservedWarpSpawn(String warpName) {
        return warpName != null && "spawn".equals(warpName.trim().toLowerCase(Locale.ROOT));
    }
}
