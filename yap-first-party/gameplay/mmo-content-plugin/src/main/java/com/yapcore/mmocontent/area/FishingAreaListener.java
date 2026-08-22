package com.yapcore.mmocontent.area;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public final class FishingAreaListener implements Listener {

    private final SkillAreaLoader loader;

    public FishingAreaListener(SkillAreaLoader loader) {
        this.loader = loader;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        double multiplier = 1.0;
        for (SkillAreaDefinition area : loader.areas().values()) {
            if (area.type() != SkillAreaDefinition.Type.FISHING) {
                continue;
            }
            if (area.contains(event.getPlayer().getLocation())) {
                multiplier = Math.max(multiplier, area.xpMultiplier());
            }
        }
        if (multiplier <= 1.0) {
            return;
        }
        final double bonus = multiplier - 1.0;
        SkillServices.find().ifPresent(svc ->
                svc.get(event.getPlayer().getUniqueId(), SkillId.of("fishing")).thenAccept(progress -> {
                    double drip = Math.max(1.0, progress.xp() * 0.001) * bonus;
                    svc.addXp(event.getPlayer().getUniqueId(), SkillId.of("fishing"), drip, XpSource.ACTION);
                }));
    }
}
