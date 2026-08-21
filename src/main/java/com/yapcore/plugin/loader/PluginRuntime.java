package com.yapcore.plugin.loader;

import com.yapcore.api.YaPPlugin;
import com.yapcore.api.YaPPluginContext;
import com.yapcore.api.YaPPluginDescription;
import com.yapcore.api.YaPScheduler;
import com.yapcore.compat.JavaPluginLoader;
import com.yapcore.compat.YaPBukkitServer;
import com.yapcore.crash.CrashLogger;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads both legacy Spigot/Paper {@code plugin.yml} jars and next-gen {@code yap.yml} jars.
 */
public final class PluginRuntime {

    private static final Logger LOG = Logger.getLogger("YaPcore.PluginRuntime");

    private final Path pluginsDir;
    private final YaPBukkitServer bukkitServer;
    private final YaPScheduler scheduler;
    private final boolean paperOwnsLegacyPlugins;
    private final List<YaPPlugin> yapPlugins = new CopyOnWriteArrayList<>();
    private final List<URLClassLoader> yapLoaders = new CopyOnWriteArrayList<>();

    public PluginRuntime(Path pluginsDir, YaPBukkitServer bukkitServer, YaPScheduler scheduler) {
        this(pluginsDir, bukkitServer, scheduler, false);
    }

    /**
     * @param paperOwnsLegacyPlugins when true (Paper game-authority), only {@code yap.yml}
     *        jars are loaded here; {@code plugin.yml} jars are left for real Paper in the
     *        same unified {@code plugins/} folder.
     */
    public PluginRuntime(Path pluginsDir, YaPBukkitServer bukkitServer, YaPScheduler scheduler,
                         boolean paperOwnsLegacyPlugins) {
        this.pluginsDir = pluginsDir;
        this.bukkitServer = bukkitServer;
        this.scheduler = scheduler;
        this.paperOwnsLegacyPlugins = paperOwnsLegacyPlugins;
    }

    public void loadAll() {
        File dir = pluginsDir.toFile();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".jar") || n.endsWith(".yap"));
        if (files == null || files.length == 0) {
            LOG.info("No plugin jars found in " + pluginsDir.toAbsolutePath());
            return;
        }
        // Pass 1: YaP plugins
        for (File file : files) {
            if (hasEntry(file, "yap.yml")) {
                try {
                    loadYaPPlugin(file);
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "Failed to load YaP plugin " + file.getName(), t);
                    CrashLogger.get().logPluginFault(file.getName(), "load", t);
                }
            }
        }
        // Pass 2: legacy Paper jars — only when YaP facade owns them (not Paper authority)
        if (paperOwnsLegacyPlugins) {
            LOG.info("Paper authority: plugin.yml jars in " + pluginsDir.toAbsolutePath()
                    + " are loaded by Paper (unified plugins folder)");
            LOG.info("Plugin runtime ready — legacy=0 (Paper-owned) yap=" + yapPlugins.size());
            return;
        }
        JavaPluginLoader sharedLoader = new JavaPluginLoader(bukkitServer);
        List<Plugin> legacy = new ArrayList<>();
        List<File> jars = new ArrayList<>();
        for (File file : files) {
            if (!hasEntry(file, "yap.yml") && JavaPluginLoader.looksLikeLegacyPlugin(file)) {
                jars.add(file);
            }
        }
        jars.sort((a, b) -> {
            try {
                return Integer.compare(
                        sharedLoader.getPluginDescription(a).getDepend().size(),
                        sharedLoader.getPluginDescription(b).getDepend().size());
            } catch (Exception e) {
                return 0;
            }
        });
        for (File file : jars) {
            try {
                Plugin plugin = bukkitServer.getPluginManager().loadPlugin(file);
                legacy.add(plugin);
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to load plugin " + file.getName()
                        + " — skipped so other plugins still run", t);
                CrashLogger.get().logPluginFault(file.getName(), "load", t);
            }
        }
        // Multiple enable passes so depends that load later can unlock dependents
        boolean progressed;
        do {
            progressed = false;
            for (Plugin plugin : legacy) {
                if (plugin.isEnabled()) {
                    continue;
                }
                try {
                    bukkitServer.getPluginManager().enablePlugin(plugin);
                    if (plugin.isEnabled()) {
                        progressed = true;
                    }
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "Failed to enable " + plugin.getName()
                            + " — skipped", t);
                    CrashLogger.get().logPluginFault(plugin.getName(), "enable", t);
                }
            }
        } while (progressed);
        for (Plugin plugin : legacy) {
            if (!plugin.isEnabled()) {
                LOG.warning("Plugin left disabled (missing depend or enable error): "
                        + plugin.getName());
            }
        }
        LOG.info("Plugin runtime ready — legacy="
                + bukkitServer.getPluginManager().getPlugins().length
                + " yap=" + yapPlugins.size()
                + " (failed plugins are skipped; server stays up)");
    }

    public void disableAll() {
        for (YaPPlugin plugin : new ArrayList<>(yapPlugins)) {
            try {
                plugin.disable();
            } catch (Throwable t) {
                CrashLogger.get().logPluginFault(plugin.getName(), "disable", t);
            }
        }
        yapPlugins.clear();
        for (URLClassLoader cl : yapLoaders) {
            try {
                cl.close();
            } catch (Exception ignored) {
            }
        }
        yapLoaders.clear();
        bukkitServer.shutdownCompat();
    }

    public List<YaPPlugin> getYaPPlugins() {
        return List.copyOf(yapPlugins);
    }

    private void loadYaPPlugin(File file) throws Exception {
        YaPPluginDescription desc;
        try (JarFile jar = new JarFile(file);
             InputStream in = jar.getInputStream(jar.getJarEntry("yap.yml"))) {
            desc = YaPPluginDescription.fromYaml(in);
        }
        File dataFolder = new File(file.getParentFile(), desc.name());
        dataFolder.mkdirs();

        URLClassLoader cl = new URLClassLoader(new URL[]{file.toURI().toURL()}, getClass().getClassLoader());
        yapLoaders.add(cl);
        Class<?> main = Class.forName(desc.main(), true, cl);
        if (!YaPPlugin.class.isAssignableFrom(main)) {
            throw new IllegalArgumentException(desc.main() + " does not extend YaPPlugin");
        }
        YaPPlugin plugin = (YaPPlugin) main.getDeclaredConstructor().newInstance();
        Logger pluginLog = Logger.getLogger(desc.name());
        plugin.init(new YaPPluginContext(desc, dataFolder, file, scheduler, pluginLog));
        plugin.onLoad();
        plugin.enable();
        yapPlugins.add(plugin);
        LOG.info("Enabled YaP plugin " + desc.name() + " v" + desc.version()
                + " (all-in-one dual-pool API)");
    }

    private static boolean hasEntry(File file, String name) {
        try (JarFile jar = new JarFile(file)) {
            return jar.getJarEntry(name) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
