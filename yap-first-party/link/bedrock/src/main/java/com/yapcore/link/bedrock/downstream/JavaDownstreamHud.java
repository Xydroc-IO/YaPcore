package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JE title / boss / scoreboard / action-bar play packets → {@link JavaDownstreamClient.Listener}.
 */
final class JavaDownstreamHud {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDownstreamHud() {
    }

    /** @return true if {@code packetId} was a HUD packet (handled or skipped). */
    static boolean handle(JavaDownstreamClient client, int packetId, ByteBuf buf) {
        return switch (packetId) {
            case JavaPlayWire.CB_BOSS_EVENT -> {
                parseBossEvent(client, buf);
                yield true;
            }
            case JavaPlayWire.CB_CLEAR_TITLES -> {
                boolean reset = buf.isReadable() && buf.readBoolean();
                if (client.listener != null) {
                    client.listener.onClearTitles(reset);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_ACTION_BAR_TEXT -> {
                String plain = JavaDownstreamParse.tryPlainFromComponent(buf);
                if (client.listener != null && plain != null) {
                    client.listener.onActionBar(plain);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_TITLE_TEXT -> {
                String plain = JavaDownstreamParse.tryPlainFromComponent(buf);
                if (client.listener != null && plain != null) {
                    client.listener.onTitle(plain);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_SUBTITLE_TEXT -> {
                String plain = JavaDownstreamParse.tryPlainFromComponent(buf);
                if (client.listener != null && plain != null) {
                    client.listener.onSubtitle(plain);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_TITLES_ANIMATION -> {
                int fadeIn = buf.readableBytes() >= 4 ? buf.readInt() : 10;
                int stay = buf.readableBytes() >= 4 ? buf.readInt() : 70;
                int fadeOut = buf.readableBytes() >= 4 ? buf.readInt() : 20;
                if (client.listener != null) {
                    client.listener.onTitleTimes(fadeIn, stay, fadeOut);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_OBJECTIVE -> {
                parseSetObjective(client, buf);
                yield true;
            }
            case JavaPlayWire.CB_SET_DISPLAY_OBJECTIVE -> {
                int position = McCodec.readVarInt(buf);
                String objective = null;
                if (buf.isReadable()) {
                    objective = JavaDownstreamParse.safeString(buf);
                    if (objective != null && objective.isEmpty()) {
                        objective = null;
                    }
                }
                if (client.listener != null) {
                    client.listener.onSetDisplayObjective(position, objective);
                }
                yield true;
            }
            case JavaPlayWire.CB_SET_SCORE -> {
                String owner = JavaDownstreamParse.safeString(buf);
                String objective = JavaDownstreamParse.safeString(buf);
                int value = McCodec.readVarInt(buf);
                if (client.listener != null && owner != null && objective != null) {
                    client.listener.onSetScore(owner, objective, value);
                }
                yield true;
            }
            case JavaPlayWire.CB_RESET_SCORE -> {
                String owner = JavaDownstreamParse.safeString(buf);
                boolean hasObjective = buf.isReadable() && buf.readBoolean();
                String objective = hasObjective ? JavaDownstreamParse.safeString(buf) : null;
                if (client.listener != null && owner != null) {
                    client.listener.onResetScore(owner, objective);
                }
                yield true;
            }
            default -> false;
        };
    }

    private static void parseBossEvent(JavaDownstreamClient client, ByteBuf buf) {
        try {
            UUID id = McCodec.readUuid(buf);
            int action = McCodec.readVarInt(buf);
            String title = "";
            float pct = 1f;
            int color = 2;
            switch (action) {
                case 0 -> { // ADD
                    title = nullToEmpty(JavaDownstreamParse.tryPlainFromComponent(buf));
                    pct = buf.isReadable() ? buf.readFloat() : 1f;
                    color = buf.isReadable() ? McCodec.readVarInt(buf) : 2;
                    if (buf.isReadable()) {
                        McCodec.readVarInt(buf); // overlay
                    }
                    if (buf.isReadable()) {
                        buf.readBoolean(); // darken sky
                    }
                    if (buf.isReadable()) {
                        buf.readBoolean(); // play music
                    }
                }
                case 1 -> { // REMOVE — id only
                }
                case 2 -> pct = buf.isReadable() ? buf.readFloat() : 1f; // UPDATE_PCT
                case 3 -> title = nullToEmpty(JavaDownstreamParse.tryPlainFromComponent(buf)); // UPDATE_NAME
                case 4 -> { // UPDATE_STYLE
                    color = buf.isReadable() ? McCodec.readVarInt(buf) : 2;
                    if (buf.isReadable()) {
                        McCodec.readVarInt(buf);
                    }
                }
                case 5 -> { // UPDATE_PROPERTIES
                    if (buf.isReadable()) {
                        buf.readBoolean();
                    }
                    if (buf.isReadable()) {
                        buf.readBoolean();
                    }
                }
                default -> {
                    return;
                }
            }
            if (client.listener != null) {
                client.listener.onBossEvent(id, action, title, pct, color);
            }
        } catch (Exception e) {
            LOG.fine("JE boss_event parse skip: " + e.getMessage());
        }
    }

    private static void parseSetObjective(JavaDownstreamClient client, ByteBuf buf) {
        try {
            String objectiveId = JavaDownstreamParse.safeString(buf);
            byte mode = buf.isReadable() ? buf.readByte() : 0;
            String display = objectiveId;
            String criteria = "dummy";
            if (mode == 0 || mode == 2) {
                display = nullToEmpty(JavaDownstreamParse.tryPlainFromComponent(buf));
                if (mode == 0 && buf.isReadable()) {
                    criteria = JavaDownstreamParse.safeString(buf);
                    if (criteria == null || criteria.isBlank()) {
                        criteria = "dummy";
                    }
                }
                if (buf.isReadable()) {
                    McCodec.readVarInt(buf); // render type
                }
                if (mode == 0 && buf.isReadable()) {
                    buf.readBoolean(); // number format present — skip best-effort
                }
            }
            if (client.listener != null && objectiveId != null) {
                client.listener.onSetObjective(objectiveId, mode & 0xff, display, criteria);
            }
        } catch (Exception e) {
            LOG.fine("JE set_objective parse skip: " + e.getMessage());
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
