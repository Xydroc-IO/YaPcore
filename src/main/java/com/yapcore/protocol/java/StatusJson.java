package com.yapcore.protocol.java;

import com.yapcore.config.ServerConfig;
import com.yapcore.protocol.ProtocolVersionRegistry;

/**
 * Server list ping JSON for vanilla / Fabric / NeoForge multiplayer screens.
 */
public final class StatusJson {

    private StatusJson() {
    }

    public static String build(ServerConfig config,
                               ProtocolVersionRegistry protocols,
                               int clientProtocol,
                               int online) {
        var resolved = protocols.resolve(com.yapcore.client.ClientEdition.JAVA, clientProtocol);
        int proto = resolved.map(ProtocolVersionRegistry.ProtocolVersion::protocolId).orElse(clientProtocol);
        String ver = resolved.map(ProtocolVersionRegistry.ProtocolVersion::minecraftVersion).orElse("1.21");
        String name = escape(config.getServerName());
        String motd = escape(config.getMotd());
        boolean shared = config.isSharedListenPort();
        String desc = motd + (shared ? " §a· Crossplay" : "") + " §7· Yapcore";
        return "{"
                + "\"version\":{\"name\":\"" + name + " " + ver + "\",\"protocol\":" + proto + "},"
                + "\"players\":{\"max\":" + config.getMaxPlayers() + ",\"online\":" + online + ",\"sample\":[]},"
                + "\"description\":{\"text\":\"" + desc + "\"},"
                + "\"enforcesSecureChat\":false,"
                + "\"preventsChatReports\":true"
                + "}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
