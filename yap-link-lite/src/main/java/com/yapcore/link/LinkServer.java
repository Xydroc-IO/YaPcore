package com.yapcore.link;

import com.yapcore.link.bedrock.BedrockUdpForwarder;
import com.yapcore.link.crypto.MinecraftCrypto;
import com.yapcore.link.protocol.McFrameCodec;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Netty accept loop for YaP Link. */
public final class LinkServer {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Server");

    private final LinkConfig config;
    private final KeyPair rsaKeyPair;
    private final RedirectTokens redirects = new RedirectTokens(60_000L);
    private final AtomicInteger online = new AtomicInteger();
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel bindChannel;
    private BedrockUdpForwarder bedrock;

    public LinkServer(LinkConfig config) {
        this.config = config;
        this.rsaKeyPair = MinecraftCrypto.generateRsa();
    }

    public LinkConfig config() {
        return config;
    }

    public KeyPair rsaKeyPair() {
        return rsaKeyPair;
    }

    public RedirectTokens redirects() {
        return redirects;
    }

    public int onlinePlayers() {
        return online.get();
    }

    void playerJoined() {
        online.incrementAndGet();
    }

    void playerLeft() {
        online.updateAndGet(v -> Math.max(0, v - 1));
    }

    public synchronized void start() throws InterruptedException {
        if (bindChannel != null) {
            return;
        }
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McFrameCodec.Encoder())
                                .addLast("client", new ClientSession(LinkServer.this));
                    }
                });
        bindChannel = b.bind(config.bindHost(), config.bindPort()).sync().channel();
        LOG.info("JE listening on " + config.bindHost() + ":" + config.bindPort()
                + " online-mode=" + config.onlineMode());

        if (config.bedrockEnabled()) {
            bedrock = new BedrockUdpForwarder(
                    config.bedrockBindHost(), config.bedrockBindPort(),
                    config.bedrockBackendHost(), config.bedrockBackendPort());
            bedrock.start();
        }
    }

    public synchronized void stop() {
        if (bedrock != null) {
            bedrock.stop();
            bedrock = null;
        }
        try {
            if (bindChannel != null) {
                bindChannel.close().syncUninterruptibly();
                bindChannel = null;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "bind close", e);
        }
        if (worker != null) {
            worker.shutdownGracefully();
            worker = null;
        }
        if (boss != null) {
            boss.shutdownGracefully();
            boss = null;
        }
        LOG.info("YaP Link stopped");
    }
}
