package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.codec.BedrockUiCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mirrors title / bossbar / scoreboard commands to Bedrock UI packets (G.31). */
public final class BedrockUiBridge {

    private static final long DEFAULT_BOSS_ID = 0x7a6170636f7265L;

    private final BedrockBridgeContext ctx;
    private final Map<String, Long> bossIds = new ConcurrentHashMap<>();
    private final Map<String, Integer> titleTimes = new ConcurrentHashMap<>();
    private final Map<String, String> sidebarObjective = new ConcurrentHashMap<>();

    public BedrockUiBridge(BedrockBridgeContext ctx) {
        this.ctx = ctx;
    }

    /** Push action bar text directly to a connected Bedrock session (M5 MMO UI). */
    public void pushActionBar(String username, String text) {
        BedrockSessionManager.BedrockSession session = ctx.sessions.byUsername(username);
        if (session == null || text == null) {
            return;
        }
        ctx.send(session.guid(), BedrockPacketCodec.setTitle(
                BedrockUiCodec.TITLE_ACTIONBAR, text, 0, 0, 0));
    }

    /** Mirror a sidebar scoreboard to Bedrock (newest line = highest score value). */
    public void pushSidebar(String username, String objectiveId, String displayName, List<String> lines) {
        BedrockSessionManager.BedrockSession session = ctx.sessions.byUsername(username);
        if (session == null || lines == null || lines.isEmpty()) {
            return;
        }
        String obj = objectiveId == null || objectiveId.isBlank() ? "yapmmo" : objectiveId;
        String title = displayName == null || displayName.isBlank() ? "YaP MMO" : displayName;
        sidebarObjective.put(username.toLowerCase(Locale.ROOT), obj);
        List<ByteBuf> packets = new ArrayList<>();
        packets.add(BedrockPacketCodec.setDisplayObjective("sidebar", obj, title, "dummy", 0));
        int score = lines.size();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                score--;
                continue;
            }
            long entryId = (username + score).hashCode() & 0xFFFFFFFFL;
            packets.add(BedrockPacketCodec.setScore(
                    0, entryId, obj, score, BedrockUiCodec.SCORE_TYPE_FAKE, 0L, trimLine(line)));
            score--;
        }
        ctx.send(session.guid(), packets);
    }

    private static String trimLine(String line) {
        String plain = line.replaceAll("§.", "");
        return plain.length() > 40 ? plain.substring(0, 40) : plain;
    }

    void applyCommandUiHints(long guid, String username, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String cmd = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        if (cmd.isEmpty()) {
            return;
        }
        String lower = cmd.toLowerCase(Locale.ROOT);
        List<ByteBuf> packets = new ArrayList<>();
        if (lower.startsWith("title ")) {
            parseTitle(username, cmd.substring(6).trim(), packets);
        } else if (lower.startsWith("bossbar ")) {
            parseBossbar(username, cmd.substring(8).trim(), packets);
        } else if (lower.startsWith("scoreboard ")) {
            parseScoreboard(username, cmd.substring(11).trim(), packets);
        }
        if (!packets.isEmpty()) {
            ctx.send(guid, packets);
        }
    }

    private void parseTitle(String username, String rest, List<ByteBuf> out) {
        if (!targetsSelf(username, rest)) {
            return;
        }
        String[] parts = rest.split("\\s+", 3);
        if (parts.length < 2) {
            return;
        }
        int idx = isSelector(parts[0]) ? 1 : 0;
        if (parts.length <= idx + 1) {
            return;
        }
        String action = parts[idx].toLowerCase(Locale.ROOT);
        String text = parts.length > idx + 1 ? stripQuotes(parts[idx + 1]) : "";
        switch (action) {
            case "title" -> out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_SET, text, 0, 0, 0));
            case "subtitle" -> out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_SUBTITLE, text, 0, 0, 0));
            case "actionbar" -> out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_ACTIONBAR, text, 0, 0, 0));
            case "clear" -> out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_CLEAR, "", 0, 0, 0));
            case "reset" -> out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_RESET, "", 0, 0, 0));
            case "times" -> {
                String[] t = text.split("\\s+");
                int fadeIn = t.length > 0 ? parseInt(t[0], 10) : 10;
                int stay = t.length > 1 ? parseInt(t[1], 70) : 70;
                int fadeOut = t.length > 2 ? parseInt(t[2], 20) : 20;
                titleTimes.put(username.toLowerCase(Locale.ROOT), fadeIn << 16 | stay << 8 | fadeOut);
                out.add(BedrockPacketCodec.setTitle(BedrockUiCodec.TITLE_TIMES, "", fadeIn * 20, stay * 20, fadeOut * 20));
            }
            default -> { }
        }
    }

    private void parseBossbar(String username, String rest, List<ByteBuf> out) {
        String[] parts = rest.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String sub = parts[0].toLowerCase(Locale.ROOT);
        if ("add".equals(sub) && parts.length >= 3) {
            String id = parts[1];
            String title = stripQuotes(parts[2]);
            long bossId = bossIds.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> DEFAULT_BOSS_ID + bossIds.size());
            float pct = 1.0f;
            int color = 2; // purple
            if (parts.length >= 5 && "progress".equalsIgnoreCase(parts[parts.length - 1])) {
                try {
                    pct = Float.parseFloat(parts[parts.length - 2]) / 100f;
                } catch (NumberFormatException ignored) {
                    pct = 1.0f;
                }
            }
            out.add(BedrockPacketCodec.bossEventShow(bossId, title, pct, color));
        } else if ("set".equals(sub) && parts.length >= 4) {
            String id = parts[1].toLowerCase(Locale.ROOT);
            Long bossId = bossIds.get(id);
            if (bossId == null) {
                return;
            }
            String field = parts[2].toLowerCase(Locale.ROOT);
            if ("name".equals(field) || "title".equals(field)) {
                out.add(BedrockPacketCodec.bossEventTitle(bossId, stripQuotes(parts[3])));
            } else if ("value".equals(field) || "max".equals(field)) {
                float pct = parseInt(parts[3], 100) / 100f;
                out.add(BedrockPacketCodec.bossEventHealth(bossId, Math.max(0f, Math.min(1f, pct))));
            } else if ("players".equals(field) && targetsSelf(username, rest.substring(rest.indexOf("players") + 7))) {
                out.add(BedrockPacketCodec.bossEventShow(bossId, id, 1.0f, 2));
            }
        } else if ("remove".equals(sub) && parts.length >= 2) {
            Long bossId = bossIds.remove(parts[1].toLowerCase(Locale.ROOT));
            if (bossId != null) {
                out.add(BedrockPacketCodec.bossEventHide(bossId));
            }
        }
    }

    private void parseScoreboard(String username, String rest, List<ByteBuf> out) {
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("objectives add ")) {
            String[] p = rest.substring(15).trim().split("\\s+", 3);
            if (p.length >= 2) {
                String obj = p[0];
                String crit = p.length >= 3 ? p[1] : "dummy";
                String disp = p.length >= 3 ? stripQuotes(p[2]) : obj;
                if (p.length == 2) {
                    disp = stripQuotes(p[1]);
                    crit = "dummy";
                }
                out.add(BedrockPacketCodec.setDisplayObjective("sidebar", obj, disp, crit, 0));
            }
        } else if (lower.startsWith("objectives setdisplay ")) {
            String[] p = rest.substring(22).trim().split("\\s+", 2);
            if (p.length >= 2) {
                out.add(BedrockPacketCodec.setDisplayObjective(p[0], p[1], p[1], "dummy", 0));
            }
        } else if (lower.startsWith("players set ")) {
            String[] p = rest.substring(12).trim().split("\\s+");
            if (p.length >= 3 && targetsSelf(username, p[0])) {
                String obj = p[p.length - 2];
                int score = parseInt(p[p.length - 1], 0);
                long entryId = username.hashCode() & 0xFFFFFFFFL;
                out.add(BedrockPacketCodec.setScore(0, entryId, obj, score,
                        BedrockUiCodec.SCORE_TYPE_FAKE, 0L, username));
            }
        }
    }

    static boolean targetsSelf(String username, String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String t = token.trim().split("\\s+")[0];
        if ("@s".equalsIgnoreCase(t) || "@p".equalsIgnoreCase(t)) {
            return true;
        }
        return username.equalsIgnoreCase(t);
    }

    private static boolean isSelector(String token) {
        return token != null && token.startsWith("@");
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
