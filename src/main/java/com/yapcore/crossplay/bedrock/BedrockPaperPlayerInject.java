package com.yapcore.crossplay.bedrock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Injects a real Paper {@code org.bukkit.entity.Player} for Bedrock sessions so
 * {@code Bukkit.getPlayerExact} / plugins / inventory APIs see them.
 * Uses an EmbeddedChannel-backed NMS {@code Connection} + {@code PlayerList.placeNewPlayer}.
 */
public final class BedrockPaperPlayerInject {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockPaperPlayer");

    private final AtomicReference<URLClassLoader> paperLoader;
    private final ConcurrentHashMap<String, Object> injected = new ConcurrentHashMap<>(); // name → CraftPlayer

    public BedrockPaperPlayerInject(AtomicReference<URLClassLoader> paperLoader) {
        this.paperLoader = paperLoader;
    }

    public boolean isInjected(String username) {
        return username != null && injected.containsKey(username.toLowerCase());
    }

    /**
     * Place a Bedrock identity onto Paper's online player list.
     *
     * @return true if {@code getPlayerExact(username)} is non-null afterward
     */
    public boolean inject(String username, UUID uuid, double x, double y, double z) {
        if (username == null || username.isBlank() || uuid == null) {
            return false;
        }
        URLClassLoader preferred = paperLoader.get();
        if (preferred == null) {
            return false;
        }
        if (injected.containsKey(username.toLowerCase())) {
            return true;
        }
        ClassLoader cl = com.yapcore.paper.PaperCommandBridge.resolvePaperLoader(preferred);
        if (cl == null) {
            return false;
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                ok.set(injectOnMain(cl, username, uuid, x, y, z));
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "BE→Paper inject failed for " + username, t);
            } finally {
                done.countDown();
            }
        };
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object server = bukkit.getMethod("getServer").invoke(null);
            if (server == null) {
                return false;
            }
            boolean primary = Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null));
            if (primary) {
                task.run();
            } else {
                schedule(bukkit, server, cl, task);
                if (!done.await(8, TimeUnit.SECONDS)) {
                    LOG.warning("BE→Paper inject timed out for " + username);
                    return false;
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "BE→Paper inject schedule failed", t);
            return false;
        }
        return ok.get();
    }

    public boolean eject(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        URLClassLoader preferred = paperLoader.get();
        if (preferred == null) {
            injected.remove(username.toLowerCase());
            return false;
        }
        ClassLoader cl = com.yapcore.paper.PaperCommandBridge.resolvePaperLoader(preferred);
        if (cl == null) {
            injected.remove(username.toLowerCase());
            return false;
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                ok.set(ejectOnMain(cl, username));
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "BE→Paper eject failed for " + username, t);
            } finally {
                done.countDown();
            }
        };
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object server = bukkit.getMethod("getServer").invoke(null);
            if (server == null) {
                injected.remove(username.toLowerCase());
                return false;
            }
            boolean primary = Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null));
            if (primary) {
                task.run();
            } else {
                schedule(bukkit, server, cl, task);
                done.await(5, TimeUnit.SECONDS);
            }
        } catch (Throwable t) {
            injected.remove(username.toLowerCase());
            LOG.log(Level.FINE, "eject schedule", t);
        }
        return ok.get();
    }

    private boolean injectOnMain(ClassLoader cl, String username, UUID uuid,
                                 double x, double y, double z) throws Exception {
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Object existing = bukkit.getMethod("getPlayerExact", String.class).invoke(null, username);
        if (existing != null) {
            injected.put(username.toLowerCase(), existing);
            LOG.info("BE→Paper player already online " + username);
            return true;
        }

        Object craftServer = bukkit.getMethod("getServer").invoke(null);
        Object nmsServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
        Object playerList = craftServer.getClass().getMethod("getHandle").invoke(craftServer);

        Class<?> gameProfileCl = Class.forName("com.mojang.authlib.GameProfile", true, cl);
        Constructor<?> gpCtor = gameProfileCl.getConstructor(UUID.class, String.class);
        Object profile = gpCtor.newInstance(uuid, username);

        Class<?> clientInfoCl = Class.forName("net.minecraft.server.level.ClientInformation", true, cl);
        Object clientInfo = clientInfoCl.getMethod("createDefault").invoke(null);

        // Overworld level
        Object level = null;
        try {
            level = nmsServer.getClass().getMethod("overworld").invoke(nmsServer);
        } catch (NoSuchMethodException e) {
            Object worlds = nmsServer.getClass().getMethod("getAllLevels").invoke(nmsServer);
            if (worlds instanceof Iterable<?> it) {
                for (Object w : it) {
                    level = w;
                    break;
                }
            }
        }
        if (level == null) {
            LOG.warning("BE→Paper inject: no ServerLevel");
            return false;
        }

        Class<?> serverPlayerCl = Class.forName("net.minecraft.server.level.ServerPlayer", true, cl);
        Class<?> minecraftServerCl = Class.forName("net.minecraft.server.MinecraftServer", true, cl);
        Class<?> serverLevelCl = Class.forName("net.minecraft.server.level.ServerLevel", true, cl);
        Constructor<?> spCtor = serverPlayerCl.getConstructor(
                minecraftServerCl, serverLevelCl, gameProfileCl, clientInfoCl);
        Object serverPlayer = spCtor.newInstance(nmsServer, level, profile, clientInfo);

        // In-memory Connection so placeNewPlayer can send without a real TCP client
        Class<?> packetFlowCl = Class.forName("net.minecraft.network.protocol.PacketFlow", true, cl);
        Object serverbound = Enum.valueOf(packetFlowCl.asSubclass(Enum.class), "SERVERBOUND");
        Class<?> connectionCl = Class.forName("net.minecraft.network.Connection", true, cl);
        Class<?> embeddedCl = Class.forName("io.netty.channel.embedded.EmbeddedChannel", true, cl);
        Object channel = embeddedCl.getConstructor().newInstance();
        Object connection;
        try {
            Class<?> pipelineCl = Class.forName("io.netty.channel.ChannelPipeline", true, cl);
            Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
            connectionCl.getMethod("configureInMemoryPipeline", pipelineCl, packetFlowCl)
                    .invoke(null, pipeline, serverbound);
            Class<?> loggerCl = Class.forName("net.minecraft.util.debugchart.LocalSampleLogger", true, cl);
            Object logger = loggerCl.getConstructor(int.class).newInstance(64);
            Class<?> channelCl = Class.forName("io.netty.channel.Channel", true, cl);
            connection = connectionCl.getMethod("fromChannel", channelCl, packetFlowCl, loggerCl)
                    .invoke(null, channel, serverbound, logger);
            // Swallow all outbound packets (no JE client) — avoid EncoderException spam/kick
            installDiscardOutbound(cl, pipeline);
        } catch (ReflectiveOperationException e) {
            connection = connectionCl.getConstructor(packetFlowCl).newInstance(serverbound);
            setField(connection, "channel", channel);
            Field preparing = findField(connectionCl, "preparing");
            if (preparing != null) {
                preparing.setAccessible(true);
                preparing.setBoolean(connection, false);
            }
            try {
                Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
                installDiscardOutbound(cl, pipeline);
            } catch (Exception ignored) {
                // best-effort
            }
        }
        try {
            setField(connection, "address", new InetSocketAddress("127.0.0.1", 19132));
        } catch (Exception ignored) {
            // optional
        }

        Class<?> cookieCl = Class.forName("net.minecraft.server.network.CommonListenerCookie", true, cl);
        Object cookie = cookieCl.getMethod("createInitial", gameProfileCl, boolean.class)
                .invoke(null, profile, false);

        // placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)
        Method place = playerList.getClass().getMethod("placeNewPlayer",
                connectionCl, serverPlayerCl, cookieCl);
        place.invoke(playerList, connection, serverPlayer, cookie);

        // Teleport to spawn
        Object craftPlayer = serverPlayerCl.getMethod("getBukkitEntity").invoke(serverPlayer);
        teleport(craftPlayer, cl, x, y, z);

        // Verify online
        Object online = bukkit.getMethod("getPlayerExact", String.class).invoke(null, username);
        if (online == null) {
            online = bukkit.getMethod("getPlayer", UUID.class).invoke(null, uuid);
        }
        if (online == null) {
            LOG.warning("BE→Paper inject: placeNewPlayer returned but player not online for " + username);
            return false;
        }
        injected.put(username.toLowerCase(), online);
        LOG.info("BE→Paper player injected " + username + " uuid=" + uuid);
        return true;
    }

    private boolean ejectOnMain(ClassLoader cl, String username) throws Exception {
        String key = username.toLowerCase();
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Object player = bukkit.getMethod("getPlayerExact", String.class).invoke(null, username);
        if (player == null) {
            injected.remove(key);
            return true;
        }
        try {
            // Prefer kick so quit events fire cleanly
            try {
                player.getClass().getMethod("kick", Class.forName("net.kyori.adventure.text.Component", true, cl))
                        .invoke(player, Class.forName("net.kyori.adventure.text.Component", true, cl)
                                .getMethod("text", String.class).invoke(null, "Bedrock disconnect"));
            } catch (NoSuchMethodException e) {
                player.getClass().getMethod("kickPlayer", String.class).invoke(player, "Bedrock disconnect");
            }
        } catch (Exception e) {
            // Fallback: PlayerList.remove(ServerPlayer)
            try {
                Object craftServer = bukkit.getMethod("getServer").invoke(null);
                Object playerList = craftServer.getClass().getMethod("getHandle").invoke(craftServer);
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                for (Method m : playerList.getClass().getMethods()) {
                    if ("remove".equals(m.getName()) && m.getParameterCount() >= 1
                            && m.getParameterTypes()[0].getName().contains("ServerPlayer")) {
                        if (m.getParameterCount() == 1) {
                            m.invoke(playerList, handle);
                        } else {
                            m.invoke(playerList, handle, null);
                        }
                        break;
                    }
                }
            } catch (Exception e2) {
                LOG.log(Level.FINE, "PlayerList.remove fallback", e2);
            }
        }
        injected.remove(key);
        LOG.info("BE→Paper player ejected " + username);
        return true;
    }

    private static void teleport(Object craftPlayer, ClassLoader cl, double x, double y, double z)
            throws Exception {
        Class<?> locCl = Class.forName("org.bukkit.Location", true, cl);
        Object world = craftPlayer.getClass().getMethod("getWorld").invoke(craftPlayer);
        Object loc = locCl.getConstructor(
                Class.forName("org.bukkit.World", true, cl),
                double.class, double.class, double.class)
                .newInstance(world, x, y, z);
        try {
            craftPlayer.getClass().getMethod("teleport", locCl).invoke(craftPlayer, loc);
        } catch (NoSuchMethodException e) {
            craftPlayer.getClass().getMethod("teleportAsync", locCl).invoke(craftPlayer, loc);
        }
    }

    private static void schedule(Class<?> bukkit, Object server, ClassLoader cl, Runnable task) {
        try {
            Object pm = server.getClass().getMethod("getPluginManager").invoke(server);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            Object plugin = null;
            if (plugins != null) {
                for (Object p : plugins) {
                    if (p != null && Boolean.TRUE.equals(p.getClass().getMethod("isEnabled").invoke(p))) {
                        plugin = p;
                        break;
                    }
                }
            }
            if (plugin != null) {
                Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
                Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
                scheduler.getClass().getMethod("runTask", pluginCl, Runnable.class)
                        .invoke(scheduler, plugin, task);
                return;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "schedule via plugin", e);
        }
        try {
            Class<?> ms = Class.forName("net.minecraft.server.MinecraftServer", true, cl);
            Object nms = ms.getMethod("getServer").invoke(null);
            nms.getClass().getMethod("execute", Runnable.class).invoke(nms, task);
        } catch (Exception e) {
            task.run();
        }
    }

    private static void installDiscardOutbound(ClassLoader cl, Object pipeline) throws Exception {
        Class<?> handlerIface = Class.forName("io.netty.channel.ChannelOutboundHandler", true, cl);
        Object handler = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{handlerIface},
                (proxy, method, args) -> {
                    String n = method.getName();
                    if ("write".equals(n) && args != null && args.length >= 3) {
                        succeedPromise(args[2]);
                        return null;
                    }
                    if (("close".equals(n) || "disconnect".equals(n) || "deregister".equals(n))
                            && args != null && args.length >= 2) {
                        succeedPromise(args[1]);
                        return null;
                    }
                    if ("toString".equals(n)) {
                        return "YapBeDiscardOutbound";
                    }
                    if ("hashCode".equals(n)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(n)) {
                        return proxy == args[0];
                    }
                    return null;
                });
        pipeline.getClass().getMethod("addLast", String.class,
                        Class.forName("io.netty.channel.ChannelHandler", true, cl))
                .invoke(pipeline, "yap-be-discard", handler);
    }

    private static void succeedPromise(Object promise) {
        if (promise == null) {
            return;
        }
        try {
            promise.getClass().getMethod("trySuccess").invoke(promise);
        } catch (Exception e) {
            try {
                promise.getClass().getMethod("setSuccess").invoke(promise);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        if (f == null) {
            throw new NoSuchFieldException(name);
        }
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(injected);
    }
}
