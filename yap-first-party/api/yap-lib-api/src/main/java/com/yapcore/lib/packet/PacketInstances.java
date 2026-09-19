package com.yapcore.lib.packet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Allocate an empty NMS/Java instance (no-arg ctor, then Unsafe). */
public final class PacketInstances {

    private static Object unsafe;
    private static java.lang.reflect.Method allocateInstance;

    private PacketInstances() {
    }

    public static Object allocate(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        Constructor<?> noArg = noArg(type);
        if (noArg != null) {
            try {
                return noArg.newInstance();
            } catch (ReflectiveOperationException ignored) {
                // unsafe
            }
        }
        Object allocated = unsafeAllocate(type);
        if (allocated != null) {
            return allocated;
        }
        throw new IllegalStateException("Cannot allocate " + type.getName());
    }

    private static Constructor<?> noArg(Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object unsafeAllocate(Class<?> type) {
        try {
            ensureUnsafe();
            if (allocateInstance == null || unsafe == null) {
                return null;
            }
            return allocateInstance.invoke(unsafe, type);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static synchronized void ensureUnsafe() throws ReflectiveOperationException {
        if (allocateInstance != null) {
            return;
        }
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = theUnsafe.get(null);
        allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
        allocateInstance.setAccessible(true);
    }
}
