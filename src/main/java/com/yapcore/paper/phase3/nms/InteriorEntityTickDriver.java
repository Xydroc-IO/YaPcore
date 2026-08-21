package com.yapcore.paper.phase3.nms;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflective interior entity tick for Phase 3 — runs on YapEngine spatial cores
 * under DLM leases. Works against stock Paper (CraftBukkit getHandle) and
 * vendored builds. Players are skipped (connection flush stays on main).
 */
public final class InteriorEntityTickDriver {

    private static final Logger LOG = Logger.getLogger("YaPcore.Phase3.Nms");
    private static final AtomicLong TICKED = new AtomicLong();
    private static final AtomicLong SKIPPED = new AtomicLong();
    private static final AtomicLong FAULTS = new AtomicLong();

    private Method getHandle;
    private Method tickMethod;
    private boolean resolved;

    public long tickedCount() {
        return TICKED.get();
    }

    public long skippedCount() {
        return SKIPPED.get();
    }

    public long faultCount() {
        return FAULTS.get();
    }

    /**
     * Tick non-player entities in {@code entities} via NMS {@code Entity#tick}.
     * Must already hold the chunk lease on the calling spatial thread.
     */
    public void tickEntities(Collection<?> bukkitEntities) {
        if (bukkitEntities == null || bukkitEntities.isEmpty()) {
            return;
        }
        for (Object entity : bukkitEntities) {
            if (entity == null) {
                continue;
            }
            try {
                if (isPlayer(entity)) {
                    SKIPPED.incrementAndGet();
                    continue;
                }
                Object handle = nmsHandle(entity);
                if (handle == null) {
                    SKIPPED.incrementAndGet();
                    continue;
                }
                if (!isActive(handle)) {
                    inactiveTick(handle);
                    SKIPPED.incrementAndGet();
                    continue;
                }
                Method tick = resolveTick(handle.getClass());
                if (tick == null) {
                    SKIPPED.incrementAndGet();
                    continue;
                }
                tick.invoke(handle);
                TICKED.incrementAndGet();
            } catch (Throwable t) {
                FAULTS.incrementAndGet();
                if (FAULTS.get() < 5 || (FAULTS.get() % 200) == 0) {
                    LOG.log(Level.FINE, "Interior NMS tick fault", t);
                }
            }
        }
    }

    private Method inactiveTickMethod;
    private Method checkIfActive;
    private boolean activationResolved;

    private boolean isActive(Object nmsEntity) {
        if (!activationResolved) {
            resolveActivation(nmsEntity.getClass().getClassLoader());
        }
        if (checkIfActive == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(checkIfActive.invoke(null, nmsEntity));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private void inactiveTick(Object nmsEntity) {
        if (!activationResolved) {
            resolveActivation(nmsEntity.getClass().getClassLoader());
        }
        if (inactiveTickMethod == null) {
            return;
        }
        try {
            inactiveTickMethod.invoke(nmsEntity);
        } catch (ReflectiveOperationException ignored) {
            // best-effort match to Paper main path
        }
    }

    private void resolveActivation(ClassLoader cl) {
        synchronized (this) {
            if (activationResolved) {
                return;
            }
            try {
                Class<?> ar = Class.forName("io.papermc.paper.entity.activation.ActivationRange", true, cl);
                Class<?> entityCl = Class.forName("net.minecraft.world.entity.Entity", true, cl);
                checkIfActive = ar.getMethod("checkIfActive", entityCl);
            } catch (ReflectiveOperationException e) {
                checkIfActive = null;
            }
            try {
                Class<?> entityCl = Class.forName("net.minecraft.world.entity.Entity", true, cl);
                inactiveTickMethod = entityCl.getMethod("inactiveTick");
                inactiveTickMethod.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                inactiveTickMethod = null;
            }
            activationResolved = true;
        }
    }

    private static boolean isPlayer(Object entity) {
        Class<?> c = entity.getClass();
        for (Class<?> i : c.getInterfaces()) {
            if (i.getName().equals("org.bukkit.entity.Player")) {
                return true;
            }
        }
        Class<?> walk = c;
        while (walk != null) {
            if (walk.getName().equals("org.bukkit.entity.Player")
                    || walk.getName().contains("CraftPlayer")) {
                return true;
            }
            walk = walk.getSuperclass();
        }
        try {
            return Boolean.TRUE.equals(entity.getClass().getMethod("isPlayer").invoke(entity));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Object nmsHandle(Object bukkitEntity) throws ReflectiveOperationException {
        if (!resolved) {
            try {
                getHandle = bukkitEntity.getClass().getMethod("getHandle");
                resolved = true;
            } catch (NoSuchMethodException e) {
                // try superclass walk
                Class<?> c = bukkitEntity.getClass();
                while (c != null && getHandle == null) {
                    try {
                        getHandle = c.getMethod("getHandle");
                    } catch (NoSuchMethodException ignored) {
                        c = c.getSuperclass();
                    }
                }
                resolved = true;
            }
        }
        if (getHandle == null) {
            return null;
        }
        return getHandle.invoke(bukkitEntity);
    }

    private Method resolveTick(Class<?> nmsClass) {
        if (tickMethod != null && tickMethod.getDeclaringClass().isAssignableFrom(nmsClass)) {
            return tickMethod;
        }
        Class<?> c = nmsClass;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod("tick");
                m.setAccessible(true);
                tickMethod = m;
                return m;
            } catch (NoSuchMethodException e) {
                // Mojang mapping alternate
                try {
                    Method m = c.getDeclaredMethod("l"); // legacy obf — unlikely on remapped
                    m.setAccessible(true);
                    tickMethod = m;
                    return m;
                } catch (NoSuchMethodException ignored) {
                    c = c.getSuperclass();
                }
            }
        }
        return null;
    }
}
