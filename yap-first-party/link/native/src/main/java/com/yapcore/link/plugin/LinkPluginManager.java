package com.yapcore.link.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yapcore.link.LinkConfig;
import com.yapcore.link.LinkServer;
import com.yapcore.link.ClientSession;
import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkMetrics;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkPluginDescription;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.api.event.LinkEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads {@code link-data/plugins/*.jar} and manages plugin lifecycle. */
public final class LinkPluginManager {

    private static final Gson GSON = new Gson();
    private static final Logger LOG = Logger.getLogger("YaP.Link.Plugins");

    private final LinkServer server;
    private final LinkProxyImpl proxy;
    private final LinkEventBus eventBus = new LinkEventBus();
    private final Map<String, LoadedPlugin> loaded = new LinkedHashMap<>();
    private final Map<String, SimpleCommand> commands = new ConcurrentHashMap<>();

    public LinkPluginManager(LinkServer server) {
        this.server = server;
        this.proxy = new LinkProxyImpl(server, eventBus, commands);
    }

    public LinkProxy proxy() {
        return proxy;
    }

    public LinkEventBus eventBus() {
        return eventBus;
    }

    public void loadAll() {
        Path dir = server.config().home().resolve("plugins");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not create plugins dir", e);
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .forEach(this::loadJar);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Plugin scan failed", e);
        }
        for (LoadedPlugin lp : loaded.values()) {
            try {
                lp.plugin.onEnable();
                eventBus.register(lp.plugin);
                LOG.info("Enabled plugin " + lp.description.id() + " v" + lp.description.version());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Enable failed for " + lp.description.id(), e);
            }
        }
    }

    public void disableAll() {
        for (LoadedPlugin lp : new ArrayList<>(loaded.values())) {
            try {
                eventBus.unregister(lp.plugin);
                lp.plugin.onDisable();
            } catch (Exception e) {
                LOG.log(Level.FINE, "Disable " + lp.description.id(), e);
            }
            try {
                lp.loader.close();
            } catch (Exception ignored) {
            }
        }
        loaded.clear();
        commands.clear();
    }

    public int loadedCount() {
        return loaded.size();
    }

    public void reloadServerRegistry() {
        proxy.reloadServerRegistry();
    }

    public boolean isRegisteredChannel(String mcChannelId) {
        return proxy.isRegisteredChannel(mcChannelId);
    }

    public java.util.Set<String> registeredChannelIds() {
        return proxy.registeredChannelIds();
    }

    private void loadJar(Path jar) {
        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toUri().toURL()},
                    LinkPlugin.class.getClassLoader());
            LinkPluginDescription desc = readDescription(loader);
            if (desc == null) {
                LOG.warning("Skipping " + jar.getFileName() + " — missing link-plugin.json");
                loader.close();
                return;
            }
            Class<?> mainClass = Class.forName(desc.main(), true, loader);
            if (!LinkPlugin.class.isAssignableFrom(mainClass)) {
                LOG.warning("Skipping " + desc.id() + " — main does not implement LinkPlugin");
                loader.close();
                return;
            }
            LinkPlugin plugin = (LinkPlugin) mainClass.getDeclaredConstructor().newInstance();
            Path dataDir = server.config().home().resolve("plugins").resolve(desc.id());
            Files.createDirectories(dataDir);
            Logger pluginLog = Logger.getLogger("YaP.Link.Plugin." + desc.id());
            LinkPlugin.LinkPluginContext ctx = new LinkPlugin.LinkPluginContext() {
                @Override
                public LinkProxy proxy() {
                    return proxy;
                }

                @Override
                public LinkPluginDescription description() {
                    return desc;
                }

                @Override
                public Path dataDirectory() {
                    return dataDir;
                }

                @Override
                public Logger logger() {
                    return pluginLog;
                }
            };
            plugin.onLoad(ctx);
            loaded.put(desc.id(), new LoadedPlugin(desc, plugin, loader));
            LOG.info("Loaded plugin " + desc.id() + " v" + desc.version() + " from " + jar.getFileName());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load " + jar.getFileName(), e);
        }
    }

    private static LinkPluginDescription readDescription(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream("link-plugin.json")) {
            if (in == null) {
                return null;
            }
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            String id = root.get("id").getAsString();
            String name = root.has("name") ? root.get("name").getAsString() : id;
            String version = root.has("version") ? root.get("version").getAsString() : "1.0.0";
            String main = root.get("main").getAsString();
            List<String> authors = new ArrayList<>();
            if (root.has("authors")) {
                JsonArray arr = root.getAsJsonArray("authors");
                for (var el : arr) {
                    authors.add(el.getAsString());
                }
            }
            return new LinkPluginDescription(id, name, version, main, List.copyOf(authors));
        } catch (Exception e) {
            LOG.log(Level.FINE, "link-plugin.json parse", e);
            return null;
        }
    }

    private record LoadedPlugin(LinkPluginDescription description, LinkPlugin plugin, URLClassLoader loader) {
    }

    /** Proxy API implementation. */
    static final class LinkProxyImpl implements LinkProxy {
        private final LinkServer server;
        private final LinkEventBus eventBus;
        private final Map<String, SimpleCommand> commands;
        private final LinkMetricsImpl metrics = new LinkMetricsImpl();
        private final Map<String, RegisteredServerImpl> servers = new ConcurrentHashMap<>();
        private final Map<String, ChannelIdentifier> channels = new ConcurrentHashMap<>();

        LinkProxyImpl(LinkServer server, LinkEventBus eventBus, Map<String, SimpleCommand> commands) {
            this.server = server;
            this.eventBus = eventBus;
            this.commands = commands;
            refreshServers();
        }

        void refreshServers() {
            servers.clear();
            for (LinkConfig.Backend b : server.config().servers().values()) {
                servers.put(b.name().toLowerCase(), new RegisteredServerImpl(server, b));
            }
        }

        /** Exposed for config reload. */
        public void reloadServerRegistry() {
            refreshServers();
        }

        @Override
        public Logger logger() {
            return LOG;
        }

        @Override
        public Path home() {
            return server.config().home();
        }

        @Override
        public Collection<LinkPlayer> players() {
            List<LinkPlayer> out = new ArrayList<>();
            for (ClientSession s : server.sessions().values()) {
                LinkPlayer p = s.playerHandle();
                if (p != null) {
                    out.add(p);
                }
            }
            return List.copyOf(out);
        }

        @Override
        public Optional<LinkPlayer> player(String username) {
            for (ClientSession s : server.sessions().values()) {
                LinkPlayer p = s.playerHandle();
                if (p != null && p.username().equalsIgnoreCase(username)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }

        @Override
        public Collection<RegisteredServer> servers() {
            return List.copyOf(servers.values());
        }

        @Override
        public Optional<RegisteredServer> server(String name) {
            return Optional.ofNullable(servers.get(name.toLowerCase()));
        }

        @Override
        public void registerChannel(ChannelIdentifier channel) {
            channels.put(channel.id(), channel);
        }

        @Override
        public void fireEvent(LinkEvent event) {
            eventBus.fire(event);
        }

        @Override
        public void registerCommand(String name, SimpleCommand command) {
            commands.put(name.toLowerCase(), command);
        }

        @Override
        public void registerCommand(String name, String permission, SimpleCommand command) {
            commands.put(name.toLowerCase(), new PermissionCommand(permission, command));
        }

        @Override
        public void broadcastPluginMessage(
                RegisteredServer target,
                ChannelIdentifier channel,
                byte[] data,
                RegisteredServer excludeSource
        ) {
            if (target instanceof RegisteredServerImpl impl) {
                impl.deliverPluginMessage(channel, data);
            }
        }

        @Override
        public LinkMetrics metrics() {
            return metrics;
        }

        SimpleCommand command(String name) {
            return commands.get(name.toLowerCase());
        }

        boolean isRegisteredChannel(ChannelIdentifier channel) {
            return channels.containsKey(channel.id());
        }

        boolean isRegisteredChannel(String mcChannelId) {
            return channels.containsKey(mcChannelId);
        }

        java.util.Set<String> registeredChannelIds() {
            return java.util.Set.copyOf(channels.keySet());
        }
    }

    private static final class PermissionCommand implements SimpleCommand {
        private final String permission;
        private final SimpleCommand delegate;

        PermissionCommand(String permission, SimpleCommand delegate) {
            this.permission = permission;
            this.delegate = delegate;
        }

        @Override
        public void execute(CommandSource source, String[] args) {
            delegate.execute(source, args);
        }

        @Override
        public boolean hasPermission(CommandSource source) {
            if (source instanceof LinkPlayerImpl impl) {
                return impl.hasPermission(permission);
            }
            return delegate.hasPermission(source);
        }
    }
}
