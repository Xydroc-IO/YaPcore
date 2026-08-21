package com.yapcore.protocol.via.transform;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Rebuilds login success (and drives config skip) so older JE clients can finish
 * auth against Paper 26.2's {@code login_finished} (GameProfile + session UUID).
 */
public final class LoginSuccessRewriter {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaLogin");

    /** Paper 776 configuration serverbound: select_known_packs. */
    private static final int CONFIG_SB_SELECT_KNOWN_PACKS = 0x07;
    /** Paper 776 configuration serverbound: finish_configuration. */
    private static final int CONFIG_SB_FINISH = 0x03;
    /** Paper 776 configuration serverbound: client_information. */
    private static final int CONFIG_SB_CLIENT_INFORMATION = 0x00;

    private LoginSuccessRewriter() {
    }

    /**
     * @param bodyAfterId Paper login_finished body (no packet id)
     * @param clientPacketId login success id on the client band (usually 0x02)
     */
    public static ByteBuf rewrite(ViaSession session, ByteBuf bodyAfterId, int clientPacketId) {
        Profile profile = readPaperLoginFinished(bodyAfterId);
        if (profile == null) {
            LOG.warning("Via login_finished parse failed — forwarding raw (client may stall)");
            ByteBuf out = Unpooled.buffer(bodyAfterId.readableBytes() + 5);
            McCodec.writeVarInt(out, clientPacketId);
            out.writeBytes(bodyAfterId, bodyAfterId.readerIndex(), bodyAfterId.readableBytes());
            return out;
        }
        session.setUsername(profile.name);
        ProtocolBand client = session.clientBand();
        int proto = session.clientProtocol();
        ByteBuf out = Unpooled.buffer(64 + profile.propsBytesEstimate());
        McCodec.writeVarInt(out, clientPacketId);
        // Pre-1.16: UUID is a hyphenated string; 1.16+: binary UUID
        boolean stringUuid = proto < 735;
        // Properties array: 1.19+ (759+)
        boolean includeProperties = proto >= 759;
        if (stringUuid) {
            McCodec.writeString(out, profile.uuid.toString());
            McCodec.writeString(out, profile.name);
        } else {
            McCodec.writeUuid(out, profile.uuid);
            McCodec.writeString(out, profile.name);
            if (includeProperties) {
                McCodec.writeVarInt(out, profile.properties.size());
                for (Prop p : profile.properties) {
                    McCodec.writeString(out, p.name);
                    McCodec.writeString(out, p.value);
                    out.writeBoolean(p.signature != null);
                    if (p.signature != null) {
                        McCodec.writeString(out, p.signature);
                    }
                }
            }
            // 1.20.5–1.21.1: strictErrorHandling bool (removed again in 1.21.2+)
            if (proto >= 766 && proto <= 767) {
                out.writeBoolean(true);
            }
            if (client.loginIncludesSessionId()) {
                McCodec.writeUuid(out, profile.sessionId != null ? profile.sessionId : profile.uuid);
            }
        }
        if (!client.hasConfigurationPhase()) {
            session.armConfigSkip();
            session.setState(ConnState.LOGIN);
            LOG.info("Via login_success → legacy layout user=" + profile.name
                    + " band=" + client.name() + " stringUuid=" + stringUuid
                    + " (config-skip armed)");
        } else {
            LOG.info("Via login_success → modern profile user=" + profile.name
                    + " band=" + client.name()
                    + " props=" + includeProperties
                    + " sessionUuid=" + client.loginIncludesSessionId());
        }
        return out;
    }

    /** Parse Paper 26.2 login_finished: GameProfile + session UUID. */
    static Profile readPaperLoginFinished(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            UUID uuid = McCodec.readUuid(body);
            String name = McCodec.readString(body, 16);
            int n = McCodec.readVarInt(body);
            if (n < 0 || n > 16) {
                body.readerIndex(mark);
                return null;
            }
            List<Prop> props = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String pn = McCodec.readString(body, 64);
                String pv = McCodec.readString(body, 32767);
                String sig = null;
                if (body.readBoolean()) {
                    sig = McCodec.readString(body, 32767);
                }
                props.add(new Prop(pn, pv, sig));
            }
            UUID session = null;
            if (body.readableBytes() >= 16) {
                session = McCodec.readUuid(body);
            }
            // Ignore any trailing unknown bytes rather than failing
            return new Profile(uuid, name, props, session);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    /** Empty Login Acknowledged (id 0x03) for Paper after legacy login_success. */
    public static ByteBuf loginAcknowledged() {
        ByteBuf out = Unpooled.buffer(2);
        McCodec.writeVarInt(out, 0x03);
        return out;
    }

    /** Minimal Client Information for config (locale, view, chat, …). */
    public static ByteBuf configClientInformation() {
        ByteBuf out = Unpooled.buffer(32);
        McCodec.writeVarInt(out, CONFIG_SB_CLIENT_INFORMATION);
        McCodec.writeString(out, "en_us");
        out.writeByte(10); // view distance
        McCodec.writeVarInt(out, 0); // chat mode
        out.writeBoolean(true); // chat colors
        out.writeByte(0x7F); // skin parts
        McCodec.writeVarInt(out, 1); // main hand
        out.writeBoolean(true); // text filtering
        out.writeBoolean(true); // allows listing
        // 1.21+: particle status varint (0=all)
        McCodec.writeVarInt(out, 0);
        return out;
    }

    /** Empty Select Known Packs response. */
    public static ByteBuf configSelectKnownPacksEmpty() {
        ByteBuf out = Unpooled.buffer(4);
        McCodec.writeVarInt(out, CONFIG_SB_SELECT_KNOWN_PACKS);
        McCodec.writeVarInt(out, 0);
        return out;
    }

    /** Acknowledge Finish Configuration. */
    public static ByteBuf configFinishAck() {
        ByteBuf out = Unpooled.buffer(2);
        McCodec.writeVarInt(out, CONFIG_SB_FINISH);
        return out;
    }

    static final class Profile {
        final UUID uuid;
        final String name;
        final List<Prop> properties;
        final UUID sessionId;

        Profile(UUID uuid, String name, List<Prop> properties, UUID sessionId) {
            this.uuid = uuid;
            this.name = name;
            this.properties = properties;
            this.sessionId = sessionId;
        }

        int propsBytesEstimate() {
            int n = 16;
            for (Prop p : properties) {
                n += 64 + (p.value != null ? p.value.length() : 0)
                        + (p.signature != null ? p.signature.length() : 0);
            }
            return n;
        }
    }

    static final class Prop {
        final String name;
        final String value;
        final String signature;

        Prop(String name, String value, String signature) {
            this.name = name;
            this.value = value;
            this.signature = signature;
        }
    }
}
