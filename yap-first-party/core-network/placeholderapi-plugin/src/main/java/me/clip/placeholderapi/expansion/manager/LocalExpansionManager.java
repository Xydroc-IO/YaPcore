package me.clip.placeholderapi.expansion.manager;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionRegisterEvent;
import me.clip.placeholderapi.events.ExpansionUnregisterEvent;
import me.clip.placeholderapi.events.ExpansionsLoadedEvent;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Cleanable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import me.clip.placeholderapi.expansion.VersionSpecific;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry + expansions/ jar loader. Clean-room clip-compatible surface.
 */
public final class LocalExpansionManager implements Listener {

    private static final String EXPANSIONS_FOLDER_NAME = "expansions";

    private final File folder;
    private final PlaceholderAPIPlugin plugin;
    private final Map<String, PlaceholderExpansion> expansions = new ConcurrentHashMap<>();
    private final List<URLClassLoader> expansionLoaders = new ArrayList<>();

    public LocalExpansionManager(@NotNull final PlaceholderAPIPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), EXPANSIONS_FOLDER_NAME);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create expansions folder!");
        }
    }

    public void load(@NotNull final CommandSender sender) {
        final List<PlaceholderExpansion> loaded = new ArrayList<>();
        final File[] jars = folder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                try {
                    for (Class<? extends PlaceholderExpansion> clazz : findExpansionsInJar(jar)) {
                        createExpansionInstance(clazz).ifPresent(expansion -> {
                            expansion.setExpansionType(PlaceholderExpansion.Type.EXTERNAL);
                            if (register(expansion)) {
                                loaded.add(expansion);
                            }
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load expansion jar " + jar.getName(), e);
                }
            }
        }
        sender.sendMessage("[PlaceholderAPI] " + loaded.size() + " external expansion(s) registered.");
        Bukkit.getPluginManager().callEvent(new ExpansionsLoadedEvent(loaded));
    }

    public void kill() {
        unregisterNonPersistent();
        closeLoaders();
    }

    public void unregisterNonPersistent() {
        for (PlaceholderExpansion expansion : new HashSet<>(expansions.values())) {
            if (expansion.persist()) {
                continue;
            }
            expansion.unregister();
        }
    }

    private void closeLoaders() {
        for (URLClassLoader loader : expansionLoaders) {
            try {
                loader.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        expansionLoaders.clear();
    }

    @NotNull
    public File getExpansionsFolder() {
        return folder;
    }

    @NotNull
    public Set<String> getIdentifiers() {
        return Collections.unmodifiableSet(new HashSet<>(expansions.keySet()));
    }

    @NotNull
    public Collection<PlaceholderExpansion> getExpansions() {
        return Collections.unmodifiableCollection(new ArrayList<>(expansions.values()));
    }

    @Nullable
    public PlaceholderExpansion getExpansion(@NotNull final String identifier) {
        return expansions.get(identifier.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Optional<PlaceholderExpansion> findExpansionByName(@NotNull final String name) {
        for (PlaceholderExpansion expansion : expansions.values()) {
            if (expansion.getName().equalsIgnoreCase(name)) {
                return Optional.of(expansion);
            }
        }
        return Optional.empty();
    }

    @NotNull
    public Optional<PlaceholderExpansion> findExpansionByIdentifier(@NotNull final String identifier) {
        return Optional.ofNullable(getExpansion(identifier));
    }

    public boolean register(@NotNull final PlaceholderExpansion expansion) {
        final String identifier = expansion.getIdentifier().toLowerCase(Locale.ROOT);

        if (!expansion.canRegister()) {
            return false;
        }

        if (expansion.getExpansionType() == PlaceholderExpansion.Type.EXTERNAL
                && expansions.containsKey(identifier)) {
            plugin.getLogger().warning("Failed to load external expansion " + identifier
                    + " — identifier already in use.");
            return false;
        }

        if (expansion instanceof Configurable configurable) {
            applyDefaults(identifier, configurable);
        }

        if (expansion instanceof VersionSpecific nms
                && !nms.isCompatibleWith(PlaceholderAPIPlugin.getServerVersion())) {
            plugin.getLogger().warning("Incompatible expansion " + identifier);
            return false;
        }

        final PlaceholderExpansion removed = getExpansion(identifier);
        if (removed != null && !removed.unregister()) {
            return false;
        }

        final ExpansionRegisterEvent event = new ExpansionRegisterEvent(expansion);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        expansions.put(identifier, expansion);

        if (expansion instanceof Listener listener) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }

        if (expansion instanceof Taskable taskable) {
            taskable.start();
        }

        plugin.getLogger().info("Registered " + expansion.getExpansionType().name().toLowerCase(Locale.ROOT)
                + " expansion: " + expansion.getIdentifier() + " [" + expansion.getVersion() + "]");
        return true;
    }

    public boolean unregister(@NotNull final PlaceholderExpansion expansion) {
        if (expansions.remove(expansion.getIdentifier().toLowerCase(Locale.ROOT)) == null) {
            return false;
        }

        Bukkit.getPluginManager().callEvent(new ExpansionUnregisterEvent(expansion));

        if (expansion instanceof Listener listener) {
            HandlerList.unregisterAll(listener);
        }
        if (expansion instanceof Taskable taskable) {
            taskable.stop();
        }
        if (expansion instanceof Cacheable cacheable) {
            cacheable.clear();
        }
        return true;
    }

    private void applyDefaults(String identifier, Configurable configurable) {
        Map<String, Object> defaults = configurable.getDefaults();
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        String pre = "expansions." + identifier + ".";
        FileConfiguration cfg = plugin.getConfig();
        boolean save = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            if (entry.getValue() == null) {
                if (cfg.contains(pre + entry.getKey())) {
                    save = true;
                    cfg.set(pre + entry.getKey(), null);
                }
            } else if (!cfg.contains(pre + entry.getKey())) {
                save = true;
                cfg.set(pre + entry.getKey(), entry.getValue());
            }
        }
        if (save) {
            plugin.saveConfig();
            plugin.reloadConfig();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends PlaceholderExpansion>> findExpansionsInJar(File jarFile) throws Exception {
        List<Class<? extends PlaceholderExpansion>> found = new ArrayList<>();
        URL[] urls = {jarFile.toURI().toURL()};
        URLClassLoader loader = new URLClassLoader(urls, plugin.getClass().getClassLoader());
        expansionLoaders.add(loader);
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class<?> clazz = Class.forName(className, false, loader);
                    if (PlaceholderExpansion.class.isAssignableFrom(clazz)
                            && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        found.add((Class<? extends PlaceholderExpansion>) clazz);
                    }
                } catch (Throwable ignored) {
                    // Missing deps in external jars — skip class.
                }
            }
        }
        return found;
    }

    private Optional<PlaceholderExpansion> createExpansionInstance(
            @NotNull Class<? extends PlaceholderExpansion> clazz) {
        try {
            PlaceholderExpansion expansion = clazz.getDeclaredConstructor().newInstance();
            Objects.requireNonNull(expansion.getAuthor(), "author");
            Objects.requireNonNull(expansion.getIdentifier(), "identifier");
            Objects.requireNonNull(expansion.getVersion(), "version");
            return Optional.of(expansion);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create expansion " + clazz.getName(), e);
            return Optional.empty();
        }
    }

    @EventHandler
    public void onQuit(@NotNull final PlayerQuitEvent event) {
        for (PlaceholderExpansion expansion : getExpansions()) {
            if (expansion instanceof Cleanable cleanable) {
                cleanable.cleanup(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPluginDisable(@NotNull final PluginDisableEvent event) {
        final String name = event.getPlugin().getName();
        if (name.equals(plugin.getName())) {
            return;
        }
        for (PlaceholderExpansion expansion : getExpansions()) {
            if (name.equalsIgnoreCase(expansion.getRequiredPlugin())) {
                expansion.unregister();
                plugin.getLogger().info("Unregistered " + expansion.getIdentifier()
                        + " (required plugin " + name + " disabled)");
            }
        }
    }
}
