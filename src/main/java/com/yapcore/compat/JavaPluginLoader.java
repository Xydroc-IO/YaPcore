package com.yapcore.compat;

import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads Paper/Spigot jars with dependency ordering and soft-fail enable.
 */
public final class JavaPluginLoader implements PluginLoader {

    private static final Logger LOG = Logger.getLogger("YaPcore.JavaPluginLoader");

    private final Server server;
    private final Map<String, PluginClassLoader> loaders = new ConcurrentHashMap<>();

    public JavaPluginLoader(Server server) {
        this.server = server;
    }

    @Override
    public Plugin loadPlugin(File file) throws Exception {
        PluginDescriptionFile description = getPluginDescription(file);
        for (String hard : description.getDepend()) {
            if (server.getPluginManager().getPlugin(hard) == null
                    && !loaders.containsKey(hard)) {
                // hard depend may load later in batch — checked again at enable
                LOG.fine("Hard depend '" + hard + "' not loaded yet for " + description.getName());
            }
        }
        for (String soft : description.getSoftDepend()) {
            if (server.getPluginManager().getPlugin(soft) == null) {
                LOG.info(description.getName() + " soft-depend missing (ok): " + soft);
            }
        }

        File dataFolder = new File(file.getParentFile(), description.getName());
        dataFolder.mkdirs();

        PluginClassLoader classLoader = new PluginClassLoader(
                description.getName(),
                new URL[]{file.toURI().toURL()},
                getClass().getClassLoader(),
                server
        );
        loaders.put(description.getName(), classLoader);

        Class<?> mainClass = Class.forName(description.getMain(), true, classLoader);
        if (!JavaPlugin.class.isAssignableFrom(mainClass)) {
            throw new IllegalArgumentException(description.getMain() + " does not extend JavaPlugin");
        }
        JavaPlugin plugin = (JavaPlugin) mainClass.getDeclaredConstructor().newInstance();
        plugin.init(this, server, description, dataFolder, file);
        registerCommandsFromYaml(plugin, description);
        try {
            plugin.onLoad();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "onLoad failed for " + description.getName(), t);
            throw t;
        }
        LOG.info("Loaded plugin " + description.getFullName()
                + " (api=" + description.getAPIVersion() + ")");
        return plugin;
    }

    @SuppressWarnings("unchecked")
    private void registerCommandsFromYaml(JavaPlugin plugin, PluginDescriptionFile description) {
        Object cmds = description.getRaw().get("commands");
        if (!(cmds instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String name = String.valueOf(e.getKey());
            org.bukkit.command.PluginCommand cmd = new org.bukkit.command.PluginCommand(name, plugin);
            if (e.getValue() instanceof Map<?, ?> meta) {
                if (meta.get("description") != null) {
                    cmd.setDescription(String.valueOf(meta.get("description")));
                }
                if (meta.get("usage") != null) {
                    cmd.setUsage(String.valueOf(meta.get("usage")));
                }
                if (meta.get("permission") != null) {
                    cmd.setPermission(String.valueOf(meta.get("permission")));
                }
                if (meta.get("aliases") instanceof List<?> aliases) {
                    cmd.setAliases(aliases.stream().map(String::valueOf).toList());
                }
            }
            cmd.setExecutor(plugin);
            server.registerPluginCommand(cmd);
        }
    }

    /** Load all jars in directory with depend topological-ish ordering. */
    public List<Plugin> loadPluginsOrdered(File directory) {
        File[] files = directory.listFiles((d, n) -> n.endsWith(".jar") || n.endsWith(".yap"));
        if (files == null) {
            return List.of();
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(f -> {
            try {
                return getPluginDescription(f).getDepend().size();
            } catch (Exception e) {
                return 0;
            }
        }));
        List<Plugin> loaded = new ArrayList<>();
        for (File file : sorted) {
            try {
                if (!looksLikeLegacyPlugin(file)) {
                    continue;
                }
                loaded.add(loadPlugin(file));
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to load " + file.getName() + " — skipped", t);
            }
        }
        return loaded;
    }

    @Override
    public PluginDescriptionFile getPluginDescription(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new IllegalArgumentException(file.getName() + " missing plugin.yml");
            }
            try (InputStream in = jar.getInputStream(entry)) {
                return new PluginDescriptionFile(in);
            }
        }
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        if (!(plugin instanceof JavaPlugin javaPlugin)) {
            return;
        }
        if (plugin.isEnabled()) {
            return;
        }
        for (String hard : javaPlugin.getDescription().getDepend()) {
            Plugin dep = server.getPluginManager().getPlugin(hard);
            if (dep == null || !dep.isEnabled()) {
                // Soft-skip: PluginRuntime retries after more plugins enable
                throw new UnknownDependencyException("Missing depend: " + hard);
            }
        }
        try {
            javaPlugin.enable();
            LOG.info("Enabled plugin " + plugin.getName());
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Enable failed for " + plugin.getName()
                    + " — plugin disabled to keep server up", t);
            try {
                if (javaPlugin.isEnabled()) {
                    javaPlugin.disable();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (plugin instanceof JavaPlugin javaPlugin) {
            try {
                javaPlugin.disable();
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "onDisable error for " + plugin.getName(), t);
            }
        }
        try {
            PluginClassLoader cl = loaders.remove(plugin.getName());
            if (cl != null) {
                cl.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error closing classloader for " + plugin.getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Event>[] createRegisteredListeners(Listener listener, Plugin plugin) {
        return new Class[0];
    }

    public static boolean looksLikeLegacyPlugin(File file) {
        try (JarFile jar = new JarFile(file)) {
            return jar.getJarEntry("plugin.yml") != null;
        } catch (Exception e) {
            return false;
        }
    }
}
