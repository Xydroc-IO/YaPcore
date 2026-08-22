package com.yapcore.protocol.via.id;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.protocol.via.id.dump.PacketIdDumpResources;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and caches {@link PacketIdDump} instances from vanilla protocol resources.
 */
final class PacketIdDumpLoader {

    private static final Logger LOG = Logger.getLogger("YaPcore.PacketDump");

    private static final ConcurrentHashMap<Integer, PacketIdDump> BY_PROTO = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> INDEX_RESOURCES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, List<String>> INDEX_COMPANIONS = new ConcurrentHashMap<>();
    private static volatile boolean indexLoaded;
    private static volatile int paperPinProtocol = 776;

    private PacketIdDumpLoader() {
    }

    static int paperPinProtocol() {
        ensureIndex();
        return paperPinProtocol;
    }

    static boolean hasExactDump(int protocol) {
        ensureIndex();
        return INDEX_RESOURCES.containsKey(protocol);
    }

    static PacketIdDump forProtocol(int protocol) {
        ensureIndex();
        return BY_PROTO.computeIfAbsent(protocol, PacketIdDumpLoader::load);
    }

    private static void ensureIndex() {
        if (indexLoaded) {
            return;
        }
        synchronized (PacketIdDumpLoader.class) {
            if (indexLoaded) {
                return;
            }
            loadIndex();
            indexLoaded = true;
        }
    }

    private static void loadIndex() {
        try (InputStream in = PacketIdDump.class.getClassLoader()
                .getResourceAsStream("protocol/vanilla/index.json")) {
            if (in == null) {
                LOG.fine("No protocol/vanilla/index.json — using hard-coded dump switch");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (root.has("paperPinProtocol")) {
                paperPinProtocol = root.get("paperPinProtocol").getAsInt();
            }
            if (root.has("dumps") && root.get("dumps").isJsonObject()) {
                for (var e : root.getAsJsonObject("dumps").entrySet()) {
                    int proto = Integer.parseInt(e.getKey());
                    JsonObject entry = e.getValue().getAsJsonObject();
                    if (entry.has("resource")) {
                        INDEX_RESOURCES.put(proto, entry.get("resource").getAsString());
                    }
                }
            }
            if (root.has("companions") && root.get("companions").isJsonObject()) {
                for (var e : root.getAsJsonObject("companions").entrySet()) {
                    int proto = Integer.parseInt(e.getKey());
                    List<String> paths = new java.util.ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        paths.add(el.getAsString());
                    }
                    INDEX_COMPANIONS.put(proto, List.copyOf(paths));
                }
            }
            LOG.info("Protocol dump index loaded: " + INDEX_RESOURCES.size()
                    + " dumps, paper pin=" + paperPinProtocol);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed loading protocol/vanilla/index.json", e);
        }
    }

    private static PacketIdDump load(int protocol) {
        String resource = resourceFor(protocol);
        if (resource == null) {
            return empty(protocol);
        }
        PacketIdDump primary = loadResource(protocol, resource);
        List<String> companions = INDEX_COMPANIONS.get(protocol);
        if (companions != null) {
            PacketIdDump merged = primary;
            for (String path : companions) {
                if (path.equals(resource)) {
                    continue;
                }
                merged = mergeById(merged, loadResource(protocol, path));
            }
            return merged;
        }
        // Legacy hard-coded companions when index absent
        if (protocol >= 776) {
            return mergeById(primary, loadResource(776, "protocol/vanilla/26.1/packets.json"));
        }
        if (protocol == 775) {
            return mergeById(primary, loadResource(775, "protocol/vanilla/26.2/packets.json"));
        }
        if (protocol == 765) {
            return mergeById(primary, loadResource(765, "protocol/vanilla/1.20.3/packets.json"));
        }
        if (protocol == 766) {
            return mergeById(primary, loadResource(766, "protocol/vanilla/1.21.1/packets.json"));
        }
        return primary;
    }

    private static PacketIdDump mergeById(PacketIdDump primary, PacketIdDump extra) {
        if (!extra.hasPlay()) {
            return primary;
        }
        Map<String, Integer> s2cName = new HashMap<>(primary.playS2cByName());
        Map<Integer, String> s2cId = new HashMap<>(primary.playS2cById());
        Map<String, Integer> c2sName = new HashMap<>(primary.playC2sByName());
        Map<Integer, String> c2sId = new HashMap<>(primary.playC2sById());
        for (var e : extra.playS2cNames().entrySet()) {
            s2cName.putIfAbsent(e.getKey(), e.getValue());
            s2cId.putIfAbsent(e.getValue(), e.getKey());
        }
        for (var e : extra.playC2sNames().entrySet()) {
            c2sName.putIfAbsent(e.getKey(), e.getValue());
            c2sId.putIfAbsent(e.getValue(), e.getKey());
        }
        // Also index every id under both dump names
        for (var e : extra.playS2cNames().entrySet()) {
            s2cName.put(e.getKey(), e.getValue());
        }
        for (var e : extra.playC2sNames().entrySet()) {
            c2sName.put(e.getKey(), e.getValue());
        }
        return new PacketIdDump(primary.protocol(), s2cName, s2cId, c2sName, c2sId,
                primary.loginS2cByName(), primary.loginS2cById());
    }

    private static PacketIdDump loadResource(int protocol, String resource) {
        try (InputStream in = PacketIdDump.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                LOG.fine("No packet dump resource " + resource);
                return empty(protocol);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject play = root.has("play") ? root.getAsJsonObject("play") : root;
            Map<String, Integer> s2cName = new HashMap<>();
            Map<Integer, String> s2cId = new HashMap<>();
            Map<String, Integer> c2sName = new HashMap<>();
            Map<Integer, String> c2sId = new HashMap<>();
            fill(play.getAsJsonObject("clientbound"), s2cName, s2cId);
            fill(play.getAsJsonObject("serverbound"), c2sName, c2sId);
            Map<String, Integer> loginS2cName = new HashMap<>();
            Map<Integer, String> loginS2cId = new HashMap<>();
            if (root.has("login")) {
                JsonObject login = root.getAsJsonObject("login");
                fill(login.getAsJsonObject("clientbound"), loginS2cName, loginS2cId);
            }
            int protoField = root.has("protocol") ? root.get("protocol").getAsInt() : protocol;
            LOG.fine(() -> "Loaded packet dump " + resource + " playS2C=" + s2cName.size());
            return new PacketIdDump(protoField, s2cName, s2cId, c2sName, c2sId, loginS2cName, loginS2cId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed loading " + resource, e);
            return empty(protocol);
        }
    }

    private static void fill(JsonObject side, Map<String, Integer> byName, Map<Integer, String> byId) {
        if (side == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : side.entrySet()) {
            String name = PacketIdDumpAliases.canonicalize(e.getKey());
            int id;
            JsonElement v = e.getValue();
            if (v.isJsonObject() && v.getAsJsonObject().has("protocol_id")) {
                id = v.getAsJsonObject().get("protocol_id").getAsInt();
            } else if (v.isJsonPrimitive()) {
                id = v.getAsInt();
            } else {
                continue;
            }
            byName.put(name, id);
            byId.putIfAbsent(id, name);
            String alias = PacketIdDumpAliases.ALIASES.get(name);
            if (alias != null) {
                byName.putIfAbsent(alias, id);
            }
            for (var a : PacketIdDumpAliases.ALIASES.entrySet()) {
                if (a.getValue().equals(name)) {
                    byName.putIfAbsent(a.getKey(), id);
                }
            }
        }
    }

    private static PacketIdDump empty(int protocol) {
        return new PacketIdDump(protocol, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static String resourceFor(int protocol) {
        ensureIndex();
        String fromIndex = INDEX_RESOURCES.get(protocol);
        if (fromIndex != null) {
            return fromIndex;
        }
        String hardcoded = PacketIdDumpResources.hardcodedResource(protocol);
        if (hardcoded != null) {
            return hardcoded;
        }
        // P4.10: future protocols — prefer highest indexed dump ≤ protocol, else nearestResource
        int best = -1;
        for (int p : INDEX_RESOURCES.keySet()) {
            if (p <= protocol && p > best) {
                best = p;
            }
        }
        if (best >= 0) {
            final int nearest = best;
            LOG.fine(() -> "P4.10 nearest indexed dump for proto " + protocol + " → " + nearest);
            return INDEX_RESOURCES.get(nearest);
        }
        return PacketIdDumpResources.nearestResource(protocol);
    }
}
