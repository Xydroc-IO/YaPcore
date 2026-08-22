package com.yapcore.server;

import com.yapcore.YaPcoreEngine;
import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientRegistry;
import com.yapcore.compat.YaPBukkitServer;
import com.yapcore.config.GameAuthorityProperties;
import com.yapcore.config.ServerConfig;
import com.yapcore.console.ConsoleBus;
import com.yapcore.crash.CrashLogger;
import com.yapcore.crossplay.CrossplayHub;
import com.yapcore.folia.FoliaKernel;
import com.yapcore.kernel.GameKernel;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.paper.PaperKernel;
import com.yapcore.plugin.PluginManager;
import com.yapcore.plugin.loader.PluginRuntime;
import com.yapcore.module.ModuleManager;
import com.yapcore.module.ModuleRuntime;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.server.console.ServerConsoleCommands;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Long-running server facade: config, plugins, player caps, console commands.
 */
public final class YaPcoreServer {

    private static final Logger LOG = Logger.getLogger("YaPcore.Server");

    private final ServerConfig config;
    private final PluginManager pluginManager;
    private final ModuleManager moduleManager;
    private final YaPcoreEngine engine;
    private final YaPBukkitServer bukkitServer;
    private final PluginRuntime pluginRuntime;
    private final ModuleRuntime moduleRuntime;
    private final ResourcePackManager resourcePacks;
    private final DualStackGateway gateway;
    private final GameKernel gameKernel;
    private final FoliaKernel foliaKernel;
    private final PaperKernel paperKernel;
    private final LinkProcessManager linkProcess;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger onlinePlayers = new AtomicInteger(0);
    private final Path pidFile;
    private final Path rootDir;

    public YaPcoreServer(Path rootDir, ServerConfig config) throws IOException {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        this.config = Objects.requireNonNull(config, "config");
        this.pluginManager = new PluginManager(rootDir.resolve(config.getPluginsDir()));
        this.pluginManager.ensureDirectory();
        this.moduleManager = new ModuleManager(rootDir.resolve(config.getModulesDir()));
        this.moduleManager.ensureDirectory();
        Files.createDirectories(rootDir.resolve(config.getLogsDir()));
        Files.createDirectories(rootDir.resolve(config.getLogsDir()).resolve("crashes"));
        this.engine = new YaPcoreEngine(config.getPort());
        this.pidFile = rootDir.resolve("yapcore.pid");
        engine.setMaxPlayers(config.getMaxPlayers());
        engine.setPlayerCountSupplier(onlinePlayers::get);

        this.bukkitServer = new YaPBukkitServer(config, engine.bridge(), engine.pluginPool());
        GameAuthorityBoot.ensureConfigHubs(rootDir, config);
        this.pluginRuntime = new PluginRuntime(
                pluginManager.getPluginsDir(),
                bukkitServer,
                bukkitServer.bridgedScheduler(),
                config.isPaperAuthority() || config.isFoliaAuthority()
        );
        this.moduleRuntime = new ModuleRuntime(
                moduleManager.getModulesDir(),
                bukkitServer.bridgedScheduler()
        );

        this.resourcePacks = new ResourcePackManager(
                rootDir.resolve(config.getResourcePackDir()), config);
        resourcePacks.ensureDirectory();
        PublicEndpoint endpoint = new PublicEndpoint(config);
        if (config.isInternetExposed()) {
            endpoint.applyInternetBind();
        }
        endpoint.applyLocalhostFriendlyBind();
        resourcePacks.setPublicHost(endpoint.publicHost());

        ClientRegistry clients = new ClientRegistry();
        ProtocolVersionRegistry protocols = new ProtocolVersionRegistry(config.isBackwardsCompatible());
        CrossplayHub crossplay = new CrossplayHub(engine.yapEngine());
        this.gateway = new DualStackGateway(
                config, engine.trafficCop(), clients, protocols, resourcePacks, crossplay);
        this.gameKernel = new GameKernel(rootDir, config);
        this.foliaKernel = new FoliaKernel(rootDir, config);
        this.paperKernel = new PaperKernel(rootDir, config, engine.yapEngine());
        this.linkProcess = new LinkProcessManager(rootDir, config);

        CrashLogger.get().configure(
                rootDir.resolve(config.getLogsDir()).resolve("crashes"),
                () -> ServerLifecycle.crashContext(this)
        );
        CrashLogger.get().installGlobalHandlers();

        config.addListener(cfg -> {
            try {
                GameAuthorityProperties.sync(rootDir, cfg);
            } catch (IOException e) {
                LOG.warning("Could not sync game server.properties: " + e.getMessage());
            }
        });
        try {
            GameAuthorityProperties.sync(rootDir, config);
        } catch (IOException e) {
            LOG.warning("Could not sync game server.properties on boot: " + e.getMessage());
        }
    }

    public PublicEndpoint publicEndpoint() {
        return new PublicEndpoint(config);
    }

    public ResourcePackManager getResourcePacks() {
        return resourcePacks;
    }

    public DualStackGateway getGateway() {
        return gateway;
    }

    public FoliaKernel foliaKernel() {
        return foliaKernel;
    }

    public PaperKernel paperKernel() {
        return paperKernel;
    }

    public LinkProcessManager getLinkProcess() {
        return linkProcess;
    }

    GameKernel gameKernel() {
        return gameKernel;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public PluginRuntime getPluginRuntime() {
        return pluginRuntime;
    }

    public ModuleRuntime getModuleRuntime() {
        return moduleRuntime;
    }

    public YaPBukkitServer getBukkitServer() {
        return bukkitServer;
    }

    public YaPcoreEngine getEngine() {
        return engine;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getOnlinePlayers() {
        return onlinePlayers.get();
    }

    public int getMaxPlayers() {
        return config.getMaxPlayers();
    }

    public Path getRootDir() {
        return rootDir;
    }

    public synchronized void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        engine.setMaxPlayers(config.getMaxPlayers());
        engine.start();
        ProtocolCompat.start();
        try {
            resourcePacks.startHttp();
        } catch (IOException e) {
            LOG.warning("Resource pack HTTP failed to start: " + e.getMessage());
        }
        try {
            GameAuthorityBoot.startAuthority(
                    config, foliaKernel, paperKernel, gameKernel, gateway);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            throw new IOException("Wrapped game start interrupted", e);
        } catch (IOException e) {
            running.set(false);
            engine.stop();
            throw e;
        }
        try {
            gateway.start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            paperKernel.stop();
            foliaKernel.stop();
            gameKernel.stop();
            throw new IOException("Gateway start interrupted", e);
        }
        ServerLifecycle.writePid(pidFile);
        ServerLifecycle.logStartupBanner(this);
        pluginRuntime.loadAll();
        moduleRuntime.loadAll();
        GameAuthorityBoot.maybeScheduleRanksAutoApply(this);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOG.info("Stopping YaPcore…");
        com.yapcore.game.command.GameCommandBridge.clearProcessDispatch();
        try {
            pluginRuntime.disableAll();
            moduleRuntime.disableAll();
        } catch (Exception e) {
            CrashLogger.get().dump("plugin-shutdown", e);
        }
        gateway.stop();
        linkProcess.stop();
        paperKernel.stop();
        foliaKernel.stop();
        gameKernel.stop();
        resourcePacks.stopHttp();
        ProtocolCompat.stop();
        engine.stop();
        ServerLifecycle.deletePid(pidFile);
        LOG.info("YaPcore stopped");
    }

    public String executeCommand(String raw) {
        return ServerConsoleCommands.execute(this, raw);
    }

    public String statusReport() {
        return ServerLifecycle.statusReport(this);
    }

    /** Apply / inspect the native YaPPerms rank pack. */
    public String ranksCommand(String[] parts) {
        return ServerConsoleCommands.ranksCommand(this, parts);
    }

    public Optional<String> tryDispatchGameCommand(String line) {
        if (config.isFoliaAuthority() && foliaKernel.isRunning()) {
            return Optional.of(com.yapcore.game.command.GameCommandBridge.dispatch(line));
        }
        if (config.isPaperAuthority() && paperKernel.isRunning()) {
            return Optional.of(com.yapcore.game.command.GameCommandBridge.dispatch(line));
        }
        return Optional.empty();
    }

    public boolean tryJoinEdition(String playerName, ClientEdition edition, int protocolId) {
        synchronized (this) {
            if (onlinePlayers.get() >= config.getMaxPlayers()) {
                LOG.warning("Join denied for " + playerName + " — server full");
                return false;
            }
            var session = gateway.acceptClient(
                    playerName, edition, protocolId,
                    new InetSocketAddress("127.0.0.1", 0));
            if (session == null) {
                return false;
            }
            int n = onlinePlayers.incrementAndGet();
            var player = bukkitServer.players().getOrCreate(playerName);
            player.setOnline(true);
            bukkitServer.getPluginManager().callEvent(
                    new PlayerJoinEvent(player, playerName + " joined the game [" + edition + "]"));
            LOG.info(playerName + " joined via " + edition + " (" + n + "/" + config.getMaxPlayers() + ")");
            return true;
        }
    }

    /** Simulates a player join respecting max-players (Java default). */
    public boolean tryJoin(String playerName) {
        return tryJoinEdition(
                playerName,
                ClientEdition.JAVA,
                gateway.getProtocols().recommended(ClientEdition.JAVA).protocolId());
    }

    public void leave(String playerName) {
        int n = onlinePlayers.updateAndGet(v -> Math.max(0, v - 1));
        gateway.getClients().get(playerName).ifPresent(gateway.getClients()::unregister);
        var player = bukkitServer.players().get(playerName);
        if (player != null) {
            player.setOnline(false);
            bukkitServer.getPluginManager().callEvent(
                    new PlayerQuitEvent(player, playerName + " left the game"));
        }
        LOG.info(playerName + " left (" + n + "/" + config.getMaxPlayers() + ")");
    }

    public void reloadLimitsFromConfig() throws IOException {
        config.load();
        engine.setMaxPlayers(config.getMaxPlayers());
        GameAuthorityProperties.sync(rootDir, config);
        LOG.info("Reloaded limits: max-players=" + config.getMaxPlayers()
                + " (MOTD / RAM changes require Stop → Start)");
    }

    public void runStdinLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                String response = executeCommand(line);
                if (!response.isBlank()) {
                    for (String part : response.split("\n")) {
                        ConsoleBus.get().publish(part);
                        System.out.println(part);
                    }
                }
                if (!running.get()) {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.fine("stdin closed: " + e.getMessage());
        }
    }
}
