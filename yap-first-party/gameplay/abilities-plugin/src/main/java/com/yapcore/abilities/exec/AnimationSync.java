package com.yapcore.abilities.exec;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.sched.YapSched;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Cast body language: hand swings, brief glow, hand sparkles, optional recoil.
 */
public final class AnimationSync {

    private AnimationSync() {
    }

    public static void play(JavaPlugin plugin, Player player, AbilityEffect effect) {
        String style = effect.param("style", "swing").toLowerCase();
        int pulses = Math.max(1, effect.intParam("pulses", 1));
        YapSched.entity(plugin, player, () -> {
            switch (style) {
                case "offhand" -> player.swingOffHand();
                case "both" -> {
                    player.swingMainHand();
                    player.swingOffHand();
                }
                case "cast" -> playCast(plugin, player, pulses);
                case "channel" -> playChannel(plugin, player, Math.max(3, pulses));
                case "slam" -> playSlam(plugin, player);
                default -> player.swingMainHand();
            }
            String pose = effect.param("pose", "").toLowerCase();
            if (!pose.isBlank()) {
                applyPose(plugin, player, pose, effect.intParam("pose-ticks", 12));
            } else if ("cast".equals(style) || "channel".equals(style)) {
                applyPose(plugin, player, "glow", 10);
            }
            handSparkles(player);
            notifyBedrockCast(player, style);
        });
    }

    /** Bedrock clients get action-bar cast feedback until animation packets land. */
    private static void notifyBedrockCast(Player player, String style) {
        BedrockUiServices.find().ifPresent(bedrock -> {
            if (!bedrock.isBedrock(player)) {
                return;
            }
            String label = switch (style) {
                case "channel" -> "§dChanneling…";
                case "slam" -> "§dSlam!";
                case "cast" -> "§dCasting…";
                default -> "§dCast";
            };
            bedrock.sendActionBar(player, label);
        });
    }

    private static void playCast(JavaPlugin plugin, Player player, int pulses) {
        player.swingMainHand();
        for (int i = 1; i < pulses; i++) {
            final int delay = i * 3;
            YapSched.entityLater(plugin, player, () -> {
                if (player.isOnline()) {
                    player.swingMainHand();
                    handSparkles(player);
                }
            }, delay);
        }
        // Subtle forward lean / recoil feel
        Vector push = player.getLocation().getDirection().multiply(-0.05).setY(0.02);
        player.setVelocity(player.getVelocity().add(push));
    }

    private static void playChannel(JavaPlugin plugin, Player player, int pulses) {
        for (int i = 0; i < pulses; i++) {
            final int delay = i * 4;
            YapSched.entityLater(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (delay % 8 == 0) {
                    player.swingMainHand();
                } else {
                    player.swingOffHand();
                }
                handSparkles(player);
            }, delay);
        }
    }

    private static void playSlam(JavaPlugin plugin, Player player) {
        player.swingMainHand();
        YapSched.entityLater(plugin, player, () -> {
            if (player.isOnline()) {
                player.swingMainHand();
                player.swingOffHand();
                handSparkles(player);
            }
        }, 3L);
        Vector down = new Vector(0, -0.15, 0);
        player.setVelocity(player.getVelocity().add(down));
    }

    private static void applyPose(JavaPlugin plugin, Player player, String pose, int ticks) {
        switch (pose) {
            case "glow", "power" -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, Math.max(5, ticks), 0, false, false, false));
            case "slow", "cast_lock" -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, Math.max(5, ticks), 0, false, false, false));
            case "levitate" -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.LEVITATION, Math.max(5, Math.min(ticks, 15)), 0, false, false, false));
            default -> {
            }
        }
        // Ambient cast particles around torso while posed
        for (int i = 0; i < Math.min(ticks, 12); i += 2) {
            final int delay = i;
            YapSched.entityLater(plugin, player, () -> {
                if (player.isOnline()) {
                    player.getWorld().spawnParticle(
                            Particle.ENCHANTED_HIT,
                            player.getLocation().add(0, 1.1, 0),
                            4, 0.35, 0.4, 0.35, 0.01);
                }
            }, delay);
        }
    }

    private static void handSparkles(Player player) {
        var eye = player.getEyeLocation();
        var dir = eye.getDirection().normalize();
        var hand = eye.add(dir.multiply(0.45)).add(0, -0.2, 0);
        player.getWorld().spawnParticle(Particle.CRIT, hand, 6, 0.12, 0.12, 0.12, 0.02);
        player.getWorld().spawnParticle(Particle.END_ROD, hand, 2, 0.05, 0.05, 0.05, 0.01);
    }
}
