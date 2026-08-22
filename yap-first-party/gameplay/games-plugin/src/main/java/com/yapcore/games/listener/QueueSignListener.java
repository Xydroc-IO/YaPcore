package com.yapcore.games.listener;

import com.yapcore.games.GameModeId;
import com.yapcore.games.GamesConfig;
import com.yapcore.games.match.MatchManager;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

public final class QueueSignListener implements Listener {

    private final GamesConfig config;
    private final MatchManager matches;

    public QueueSignListener(GamesConfig config, MatchManager matches) {
        this.config = config;
        this.matches = matches;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        if (!config.signsEnabled()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        if (!event.getPlayer().hasPermission("yapgames.use")) {
            return;
        }
        String modeId = parseMode(sign);
        if (modeId == null) {
            return;
        }
        event.setCancelled(true);
        if (!matches.joinQueue(event.getPlayer().getUniqueId(), GameModeId.of(modeId))) {
            event.getPlayer().sendMessage("§cCould not join queue.");
        }
    }

    private String parseMode(Sign sign) {
        for (String template : config.signLines()) {
            if (!template.toLowerCase(Locale.ROOT).startsWith("[queue")) {
                continue;
            }
            int start = template.indexOf(']');
            if (start >= 0 && start + 1 < template.length()) {
                return template.substring(start + 1).trim();
            }
        }
        var side = sign.getSide(Side.FRONT);
        for (String line : side.getLines()) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("[queue")) {
                int end = trimmed.indexOf(']');
                if (end >= 0 && end + 1 < trimmed.length()) {
                    return trimmed.substring(end + 1).trim();
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }
}
