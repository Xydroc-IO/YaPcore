package com.yapcore.tailor.papi;

import com.yapcore.tailor.ActiveSkin;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Soft PlaceholderAPI registration via reflection when classes exist at runtime.
 * Placeholders: {@code %yaptailor_skin%}, {@code %yaptailor_model%}, {@code %yaptailor_slots%}.
 * <p>
 * Does not reference PlaceholderAPI types at compile/link time beyond optional
 * {@code compileOnly} — registration goes through {@link Class#forName} and a
 * generated subclass when possible, otherwise a reflective register call.
 */
public final class TailorPlaceholders {

    private final TailorServiceImpl service;
    private final Logger logger;
    private Object expansionInstance;
    private Method unregisterMethod;

    public TailorPlaceholders(TailorServiceImpl service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        Class<?> expansionClass;
        try {
            expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            // Prefer concrete subclass if our optional compile dependency was shaded/available
            Object expansion = tryCreateConcrete(expansionClass);
            if (expansion == null) {
                expansion = tryCreateViaSubclassLoader(expansionClass);
            }
            if (expansion == null) {
                logger.info("PlaceholderAPI found but expansion could not be constructed");
                return;
            }
            Method register = findMethod(expansion.getClass(), "register");
            if (register == null) {
                register = findMethod(expansionClass, "register");
            }
            if (register == null) {
                return;
            }
            Object ok = register.invoke(expansion);
            if (ok instanceof Boolean b && !b) {
                logger.warning("PlaceholderAPI rejected yaptailor expansion");
                return;
            }
            expansionInstance = expansion;
            unregisterMethod = findMethod(expansion.getClass(), "unregister");
            if (unregisterMethod == null) {
                unregisterMethod = findMethod(expansionClass, "unregister");
            }
            logger.info("Registered PlaceholderAPI expansion: yaptailor");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "PlaceholderAPI soft-register failed", t);
        }
    }

    private Object tryCreateConcrete(Class<?> expansionClass) {
        try {
            Class<?> concrete = Class.forName(
                    "com.yapcore.tailor.papi.TailorPlaceholderExpansion",
                    true,
                    getClass().getClassLoader());
            if (!expansionClass.isAssignableFrom(concrete)) {
                return null;
            }
            return concrete.getConstructor(TailorServiceImpl.class).newInstance(service);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Last-resort: some PAPI builds expose registerExpansion(PlaceholderHook).
     * Not used unless concrete expansion is missing.
     */
    private Object tryCreateViaSubclassLoader(Class<?> expansionClass) {
        try {
            Class<?> hookClass = Class.forName("me.clip.placeholderapi.PlaceholderHook");
            InvocationHandler handler = (proxy, method, args) -> handleHook(method, args);
            Object hook = Proxy.newProxyInstance(
                    expansionClass.getClassLoader(),
                    new Class<?>[]{hookClass},
                    handler);
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method registerExpansion = null;
            for (Method m : papi.getMethods()) {
                if ("registerExpansion".equals(m.getName()) && m.getParameterCount() == 1) {
                    registerExpansion = m;
                    break;
                }
            }
            // PlaceholderHook alone is not enough for expansions — need PlaceholderExpansion
            if (registerExpansion != null && expansionClass.isInstance(hook)) {
                return hook;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object handleHook(Method method, Object[] args) throws Exception {
        String name = method.getName();
        return switch (name) {
            case "onRequest" -> resolve(args);
            case "onPlaceholderRequest" -> resolve(args);
            case "equals" -> args != null && args.length == 1 && proxyEquals(args[0]);
            case "hashCode" -> System.identityHashCode(this);
            case "toString" -> "YaPTailorPlaceholderHook";
            default -> defaultValue(method);
        };
    }

    private static Object defaultValue(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == boolean.class) {
            return false;
        }
        if (rt == int.class) {
            return 0;
        }
        if (rt == long.class) {
            return 0L;
        }
        return null;
    }

    private boolean proxyEquals(Object other) {
        return other != null && other.getClass().getName().contains("yaptailor");
    }

    private String resolve(Object[] args) throws TailorException {
        if (args == null || args.length < 2 || !(args[0] instanceof OfflinePlayer player)) {
            return "";
        }
        String params = args[1] == null ? "" : String.valueOf(args[1]);
        return resolveParams(player, params);
    }

    String resolveParams(OfflinePlayer player, String params) throws TailorException {
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "skin", "url" -> service.getActiveSkin(player.getUniqueId())
                    .map(s -> s.sourceUrl() == null ? "" : s.sourceUrl()).orElse("");
            case "model" -> service.getActiveSkin(player.getUniqueId())
                    .map(s -> s.model().name().toLowerCase(Locale.ROOT)).orElse("");
            case "slots", "wardrobe" -> Integer.toString(service.listWardrobe(player.getUniqueId()).size());
            case "cape" -> service.getActiveSkin(player.getUniqueId())
                    .map(s -> s.capeUrl() == null ? "" : s.capeUrl()).orElse("");
            default -> null;
        };
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    public void unregisterSafe() {
        if (expansionInstance == null) {
            return;
        }
        try {
            if (unregisterMethod != null) {
                unregisterMethod.invoke(expansionInstance);
            }
        } catch (Throwable ignored) {
        }
        expansionInstance = null;
        unregisterMethod = null;
    }
}
