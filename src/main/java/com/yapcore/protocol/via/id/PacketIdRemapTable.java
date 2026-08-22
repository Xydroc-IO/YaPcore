package com.yapcore.protocol.via.id;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precomputed play packet-id remap tables for one (fromProto → toProto) pair.
 * Hot path is an array index — avoids {@link PacketIdDump#forProtocol(int)} and
 * name-map scans on every packet.
 */
public final class PacketIdRemapTable {

    private static final ConcurrentHashMap<Long, PacketIdRemapTable> S2C = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, PacketIdRemapTable> C2S = new ConcurrentHashMap<>();

    /** fromId → toId; missing / unmapped = -1 */
    private final int[] table;

    private PacketIdRemapTable(int[] table) {
        this.table = table;
    }

    public int remap(int fromId) {
        if (fromId < 0 || fromId >= table.length) {
            return -1;
        }
        return table[fromId];
    }

    public int capacity() {
        return table.length;
    }

    /** S2C: server protocol dump → client protocol dump. */
    public static PacketIdRemapTable playS2c(int fromProto, int toProto) {
        return S2C.computeIfAbsent(key(fromProto, toProto), k -> buildS2c(
                PacketIdDump.forProtocol(fromProto), PacketIdDump.forProtocol(toProto)));
    }

    /** C2S: client protocol dump → server protocol dump. */
    public static PacketIdRemapTable playC2s(int fromProto, int toProto) {
        return C2S.computeIfAbsent(key(fromProto, toProto), k -> buildC2s(
                PacketIdDump.forProtocol(fromProto), PacketIdDump.forProtocol(toProto)));
    }

    /** S2C from already-resolved dumps (session ctor). */
    public static PacketIdRemapTable playS2c(PacketIdDump from, PacketIdDump to) {
        return S2C.computeIfAbsent(key(from.protocol(), to.protocol()), k -> buildS2c(from, to));
    }

    /** C2S from already-resolved dumps (session ctor). */
    public static PacketIdRemapTable playC2s(PacketIdDump from, PacketIdDump to) {
        return C2S.computeIfAbsent(key(from.protocol(), to.protocol()), k -> buildC2s(from, to));
    }

    private static long key(int from, int to) {
        return ((long) from << 32) | (to & 0xffffffffL);
    }

    private static PacketIdRemapTable buildS2c(PacketIdDump from, PacketIdDump to) {
        int max = maxId(from.playS2cNames());
        int[] table = new int[Math.max(max + 1, 0)];
        Arrays.fill(table, -1);
        if (!from.hasPlay() || !to.hasPlay()) {
            return new PacketIdRemapTable(table);
        }
        for (int id : from.playS2cNames().values()) {
            if (id >= 0 && id < table.length) {
                table[id] = PacketIdDump.remapPlayS2c(from, to, id);
            }
        }
        return new PacketIdRemapTable(table);
    }

    private static PacketIdRemapTable buildC2s(PacketIdDump from, PacketIdDump to) {
        int max = maxId(from.playC2sNames());
        int[] table = new int[Math.max(max + 1, 0)];
        Arrays.fill(table, -1);
        if (!from.hasPlay() || !to.hasPlay()) {
            return new PacketIdRemapTable(table);
        }
        for (int id : from.playC2sNames().values()) {
            if (id >= 0 && id < table.length) {
                table[id] = PacketIdDump.remapPlayC2s(from, to, id);
            }
        }
        return new PacketIdRemapTable(table);
    }

    private static int maxId(java.util.Map<String, Integer> byName) {
        int max = -1;
        for (int id : byName.values()) {
            if (id > max) {
                max = id;
            }
        }
        return max;
    }
}
