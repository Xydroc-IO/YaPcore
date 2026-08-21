package com.yapcore.paper.phase3;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/**
 * Paperclip host loader for Phase 3.
 * <p>
 * Parent is the <strong>platform</strong> loader so YaPcore’s stub
 * {@code io.papermc.*}/{@code org.bukkit.*} types never shadow real Paper.
 * Host bridge packages ({@code com.yapcore.paper.phase3.*},
 * {@code com.yaplabs.yapengine.*}) still resolve from the application loader
 * when reflected by the Phase 3 bridge plugin via the system classloader.
 */
public final class Phase3PaperClassLoader extends URLClassLoader {

    private final ClassLoader host;

    public Phase3PaperClassLoader(URL[] urls, ClassLoader host) {
        super(urls, ClassLoader.getPlatformClassLoader());
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            if (isHostBridge(name)) {
                c = host.loadClass(name);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(name, resolve);
        }
    }

    static boolean isHostBridge(String name) {
        return name.startsWith("com.yapcore.paper.phase3.")
                || name.startsWith("com.yaplabs.yapengine.");
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
