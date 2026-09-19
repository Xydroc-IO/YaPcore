package com.yapcore.lib.dispatch;

import com.yapcore.lib.packet.PacketDirection;
import com.yapcore.lib.packet.PacketListener;
import com.yapcore.lib.packet.PacketPriority;
import com.yapcore.lib.packet.PacketThreadMode;
import com.yapcore.lib.packet.PacketType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ListenerRegistry {

    public record Binding(Plugin plugin, PacketListener listener, PacketPriority priority,
                          PacketThreadMode threadMode) {
    }

    private final CopyOnWriteArrayList<Binding> bindings = new CopyOnWriteArrayList<>();

    public void add(Plugin plugin, PacketListener listener) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(listener, "listener");
        remove(listener);
        bindings.add(new Binding(plugin, listener, listener.priority(), listener.threadMode()));
        bindings.sort(Comparator.comparingInt(b -> b.priority().slot()));
    }

    public void remove(PacketListener listener) {
        bindings.removeIf(b -> b.listener() == listener);
    }

    public void remove(Plugin plugin) {
        bindings.removeIf(b -> b.plugin().equals(plugin));
    }

    public List<Binding> snapshot() {
        return List.copyOf(bindings);
    }

    public List<Binding> netty() {
        List<Binding> out = new ArrayList<>();
        for (Binding binding : bindings) {
            if (binding.threadMode() == PacketThreadMode.NETTY) {
                out.add(binding);
            }
        }
        return out;
    }

    public List<Binding> region() {
        List<Binding> out = new ArrayList<>();
        for (Binding binding : bindings) {
            if (binding.threadMode() == PacketThreadMode.REGION) {
                out.add(binding);
            }
        }
        return out;
    }

    public boolean hasRegionCancel(PacketType type, PacketDirection direction) {
        for (Binding binding : bindings) {
            if (binding.threadMode() != PacketThreadMode.REGION) {
                continue;
            }
            if (!binding.priority().canCancel()) {
                continue;
            }
            if (!binding.plugin().isEnabled()) {
                continue;
            }
            if (binding.listener().listening(type, direction)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return bindings.size();
    }

    public void clear() {
        bindings.clear();
    }
}
