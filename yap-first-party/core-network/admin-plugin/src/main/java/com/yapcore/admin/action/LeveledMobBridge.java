package com.yapcore.admin.action;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Soft-hooks YaPMobs / YaPLeveledMobs to set an explicit mob level. */
public final class LeveledMobBridge {

    private LeveledMobBridge() {
    }

    public static boolean setLevel(LivingEntity entity, int level, Logger log) {
        if (entity == null) {
            return false;
        }
        Object applier = findApplier();
        if (applier == null) {
            return false;
        }
        try {
            Method set = applier.getClass().getMethod("setLevel", LivingEntity.class, int.class);
            set.invoke(applier, entity, level);
            return true;
        } catch (ReflectiveOperationException e) {
            if (log != null) {
                log.warning("leveled setLevel failed: " + e.getMessage());
            }
            return false;
        }
    }

    private static Object findApplier() {
        Plugin mobs = Bukkit.getPluginManager().getPlugin("YaPMobs");
        if (mobs != null && mobs.isEnabled()) {
            Object viaHost = invokeNoArg(mobs, "leveled");
            if (viaHost != null) {
                Object applier = invokeNoArg(viaHost, "applier");
                if (applier != null) {
                    return applier;
                }
            }
            Object direct = invokeNoArg(mobs, "applier");
            if (direct != null) {
                return direct;
            }
        }
        Plugin leveled = Bukkit.getPluginManager().getPlugin("YaPLeveledMobs");
        if (leveled != null && leveled.isEnabled()) {
            Object applier = invokeNoArg(leveled, "applier");
            if (applier != null) {
                return applier;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
