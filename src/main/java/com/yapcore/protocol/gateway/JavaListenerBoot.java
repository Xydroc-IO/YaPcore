package com.yapcore.protocol.gateway;

import com.yapcore.config.ServerConfig;
import com.yapcore.kernel.JavaKernelProxyHandler;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.java.JavaProtocolHandler;
import com.yapcore.protocol.java.codec.McFrameCodec;
import com.yapcore.protocol.via.ViaProxyHandler;
import com.yapcore.util.ThreadMetrics;
import com.yaplabs.yapengine.network.traffic.NativeEventLoops;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;

import java.util.logging.Logger;

/**
 * Java Edition TCP listener: Via proxy, wrapped game proxy, or native JE handler.
 */
public final class JavaListenerBoot {

    private static final Logger LOG = Logger.getLogger("YaPcore.Gateway");

    private JavaListenerBoot() {
    }

    public static void start(DualStackGateway gateway) {
        ServerConfig config = gateway.config();
        if (!config.isJavaEnabled() || !config.isYaPcoreJavaListener()) {
            if (config.isJavaEnabled() && config.isFoliaAuthority() && config.isFoliaEmbed()) {
                LOG.info("Java Edition TCP owned by Folia on :" + config.foliaListenPort()
                        + " (YaPcore JE listener/proxy retired — use YaP Link/Velocity for public JE)");
            } else if (config.isJavaEnabled() && config.isPaperAuthority() && config.isPaperEmbed()) {
                LOG.info("Java Edition TCP owned by Paper on :" + config.getPort()
                        + " (YaPcore JE listener/proxy retired)");
            }
            return;
        }

        int viaWorkers = Math.max(8, Runtime.getRuntime().availableProcessors());
        String viaWorkersProp = System.getProperty("yapcore.via.netty.workers");
        if (viaWorkersProp != null && !viaWorkersProp.isBlank()) {
            try {
                viaWorkers = Math.max(2, Integer.parseInt(viaWorkersProp.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        NativeEventLoops.Transport transport = NativeEventLoops.create(1, viaWorkers);
        final boolean kernelProxy = gateway.isProxyToGameKernel();
        ProtocolVersionRegistry protocols = gateway.getProtocols();
        ServerBootstrap boot = new ServerBootstrap();
        boot.group(transport.boss(), transport.worker())
                .channel(transport.serverChannelClass())
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.AUTO_READ, !kernelProxy)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (kernelProxy) {
                            if (config.isProtocolViaEnabled()
                                    && (config.isPaperAuthority() || config.isFoliaAuthority())) {
                                int gamePort = config.isFoliaAuthority()
                                        ? config.foliaListenPort()
                                        : config.paperListenPort();
                                ch.pipeline().addLast("via-proxy", new ViaProxyHandler(
                                        "127.0.0.1",
                                        gamePort,
                                        ProtocolCompat.SERVER_PROTOCOL,
                                        gateway.resourcePackForwardEnabled()));
                            } else {
                                ch.pipeline().addLast("kernel-proxy", new JavaKernelProxyHandler(
                                        "127.0.0.1",
                                        config.getWrappedGamePort(),
                                        transport.worker()));
                            }
                        } else {
                            ch.pipeline()
                                    .addLast("frame-dec", new McFrameCodec.Decoder())
                                    .addLast("frame-enc", new McFrameCodec.Encoder())
                                    .addLast("je", new JavaProtocolHandler(
                                            gateway, config, protocols));
                        }
                    }
                });
        int javaPort = config.getPort();
        try {
            Channel channel = boot.bind(
                    config.getBindHost().equals("0.0.0.0") ? "0.0.0.0" : config.getBindHost(),
                    javaPort).sync().channel();
            gateway.setJavaTransport(transport, channel);
            if (kernelProxy) {
                boolean via = config.isProtocolViaEnabled()
                        && (config.isPaperAuthority() || config.isFoliaAuthority());
                int viaPort = config.isFoliaAuthority()
                        ? config.foliaListenPort()
                        : config.paperListenPort();
                LOG.info("Java Edition listener on :" + javaPort
                        + " transport=" + transport.kind()
                        + (via
                        ? " | mode=VIA_PROXY → 127.0.0.1:" + viaPort
                        + " (Via parity, serverProto=" + ProtocolCompat.SERVER_PROTOCOL + ")"
                        : " | mode=WRAPPED_GAME_PROXY → 127.0.0.1:" + config.getWrappedGamePort()
                        + " (authority=" + config.getGameAuthority() + ")"));
            } else {
                LOG.info("Java Edition listener on :" + javaPort
                        + " transport=" + transport.kind()
                        + " | multi-version=" + ProtocolCompat.isOnline()
                        + " (backwards-compat=" + protocols.isLenient() + ")");
            }
            if (config.isAllowLocalhost()) {
                LOG.info("Same-PC clients: connect to 127.0.0.1:" + javaPort
                        + " or localhost:" + javaPort);
            }
        } catch (Exception e) {
            LOG.info("Java TCP port " + javaPort + " bind note: " + e.getMessage());
            transport.shutdown();
        }
        ThreadMetrics.record("Gateway", "java-online");
    }

    public static void shutdown(DualStackGateway gateway) {
        Channel channel = gateway.javaChannel();
        if (channel != null) {
            channel.close();
        }
        NativeEventLoops.Transport transport = gateway.javaTransport();
        if (transport != null) {
            transport.shutdown();
            gateway.clearJavaTransport();
        }
    }
}
