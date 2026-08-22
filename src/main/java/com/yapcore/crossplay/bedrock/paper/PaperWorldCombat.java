package com.yapcore.crossplay.bedrock.paper;

import java.util.Collection;
import java.util.logging.Level;

final class PaperWorldCombat {

    private final PaperWorldSyncBackend backend;

    PaperWorldCombat(PaperWorldSyncBackend backend) {
        this.backend = backend;
    }

    void attackEntity(String attacker, String targetRuntime, String targetName, String targetUuid) {
        try {
            ClassLoader cl = backend.paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object attackerPlayer = PaperWorldMainThread.findPlayer(bukkit, attacker);
            if (attackerPlayer == null) {
                return;
            }
            Object victim = null;
            if (targetUuid != null && !targetUuid.isBlank()) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(targetUuid.trim());
                    victim = bukkit.getMethod("getEntity", java.util.UUID.class).invoke(null, uuid);
                } catch (Exception ignored) {
                }
            }
            if (victim == null && targetName != null && !targetName.isBlank()) {
                victim = PaperWorldMainThread.findPlayer(bukkit, targetName);
            }
            if (victim == null) {
                victim = nearestLivingTarget(attackerPlayer, cl, attacker);
            }
            if (victim == null) {
                return;
            }
            try {
                attackerPlayer.getClass().getMethod("attack",
                                Class.forName("org.bukkit.entity.Entity", true, cl))
                        .invoke(attackerPlayer, victim);
            } catch (NoSuchMethodException e) {
                victim.getClass().getMethod("damage", double.class).invoke(victim, 1.0);
            }
            final Object v = victim;
            PaperWorldSyncBackend.LOG.fine(() -> "Paper ATTACK " + attacker + " → " + v.getClass().getSimpleName());
        } catch (Exception e) {
            PaperWorldSyncBackend.LOG.log(Level.FINE, "Paper ATTACK failed", e);
        }
    }

    /** Closest LivingEntity within 4.5 blocks (prefers non-players). */
    private Object nearestLivingTarget(Object attackerPlayer, ClassLoader cl, String attackerName)
            throws ReflectiveOperationException {
        Object loc = attackerPlayer.getClass().getMethod("getLocation").invoke(attackerPlayer);
        Object world = loc.getClass().getMethod("getWorld").invoke(loc);
        if (world == null) {
            return null;
        }
        Class<?> living = Class.forName("org.bukkit.entity.LivingEntity", true, cl);
        @SuppressWarnings("unchecked")
        Collection<Object> nearby = (Collection<Object>) world.getClass()
                .getMethod("getNearbyEntities",
                        loc.getClass(), double.class, double.class, double.class)
                .invoke(world, loc, 4.5, 4.5, 4.5);
        Object bestMob = null;
        Object bestAny = null;
        double bestMobD = Double.MAX_VALUE;
        double bestAnyD = Double.MAX_VALUE;
        Class<?> playerCl = Class.forName("org.bukkit.entity.Player", true, cl);
        for (Object e : nearby) {
            if (e == null || !living.isInstance(e)) {
                continue;
            }
            try {
                String name = (String) e.getClass().getMethod("getName").invoke(e);
                if (name != null && name.equalsIgnoreCase(attackerName)) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            Object el = e.getClass().getMethod("getLocation").invoke(e);
            double d = ((Number) loc.getClass().getMethod("distance", el.getClass())
                    .invoke(loc, el)).doubleValue();
            boolean isPlayer = playerCl.isInstance(e);
            if (!isPlayer && d < bestMobD) {
                bestMobD = d;
                bestMob = e;
            }
            if (d < bestAnyD) {
                bestAnyD = d;
                bestAny = e;
            }
        }
        return bestMob != null ? bestMob : bestAny;
    }
}
