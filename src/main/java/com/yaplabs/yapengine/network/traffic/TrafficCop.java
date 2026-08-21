package com.yaplabs.yapengine.network.traffic;

import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.network.compression.PacketCompressor;
import com.yaplabs.yapengine.network.compression.PacketCompressors;
import com.yaplabs.yapengine.sandbox.PluginSandbox;
import com.yaplabs.yapengine.sequencing.InteractionSequencer;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sequencing.StrictOrderedQueue;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.CharsetUtil;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 2 — Traffic Cop.
 * Native Epoll/KQueue ingest, Zstd packet compression, µs SequenceToken ordering
 * per player via {@link InteractionSequencer}.
 */
public final class TrafficCop implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.TrafficCop");

    public record SequencedPacket(
            SequenceToken token,
            String type,
            String player,
            Map<String, String> payload,
            byte[] compressedBody
    ) {
    }

    private final ParallelGameCore gameCore;
    private final PluginSandbox sandbox;
    private final PacketCompressor compressor;
    private final InteractionSequencer<SequencedPacket> sequencer = new InteractionSequencer<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong ingested = new AtomicLong();
    private final int bindPort;
    private volatile Thread thread;
    private NativeEventLoops.Transport transport;
    private Channel serverChannel;

    public TrafficCop(ParallelGameCore gameCore, PluginSandbox sandbox) {
        this(gameCore, sandbox, 0);
    }

    public TrafficCop(ParallelGameCore gameCore, PluginSandbox sandbox, int bindPort) {
        this.gameCore = Objects.requireNonNull(gameCore);
        this.sandbox = Objects.requireNonNull(sandbox);
        this.compressor = PacketCompressors.shared();
        this.bindPort = bindPort;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Always allocate native loops so Thread 2 never falls back to plain NIO by default.
        transport = NativeEventLoops.create(1, 2);
        thread = new Thread(this, "yap-t2-traffic-cop");
        thread.start();
        LOG.info("Traffic Cop online (Thread 2) transport=" + transport.kind()
                + " compressor=" + compressor.name());
    }

    public void stop() {
        running.set(false);
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (transport != null) {
            transport.shutdown();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    public Thread getThread() {
        return thread;
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public long getIngested() {
        return ingested.get();
    }

    public PacketCompressor compressor() {
        return compressor;
    }

    public SequenceToken ingest(String type, String player, Map<String, String> payload) {
        if (paused.get()) {
            LOG.warning("Dropped packet while paused: " + type);
            return null;
        }
        String stream = "player:" + player;
        SequenceToken token = sequencer.stamp(stream);
        Map<String, String> safe = Map.copyOf(payload);
        byte[] raw = (type + "|" + player + "|" + safe).getBytes(CharsetUtil.UTF_8);
        byte[] compressed = compressor.compress(raw);
        SequencedPacket packet = new SequencedPacket(token, type, player, safe, compressed);
        ingested.incrementAndGet();
        LOG.info(() -> "Ingest " + type + " player=" + player
                + " seq=" + token.getStreamSeq()
                + " g=" + token.getGlobalId()
                + " µs=" + token.getIngestMicros()
                + " zstd=" + compressed.length + "B");

        sequencer.publish(token, packet, this::dispatchReady);
        return token;
    }

    private void dispatchReady(StrictOrderedQueue.Sequenced<SequencedPacket> item) {
        route(item.payload());
    }

    @Override
    public void run() {
        if (bindPort > 0) {
            bindNativeServer(bindPort);
        }
        while (running.get()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Traffic Cop shut down — ingested=" + ingested.get()
                + " transport=" + (transport == null ? "none" : transport.kind()));
    }

    private void bindNativeServer(int port) {
        try {
            if (transport == null) {
                transport = NativeEventLoops.create(1, 2);
            }
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(transport.boss(), transport.worker())
                    .channel(transport.serverChannelClass())
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new IngestHandler());
                        }
                    });
            serverChannel = boot.bind(port).sync().channel();
            LOG.info("Traffic Cop native bind :" + port + " via " + transport.kind());
        } catch (Exception e) {
            LOG.warning("Native bind skipped: " + e.getMessage());
        }
    }

    private void route(SequencedPacket packet) {
        switch (packet.type()) {
            case "GUI_CLICK", "STORE_CLICK" -> {
                int x = parseInt(packet.payload().getOrDefault("x", "8"), 8);
                int z = parseInt(packet.payload().getOrDefault("z", "-8"), -8);
                String item = packet.payload().getOrDefault("item", "diamond_sword");
                sandbox.simulateItemClick(packet.player(), item, x, z);
            }
            case "MOVE" -> {
                int x = parseInt(packet.payload().getOrDefault("x", "0"), 0);
                int z = parseInt(packet.payload().getOrDefault("z", "0"), 0);
                gameCore.getPartition().registerEntity(packet.player(), x, z);
                gameCore.dispatch(x, z, packet.token(), "move:" + packet.player(),
                        () -> LOG.fine("Move applied " + packet.player()
                                + " µsAge=" + packet.token().ageMicros()));
            }
            default -> LOG.fine("Unhandled " + packet.type());
        }
    }

    private final class IngestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            // Accept pre-compressed or plaintext line protocol
            String line;
            try {
                byte[] plain = compressor.decompress(bytes, 64 * 1024);
                line = new String(plain, CharsetUtil.UTF_8).trim();
            } catch (RuntimeException e) {
                line = new String(bytes, CharsetUtil.UTF_8).trim();
            }
            if (line.isEmpty()) {
                return;
            }
            String[] parts = line.split("\\|", 3);
            String type = parts[0];
            String player = parts.length > 1 ? parts[1] : "unknown";
            Map<String, String> payload = Map.of();
            if (parts.length > 2 && !parts[2].isBlank()) {
                // minimal key=value parse
                java.util.concurrent.ConcurrentHashMap<String, String> map =
                        new java.util.concurrent.ConcurrentHashMap<>();
                for (String pair : parts[2].replace("{", "").replace("}", "").split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        map.put(kv[0].trim(), kv[1].trim());
                    }
                }
                payload = Map.copyOf(map);
            }
            ingest(type, player, payload);
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
