package com.yapcore.protocol;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientRegistry;
import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.crossplay.CrossplayHub;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.BedrockGameplayBridge;
import com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.raknet.RakNetSessionManager;
import com.yapcore.crossplay.skin.SkinService;
import com.yapcore.model.GameEvent;
import com.yapcore.network.TrafficCop;
import com.yapcore.protocol.gateway.BedrockUdpBoot;
import com.yapcore.protocol.gateway.JavaListenerBoot;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.resourcepack.ResourcePackOffer;
import com.yapcore.util.ThreadMetrics;
import com.yaplabs.yapengine.network.traffic.NativeEventLoops;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Dual-stack gateway: Java TCP + Bedrock UDP on a streamlined shared port
 * (when enabled), with Geyser-class crossplay into one world.
 */
public final class DualStackGateway {

    private static final Logger LOG = Logger.getLogger("YaPcore.Gateway");

    private final ServerConfig config;
    private final TrafficCop trafficCop;
    private final ClientRegistry clients;
    private final ProtocolVersionRegistry protocols;
    private final ResourcePackManager packs;
    private final CrossplayHub crossplay;
    private final BedrockSessionManager bedrockSessions = new BedrockSessionManager();
    private final FloodgateAuth floodgateAuth = new FloodgateAuth();
    private final SkinService skinService = new SkinService();
    private final FormService formService = new FormService();
    private final BedrockGameplayBridge bedrockBridge;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean proxyToGameKernel;
    private volatile RakNetSessionManager rakNetSessions;

    private NativeEventLoops.Transport javaTransport;
    private EventLoopGroup bedrockGroup;
    private Channel javaChannel;
    private Channel bedrockChannel;

    public DualStackGateway(ServerConfig config,
                            TrafficCop trafficCop,
                            ClientRegistry clients,
                            ProtocolVersionRegistry protocols,
                            ResourcePackManager packs,
                            CrossplayHub crossplay) {
        this.config = config;
        this.trafficCop = trafficCop;
        this.clients = clients;
        this.protocols = protocols;
        this.packs = packs;
        this.crossplay = crossplay;
        this.bedrockBridge = new BedrockGameplayBridge(
                bedrockSessions, floodgateAuth, skinService, formService);
        this.bedrockBridge.setResourcePackOfferSupplier(() -> {
            if (!config.isResourcePackEnabled()) {
                return Optional.empty();
            }
            return packs.createOffer(null);
        });
        if (crossplay != null) {
            crossplay.attachFloodgate(floodgateAuth, skinService, formService);
        }
    }

    public ServerConfig config() {
        return config;
    }

    public TrafficCop trafficCop() {
        return trafficCop;
    }

    public FloodgateAuth floodgateAuth() {
        return floodgateAuth;
    }

    public SkinService skinService() {
        return skinService;
    }

    public FormService formService() {
        return formService;
    }

    public BedrockGameplayBridge bedrockBridge() {
        return bedrockBridge;
    }

    public ClientRegistry getClients() {
        return clients;
    }

    public ProtocolVersionRegistry getProtocols() {
        return protocols;
    }

    public CrossplayHub crossplay() {
        return crossplay;
    }

    public BedrockSessionManager bedrockSessions() {
        return bedrockSessions;
    }

    /** When true, public JE TCP is raw-proxied to the Mojang game kernel (full vanilla game). */
    public void setProxyToGameKernel(boolean enabled) {
        this.proxyToGameKernel = enabled;
    }

    public boolean isProxyToGameKernel() {
        return proxyToGameKernel;
    }

    public NativeEventLoops.Transport javaTransport() {
        return javaTransport;
    }

    public Channel javaChannel() {
        return javaChannel;
    }

    public void setJavaTransport(NativeEventLoops.Transport transport, Channel channel) {
        this.javaTransport = transport;
        this.javaChannel = channel;
    }

    public void clearJavaTransport() {
        this.javaTransport = null;
        this.javaChannel = null;
    }

    public EventLoopGroup bedrockGroup() {
        return bedrockGroup;
    }

    public Channel bedrockChannel() {
        return bedrockChannel;
    }

    public void setBedrockTransport(EventLoopGroup group, Channel channel) {
        this.bedrockGroup = group;
        this.bedrockChannel = channel;
    }

    public void clearBedrockTransport() {
        this.bedrockGroup = null;
        this.bedrockChannel = null;
    }

    public void setRakNetSessions(RakNetSessionManager rakNetSessions) {
        this.rakNetSessions = rakNetSessions;
    }

    public synchronized void start() throws InterruptedException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        JavaListenerBoot.start(this);
        BedrockUdpBoot.start(this);
        BedrockUiGatewayHolder.attach(this);
        LOG.info("Dual-stack gateway ready — Java=" + config.isJavaEnabled()
                + " Bedrock=" + config.isBedrockEnabled()
                + " shared-port=" + config.isSharedListenPort()
                + " crossplay=" + config.isCrossplayEnabled()
                + " resource-packs=" + config.isResourcePackEnabled());
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        JavaListenerBoot.shutdown(this);
        BedrockUdpBoot.shutdown(this);
        BedrockUiGatewayHolder.detach();
        clients.clear();
        if (crossplay != null) {
            crossplay.clear();
        }
        ThreadMetrics.record("Gateway", "stopped");
    }

    /**
     * Programmatic join used by demos / TrafficCop line protocol / Bedrock handshakes.
     */
    public ClientSession acceptClient(String username,
                                      ClientEdition edition,
                                      int protocolId,
                                      InetSocketAddress address) {
        Optional<ProtocolVersionRegistry.ProtocolVersion> resolved =
                protocols.resolve(edition, protocolId);
        if (resolved.isEmpty()) {
            LOG.warning("Rejected " + edition + " client " + username
                    + " unsupported protocol " + protocolId);
            trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_REJECTED, username, Map.of(
                    "edition", edition.name(),
                    "protocol", Integer.toString(protocolId),
                    "reason", "unsupported-protocol"
            )));
            return null;
        }
        ProtocolVersionRegistry.ProtocolVersion ver = resolved.get();
        if (ver.protocolId() != protocolId) {
            LOG.info("Back-compat map " + edition + " protocol " + protocolId
                    + " → " + ver.protocolId() + " (" + ver.minecraftVersion() + ")");
        }
        ClientSession session = new ClientSession(username, edition, ver, address);
        clients.register(session);
        if (config.isCrossplayEnabled() && crossplay != null) {
            crossplay.join(session);
        }
        trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_JOIN, username, Map.of(
                "edition", edition.name(),
                "protocol", Integer.toString(ver.protocolId()),
                "mc-version", ver.minecraftVersion(),
                "session", session.getSessionId().toString(),
                "crossplay", Boolean.toString(config.isCrossplayEnabled()),
                "shared-port", Boolean.toString(config.isSharedListenPort())
        )));

        var offers = packs.createOffers(session);
        if (!offers.isEmpty()) {
            ResourcePackOffer o = offers.get(0);
            trafficCop.ingest(new GameEvent(
                    GameEvent.Type.RESOURCE_PACK_OFFER,
                    username,
                    Map.of(
                            "url", o.url(),
                            "sha1", o.sha1Hex(),
                            "forced", Boolean.toString(o.forced()),
                            "prompt", o.prompt(),
                            "edition", edition.name(),
                            "count", Integer.toString(offers.size())
                    )
            ));
        }
        return session;
    }

    public void handlePackStatus(String username, ClientSession.ResourcePackState state) {
        clients.get(username).ifPresent(session -> {
            session.setPackState(state);
            trafficCop.ingest(new GameEvent(GameEvent.Type.RESOURCE_PACK_STATUS, username, Map.of(
                    "state", state.name(),
                    "edition", session.getEdition().name()
            )));
            LOG.info("Resource pack status from " + username + ": " + state);
        });
    }
}
