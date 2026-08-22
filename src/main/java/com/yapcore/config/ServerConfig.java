package com.yapcore.config;

import com.yapcore.config.authority.FoliaAuthorityConfig;
import com.yapcore.config.authority.GameAuthorityConfig;
import com.yapcore.config.authority.PaperAuthorityConfig;
import com.yapcore.config.core.CoreServerConfig;
import com.yapcore.config.kernel.GameKernelConfig;
import com.yapcore.config.network.PublicEndpointConfig;
import com.yapcore.config.plugin.PluginCompatConfig;
import com.yapcore.config.protocol.ProtocolEdgeConfig;
import com.yapcore.config.proxy.VelocityProxyConfig;
import com.yapcore.config.resource.ResourcePackConfig;
import com.yapcore.config.web.WebDashboardConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Persistent server settings: RAM, player caps, network, paths.
 */
public final class ServerConfig {

    public static final Path DEFAULT_DIR = Path.of("config");
    public static final Path DEFAULT_FILE = DEFAULT_DIR.resolve("server.properties");

    private final Path file;
    private final Properties props = new Properties();
    private final CopyOnWriteArrayList<Consumer<ServerConfig>> listeners = new CopyOnWriteArrayList<>();

    private final CoreServerConfig core;
    private final GameAuthorityConfig authority;
    private final FoliaAuthorityConfig folia;
    private final PaperAuthorityConfig paper;
    private final GameKernelConfig gameKernel;
    private final VelocityProxyConfig velocity;
    private final ProtocolEdgeConfig protocol;
    private final PublicEndpointConfig publicEndpoint;
    private final PluginCompatConfig pluginCompat;
    private final ResourcePackConfig resourcePack;
    private final WebDashboardConfig webDashboard;

    public ServerConfig(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.core = new CoreServerConfig(props);
        this.authority = new GameAuthorityConfig(props);
        this.folia = new FoliaAuthorityConfig(this, props);
        this.paper = new PaperAuthorityConfig(this, props);
        this.gameKernel = new GameKernelConfig(this, props);
        this.velocity = new VelocityProxyConfig(props);
        this.protocol = new ProtocolEdgeConfig(this, props);
        this.publicEndpoint = new PublicEndpointConfig(props);
        this.pluginCompat = new PluginCompatConfig(props);
        this.resourcePack = new ResourcePackConfig(props);
        this.webDashboard = new WebDashboardConfig(props);
    }

    public static ServerConfig loadOrCreate(Path file) throws IOException {
        ServerConfig cfg = new ServerConfig(file);
        if (Files.exists(file)) {
            cfg.load();
        } else {
            cfg.applyDefaults();
            cfg.save();
        }
        return cfg;
    }

    public void addListener(Consumer<ServerConfig> listener) {
        listeners.add(listener);
    }

    public Path getFile() {
        return file;
    }

    public void load() throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            props.clear();
            props.load(in);
        }
        applyMissingDefaults();
        fireChanged();
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaPcore server configuration");
        }
        fireChanged();
    }

    private void fireChanged() {
        for (Consumer<ServerConfig> listener : listeners) {
            listener.accept(this);
        }
    }

    private void applyDefaults() {
        CoreServerConfig.applyDefaults(props);
        GameAuthorityConfig.applyDefaults(props);
        FoliaAuthorityConfig.applyDefaults(props);
        PaperAuthorityConfig.applyDefaults(props);
        GameKernelConfig.applyDefaults(props);
        VelocityProxyConfig.applyDefaults(props);
        ProtocolEdgeConfig.applyDefaults(props);
        PublicEndpointConfig.applyDefaults(props);
        PluginCompatConfig.applyDefaults(props);
        ResourcePackConfig.applyDefaults(props);
        WebDashboardConfig.applyDefaults(props);
    }

    private void applyMissingDefaults() {
        Properties defaults = new Properties();
        ServerConfig tmp = new ServerConfig(file);
        tmp.applyDefaults();
        defaults.putAll(tmp.props);
        for (String key : defaults.stringPropertyNames()) {
            if (!props.containsKey(key)) {
                props.setProperty(key, defaults.getProperty(key));
            }
        }
    }

    public String getServerName() { return core.getServerName(); }
    public void setServerName(String value) { core.setServerName(value); }
    public String getBindHost() { return core.getBindHost(); }
    public void setBindHost(String host) { core.setBindHost(host); }
    public int getPort() { return core.getPort(); }
    public void setPort(int port) { core.setPort(port); }
    public int getMaxPlayers() { return core.getMaxPlayers(); }
    public void setMaxPlayers(int max) { core.setMaxPlayers(max); }
    public int getRamMb() { return core.getRamMb(); }
    public void setRamMb(int mb) { core.setRamMb(mb); }
    public int getRamMinMb() { return core.getRamMinMb(); }
    public void setRamMinMb(int mb) { core.setRamMinMb(mb); }
    public int getViewDistance() { return core.getViewDistance(); }
    public void setViewDistance(int chunks) { core.setViewDistance(chunks); }
    public String getMotd() { return core.getMotd(); }
    public void setMotd(String motd) { core.setMotd(motd); }
    public Path getPluginsDir() { return core.getPluginsDir(); }
    public Path getModulesDir() { return core.getModulesDir(); }
    public Path getLogsDir() { return core.getLogsDir(); }
    public boolean isOnlineMode() { return core.isOnlineMode(); }
    public void setOnlineMode(boolean online) { core.setOnlineMode(online); }
    public boolean isAutoOp() { return core.isAutoOp(); }
    public List<String> getOps() { return core.getOps(); }
    public boolean isBackwardsCompatible() { return core.isBackwardsCompatible(); }
    public void setBackwardsCompatible(boolean enabled) { core.setBackwardsCompatible(enabled); }
    public boolean isGuiEnabled() { return core.isGuiEnabled(); }
    public void setGuiEnabled(boolean enabled) { core.setGuiEnabled(enabled); }

    public boolean isInternetExposed() { return publicEndpoint.isInternetExposed(); }
    public void setInternetExposed(boolean exposed) { publicEndpoint.setInternetExposed(exposed); }
    public String getPublicHost() { return publicEndpoint.getPublicHost(); }
    public void setPublicHost(String host) { publicEndpoint.setPublicHost(host); }
    public String getServerDomain() { return publicEndpoint.getServerDomain(); }
    public void setServerDomain(String domain) { publicEndpoint.setServerDomain(domain); }
    public int getPublicPort() { return publicEndpoint.getPublicPort(); }
    public void setPublicPort(int port) { publicEndpoint.setPublicPort(port); }
    public int getPublicBedrockPort() { return publicEndpoint.getPublicBedrockPort(); }
    public void setPublicBedrockPort(int port) { publicEndpoint.setPublicBedrockPort(port); }
    public int getPublicPackPort() { return publicEndpoint.getPublicPackPort(); }
    public void setPublicPackPort(int port) { publicEndpoint.setPublicPackPort(port); }
    public boolean isSrvEnabled() { return publicEndpoint.isSrvEnabled(); }
    public void setSrvEnabled(boolean enabled) { publicEndpoint.setSrvEnabled(enabled); }
    public int getSrvPriority() { return publicEndpoint.getSrvPriority(); }
    public int getSrvWeight() { return publicEndpoint.getSrvWeight(); }
    public int getNginxPublicPort() { return publicEndpoint.getNginxPublicPort(); }
    public void setNginxPublicPort(int port) { publicEndpoint.setNginxPublicPort(port); }
    public int getNginxPackPort() { return publicEndpoint.getNginxPackPort(); }
    public void setNginxPackPort(int port) { publicEndpoint.setNginxPackPort(port); }
    public String getNginxDomain() { return publicEndpoint.getNginxDomain(); }
    public void setNginxDomain(String domain) { publicEndpoint.setNginxDomain(domain); }

    public boolean isVelocityEnabled() { return velocity.isVelocityEnabled(); }
    public void setVelocityEnabled(boolean enabled) { velocity.setVelocityEnabled(enabled); }
    public String getVelocitySecret() { return velocity.getVelocitySecret(); }
    public void setVelocitySecret(String secret) { velocity.setVelocitySecret(secret); }
    public String getVelocitySecretFile() { return velocity.getVelocitySecretFile(); }
    public void setVelocitySecretFile(String path) { velocity.setVelocitySecretFile(path); }
    public boolean isVelocityOnlineMode() { return velocity.isVelocityOnlineMode(); }
    public void setVelocityOnlineMode(boolean online) { velocity.setVelocityOnlineMode(online); }
    public boolean isVelocityBindLocalhost() { return velocity.isVelocityBindLocalhost(); }
    public void setVelocityBindLocalhost(boolean localhostOnly) { velocity.setVelocityBindLocalhost(localhostOnly); }
    public boolean isLinkEmbed() { return velocity.isLinkEmbed(); }
    public void setLinkEmbed(boolean embed) { velocity.setLinkEmbed(embed); }
    public String getLinkEmbedHome() { return velocity.getLinkEmbedHome(); }
    public void setLinkEmbedHome(String path) { velocity.setLinkEmbedHome(path); }

    public boolean isJavaEnabled() { return protocol.isJavaEnabled(); }
    public void setJavaEnabled(boolean enabled) { protocol.setJavaEnabled(enabled); }
    public boolean isBedrockEnabled() { return protocol.isBedrockEnabled(); }
    public void setBedrockEnabled(boolean enabled) { protocol.setBedrockEnabled(enabled); }
    public int getBedrockPort() { return protocol.getBedrockPort(); }
    public void setBedrockPort(int port) { protocol.setBedrockPort(port); }
    public boolean isSharedListenPort() { return protocol.isSharedListenPort(); }
    public void setSharedListenPort(boolean shared) { protocol.setSharedListenPort(shared); }
    public int effectiveBedrockPort() { return protocol.effectiveBedrockPort(); }
    public boolean isCrossplayEnabled() { return protocol.isCrossplayEnabled(); }
    public void setCrossplayEnabled(boolean enabled) { protocol.setCrossplayEnabled(enabled); }
    public boolean isAllowLocalhost() { return protocol.isAllowLocalhost(); }
    public void setAllowLocalhost(boolean allow) { protocol.setAllowLocalhost(allow); }
    public boolean isProtocolViaEnabled() { return protocol.isProtocolViaEnabled(); }
    public void setProtocolViaEnabled(boolean enabled) { protocol.setProtocolViaEnabled(enabled); }
    public boolean isProtocolGeyserEnabled() { return protocol.isProtocolGeyserEnabled(); }
    public void setProtocolGeyserEnabled(boolean enabled) { protocol.setProtocolGeyserEnabled(enabled); }

    public boolean isPluginCompatEnabled() { return pluginCompat.isPluginCompatEnabled(); }
    public void setPluginCompatEnabled(boolean enabled) { pluginCompat.setPluginCompatEnabled(enabled); }
    public boolean isPluginCompatRewrite() { return pluginCompat.isPluginCompatRewrite(); }
    public void setPluginCompatRewrite(boolean enabled) { pluginCompat.setPluginCompatRewrite(enabled); }
    public boolean isPluginCompatBackup() { return pluginCompat.isPluginCompatBackup(); }
    public void setPluginCompatBackup(boolean enabled) { pluginCompat.setPluginCompatBackup(enabled); }

    public boolean isResourcePackEnabled() { return resourcePack.isResourcePackEnabled(); }
    public void setResourcePackEnabled(boolean enabled) { resourcePack.setResourcePackEnabled(enabled); }
    public Path getResourcePackDir() { return resourcePack.getResourcePackDir(); }
    public String getResourcePackFile() { return resourcePack.getResourcePackFile(); }
    public void setResourcePackFile(String fileName) { resourcePack.setResourcePackFile(fileName); }
    public List<String> getResourcePackFiles() { return resourcePack.getResourcePackFiles(); }
    public void setResourcePackFiles(List<String> fileNames) { resourcePack.setResourcePackFiles(fileNames); }
    public int getResourcePackHttpPort() { return resourcePack.getResourcePackHttpPort(); }
    public void setResourcePackHttpPort(int port) { resourcePack.setResourcePackHttpPort(port); }
    public String getResourcePackPublicHost() { return resourcePack.getResourcePackPublicHost(); }
    public void setResourcePackPublicHost(String host) { resourcePack.setResourcePackPublicHost(host); }
    public String getResourcePackUrl() { return resourcePack.getResourcePackUrl(); }
    public void setResourcePackUrl(String url) { resourcePack.setResourcePackUrl(url); }
    public boolean isResourcePackForced() { return resourcePack.isResourcePackForced(); }
    public void setResourcePackForced(boolean forced) { resourcePack.setResourcePackForced(forced); }
    public String getResourcePackPrompt() { return resourcePack.getResourcePackPrompt(); }
    public void setResourcePackPrompt(String prompt) { resourcePack.setResourcePackPrompt(prompt); }

    public boolean isWebDashboardEnabled() { return webDashboard.isWebDashboardEnabled(); }
    public void setWebDashboardEnabled(boolean enabled) { webDashboard.setWebDashboardEnabled(enabled); }
    public int getWebDashboardPort() { return webDashboard.getWebDashboardPort(); }
    public void setWebDashboardPort(int port) { webDashboard.setWebDashboardPort(port); }
    public String getWebDashboardBind() { return webDashboard.getWebDashboardBind(); }
    public void setWebDashboardBind(String bind) { webDashboard.setWebDashboardBind(bind); }
    public String getWebDashboardToken() { return webDashboard.getWebDashboardToken(); }
    public void setWebDashboardToken(String token) { webDashboard.setWebDashboardToken(token); }
    public boolean isWebDashboardLocalhostOnly() { return webDashboard.isWebDashboardLocalhostOnly(); }
    public void setWebDashboardLocalhostOnly(boolean localhostOnly) { webDashboard.setWebDashboardLocalhostOnly(localhostOnly); }

    public GameAuthority getGameAuthority() { return authority.getGameAuthority(); }
    public void setGameAuthority(GameAuthority value) { authority.setGameAuthority(value); }
    public boolean isFoliaAuthority() { return folia.isFoliaAuthority(); }
    public boolean isPaperAuthority() { return paper.isPaperAuthority(); }
    public boolean isNativeAuthority() { return authority.isNativeAuthority(); }
    public boolean isYapRanksAutoApply() { return authority.isYapRanksAutoApply(); }
    public void setYapRanksAutoApply(boolean enabled) { authority.setYapRanksAutoApply(enabled); }

    public boolean isFoliaEmbed() { return folia.isFoliaEmbed(); }
    public void setFoliaEmbed(boolean embed) { folia.setFoliaEmbed(embed); }
    public String getFoliaDir() { return folia.getFoliaDir(); }
    public int getFoliaPort() { return folia.getFoliaPort(); }
    public String getFoliaVersion() { return folia.getFoliaVersion(); }
    public String getFoliaJarUrl() { return folia.getFoliaJarUrl(); }
    public int getFoliaReadyTimeoutSec() { return folia.getFoliaReadyTimeoutSec(); }
    public int foliaListenPort() { return folia.foliaListenPort(); }

    public boolean isPaperEmbed() { return paper.isPaperEmbed(); }
    public void setPaperEmbed(boolean embed) { paper.setPaperEmbed(embed); }
    public boolean isPaperSameJvm() { return paper.isPaperSameJvm(); }
    public int paperListenPort() { return paper.paperListenPort(); }
    public String getPaperDir() { return paper.getPaperDir(); }
    public int getPaperPort() { return paper.getPaperPort(); }
    public String getPaperVersion() { return paper.getPaperVersion(); }
    public String getPaperJarUrl() { return paper.getPaperJarUrl(); }
    public int getPaperReadyTimeoutSec() { return paper.getPaperReadyTimeoutSec(); }

    public boolean isGameKernelEnabled() { return gameKernel.isGameKernelEnabled(); }
    public void setGameKernelEnabled(boolean enabled) { gameKernel.setGameKernelEnabled(enabled); }
    public String getGameKernelDir() { return gameKernel.getGameKernelDir(); }
    public int getGameKernelPort() { return gameKernel.getGameKernelPort(); }
    public String getGameKernelVersion() { return gameKernel.getGameKernelVersion(); }
    public String getGameKernelJarUrl() { return gameKernel.getGameKernelJarUrl(); }
    public int getGameKernelReadyTimeoutSec() { return gameKernel.getGameKernelReadyTimeoutSec(); }

    public int getWrappedGamePort() {
        return switch (getGameAuthority()) {
            case FOLIA -> getFoliaPort();
            case PAPER -> getPaperPort();
            case MOJANG -> getGameKernelPort();
            case NATIVE -> getPort();
        };
    }

    public boolean isWrappedGameProxy() {
        return switch (getGameAuthority()) {
            case MOJANG -> true;
            case FOLIA -> folia.isWrappedGameProxy();
            case PAPER -> paper.isWrappedGameProxy();
            case NATIVE -> false;
        };
    }

    public boolean isYaPcoreJavaListener() {
        return switch (getGameAuthority()) {
            case NATIVE, MOJANG -> true;
            case FOLIA -> folia.isYaPcoreJavaListener();
            case PAPER -> paper.isYaPcoreJavaListener();
        };
    }

    public String get(String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }
}
