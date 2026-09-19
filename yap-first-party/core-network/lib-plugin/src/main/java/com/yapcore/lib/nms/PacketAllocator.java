package com.yapcore.lib.nms;

import com.yapcore.lib.packet.PacketClassNames;
import com.yapcore.lib.packet.PacketContainer;
import com.yapcore.lib.packet.PacketInstances;
import com.yapcore.lib.packet.PacketType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Resolve NMS packet class + allocate empty instance for {@code createPacket}. */
public final class PacketAllocator {

    private final ClassLoader loader;

    public PacketAllocator(JavaPlugin plugin) {
        this.loader = plugin.getServer().getClass().getClassLoader();
    }

    public Class<?> nmsClass(PacketType type) {
        List<String> names = PacketClassNames.candidates(type);
        for (String name : names) {
            if (name == null) {
                continue;
            }
            Class<?> found = load(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public PacketContainer create(PacketType type) {
        Class<?> nms = nmsClass(type);
        if (nms == null) {
            throw new IllegalArgumentException("No NMS class for " + type);
        }
        return PacketContainer.wrap(PacketInstances.allocate(nms), type);
    }

    private Class<?> load(String name) {
        try {
            int dollar = name.indexOf('$');
            if (dollar < 0) {
                return Class.forName(name, true, loader);
            }
            Class<?> outer = Class.forName(name.substring(0, dollar), true, loader);
            String inner = name.substring(dollar + 1);
            for (Class<?> nested : outer.getDeclaredClasses()) {
                if (inner.equals(nested.getSimpleName())) {
                    return nested;
                }
            }
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
