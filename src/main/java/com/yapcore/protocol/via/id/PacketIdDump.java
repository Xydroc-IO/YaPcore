package com.yapcore.protocol.via.id;

import com.yapcore.protocol.java.ProtocolBand;

import java.util.Collections;
import java.util.Map;

/**
 * Per-protocol play/login packet ID dumps (vanilla Mojang / minecraft-data).
 * Enables complete mid-band remaps by <em>packet name</em> rather than approximate buckets.
 */
public final class PacketIdDump {

    private final int protocol;
    private final Map<String, Integer> playS2cByName;
    private final Map<Integer, String> playS2cById;
    private final Map<String, Integer> playC2sByName;
    private final Map<Integer, String> playC2sById;
    private final Map<String, Integer> loginS2cByName;
    private final Map<Integer, String> loginS2cById;

    PacketIdDump(int protocol,
                 Map<String, Integer> playS2cByName,
                 Map<Integer, String> playS2cById,
                 Map<String, Integer> playC2sByName,
                 Map<Integer, String> playC2sById,
                 Map<String, Integer> loginS2cByName,
                 Map<Integer, String> loginS2cById) {
        this.protocol = protocol;
        this.playS2cByName = playS2cByName;
        this.playS2cById = playS2cById;
        this.playC2sByName = playC2sByName;
        this.playC2sById = playC2sById;
        this.loginS2cByName = loginS2cByName;
        this.loginS2cById = loginS2cById;
    }

    public int protocol() {
        return protocol;
    }

    public boolean hasPlay() {
        return !playS2cByName.isEmpty();
    }

    public String playS2cName(int id) {
        return playS2cById.get(id);
    }

    public String playC2sName(int id) {
        return playC2sById.get(id);
    }

    public int playS2cId(String name) {
        return PacketIdDumpAliases.resolveId(playS2cByName, name);
    }

    public int playC2sId(String name) {
        return PacketIdDumpAliases.resolveId(playC2sByName, name);
    }

    public String loginS2cName(int id) {
        return loginS2cById.get(id);
    }

    public int loginS2cId(String name) {
        return PacketIdDumpAliases.resolveId(loginS2cByName, name);
    }

    public Map<String, Integer> playS2cNames() {
        return Collections.unmodifiableMap(playS2cByName);
    }

    public Map<String, Integer> playC2sNames() {
        return Collections.unmodifiableMap(playC2sByName);
    }

    Map<String, Integer> playS2cByName() {
        return playS2cByName;
    }

    Map<Integer, String> playS2cById() {
        return playS2cById;
    }

    Map<String, Integer> playC2sByName() {
        return playC2sByName;
    }

    Map<Integer, String> playC2sById() {
        return playC2sById;
    }

    Map<String, Integer> loginS2cByName() {
        return loginS2cByName;
    }

    Map<Integer, String> loginS2cById() {
        return loginS2cById;
    }

    public static String canonicalize(String name) {
        return PacketIdDumpAliases.canonicalize(name);
    }

    /** Prefer exact protocol dump; else nearest lower known dump for the band. */
    public static PacketIdDump forProtocol(int protocol) {
        return PacketIdDumpLoader.forProtocol(protocol);
    }

    public static PacketIdDump forBand(ProtocolBand band) {
        // Prefer max protocol of band (most specific), then min
        PacketIdDump d = forProtocol(band.maxProtocol());
        if (d.hasPlay()) {
            return d;
        }
        return forProtocol(band.minProtocol());
    }

    /** Product Paper pin protocol from index (default 776). */
    public static int paperPinProtocol() {
        return PacketIdDumpLoader.paperPinProtocol();
    }

    /** True when index lists an exact dump for this protocol (not merely nearest). */
    public static boolean hasExactDump(int protocol) {
        return PacketIdDumpLoader.hasExactDump(protocol);
    }

    /**
     * Remap S2C play id from {@code fromProto} → {@code toProto} by packet name.
     * Uses a cached {@link PacketIdRemapTable} (hot path = array index).
     */
    public static int remapPlayS2c(int fromProto, int toProto, int fromId) {
        return PacketIdRemapTable.playS2c(fromProto, toProto).remap(fromId);
    }

    /**
     * Remap C2S play id from client proto → server proto by name.
     * Uses a cached {@link PacketIdRemapTable} (hot path = array index).
     */
    public static int remapPlayC2s(int fromProto, int toProto, int fromId) {
        return PacketIdRemapTable.playC2s(fromProto, toProto).remap(fromId);
    }

    /** Name-scan remap used to build {@link PacketIdRemapTable} (not on the packet hot path). */
    public static int remapPlayS2c(PacketIdDump from, PacketIdDump to, int fromId) {
        if (!from.hasPlay() || !to.hasPlay()) {
            return -1;
        }
        // Try every name registered for this id (Mojang + minecraft-data aliases)
        for (var e : from.playS2cNames().entrySet()) {
            if (e.getValue() != fromId) {
                continue;
            }
            int toId = to.playS2cId(e.getKey());
            if (toId >= 0) {
                return toId;
            }
        }
        String name = from.playS2cName(fromId);
        return name == null ? -1 : to.playS2cId(name);
    }

    /** Name-scan remap used to build {@link PacketIdRemapTable} (not on the packet hot path). */
    public static int remapPlayC2s(PacketIdDump from, PacketIdDump to, int fromId) {
        if (!from.hasPlay() || !to.hasPlay()) {
            return -1;
        }
        for (var e : from.playC2sNames().entrySet()) {
            if (e.getValue() != fromId) {
                continue;
            }
            int toId = to.playC2sId(e.getKey());
            if (toId >= 0) {
                return toId;
            }
        }
        String name = from.playC2sName(fromId);
        return name == null ? -1 : to.playC2sId(name);
    }
}
