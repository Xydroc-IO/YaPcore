package com.yapcore.tab;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Cross-server TAB overlay received via {@code yap:tab} plugin messages. */
public final class TabNetworkState {

    private volatile String sourceServer = "";
    private volatile List<String> header = List.of();
    private volatile List<String> footer = List.of();
    private volatile List<String> sidebar = List.of();
    private volatile boolean sidebarEnabled = true;
    private volatile boolean nametagTeams = true;
    private volatile boolean active;

    public void apply(String serverId, List<String> header, List<String> footer, List<String> sidebar,
                      boolean sidebarEnabled, boolean nametagTeams) {
        this.sourceServer = serverId == null ? "" : serverId;
        this.header = List.copyOf(header);
        this.footer = List.copyOf(footer);
        this.sidebar = List.copyOf(sidebar);
        this.sidebarEnabled = sidebarEnabled;
        this.nametagTeams = nametagTeams;
        this.active = true;
    }

    public void clear() {
        active = false;
        sourceServer = "";
        header = List.of();
        footer = List.of();
        sidebar = List.of();
    }

    public boolean active() {
        return active;
    }

    public String sourceServer() {
        return sourceServer;
    }

    public List<String> header() {
        return header;
    }

    public List<String> footer() {
        return footer;
    }

    public List<String> sidebar() {
        return sidebar;
    }

    public boolean sidebarEnabled() {
        return sidebarEnabled;
    }

    public boolean nametagTeams() {
        return nametagTeams;
    }

    public static byte[] encodeSync(String serverId, List<String> header, List<String> footer,
                                    List<String> sidebar, boolean sidebarEnabled, boolean nametagTeams) {
        String payload = "SYNC|" + serverId + "|"
                + b64(header) + "|"
                + b64(footer) + "|"
                + b64(sidebar) + "|"
                + (sidebarEnabled ? "1" : "0") + "|"
                + (nametagTeams ? "1" : "0");
        return payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static Optional<SyncPacket> decode(byte[] data) {
        String payload = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        if (!payload.startsWith("SYNC|")) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|", 7);
        if (parts.length < 7) {
            return Optional.empty();
        }
        String serverId = parts[1];
        List<String> header = decodeLines(parts[2]);
        List<String> footer = decodeLines(parts[3]);
        List<String> sidebar = decodeLines(parts[4]);
        boolean sidebarEnabled = !"0".equals(parts[5]);
        boolean nametagTeams = !"0".equals(parts[6]);
        return Optional.of(new SyncPacket(serverId, header, footer, sidebar, sidebarEnabled, nametagTeams));
    }

    private static String b64(List<String> lines) {
        String joined = String.join("\n", lines == null ? List.<String>of() : lines);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<String> decodeLines(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(encoded);
            String joined = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            if (joined.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String line : joined.split("\n", -1)) {
                out.add(line);
            }
            return out;
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public record SyncPacket(
            String serverId,
            List<String> header,
            List<String> footer,
            List<String> sidebar,
            boolean sidebarEnabled,
            boolean nametagTeams) {
    }
}
