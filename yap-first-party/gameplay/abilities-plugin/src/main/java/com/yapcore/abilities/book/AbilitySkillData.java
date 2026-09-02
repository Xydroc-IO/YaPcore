package com.yapcore.abilities.book;

import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Loads skill rows for the book without blocking forever if MariaDB is down. */
final class AbilitySkillData {

    private AbilitySkillData() {
    }

    static void load(JavaPlugin plugin, Player player, Consumer<Collection<SkillProgress>> consumer) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills == null) {
            YapSched.entity(plugin, player, () -> consumer.accept(List.of()));
            return;
        }
        skills.getAll(player.getUniqueId())
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Skill lookup for ability book failed", ex);
                    return List.of();
                })
                .thenAccept(all -> YapSched.entity(plugin, player, () ->
                        consumer.accept(all == null ? List.of() : all)));
    }
}
