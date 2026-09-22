package com.yapcore.admin.action;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff night vision — timed, unlimited, and auto-on for {@code yapadmin.auto-nv}. */
public final class AdminNightVision {

    public enum Mode {
        MINUTES_15(20 * 60 * 15, "15 minutes"),
        HOUR(20 * 60 * 60, "1 hour"),
        UNLIMITED(PotionEffect.INFINITE_DURATION, "unlimited"),
        OFF(0, "off");

        private final int ticks;
        private final String label;

        Mode(int ticks, String label) {
            this.ticks = ticks;
            this.label = label;
        }

        public int ticks() {
            return ticks;
        }

        public String label() {
            return label;
        }
    }

    private final AdminPlugin plugin;
    /** Players with staff-unlimited NV (until turned off). */
    private final Set<UUID> unlimited = ConcurrentHashMap.newKeySet();
    /** Auto-nv holders who opted out this session (until they turn it back on). */
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();

    public AdminNightVision(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isUnlimited(UUID uuid) {
        return unlimited.contains(uuid);
    }

    /** Drop session opt-out on quit so auto-nv returns next join; unlimited sticks until turned off. */
    public void onQuit(UUID uuid) {
        optedOut.remove(uuid);
    }

    public static Optional<Mode> parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "15", "15m", "15min", "15mins", "m15" -> Optional.of(Mode.MINUTES_15);
            case "1h", "1hr", "1hour", "60", "60m", "hour" -> Optional.of(Mode.HOUR);
            case "on", "u", "unlim", "unlimited", "inf", "infinite", "forever" -> Optional.of(Mode.UNLIMITED);
            case "off", "clear", "stop", "0" -> Optional.of(Mode.OFF);
            default -> Optional.empty();
        };
    }

    /** Apply NV for {@code target}. {@code admin} may equal {@code target} (self). */
    public void apply(Player admin, Player target, Mode mode) {
        YapSched.entity(plugin, target, () -> {
            if (mode == Mode.OFF) {
                unlimited.remove(target.getUniqueId());
                if (target.hasPermission("yapadmin.auto-nv")) {
                    optedOut.add(target.getUniqueId());
                }
                target.removePotionEffect(PotionEffectType.NIGHT_VISION);
                notify(admin, target, "§7Night vision off.");
                return;
            }
            optedOut.remove(target.getUniqueId());
            if (mode == Mode.UNLIMITED) {
                unlimited.add(target.getUniqueId());
            } else {
                unlimited.remove(target.getUniqueId());
            }
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, mode.ticks(), 0, false, false, true));
            notify(admin, target, "§aNight vision §f" + mode.label() + "§a.");
        });
    }

    /** Join / respawn: restore unlimited or auto-nv. */
    public void restoreIfNeeded(Player player) {
        UUID id = player.getUniqueId();
        boolean auto = player.hasPermission("yapadmin.auto-nv") && !optedOut.contains(id);
        boolean keep = unlimited.contains(id);
        if (!auto && !keep) {
            return;
        }
        if (auto) {
            unlimited.add(id);
        }
        YapSched.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, Mode.UNLIMITED.ticks(), 0, false, false, true));
        });
    }

    /** Re-apply after milk / effect clear while still marked unlimited. */
    public void reapplyIfUnlimited(Player player) {
        if (!unlimited.contains(player.getUniqueId())) {
            return;
        }
        YapSched.entityLater(plugin, player, () -> {
            if (!player.isOnline() || !unlimited.contains(player.getUniqueId())) {
                return;
            }
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                return;
            }
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, Mode.UNLIMITED.ticks(), 0, false, false, true));
        }, 2L);
    }

    private static void notify(Player admin, Player target, String message) {
        if (admin.getUniqueId().equals(target.getUniqueId())) {
            admin.sendMessage(message);
        } else {
            admin.sendMessage(message + " §7→ §f" + target.getName());
            target.sendMessage(message);
        }
    }
}
