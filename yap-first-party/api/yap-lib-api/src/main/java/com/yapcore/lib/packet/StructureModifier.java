package com.yapcore.lib.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Typed view over packet fields of one Java type (ProtocolLib {@code StructureModifier}).
 */
public final class StructureModifier<T> {

    private final PacketContainer packet;
    private final Class<?> token;
    private final EquivalentConverter<T> converter;

    StructureModifier(PacketContainer packet, Class<?> token, EquivalentConverter<T> converter) {
        this.packet = Objects.requireNonNull(packet, "packet");
        this.token = Objects.requireNonNull(token, "token");
        this.converter = converter;
    }

    public int size() {
        return packet.count(token);
    }

    public T read(int index) {
        Object raw = packet.read(token, index);
        if (converter == null) {
            @SuppressWarnings("unchecked")
            T cast = (T) raw;
            return cast;
        }
        return converter.fromNms(raw);
    }

    public T readOrNull(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        return read(index);
    }

    public StructureModifier<T> write(int index, T value) {
        Object nms = converter == null ? value : converter.toNms(value);
        packet.write(token, index, nms);
        return this;
    }

    public List<T> getValues() {
        List<T> out = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            out.add(read(i));
        }
        return out;
    }
}
