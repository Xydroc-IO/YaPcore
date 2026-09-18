package com.yapcore.sched.agent;

/**
 * Optional ThreadLocal hints so the shim can pick EntityScheduler / RegionScheduler
 * instead of GlobalRegionScheduler.
 *
 * <p>Event dispatch ({@code RegisteredListener.callEvent}) sets these from the
 * event's entity/block/chunk. Plugins may also set them around known work.
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
        return () -> restore(ENTITY, prev);
    }

    public static AutoCloseable scopedLocation(Object location) {
        Object prev = LOCATION.get();
        setLocation(location);
        return () -> restore(LOCATION, prev);
    }

    /**
     * Bind affinity from a Bukkit event (entity / player / block / chunk / vehicle).
     * No-op for events without a world object (plugin lifecycle, etc.).
     */
    public static AutoCloseable scopedFromEvent(Object event) {
        if (event == null) {
            return () -> {
            };
        }
        Object entity = EventAffinity.entityOf(event);
        Object location = EventAffinity.locationOf(event);
        Object prevE = ENTITY.get();
        Object prevL = LOCATION.get();
        if (entity != null) {
            ENTITY.set(entity);
        }
        if (location != null) {
            LOCATION.set(location);
        }
        return () -> {
            restore(ENTITY, prevE);
            restore(LOCATION, prevL);
        };
    }

    private static void restore(ThreadLocal<Object> slot, Object prev) {
        if (prev == null) {
            slot.remove();
        } else {
            slot.set(prev);
        }
    }
}
