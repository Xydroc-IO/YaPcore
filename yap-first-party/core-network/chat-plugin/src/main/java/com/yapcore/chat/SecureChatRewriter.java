package com.yapcore.chat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Offline / Via / Link joins cannot present Mojang chat-signing keys. Vanilla then
 * shows "Chat messages cannot be verified" for two reasons:
 * <ol>
 *   <li>Play login {@code enforcesSecureChat=false} — join toast</li>
 *   <li>Leftover {@code ClientboundPlayerChatPacket} — per-message gray bar</li>
 * </ol>
 * Advertise secure chat on login and rewrite any player-chat packets to system chat.
 */
public final class SecureChatRewriter {

    private static final String HANDLER = "yapchat-unsigned";
    private static final Key LISTENER_KEY = Key.key("yapchat", "unsigned");

    private final Plugin plugin;
    private final Logger log;
    private boolean installed;

    public SecureChatRewriter(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void install() {
        if (installed) {
            return;
        }
        ClassLoader serverCl = plugin.getServer().getClass().getClassLoader();
        try {
            Class<?> holder = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListenerHolder", true, serverCl);
            Class<?> listener = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListener", true, serverCl);
            Object proxy = Proxy.newProxyInstance(serverCl, new Class<?>[]{listener}, (p, m, args) -> {
                if ("afterInitChannel".equals(m.getName()) && args != null && args.length == 1) {
                    inject((Channel) args[0]);
                }
                return null;
            });
            holder.getMethod("addListener", Key.class, listener).invoke(null, LISTENER_KEY, proxy);
            installed = true;
            log.info("Secure-chat rewriter installed (login flag + player-chat → system)");
        } catch (ReflectiveOperationException e) {
            log.log(Level.WARNING, "Could not hook connection init — chat toast may remain", e);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            injectPlayer(player);
        }
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        ClassLoader serverCl = plugin.getServer().getClass().getClassLoader();
        try {
            Class<?> holder = Class.forName(
                    "io.papermc.paper.network.ChannelInitializeListenerHolder", true, serverCl);
            holder.getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
        } catch (ReflectiveOperationException ignored) {
            // server shutting down
        }
        installed = false;
    }

    public void injectPlayer(Player player) {
        Channel channel = playerChannel(player);
        if (channel != null) {
            inject(channel);
        }
    }

    private void inject(Channel channel) {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        channel.eventLoop().execute(() -> {
            try {
                if (channel.pipeline().get(HANDLER) != null) {
                    return;
                }
                if (channel.pipeline().get("packet_handler") == null) {
                    return;
                }
                channel.pipeline().addBefore("packet_handler", HANDLER, new RewriteHandler());
            } catch (RuntimeException e) {
                log.log(Level.FINE, "Secure-chat inject skipped", e);
            }
        });
    }

    private static Channel playerChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = field(handle, "connection", "f", "c");
            Object network = field(connection, "connection", "connection", "e", "c");
            Object channel = field(network, "channel", "m", "n", "k");
            return channel instanceof Channel ch ? ch : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object field(Object target, String... names) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    var f = type.getDeclaredField(name);
                    f.setAccessible(true);
                    Object value = f.get(target);
                    if (value != null) {
                        return value;
                    }
                } catch (NoSuchFieldException ignored) {
                    // try next
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    static final class RewriteHandler extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, rewrite(msg), promise);
        }
    }

    static Object rewrite(Object msg) {
        String name = msg.getClass().getName();
        if (name.endsWith(".ClientboundLoginPacket")) {
            return rewriteLogin(msg);
        }
        if (name.endsWith(".ClientboundPlayerChatPacket")) {
            return rewritePlayerChat(msg);
        }
        return msg;
    }

    private static Object rewriteLogin(Object packet) {
        try {
            if (Boolean.TRUE.equals(packet.getClass().getMethod("enforcesSecureChat").invoke(packet))) {
                return packet;
            }
            var components = packet.getClass().getRecordComponents();
            if (components == null || components.length < 2) {
                return packet;
            }
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = components[i].getAccessor().invoke(packet);
                types[i] = components[i].getType();
            }
            // Last component is enforcesSecureChat on modern ClientboundLoginPacket.
            args[args.length - 1] = Boolean.TRUE;
            Constructor<?> ctor = matchingConstructor(packet.getClass(), types, args.length);
            if (ctor == null) {
                return packet;
            }
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Never break the login channel — keep the original packet.
            return packet;
        }
    }

    private static Constructor<?> matchingConstructor(Class<?> type, Class<?>[] types, int arity) {
        try {
            Constructor<?> exact = type.getDeclaredConstructor(types);
            exact.setAccessible(true);
            return exact;
        } catch (NoSuchMethodException ignored) {
            // fall through
        }
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == arity) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        return null;
    }

    private static Object rewritePlayerChat(Object packet) {
        try {
            Object content = packet.getClass().getMethod("unsignedContent").invoke(packet);
            if (content == null) {
                Object body = packet.getClass().getMethod("body").invoke(packet);
                String text = String.valueOf(body.getClass().getMethod("content").invoke(body));
                Class<?> component = Class.forName("net.minecraft.network.chat.Component");
                content = component.getMethod("literal", String.class).invoke(null, text);
            }
            Class<?> system = Class.forName("net.minecraft.network.protocol.game.ClientboundSystemChatPacket");
            try {
                return system.getConstructor(content.getClass(), boolean.class).newInstance(content, false);
            } catch (NoSuchMethodException e) {
                return system.getConstructor(
                        Class.forName("net.minecraft.network.chat.Component"), boolean.class)
                        .newInstance(content, false);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return packet;
        }
    }
}
