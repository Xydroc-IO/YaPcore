package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnimationSync {

    private AnimationSync() {
    }

    public static void play(JavaPlugin plugin, Player player, AbilityEffect effect) {
        String style = effect.param("style", "swing").toLowerCase();
        YapSched.entity(plugin, player, () -> {
            switch (style) {
                case "offhand" -> player.swingOffHand();
                case "both" -> {
                    player.swingMainHand();
                    player.swingOffHand();
                }
                default -> player.swingMainHand();
            }
            String pose = effect.param("pose", "");
            if (!pose.isBlank()) {
                // Reserved for future pose sync / Bedrock animation bridge
            }
        });
    }
}
