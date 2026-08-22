package com.yapcore.link.session;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks every player connected through YaP Link (all backends). */
public final class PlayerHub {

    public record PlayerRecord(
            UUID id,
            String username,
            String backendName,
            int protocolVersion,
            ChatSink chatSink
    ) {
    }

    /** Sends play-phase system chat to a connected player. */
    @FunctionalInterface
    public interface ChatSink {
        void sendSystemChat(String jsonText);
    }

    private final Map<UUID, PlayerRecord> byId = new ConcurrentHashMap<>();

    public void join(PlayerRecord record) {
        byId.put(record.id(), record);
    }

    public void leave(UUID id) {
        byId.remove(id);
    }

    public int onlineCount() {
        return byId.size();
    }

    public Collection<PlayerRecord> all() {
        return byId.values();
    }

    public void broadcastSystemChat(String jsonText, UUID except) {
        for (PlayerRecord r : byId.values()) {
            if (except != null && except.equals(r.id())) {
                continue;
            }
            r.chatSink().sendSystemChat(jsonText);
        }
    }

    public void broadcastPlain(String plain, UUID except) {
        String json = "{\"text\":" + quoteJson(plain) + "}";
        broadcastSystemChat(json, except);
    }

    private static String quoteJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
