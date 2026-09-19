package com.yapcore.lib.inject;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** CraftPlayer → Netty channel / NMS Connection.send. */
public final class ChannelAccess {

    private ChannelAccess() {
    }

    public static Channel channel(Player player) {
        Object connection = connection(player);
        if (connection == null) {
            return null;
        }
        Object ch = read(connection, "channel");
        return ch instanceof Channel channel ? channel : null;
    }

    public static Object connection(Player player) {
        Object handle = handle(player);
        if (handle == null) {
            return null;
        }
        Object listener = read(handle, "connection");
        if (listener == null) {
            return null;
        }
        Object nested = read(listener, "connection");
        return nested != null ? nested : listener;
    }

    public static boolean send(Player player, Object nmsPacket) {
        Object connection = connection(player);
        if (connection == null || nmsPacket == null) {
            return false;
        }
        Class<?> packetBase = findPacketBase(nmsPacket.getClass());
        if (packetBase == null) {
            packetBase = nmsPacket.getClass();
        }
        try {
            Method send = findSend(connection.getClass(), packetBase);
            if (send == null) {
                return false;
            }
            send.invoke(connection, nmsPacket);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static Object handle(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            return getHandle.invoke(player);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findSend(Class<?> type, Class<?> packetBase) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!"send".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(packetBase)
                        || packetBase.isAssignableFrom(method.getParameterTypes()[0])) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static Class<?> findPacketBase(Class<?> nmsClass) {
        Class<?> cursor = nmsClass;
        while (cursor != null) {
            for (Class<?> iface : cursor.getInterfaces()) {
                if (iface.getName().equals("net.minecraft.network.protocol.Packet")) {
                    return iface;
                }
            }
            cursor = cursor.getSuperclass();
        }
        try {
            return Class.forName("net.minecraft.network.protocol.Packet", true, nmsClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            return nmsClass;
        }
    }

    static Object read(Object target, String... names) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) {
                        return value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // next
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
