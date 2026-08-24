package com.yapcore.sched.agent;

/**
 * Optional ThreadLocal hints so the shim can pick EntityScheduler / RegionScheduler
 * instead of GlobalRegionScheduler. Plugins (or FoliaBridge) may set these around
 * known entity/location work.
 */
public final class SchedCompatContext {

    private static final ThreadLocal<Object> ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<Object> LOCATION = new ThreadLocal<>();

    private SchedCompatContext() {
    }

    public static void setEntity(Object entity) {
        if (entity == null) {
            ENTITY.remove();
        } else {
            ENTITY.set(entity);
        }
    }

    public static void setLocation(Object location) {
        if (location == null) {
            LOCATION.remove();
        } else {
            LOCATION.set(location);
        }
    }

    public static Object currentEntity() {
        return ENTITY.get();
    }

    public static Object currentLocation() {
        return LOCATION.get();
    }

    public static void clear() {
        ENTITY.remove();
        LOCATION.remove();
    }

    public static AutoCloseable scopedEntity(Object entity) {
        Object prev = ENTITY.get();
        setEntity(entity);
        return () -> {
            if (prev == null) {
                ENTITY.remove();
            } else {
                ENTITY.set(prev);
            }
        };
    }

    public static AutoCloseable scopedLocation(Object location) {
        Object prev = LOCATION.get();
        setLocation(location);
        return () -> {
            if (prev == null) {
                LOCATION.remove();
            } else {
                LOCATION.set(prev);
            }
        };
    }
}
