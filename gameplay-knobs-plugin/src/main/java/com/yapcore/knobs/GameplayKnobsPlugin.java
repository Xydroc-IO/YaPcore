package com.yapcore.knobs;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Purpur-inspired gameplay + mob encyclopedia for YaPcore (Paper plugin).
 * Original MIT implementation — WASD ridables, MobGoal AI, and block knobs.
 */
public final class GameplayKnobsPlugin extends JavaPlugin {

    private KnobsConfig knobs;
    private BukkitTask ridableTask;

    @Override
    public void onEnable() {
        knobs = new KnobsConfig(this);
        knobs.reload();
        getServer().getPluginManager().registerEvents(new KnobsListener(this, knobs), this);
        getServer().getPluginManager().registerEvents(new BlockKnobsListener(knobs), this);
        ridableTask = Bukkit.getScheduler().runTaskTimer(this, this::tickRidables, 1L, 1L);
        getLogger().info("YaP Gameplay Knobs online — WASD ridables + AI + block patches");
        getLogger().info("Edit plugins/YaPGameplayKnobs/knobs.yml · /yapknobs reload");
    }

    @Override
    public void onDisable() {
        if (ridableTask != null) {
            ridableTask.cancel();
            ridableTask = null;
        }
    }

    private void tickRidables() {
        if (!knobs.enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof LivingEntity mount)) {
                continue;
            }
            KnobsConfig.MobKnobs mk = knobs.mob(mount.getType().name());
            if (mk != null && mk.ridable() && mk.controllable()) {
                RidableController.tickMount(mount, player, mk);
            }
        }
    }

    public KnobsConfig knobs() {
        return knobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapknobs")) {
            return false;
        }
        if (args.length == 0) {
            sender.sendMessage("YaPGameplayKnobs — mobs=" + knobs.mobs().size()
                    + " enabled=" + knobs.enabled());
            sender.sendMessage("Usage: /yapknobs reload | status");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("yapknobs.reload")) {
                sender.sendMessage("No permission.");
                return true;
            }
            knobs.reload();
            sender.sendMessage("Knobs reloaded (" + knobs.mobs().size() + " mobs).");
            return true;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("enabled=" + knobs.enabled()
                    + " mobs=" + knobs.mobs().size()
                    + " barrelRows=" + knobs.barrelRows()
                    + " beehiveMax=" + knobs.beehiveMaxBees()
                    + " lightningRodRange=" + knobs.lightningRodRange());
            return true;
        }
        return false;
    }
}
