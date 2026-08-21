package com.yapcore.server;

import com.yapcore.YaPcoreEngine;
import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientRegistry;
import com.yapcore.compat.YaPBukkitServer;
import com.yapcore.config.ConfigHub;
import com.yapcore.config.ServerConfig;
import com.yapcore.console.ConsoleBus;
import com.yapcore.crash.CrashLogger;
import com.yapcore.crossplay.CrossplayHub;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.network.publicity.PublicityCommands;
import com.yapcore.plugin.PluginManager;
import com.yapcore.plugin.loader.PluginRuntime;
import com.yapcore.module.ModuleManager;
import com.yapcore.module.ModuleRuntime;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.kernel.GameKernel;
import com.yapcore.paper.PaperKernel;
import com.yapcore.paper.PaperPluginsLayout;
import com.yapcore.paper.phase3.PaperTickBridge;
import com.yapcore.ranks.YapRanks;
import com.yapcore.resourcepack.ResourcePackManager;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private final PaperKernel paperKernel;
    private final PaperTickBridge paperTickBridge;
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
        if (config.isPaperAuthority()) {
            try {
                PaperPluginsLayout.ensureUnified(rootDir, rootDir.resolve(config.getPaperDir()));
                ConfigHub.ensure(rootDir, rootDir.resolve(config.getPaperDir()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not unify plugins/config hub", e);
            }
        } else {
            try {
                ConfigHub.ensure(rootDir, rootDir.resolve(config.getPaperDir()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not ensure config hub", e);
            }
        }
        this.pluginRuntime = new PluginRuntime(
                pluginManager.getPluginsDir(),
                bukkitServer,
                bukkitServer.bridgedScheduler(),
                config.isPaperAuthority()
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
        this.paperKernel = new PaperKernel(rootDir, config, engine.yapEngine());
        this.paperTickBridge = new PaperTickBridge(engine.parallelGameCore());

        CrashLogger.get().configure(
                rootDir.resolve(config.getLogsDir()).resolve("crashes"),
                this::crashContext
        );
        CrashLogger.get().installGlobalHandlers();
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

    private boolean phase3BridgeOnline() {
        if (paperKernel.isPhase3() && paperKernel.phase3() != null) {
            return paperKernel.phase3().coordinator().isOnline();
        }
        return paperTickBridge.isOnline();
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
            switch (config.getGameAuthority()) {
                case PAPER -> {
                    paperKernel.start();
                    gateway.setProxyToGameKernel(config.isWrappedGameProxy());
                    // Phase 3 same-JVM runtime owns the bridge; only start the
                    // local scaffold when Phase 3 fell back to managed Paper.
                    if (config.isPaperPhase3TickBridge() && !paperKernel.isPhase3()) {
                        paperTickBridge.start();
                    }
                    attachBedrockPaperWorldSync();
                }
                case MOJANG -> {
                    gameKernel.start();
                    gateway.setProxyToGameKernel(true);
                }
                case NATIVE -> gateway.setProxyToGameKernel(false);
            }
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
            gameKernel.stop();
            throw new IOException("Gateway start interrupted", e);
        }
        writePid();
        PublicEndpoint endpoint = publicEndpoint();
        LOG.info("Server '" + config.getServerName() + "' dual-stack online"
                + " | Java TCP :" + config.getPort()
                + " | Bedrock UDP :" + config.effectiveBedrockPort()
                + (config.isSharedListenPort() ? " (shared port)" : "")
                + " | crossplay=" + config.isCrossplayEnabled()
                + " | packs HTTP :" + config.getResourcePackHttpPort()
                + " | max-players=" + config.getMaxPlayers()
                + " | game-authority=" + config.getGameAuthority()
                + " | paper-embed=" + (config.isPaperAuthority() && config.isPaperEmbed())
                + " | phase3=" + (config.isPaperAuthority() && paperKernel.isPhase3())
                + " | phase3-bridge=" + (config.isPaperAuthority() && phase3BridgeOnline())
                + " | wrapped-proxy=" + (config.isWrappedGameProxy()
                    && (paperKernel.isRunning() || gameKernel.isRunning()))
                + " | multi-version=" + ProtocolCompat.isOnline()
                + " | backwards-compat=" + config.isBackwardsCompatible());
        if (config.isPaperAuthority() && paperKernel.isRunning()) {
            if (paperKernel.isPhase3()) {
                LOG.info("Paper Phase 3: same-JVM + leased spatial tick on JE :" + config.getPort()
                        + " | nms=" + (paperKernel.phase3() != null
                            && com.yapcore.paper.phase3.PaperTickBridgeHolder.NMS_TICK_ENABLED)
                        + " | plugins → " + config.getPaperDir() + "/plugins"
                        + " | docs/PAPER_YAPENGINE_PORT.md");
            } else if (config.isPaperEmbed()) {
                LOG.info("Paper Phase 2: owns JE :" + config.getPort()
                        + " | Phase3 bridge scaffold=" + paperTickBridge.isOnline()
                        + " | plugins → " + config.getPaperDir() + "/plugins");
            } else {
                LOG.info("Paper Phase 1 wrap :" + config.getPaperPort());
            }
        } else if (config.isGameKernelEnabled() && gameKernel.isRunning()) {
            LOG.info("JE :" + config.getPort() + " → Mojang kernel :"
                    + config.getGameKernelPort());
        }
        for (String line : endpoint.banner().split("\n")) {
            LOG.info(line);
        }
        pluginRuntime.loadAll();
        moduleRuntime.loadAll();
        LOG.info("Plugins folder: " + pluginManager.getPluginsDir().toAbsolutePath()
                + " (" + pluginManager.listPlugins().size() + " jars on disk)"
                + (config.isPaperAuthority()
                ? " — Paper + YaP share this folder" : ""));
        LOG.info("Modules folder: " + moduleManager.getModulesDir().toAbsolutePath()
                + " (" + moduleManager.listModules().size() + " jars on disk)");
        LOG.info("Resource packs: " + resourcePacks.listPacks().size()
                + " | active=" + resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none"));
        maybeScheduleRanksAutoApply();
    }

    private void maybeScheduleRanksAutoApply() {
        if (!config.isYapRanksAutoApply()) {
            return;
        }
        if (!config.isPaperAuthority() || !paperKernel.isRunning()) {
            LOG.warning("yap-ranks-auto-apply ignored — Paper not running");
            return;
        }
        if (!YapRanks.luckPermsInstalled(pluginManager.getPluginsDir())) {
            LOG.warning("yap-ranks-auto-apply set but LuckPerms jar not found in plugins/ — "
                    + "run scripts/install-luckperms.sh");
            return;
        }
        if (YapRanks.isApplied(rootDir)) {
            LOG.info("YaP ranks pack already applied (config/yap-ranks-applied)");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(8_000L);
                if (!running.get() || !paperKernel.isRunning()) {
                    return;
                }
                var result = YapRanks.apply(rootDir, paperKernel::dispatchConsoleCommand, false);
                LOG.info(result.summary());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "yap-ranks-auto-apply failed", e);
            }
        }, "yap-ranks-auto-apply");
        t.setDaemon(true);
        t.start();
        LOG.info("Scheduled YaP ranks auto-apply in ~8s (LuckPerms detected)");
    }

    /** Apply / inspect the LuckPerms YaP group pack. */
    public String ranksCommand(String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "status";
        try {
            return switch (sub) {
                case "status" -> {
                    boolean lp = YapRanks.luckPermsInstalled(pluginManager.getPluginsDir());
                    boolean applied = YapRanks.isApplied(rootDir);
                    int cmds = YapRanks.loadCommands(rootDir).size();
                    yield "YaP ranks\n"
                            + "  luckperms-jar=" + lp + "\n"
                            + "  pack-applied=" + applied + "\n"
                            + "  pack-commands=" + cmds + "\n"
                            + "  auto-apply=" + config.isYapRanksAutoApply() + "\n"
                            + "  paper-running=" + paperKernel.isRunning() + "\n"
                            + "  install: scripts/install-luckperms.sh\n"
                            + "  apply: ranks apply   (or dashboard Ranks tab)";
                }
                case "apply" -> {
                    boolean force = parts.length > 2 && "force".equalsIgnoreCase(parts[2]);
                    if (!paperKernel.isRunning()) {
                        yield "Paper must be running to apply LuckPerms commands.";
                    }
                    if (!YapRanks.luckPermsInstalled(pluginManager.getPluginsDir())) {
                        yield "LuckPerms not found in plugins/. Run: scripts/install-luckperms.sh";
                    }
                    var result = YapRanks.apply(rootDir, paperKernel::dispatchConsoleCommand, force);
                    yield result.summary();
                }
                case "reset-marker" -> {
                    YapRanks.clearApplied(rootDir);
                    yield "Cleared config/yap-ranks-applied — next ranks apply will run the pack again.";
                }
                case "show" -> String.join("\n", YapRanks.loadCommands(rootDir));
                default -> "Usage: ranks <status|apply [force]|reset-marker|show>";
            };
        } catch (Exception e) {
            return "ranks failed: " + e.getMessage();
        }
    }

    private void attachBedrockPaperWorldSync() {
        if (!config.isPaperAuthority() || !paperKernel.isPhase3() || paperKernel.phase3() == null) {
            return;
        }
        var loader = paperKernel.phase3().paperClassLoader();
        if (loader == null) {
            return;
        }
        var sync = new com.yapcore.crossplay.bedrock.BedrockPaperWorldSync();
        sync.attach(loader);
        gateway.crossplay().attachPaperWorld(sync);
        gateway.bedrockBridge().setPaperWorld(sync);
        LOG.info("Bedrock→Paper world sync online (BREAK/PLACE → Paper main thread; BE spawn mirrors Paper)");
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOG.info("Stopping YaPcore…");
        try {
            pluginRuntime.disableAll();
            moduleRuntime.disableAll();
        } catch (Exception e) {
            CrashLogger.get().dump("plugin-shutdown", e);
        }
        gateway.stop();
        if (!paperKernel.isPhase3()) {
            paperTickBridge.stop();
        }
        paperKernel.stop();
        gameKernel.stop();
        resourcePacks.stopHttp();
        ProtocolCompat.stop();
        engine.stop();
        deletePid();
        LOG.info("YaPcore stopped");
    }

    public String executeCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String line = raw.trim();
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        return switch (cmd) {
            case "help", "?" -> """
                    Commands:
                      help                 Show this help
                      status               Engine / player / memory status
                      say <msg>            Broadcast a message
                      list                 List online player count
                      maxplayers [n]       Get/set max players (saved)
                      plugins              List installed plugins
                      packs                List resource / texture packs
                      setpack <file>       Set active pack (seamless client DL)
                      clearpack            Clear active pack
                      joinjava <name> [proto]   Simulate Java client join
                      joinbedrock <name> [proto] Simulate Bedrock client join
                      crashdump [reason]   Write full diagnostic crash report
                      ranks [status|apply] LuckPerms YaP group pack (vip/mod/admin)
                      stop / end           Graceful shutdown
                      demo                 Run store-purchase lifecycle demo

                      Minecraft / Paper / plugin commands (when Paper is running):
                      Type them here — e.g. give @p diamond 1, tp Steve 0 64 0,
                      gamemode creative @a, op YourName, difficulty hard
                      Leading / is optional. In-game vanilla commands need OP:
                      set ops=YourName in config, or run: op YourName
                    """ + PublicityCommands.helpLines();
            case "status" -> statusReport();
            case "ranks", "yapranks" -> ranksCommand(parts);
            case "say" -> {
                String msg = line.length() > 4 ? line.substring(4).trim() : "";
                bukkitServer.broadcastMessage(msg);
                yield "Broadcast: " + msg;
            }
            case "list" -> "Players: " + onlinePlayers.get() + " / " + config.getMaxPlayers();
            case "maxplayers" -> {
                if (parts.length == 1) {
                    yield "max-players=" + config.getMaxPlayers();
                }
                try {
                    int n = Integer.parseInt(parts[1]);
                    config.setMaxPlayers(n);
                    config.save();
                    engine.setMaxPlayers(config.getMaxPlayers());
                    yield "max-players set to " + config.getMaxPlayers() + " (saved)";
                } catch (Exception e) {
                    yield "Usage: maxplayers <number>";
                }
            }
            case "plugins" -> {
                var list = pluginManager.listPlugins();
                int legacy = bukkitServer.getPluginManager().getPlugins().length;
                int yap = pluginRuntime.getYaPPlugins().size();
                if (list.isEmpty()) {
                    yield "No plugins installed in " + pluginManager.getPluginsDir();
                }
                StringBuilder sb = new StringBuilder("Plugins on disk (" + list.size() + "), loaded legacy="
                        + legacy + " yap=" + yap + ":\n");
                for (var p : list) {
                    sb.append("  - ").append(p.fileName()).append(" (").append(p.sizeLabel()).append(")\n");
                }
                yield sb.toString().trim();
            }
            case "packs" -> {
                var packs = resourcePacks.listPacks();
                String active = resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none");
                if (packs.isEmpty()) {
                    yield "No packs in " + resourcePacks.getPacksDir() + " (active=" + active + ")";
                }
                StringBuilder sb = new StringBuilder("Resource packs (active=" + active + "):\n");
                for (var p : packs) {
                    sb.append("  - ").append(p.getFileName())
                            .append(" sha1=").append(p.getSha1Hex().substring(0, 8))
                            .append("… (").append(p.sizeLabel()).append(")\n");
                }
                yield sb.toString().trim();
            }
            case "setpack" -> {
                if (parts.length < 2) {
                    yield "Usage: setpack <filename.zip|filename.mcpack>";
                }
                try {
                    resourcePacks.setActivePack(parts[1]);
                    yield "Active pack set to " + parts[1]
                            + " → " + resourcePacks.buildPublicUrl(parts[1]);
                } catch (Exception e) {
                    yield "setpack failed: " + e.getMessage();
                }
            }
            case "clearpack" -> {
                try {
                    resourcePacks.setActivePack("");
                    yield "Active pack cleared";
                } catch (Exception e) {
                    yield "clearpack failed: " + e.getMessage();
                }
            }
            case "joinjava" -> {
                String name = parts.length > 1 ? parts[1] : "Steve";
                int proto = parts.length > 2 ? Integer.parseInt(parts[2])
                        : gateway.getProtocols().recommended(ClientEdition.JAVA).protocolId();
                boolean ok = tryJoinEdition(name, ClientEdition.JAVA, proto);
                yield ok ? "Java join OK: " + name : "Java join denied: " + name;
            }
            case "joinbedrock" -> {
                String name = parts.length > 1 ? parts[1] : "Alex";
                int proto = parts.length > 2 ? Integer.parseInt(parts[2])
                        : gateway.getProtocols().recommended(ClientEdition.BEDROCK).protocolId();
                boolean ok = tryJoinEdition(name, ClientEdition.BEDROCK, proto);
                yield ok ? "Bedrock join OK: " + name : "Bedrock join denied: " + name;
            }
            case "crashdump", "crash" -> {
                String reason = parts.length > 1 ? line.substring(cmd.length()).trim() : "manual";
                Path file = CrashLogger.get().dump("manual-" + reason, null, Map.of("reason", reason));
                yield file == null ? "Failed to write crash dump" : "Crash dump written: " + file;
            }
            case "demo" -> {
                try {
                    engine.runLifecycleDemo();
                    yield "Demo finished";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    yield "Demo interrupted";
                }
            }
            case "stop", "end", "shutdown" -> {
                LOG.info("Stop requested via console");
                // Non-daemon so shutdown can finish even if the main loop exits first
                Thread t = new Thread(this::stop, "yap-console-stop");
                t.start();
                yield "Stopping…";
            }
            default -> {
                var publicity = PublicityCommands.tryHandle(cmd, parts, line, config, resourcePacks);
                if (publicity.isPresent()) {
                    yield publicity.get();
                }
                if (config.isPaperAuthority() && paperKernel.isRunning()) {
                    yield paperKernel.dispatchConsoleCommand(line);
                }
                yield "Unknown command: " + cmd + " (type 'help')";
            }
        };
    }

    public String statusReport() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        return "YaPcore status\n"
                + "  running=" + running.get() + "\n"
                + "  ticks=" + engine.gameCore().getTickCounter() + "\n"
                + "  players=" + onlinePlayers.get() + "/" + config.getMaxPlayers()
                + " (java=" + gateway.getClients().countEdition(ClientEdition.JAVA)
                + " bedrock=" + gateway.getClients().countEdition(ClientEdition.BEDROCK) + ")\n"
                + "  ports=java-tcp:" + config.getPort()
                + " bedrock-udp:" + config.effectiveBedrockPort()
                + (config.isSharedListenPort() ? " (shared)" : "")
                + " packs-http:" + config.getResourcePackHttpPort() + "\n"
                + "  crossplay=" + config.isCrossplayEnabled()
                + " shared-world=" + gateway.crossplay().size() + "\n"
                + "  join=" + publicEndpoint().crossplayJoinAddress()
                + " exposed=" + config.isInternetExposed() + "\n"
                + "  backwards-compat=" + config.isBackwardsCompatible() + "\n"
                + "  active-pack=" + resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none") + "\n"
                + "  heap=" + usedMb + "MB / " + maxMb + "MB (config Xmx=" + config.getRamMb() + "MB)\n"
                + "  plugins-disk=" + pluginManager.listPlugins().size()
                + " legacy-loaded=" + bukkitServer.getPluginManager().getPlugins().length
                + " yap-loaded=" + pluginRuntime.getYaPPlugins().size();
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
        LOG.info("Reloaded limits: max-players=" + config.getMaxPlayers()
                + " (RAM changes require restart)");
    }

    private Map<String, String> crashContext() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("server-name", config.getServerName());
        map.put("port", Integer.toString(config.getPort()));
        map.put("bedrock-port", Integer.toString(config.getBedrockPort()));
        map.put("public-join", publicEndpoint().javaJoinAddress());
        map.put("internet-exposed", Boolean.toString(config.isInternetExposed()));
        map.put("server-domain", config.getServerDomain());
        map.put("backwards-compatible", Boolean.toString(config.isBackwardsCompatible()));
        map.put("velocity-enabled", Boolean.toString(config.isVelocityEnabled()));
        map.put("multi-version", Boolean.toString(ProtocolCompat.isOnline()));
        map.put("active-pack", resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none"));
        map.put("java-clients", Long.toString(gateway.getClients().countEdition(ClientEdition.JAVA)));
        map.put("bedrock-clients", Long.toString(gateway.getClients().countEdition(ClientEdition.BEDROCK)));
        map.put("max-players", Integer.toString(config.getMaxPlayers()));
        map.put("ram-mb", Integer.toString(config.getRamMb()));
        map.put("online-players", Integer.toString(onlinePlayers.get()));
        map.put("ticks", Long.toString(engine.gameCore().getTickCounter()));
        map.put("bridge-pending", Integer.toString(engine.bridge().pendingCount()));
        map.put("bridge-submitted", Long.toString(engine.bridge().getSubmitted()));
        map.put("bridge-drained", Long.toString(engine.bridge().getDrained()));
        map.put("plugins-disk", pluginManager.listPlugins().stream()
                .map(PluginManager.PluginInfo::fileName)
                .collect(Collectors.joining(", ")));
        map.put("legacy-plugins", java.util.Arrays.stream(bukkitServer.getPluginManager().getPlugins())
                .map(org.bukkit.plugin.Plugin::getName)
                .collect(Collectors.joining(", ")));
        map.put("yap-plugins", pluginRuntime.getYaPPlugins().stream()
                .map(com.yapcore.api.YaPPlugin::getName)
                .collect(Collectors.joining(", ")));
        map.put("yap-modules", moduleRuntime.getModules().stream()
                .map(com.yapcore.api.module.YaPModule::getName)
                .collect(Collectors.joining(", ")));
        map.put("modules-disk", moduleManager.listModules().stream()
                .map(ModuleManager.ModuleInfo::fileName)
                .collect(Collectors.joining(", ")));
        map.put("running", Boolean.toString(running.get()));
        return map;
    }

    private void writePid() throws IOException {
        long pid = ProcessHandle.current().pid();
        Files.writeString(pidFile, Long.toString(pid), StandardCharsets.UTF_8);
    }

    private void deletePid() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
        }
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
