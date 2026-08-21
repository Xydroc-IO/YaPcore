package com.yapcore.compat;

import org.bukkit.Server;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * Plugin classloader — Bukkit/Paper/Adventure/NMS resolve from the server;
 * plugin-private classes load from the jar first.
 */
public final class PluginClassLoader extends URLClassLoader {

    private static final Set<String> PARENT_FIRST = Set.of(
            "org.bukkit.",
            "io.papermc.",
            "com.destroystokyo.paper.",
            "com.mojang.",
            "net.kyori.",
            "net.minecraft.",
            "com.yapcore.",
            "com.yaplabs.",
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "jdk.internal."
    );

    private final Server server;
    private final String pluginName;

    public PluginClassLoader(String pluginName, URL[] urls, ClassLoader parent, Server server) {
        super(urls, parent);
        this.pluginName = pluginName;
        this.server = server;
    }

    public Server getServer() {
        return server;
    }

    public String getPluginName() {
        return pluginName;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            if (isParentFirst(name)) {
                try {
                    return getParent().loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // fall through — rare generated types
                }
            }
            try {
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            } catch (ClassNotFoundException e) {
                return getParent().loadClass(name);
            }
        }
    }

    private static boolean isParentFirst(String name) {
        for (String p : PARENT_FIRST) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
