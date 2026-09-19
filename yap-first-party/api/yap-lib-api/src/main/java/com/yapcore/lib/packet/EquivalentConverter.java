package com.yapcore.lib.packet;

/**
 * Converts a Bukkit/API value to the NMS field type on a packet (and back).
 */
public interface EquivalentConverter<T> {

    Object toNms(T value);

    T fromNms(Object nms);

    Class<?> nmsHint();
}
