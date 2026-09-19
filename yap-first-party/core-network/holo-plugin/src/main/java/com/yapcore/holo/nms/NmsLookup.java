package com.yapcore.holo.nms;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

/** Server-classloader NMS lookups. */
public final class NmsLookup {

    private final ClassLoader loader;
    private final JavaPlugin plugin;

    public NmsLookup(JavaPlugin plugin) {
        this.plugin = plugin;
        this.loader = plugin.getServer().getClass().getClassLoader();
    }

    public ClassLoader loader() {
        return loader;
    }

    public Class<?> clazz(String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public Object staticField(Class<?> type, String... names) {
        if (type == null) {
            return null;
        }
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (String name : names) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(null);
                } catch (ReflectiveOperationException ignored) {
                    // next
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    public Object field(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> cursor = target.getClass();
        while (cursor != null && cursor != Object.class) {
            for (String name : names) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    // next
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    public Method method(Class<?> type, String name, Class<?>... params) {
        if (type == null) {
            return null;
        }
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            Class<?> cursor = type;
            while (cursor != null) {
                try {
                    Method method = cursor.getDeclaredMethod(name, params);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    cursor = cursor.getSuperclass();
                }
            }
            return null;
        }
    }

    public Constructor<?> ctor(Class<?> type, Class<?>... params) {
        if (type == null) {
            return null;
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(params);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public void warn(String message, Throwable error) {
        plugin.getLogger().log(Level.WARNING, message, error);
    }

    public void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public Optional<Object> invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (ReflectiveOperationException e) {
            warn("NMS invoke " + method.getName(), e);
            return Optional.empty();
        }
    }
}
