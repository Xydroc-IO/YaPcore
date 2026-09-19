package com.yapcore.lib.packet;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet type constants. Nested {@code Client} / {@code Server} match ProtocolLib:
 * {@code Client} = from the player (serverbound), {@code Server} = to the player (clientbound).
 * Canonical names are Mojang protocol snake_case ({@code add_entity}). ProtocolLib aliases
 * ({@code SPAWN_ENTITY}, {@code USE_ENTITY}) point at the same instances.
 */
public final class PacketTypes {

    private static final Map<String, PacketType> CACHE = new ConcurrentHashMap<>();

    public static final PacketType ALL = PacketType.ALL;

    private PacketTypes() {
    }

    public static PacketType of(PacketState state, PacketDirection direction, String name) {
        String key = state.name() + "/" + (direction == null ? "*" : direction.name()) + "/"
                + name.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(key, k -> new PacketType(state, direction, name));
    }

    /** From the player (ProtocolLib {@code Client}). */
    public static PacketType client(PacketState state, String name) {
        return of(state, PacketDirection.SERVERBOUND, name);
    }

    /** To the player (ProtocolLib {@code Server}). */
    public static PacketType server(PacketState state, String name) {
        return of(state, PacketDirection.CLIENTBOUND, name);
    }

    public static PacketType playClient(String name) {
        return client(PacketState.PLAY, name);
    }

    public static PacketType playServer(String name) {
        return server(PacketState.PLAY, name);
    }

    public static PacketType handshake(String name) {
        return client(PacketState.HANDSHAKE, name);
    }

    public static PacketType loginClient(String name) {
        return client(PacketState.LOGIN, name);
    }

    public static PacketType loginServer(String name) {
        return server(PacketState.LOGIN, name);
    }

    public static PacketType statusClient(String name) {
        return client(PacketState.STATUS, name);
    }

    public static PacketType statusServer(String name) {
        return server(PacketState.STATUS, name);
    }

    public static PacketType configClient(String name) {
        return client(PacketState.CONFIGURATION, name);
    }

    public static PacketType configServer(String name) {
        return server(PacketState.CONFIGURATION, name);
    }

    public static PacketType commonClient(String name) {
        return client(PacketState.COMMON, name);
    }

    public static PacketType commonServer(String name) {
        return server(PacketState.COMMON, name);
    }

    public static final class Handshake {
        public static final class Client {
            public static final PacketType INTENTION = handshake("intention");
            public static final PacketType SET_PROTOCOL = INTENTION;

            private Client() {
            }
        }

        private Handshake() {
        }
    }

    public static final class Status {
        public static final class Client {
            public static final PacketType STATUS_REQUEST = statusClient("status_request");
            public static final PacketType PING_REQUEST = statusClient("ping_request");

            private Client() {
            }
        }

        public static final class Server {
            public static final PacketType STATUS_RESPONSE = statusServer("status_response");
            public static final PacketType PONG_RESPONSE = statusServer("pong_response");

            private Server() {
            }
        }

        private Status() {
        }
    }

    public static final class Login {
        public static final class Client {
            public static final PacketType HELLO = loginClient("hello");
            public static final PacketType KEY = loginClient("key");
            public static final PacketType CUSTOM_QUERY_ANSWER = loginClient("custom_query_answer");
            public static final PacketType LOGIN_ACKNOWLEDGED = loginClient("login_acknowledged");
            public static final PacketType COOKIE_RESPONSE = loginClient("cookie_response");

            private Client() {
            }
        }

        public static final class Server {
            public static final PacketType LOGIN_DISCONNECT = loginServer("login_disconnect");
            public static final PacketType HELLO = loginServer("hello");
            public static final PacketType LOGIN_FINISHED = loginServer("login_finished");
            public static final PacketType LOGIN_COMPRESSION = loginServer("login_compression");
            public static final PacketType CUSTOM_QUERY = loginServer("custom_query");
            public static final PacketType COOKIE_REQUEST = loginServer("cookie_request");

            private Server() {
            }
        }

        private Login() {
        }
    }

    public static final class Configuration {
        public static final class Client {
            public static final PacketType CLIENT_INFORMATION = configClient("client_information");
            public static final PacketType COOKIE_RESPONSE = configClient("cookie_response");
            public static final PacketType CUSTOM_PAYLOAD = configClient("custom_payload");
            public static final PacketType FINISH_CONFIGURATION = configClient("finish_configuration");
            public static final PacketType KEEP_ALIVE = configClient("keep_alive");
            public static final PacketType PONG = configClient("pong");
            public static final PacketType RESOURCE_PACK = configClient("resource_pack");
            public static final PacketType SELECT_KNOWN_PACKS = configClient("select_known_packs");

            private Client() {
            }
        }

        public static final class Server {
            public static final PacketType COOKIE_REQUEST = configServer("cookie_request");
            public static final PacketType CUSTOM_PAYLOAD = configServer("custom_payload");
            public static final PacketType DISCONNECT = configServer("disconnect");
            public static final PacketType FINISH_CONFIGURATION = configServer("finish_configuration");
            public static final PacketType KEEP_ALIVE = configServer("keep_alive");
            public static final PacketType PING = configServer("ping");
            public static final PacketType REGISTRY_DATA = configServer("registry_data");
            public static final PacketType RESOURCE_PACK_POP = configServer("resource_pack_pop");
            public static final PacketType RESOURCE_PACK_PUSH = configServer("resource_pack_push");
            public static final PacketType SELECT_KNOWN_PACKS = configServer("select_known_packs");
            public static final PacketType UPDATE_ENABLED_FEATURES = configServer("update_enabled_features");
            public static final PacketType UPDATE_TAGS = configServer("update_tags");

            private Server() {
            }
        }

        private Configuration() {
        }
    }

    /** PLAY. {@link Server} = to player, {@link Client} = from player. */
    public static final class Play {
        public static final class Server extends PacketTypesPlay.Server {
            private Server() {
            }
        }

        public static final class Client extends PacketTypesPlay.Client {
            private Client() {
            }
        }

        private Play() {
        }
    }
}
