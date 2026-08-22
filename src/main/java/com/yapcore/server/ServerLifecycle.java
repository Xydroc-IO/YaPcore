package com.yapcore.server;

import com.yapcore.client.ClientEdition;
import com.yapcore.config.ServerConfig;
import com.yapcore.crash.CrashLogger;
import com.yapcore.module.ModuleManager;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.plugin.PluginManager;
import com.yapcore.plugin.loader.PluginRuntime;
import com.yapcore.module.ModuleRuntime;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.paper.PaperKernel;
import com.yapcore.folia.FoliaKernel;
import com.yapcore.kernel.GameKernel;
import com.yapcore.YaPcoreEngine;
import com.yapcore.resourcepack.ResourcePackManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * PID file, crash context, status report, and startup banner logging.
 */
final class ServerLifecycle {

    private static final Logger LOG = Logger.getLogger("YaPcore.Server");

    private ServerLifecycle() {
    }

    static void writePid(Path pidFile) throws IOException {
        long pid = ProcessHandle.current().pid();
        Files.writeString(pidFile, Long.toString(pid), StandardCharsets.UTF_8);
    }

    static void deletePid(Path pidFile) {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
        }
    }

    static Map<String, String> crashContext(YaPcoreServer server) {
        ServerConfig config = server.getConfig();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("server-name", config.getServerName());
        map.put("port", Integer.toString(config.getPort()));
        map.put("bedrock-port", Integer.toString(config.getBedrockPort()));
        map.put("public-join", server.publicEndpoint().javaJoinAddress());
        map.put("internet-exposed", Boolean.toString(config.isInternetExposed()));
        map.put("server-domain", config.getServerDomain());
        map.put("backwards-compatible", Boolean.toString(config.isBackwardsCompatible()));
        map.put("velocity-enabled", Boolean.toString(config.isVelocityEnabled()));
        map.put("multi-version", Boolean.toString(ProtocolCompat.isOnline()));
        map.put("active-pack", server.getResourcePacks().getActivePack().map(p -> p.getFileName()).orElse("none"));
        DualStackGateway gateway = server.getGateway();
        map.put("java-clients", Long.toString(gateway.getClients().countEdition(ClientEdition.JAVA)));
        map.put("bedrock-clients", Long.toString(gateway.getClients().countEdition(ClientEdition.BEDROCK)));
        map.put("max-players", Integer.toString(config.getMaxPlayers()));
        map.put("ram-mb", Integer.toString(config.getRamMb()));
        map.put("online-players", Integer.toString(server.getOnlinePlayers()));
        YaPcoreEngine engine = server.getEngine();
        map.put("ticks", Long.toString(engine.gameCore().getTickCounter()));
        map.put("bridge-pending", Integer.toString(engine.bridge().pendingCount()));
        map.put("bridge-submitted", Long.toString(engine.bridge().getSubmitted()));
        map.put("bridge-drained", Long.toString(engine.bridge().getDrained()));
        PluginManager pluginManager = server.getPluginManager();
        map.put("plugins-disk", pluginManager.listPlugins().stream()
                .map(PluginManager.PluginInfo::fileName)
                .collect(Collectors.joining(", ")));
        map.put("legacy-plugins", java.util.Arrays.stream(server.getBukkitServer().getPluginManager().getPlugins())
                .map(org.bukkit.plugin.Plugin::getName)
                .collect(Collectors.joining(", ")));
        PluginRuntime pluginRuntime = server.getPluginRuntime();
        map.put("yap-plugins", pluginRuntime.getYaPPlugins().stream()
                .map(com.yapcore.api.YaPPlugin::getName)
                .collect(Collectors.joining(", ")));
        ModuleRuntime moduleRuntime = server.getModuleRuntime();
        map.put("yap-modules", moduleRuntime.getModules().stream()
                .map(com.yapcore.api.module.YaPModule::getName)
                .collect(Collectors.joining(", ")));
        map.put("modules-disk", server.getModuleManager().listModules().stream()
                .map(ModuleManager.ModuleInfo::fileName)
                .collect(Collectors.joining(", ")));
        map.put("running", Boolean.toString(server.isRunning()));
        return map;
    }

    static String statusReport(YaPcoreServer server) {
        ServerConfig config = server.getConfig();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        DualStackGateway gateway = server.getGateway();
        ResourcePackManager resourcePacks = server.getResourcePacks();
        PluginManager pluginManager = server.getPluginManager();
        PluginRuntime pluginRuntime = server.getPluginRuntime();
        YaPcoreEngine engine = server.getEngine();
        return "YaPcore status\n"
                + "  running=" + server.isRunning() + "\n"
                + "  ticks=" + engine.gameCore().getTickCounter() + "\n"
                + "  players=" + server.getOnlinePlayers() + "/" + config.getMaxPlayers()
                + " (java=" + gateway.getClients().countEdition(ClientEdition.JAVA)
                + " bedrock=" + gateway.getClients().countEdition(ClientEdition.BEDROCK) + ")\n"
                + "  ports=java-tcp:" + config.getPort()
                + " bedrock-udp:" + config.effectiveBedrockPort()
                + (config.isSharedListenPort() ? " (shared)" : "")
                + " packs-http:" + config.getResourcePackHttpPort() + "\n"
                + "  crossplay=" + config.isCrossplayEnabled()
                + " shared-world=" + gateway.crossplay().size() + "\n"
                + "  join=" + server.publicEndpoint().crossplayJoinAddress()
                + " exposed=" + config.isInternetExposed() + "\n"
                + "  backwards-compat=" + config.isBackwardsCompatible() + "\n"
                + "  active-pack=" + resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none") + "\n"
                + "  heap=" + usedMb + "MB / " + maxMb + "MB (config Xmx=" + config.getRamMb() + "MB)\n"
                + "  plugins-disk=" + pluginManager.listPlugins().size()
                + " legacy-loaded=" + server.getBukkitServer().getPluginManager().getPlugins().length
                + " yap-loaded=" + pluginRuntime.getYaPPlugins().size();
    }

    static void logStartupBanner(YaPcoreServer server) {
        ServerConfig config = server.getConfig();
        FoliaKernel foliaKernel = server.foliaKernel();
        PaperKernel paperKernel = server.paperKernel();
        GameKernel gameKernel = server.gameKernel();
        PublicEndpoint endpoint = server.publicEndpoint();
        LOG.info("Server '" + config.getServerName() + "' dual-stack online"
                + " | Java TCP :" + config.getPort()
                + " | Bedrock UDP :" + config.effectiveBedrockPort()
                + (config.isSharedListenPort() ? " (shared port)" : "")
                + " | crossplay=" + config.isCrossplayEnabled()
                + " | packs HTTP :" + config.getResourcePackHttpPort()
                + " | max-players=" + config.getMaxPlayers()
                + " | game-authority=" + config.getGameAuthority()
                + " | folia-embed=" + (config.isFoliaAuthority() && config.isFoliaEmbed())
                + " | paper-embed=" + (config.isPaperAuthority() && config.isPaperEmbed())
                + " | wrapped-proxy=" + (config.isWrappedGameProxy()
                    && (foliaKernel.isRunning() || paperKernel.isRunning() || gameKernel.isRunning()))
                + " | multi-version=" + ProtocolCompat.isOnline()
                + " | backwards-compat=" + config.isBackwardsCompatible());
        if (config.isFoliaAuthority() && foliaKernel.isRunning()) {
            if (config.isFoliaEmbed()) {
                LOG.info("Folia: owns JE :" + config.foliaListenPort()
                        + " | plugins → " + config.getFoliaDir() + "/plugins");
            } else {
                LOG.info("Folia wrap :" + config.getFoliaPort());
            }
        } else if (config.isPaperAuthority() && paperKernel.isRunning()) {
            if (config.isPaperEmbed()) {
                LOG.info("Paper Phase 2: owns JE :" + config.getPort()
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
        PluginManager pluginManager = server.getPluginManager();
        ModuleManager moduleManager = server.getModuleManager();
        ResourcePackManager resourcePacks = server.getResourcePacks();
        LOG.info("Plugins folder: " + pluginManager.getPluginsDir().toAbsolutePath()
                + " (" + pluginManager.listPlugins().size() + " jars on disk)"
                + (config.isFoliaAuthority()
                ? " — Folia + YaP share this folder"
                : config.isPaperAuthority()
                ? " — Paper + YaP share this folder" : ""));
        LOG.info("Modules folder: " + moduleManager.getModulesDir().toAbsolutePath()
                + " (" + moduleManager.listModules().size() + " jars on disk)");
        LOG.info("Resource packs: " + resourcePacks.listPacks().size()
                + " | active=" + resourcePacks.getActivePack().map(p -> p.getFileName()).orElse("none"));
    }
}
