package com.yapcore.regions;

import com.yapcore.messages.YapText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;

/** Enter / leave popups for admin regions (title, action bar, optional chat). */
public final class RegionBoundaryNotify {

    private final RegionsConfig config;
    private final RegionService regions;

    public RegionBoundaryNotify(RegionsConfig config, RegionService regions) {
        this.config = config;
        this.regions = regions;
    }

    public void onCross(Player player, Optional<AdminRegion> from, Optional<AdminRegion> to) {
        if (!config.notifyEnabled()) {
            return;
        }
        if (from.map(AdminRegion::id).equals(to.map(AdminRegion::id))) {
            return;
        }
        from.ifPresent(r -> leave(player, r));
        to.ifPresent(r -> enter(player, r));
    }

    public void enter(Player player, AdminRegion region) {
        if (!config.notifyEnabled()) {
            return;
        }
        String custom = regions.message(region.id(), RegionMessageKind.GREETING).orElse(null);
        String name = displayName(region);
        popup(
                player,
                config.notifyEnterTitle(),
                custom != null && !custom.isBlank() ? custom : config.notifyEnterSubtitle(),
                config.notifyActionBarEnter(),
                name,
                custom);
    }

    public void leave(Player player, AdminRegion region) {
        if (!config.notifyEnabled()) {
            return;
        }
        String custom = regions.message(region.id(), RegionMessageKind.FAREWELL).orElse(null);
        String name = displayName(region);
        popup(
                player,
                config.notifyLeaveTitle(),
                custom != null && !custom.isBlank() ? custom : config.notifyLeaveSubtitle(),
                config.notifyActionBarLeave(),
                name,
                custom);
    }

    private void popup(
            Player player,
            String titleTpl,
            String subtitleTpl,
            String actionBarTpl,
            String regionName,
            String customChat) {
        Component title = YapText.component(titleTpl, "region", regionName);
        Component subtitle = YapText.component(subtitleTpl, "region", regionName);
        if (config.notifyTitle()) {
            player.showTitle(Title.title(
                    title,
                    subtitle,
                    Title.Times.times(
                            Duration.ofMillis(config.notifyFadeInMs()),
                            Duration.ofMillis(config.notifyStayMs()),
                            Duration.ofMillis(config.notifyFadeOutMs()))));
        }
        if (config.notifyActionBar()) {
            player.sendActionBar(YapText.component(actionBarTpl, "region", regionName));
        }
        if (config.notifyChat() && customChat != null && !customChat.isBlank()) {
            player.sendMessage(YapText.component(customChat));
        }
    }

    private static String displayName(AdminRegion region) {
        String raw = region.name() == null ? "region" : region.name().trim();
        if (raw.isEmpty()) {
            return "region";
        }
        // spawn → Spawn; my-hub → My Hub
        String[] parts = raw.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
        }
        return sb.toString();
    }
}
