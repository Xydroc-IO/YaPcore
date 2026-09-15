package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.ScoreInfo;
import org.cloudburstmc.protocol.bedrock.packet.BossEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDisplayObjectivePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetScorePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;

/**
 * JE title / action bar / boss bar / scoreboard → Bedrock UI packets (G.31 on Link-native).
 */
public final class JavaHudTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final long DEFAULT_BOSS_BASE = 0x7a6170636f7265L;
    /** Per-session boss UUID → Bedrock unique entity id. */
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> BOSS_IDS =
            new ConcurrentHashMap<>();

    private JavaHudTranslator() {
    }

    public static void onTitle(LinkBedrockSession session, String text) {
        sendTitle(session, SetTitlePacket.Type.TITLE, text, 0, 0, 0);
    }

    public static void onSubtitle(LinkBedrockSession session, String text) {
        sendTitle(session, SetTitlePacket.Type.SUBTITLE, text, 0, 0, 0);
    }

    public static void onActionBar(LinkBedrockSession session, String text) {
        sendTitle(session, SetTitlePacket.Type.ACTIONBAR, text, 0, 0, 0);
    }

    public static void onTitleTimes(LinkBedrockSession session, int fadeIn, int stay, int fadeOut) {
        // JE ticks → Bedrock expects ticks as well for TIMES.
        sendTitle(session, SetTitlePacket.Type.TIMES, "", fadeIn, stay, fadeOut);
    }

    public static void onClearTitles(LinkBedrockSession session, boolean reset) {
        sendTitle(session, reset ? SetTitlePacket.Type.RESET : SetTitlePacket.Type.CLEAR, "", 0, 0, 0);
    }

    public static void onBossEvent(LinkBedrockSession session, UUID bossId, int action,
                                   String title, float pct, int color) {
        if (session == null || !session.isSentSpawnPacket() || bossId == null) {
            return;
        }
        long beId = bossUnique(session, bossId);
        BossEventPacket packet = new BossEventPacket();
        packet.setBossUniqueEntityId(beId);
        packet.setPlayerUniqueEntityId(session.javaEntityId() & 0xffffffffL);
        switch (action) {
            case 0 -> { // ADD
                packet.setAction(BossEventPacket.Action.CREATE);
                packet.setTitle(title != null ? title : "");
                packet.setFilteredTitle(title != null ? title : "");
                packet.setHealthPercentage(clamp01(pct));
                packet.setColor(color >= 0 ? color : 2);
                packet.setOverlay(0);
            }
            case 1 -> packet.setAction(BossEventPacket.Action.REMOVE);
            case 2 -> { // UPDATE_PCT
                packet.setAction(BossEventPacket.Action.UPDATE_PERCENTAGE);
                packet.setHealthPercentage(clamp01(pct));
            }
            case 3 -> { // UPDATE_NAME
                packet.setAction(BossEventPacket.Action.UPDATE_NAME);
                packet.setTitle(title != null ? title : "");
                packet.setFilteredTitle(title != null ? title : "");
            }
            case 4, 5 -> { // STYLE / PROPERTIES
                packet.setAction(BossEventPacket.Action.UPDATE_STYLE);
                packet.setColor(color >= 0 ? color : 2);
                packet.setOverlay(0);
            }
            default -> {
                return;
            }
        }
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(), "java_boss→be action=" + action);
    }

    public static void onSetObjective(LinkBedrockSession session, String objectiveId,
                                      int mode, String displayName, String criteria) {
        if (session == null || !session.isSentSpawnPacket() || objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        if (mode == 1) {
            // Remove — clear sidebar display for this objective.
            SetDisplayObjectivePacket clear = new SetDisplayObjectivePacket();
            clear.setDisplaySlot("sidebar");
            clear.setObjectiveId("");
            clear.setDisplayName("");
            clear.setCriteria("dummy");
            clear.setSortOrder(0);
            session.sendUpstreamPacket(clear);
            return;
        }
        SetDisplayObjectivePacket packet = new SetDisplayObjectivePacket();
        packet.setDisplaySlot("sidebar");
        packet.setObjectiveId(objectiveId);
        packet.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : objectiveId);
        packet.setCriteria(criteria != null && !criteria.isBlank() ? criteria : "dummy");
        packet.setSortOrder(0);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(), "java_objective→be id=" + objectiveId + " mode=" + mode);
    }

    public static void onSetDisplayObjective(LinkBedrockSession session, int position, String objectiveId) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        String slot = switch (position) {
            case 0 -> "list";
            case 1 -> "sidebar";
            case 2 -> "belowname";
            default -> "sidebar";
        };
        SetDisplayObjectivePacket packet = new SetDisplayObjectivePacket();
        packet.setDisplaySlot(slot);
        packet.setObjectiveId(objectiveId != null ? objectiveId : "");
        packet.setDisplayName(objectiveId != null ? objectiveId : "");
        packet.setCriteria("dummy");
        packet.setSortOrder(0);
        session.sendUpstreamPacket(packet);
    }

    public static void onSetScore(LinkBedrockSession session, String owner, String objective, int score) {
        if (session == null || !session.isSentSpawnPacket()
                || objective == null || objective.isBlank() || owner == null) {
            return;
        }
        String line = trimLine(owner);
        long entryId = (objective + ":" + line).hashCode() & 0xffffffffL;
        SetScorePacket packet = new SetScorePacket();
        packet.setAction(SetScorePacket.Action.SET);
        packet.setInfos(List.of(new ScoreInfo(entryId, objective, score, line)));
        session.sendUpstreamPacket(packet);
    }

    public static void onResetScore(LinkBedrockSession session, String owner, String objective) {
        if (session == null || !session.isSentSpawnPacket() || owner == null) {
            return;
        }
        String obj = objective != null ? objective : "";
        String line = trimLine(owner);
        long entryId = (obj + ":" + line).hashCode() & 0xffffffffL;
        SetScorePacket packet = new SetScorePacket();
        packet.setAction(SetScorePacket.Action.REMOVE);
        packet.setInfos(List.of(new ScoreInfo(entryId, obj, 0, line)));
        session.sendUpstreamPacket(packet);
    }

    private static void sendTitle(LinkBedrockSession session, SetTitlePacket.Type type, String text,
                                  int fadeIn, int stay, int fadeOut) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        SetTitlePacket packet = new SetTitlePacket();
        packet.setType(type);
        packet.setText(text != null ? text : "");
        packet.setFilteredTitleText(text != null ? text : "");
        packet.setFadeInTime(fadeIn);
        packet.setStayTime(stay);
        packet.setFadeOutTime(fadeOut);
        packet.setXuid("");
        packet.setPlatformOnlineId("");
        session.sendUpstreamPacket(packet);
        LOG.fine("BE title type=" + type + " user=" + session.username());
    }

    private static long bossUnique(LinkBedrockSession session, UUID bossId) {
        ConcurrentHashMap<String, Long> map = BOSS_IDS.computeIfAbsent(
                Long.toString(session.guid()), g -> new ConcurrentHashMap<>());
        String key = bossId.toString();
        return map.computeIfAbsent(key, k -> DEFAULT_BOSS_BASE + (map.size() & 0xffff));
    }

    private static float clamp01(float pct) {
        if (Float.isNaN(pct)) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, pct));
    }

    private static String trimLine(String line) {
        String plain = line.replaceAll("§.", "");
        return plain.length() > 40 ? plain.substring(0, 40) : plain;
    }
}
