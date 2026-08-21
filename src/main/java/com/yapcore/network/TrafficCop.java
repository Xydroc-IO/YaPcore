package com.yapcore.network;

import com.yapcore.model.GameEvent;
import com.yapcore.plugin.PluginSandboxPool;
import com.yapcore.util.ThreadMetrics;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 2 — The Traffic Cop (Network I/O).
 * Netty pipeline ingests packets / GUI clicks, sanitizes them into immutable
 * GameEvent models, and pushes them onto a lock-free event stream.
 */
public final class TrafficCop implements Runnable {

    private static final Logger LOG = Logger.getLogger("YaPcore.TrafficCop");
    private static final int DEFAULT_PORT = 25566;

    private final ConcurrentLinkedQueue<GameEvent> eventStream;
    private final PluginSandboxPool pluginPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean trafficPaused = new AtomicBoolean(false);
    private final AtomicLong ingested = new AtomicLong();
    private final int port;
    private final boolean bindSocket;

    private volatile Thread copThread;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public TrafficCop(ConcurrentLinkedQueue<GameEvent> eventStream, PluginSandboxPool pluginPool) {
        this(eventStream, pluginPool, DEFAULT_PORT, true);
    }

    public TrafficCop(ConcurrentLinkedQueue<GameEvent> eventStream,
                      PluginSandboxPool pluginPool,
                      int port) {
        this(eventStream, pluginPool, port, true);
    }

    public TrafficCop(ConcurrentLinkedQueue<GameEvent> eventStream,
                      PluginSandboxPool pluginPool,
                      int port,
                      boolean bindSocket) {
        this.eventStream = eventStream;
        this.pluginPool = pluginPool;
        this.port = port;
        this.bindSocket = bindSocket;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        copThread = new Thread(this, "yap-core2-traffic-cop");
        copThread.setDaemon(false);
        copThread.start();
        ThreadMetrics.record("TrafficCop", "started");
    }

    public void stop() {
        running.set(false);
        closeNetty();
        if (copThread != null) {
            copThread.interrupt();
        }
        ThreadMetrics.record("TrafficCop", "stopped");
    }

    public void pauseTraffic() {
        trafficPaused.set(true);
        ThreadMetrics.record("TrafficCop", "traffic-paused");
    }

    public void resumeTraffic() {
        trafficPaused.set(false);
        ThreadMetrics.record("TrafficCop", "traffic-resumed");
    }

    public Thread getCopThread() {
        return copThread;
    }

    public long getIngested() {
        return ingested.get();
    }

    public ConcurrentLinkedQueue<GameEvent> getEventStream() {
        return eventStream;
    }

    /**
     * Programmatic inject path used by the lifecycle demo (no real client required).
     */
    public void ingest(GameEvent event) {
        if (trafficPaused.get()) {
            ThreadMetrics.record("TrafficCop", "dropped-while-paused");
            return;
        }
        eventStream.offer(event);
        ingested.incrementAndGet();
        ThreadMetrics.record("TrafficCop", "ingest:" + event.getType());

        // Route GUI / store interactions to the high-speed plugin pool immediately
        if (event.getType() == GameEvent.Type.GUI_CLICK
                || event.getType() == GameEvent.Type.STORE_PURCHASE_REQUEST) {
            pluginPool.submitUiTask(() -> pluginPool.handleGuiEvent(event));
        }
    }

    @Override
    public void run() {
        if (!bindSocket) {
            LOG.info("Traffic Cop online — ingest-only mode (DualStackGateway owns sockets)");
            ThreadMetrics.record("TrafficCop", "ingest-only");
            while (running.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOG.info("Traffic Cop shut down — ingested=" + ingested.get());
            return;
        }
        LOG.info("Traffic Cop online — Netty listening on :" + port);
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new PacketSanitizer());
                        }
                    });
            serverChannel = bootstrap.bind(port).sync().channel();
            ThreadMetrics.record("TrafficCop", "netty-bound-" + port);

            while (running.get()) {
                Thread.sleep(50);
            }
            serverChannel.close().syncUninterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            LOG.warning("Netty bind skipped/failed (" + ex.getMessage()
                    + "); using programmatic ingest only");
            ThreadMetrics.record("TrafficCop", "netty-fallback");
            while (running.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            closeNetty();
            LOG.info("Traffic Cop shut down — ingested=" + ingested.get());
        }
    }

    private void closeNetty() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * Minimal line-oriented sanitizer: "TYPE|player|key=value,key=value"
     */
    private final class PacketSanitizer extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            if (trafficPaused.get()) {
                return;
            }
            String raw = msg.toString(CharsetUtil.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }
            try {
                GameEvent event = parsePacket(raw);
                ingest(event);
            } catch (IllegalArgumentException ex) {
                LOG.fine("Rejected packet: " + ex.getMessage());
                ThreadMetrics.record("TrafficCop", "rejected-packet");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.fine("Channel error: " + cause.getMessage());
            ctx.close();
        }
    }

    public static GameEvent parsePacket(String raw) {
        String[] parts = raw.split("\\|", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed packet");
        }
        GameEvent.Type type = GameEvent.Type.valueOf(parts[0].trim().toUpperCase());
        String player = parts[1].trim();
        Map<String, String> payload = Map.of();
        if (parts.length == 3 && !parts[2].isBlank()) {
            java.util.HashMap<String, String> map = new java.util.HashMap<>();
            for (String pair : parts[2].split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
            payload = Map.copyOf(map);
        }
        return new GameEvent(type, player, payload);
    }
}
