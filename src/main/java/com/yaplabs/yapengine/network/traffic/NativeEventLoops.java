package com.yaplabs.yapengine.network.traffic;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * OS-native Netty transport for Traffic Cop (Thread 2).
 * Linux → Epoll (zero-copy-ish kernel hooks), macOS → KQueue, else NIO fallback.
 */
public final class NativeEventLoops {

    private static final Logger LOG = Logger.getLogger("YapEngine.NativeIO");

    public enum TransportKind {
        EPOLL, KQUEUE, NIO
    }

    public record Transport(
            TransportKind kind,
            EventLoopGroup boss,
            EventLoopGroup worker,
            Class<? extends ServerChannel> serverChannelClass
    ) {
        public void shutdown() {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    private NativeEventLoops() {
    }

    public static Transport create(int bossThreads, int workerThreads) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux") && epollAvailable()) {
            LOG.info("Traffic Cop transport: Linux Epoll (native)");
            return epoll(bossThreads, workerThreads);
        }
        if ((os.contains("mac") || os.contains("darwin")) && kqueueAvailable()) {
            LOG.info("Traffic Cop transport: macOS KQueue (native)");
            return kqueue(bossThreads, workerThreads);
        }
        LOG.warning("Traffic Cop transport: NIO fallback (native epoll/kqueue unavailable)");
        return nio(bossThreads, workerThreads);
    }

    private static boolean epollAvailable() {
        try {
            Class<?> epoll = Class.forName("io.netty.channel.epoll.Epoll");
            return Boolean.TRUE.equals(epoll.getMethod("isAvailable").invoke(null));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean kqueueAvailable() {
        try {
            Class<?> kqueue = Class.forName("io.netty.channel.kqueue.KQueue");
            return Boolean.TRUE.equals(kqueue.getMethod("isAvailable").invoke(null));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Transport epoll(int bossThreads, int workerThreads) {
        try {
            Class<?> groupCl = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
            Class<?> chCl = Class.forName("io.netty.channel.epoll.EpollServerSocketChannel");
            EventLoopGroup boss = (EventLoopGroup) groupCl
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(bossThreads, factory("yap-epoll-boss"));
            EventLoopGroup worker = (EventLoopGroup) groupCl
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(workerThreads, factory("yap-epoll-worker"));
            return new Transport(TransportKind.EPOLL, boss, worker,
                    (Class<? extends ServerChannel>) chCl);
        } catch (ReflectiveOperationException e) {
            LOG.warning("Epoll init failed: " + e.getMessage());
            return nio(bossThreads, workerThreads);
        }
    }

    @SuppressWarnings("unchecked")
    private static Transport kqueue(int bossThreads, int workerThreads) {
        try {
            Class<?> groupCl = Class.forName("io.netty.channel.kqueue.KQueueEventLoopGroup");
            Class<?> chCl = Class.forName("io.netty.channel.kqueue.KQueueServerSocketChannel");
            EventLoopGroup boss = (EventLoopGroup) groupCl
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(bossThreads, factory("yap-kqueue-boss"));
            EventLoopGroup worker = (EventLoopGroup) groupCl
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(workerThreads, factory("yap-kqueue-worker"));
            return new Transport(TransportKind.KQUEUE, boss, worker,
                    (Class<? extends ServerChannel>) chCl);
        } catch (ReflectiveOperationException e) {
            LOG.warning("KQueue init failed: " + e.getMessage());
            return nio(bossThreads, workerThreads);
        }
    }

    private static Transport nio(int bossThreads, int workerThreads) {
        return new Transport(
                TransportKind.NIO,
                new NioEventLoopGroup(bossThreads, factory("yap-nio-boss")),
                new NioEventLoopGroup(workerThreads, factory("yap-nio-worker")),
                NioServerSocketChannel.class
        );
    }

    private static ThreadFactory factory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
