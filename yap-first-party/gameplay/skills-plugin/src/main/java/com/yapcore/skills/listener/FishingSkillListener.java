package com.yapcore.skills.listener;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import com.yapcore.mechanics.MechanicsServices;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsPlugin;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public final class FishingSkillListener implements Listener {

    private static final SkillId FISHING = SkillId.of("fishing");

    private final SkillsPlugin plugin;

    public FishingSkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        SkillDefinition def = plugin.skillService().definition(FISHING).orElse(null);
        if (def == null || !def.enabled()) {
            return;
        }
        double xp = resolveXp(def, event);
        if (xp <= 0) {
            return;
        }
        xp *= MechanicsServices.find()
                .map(s -> s.fishingXpMultiplier(player))
                .orElse(1.0);
        final double grant = xp;
        var skills = plugin.skillService();
        YapSched.async(plugin, () -> skills.addXp(player.getUniqueId(), FISHING, grant, XpSource.ACTION)
                .thenAccept(updated -> YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        skills.showXpGain(player, FISHING, grant);
                    }
                })));
    }

    private static double resolveXp(SkillDefinition def, PlayerFishEvent event) {
        if (def.fishActions().isEmpty()) {
            return 0;
        }
        SkillDefinition.FishAction defaultAction = def.fishActions().get("CAUGHT");
        if (event.getCaught() instanceof Item item) {
            ItemStack stack = item.getItemStack();
            String matKey = stack.getType().name();
            SkillDefinition.FishAction specific = def.fishActions().get(matKey);
            if (specific != null) {
                return specific.xp();
            }
        }
        return defaultAction == null ? 0 : defaultAction.xp();
    }
}
