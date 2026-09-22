package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.util.UUID;
import java.util.logging.Logger;

/** Play-phase clientbound dispatch (split from {@link JavaDownstreamClient}). */
final class JavaDownstreamPlay {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final JavaDownstreamClient client;

    JavaDownstreamPlay(JavaDownstreamClient client) {
        this.client = client;
    }

    void handlePlay(ChannelHandlerContext ctx, ByteBuf buf) {
        // Survive Folia packet-id drift: sniff Connect anywhere in the play frame (native Link does this).
        if (trySniffBungeeConnect(buf)) {
            if (buf.refCnt() > 0) {
                buf.release();
            }
            return;
        }
        int packetId = McCodec.readVarInt(buf);
        try {
            switch (packetId) {
                case JavaPlayWire.CB_DISCONNECT -> {
                    String reason = JavaDownstreamParse.safeString(buf);
                    client.fail(reason);
                    return;
                }
                case JavaPlayWire.CB_KEEP_ALIVE -> {
                    // Reply immediately on the Netty event loop — chunk translation must never
                    // starve this (Folia kicks at paper.playerconnection.keepalive=30s).
                    long id = buf.readLong();
                    ByteBuf ka = Unpooled.buffer(16);
                    McCodec.writeVarInt(ka, JavaPlayWire.SB_KEEP_ALIVE);
                    ka.writeLong(id);
                    ctx.writeAndFlush(ka);
                    LOG.fine("JE keep_alive pong id=" + id + " user=" + client.username);
                    return;
                }
                case JavaPlayWire.CB_PING -> {
                    // ignore body
                    return;
                }
                case JavaPlayWire.CB_LOGIN -> {
                    JavaDownstreamClient.LoginPlayInfo info = JavaDownstreamNbt.parseLoginPlay(client, buf);
                    client.javaEntityId = info.entityId();
                    LOG.info("JE Login Play entityId=" + info.entityId()
                            + " view=" + info.viewDistance()
                            + " dim=" + info.dimensionName()
                            + " user=" + client.username);
                    // Must register before any portal Connect — Folia gates on CraftPlayer.channels().
                    client.ensureProxyChannelsRegistered();
                    if (client.listener != null) {
                        client.listener.onLoginPlay(info);
                    }
                    return;
                }
                case JavaPlayWire.CB_RESPAWN -> {
                    JavaDownstreamClient.RespawnInfo info = JavaDownstreamNbt.parseRespawn(buf);
                    LOG.info("JE Respawn dim=" + info.dimensionName()
                            + " type=" + info.dimensionType()
                            + " dataKept=" + info.dataKept()
                            + " user=" + client.username);
                    if (client.listener != null) {
                        client.listener.onRespawn(info);
                    }
                    return;
                }
                case JavaPlayWire.CB_LEVEL_CHUNK -> {
                    int chunkX = buf.readInt();
                    int chunkZ = buf.readInt();
                    ByteBuf payload = buf.readableBytes() > 0
                            ? buf.readRetainedSlice(buf.readableBytes())
                            : Unpooled.EMPTY_BUFFER;
                    LOG.fine("JE LevelChunk cx=" + chunkX + " cz=" + chunkZ
                            + " bytes=" + payload.readableBytes()
                            + " user=" + client.username);
                    if (client.listener != null) {
                        // Offload translate off the JE inbound loop so keep_alive replies
                        // are not stuck behind thousands of chunk conversions.
                        final ByteBuf chunkPayload = payload;
                        client.chunkExecutor().execute(() -> {
                            try {
                                client.listener.onLevelChunk(chunkX, chunkZ, chunkPayload);
                            } catch (Throwable t) {
                                if (chunkPayload.refCnt() > 0) {
                                    chunkPayload.release();
                                }
                                LOG.log(java.util.logging.Level.WARNING,
                                        "JE LevelChunk translate fail cx=" + chunkX
                                                + " cz=" + chunkZ + " user=" + client.username, t);
                            }
                        });
                    } else {
                        payload.release();
                    }
                    return;
                }
                case 0 -> {
                    // bundle_delimiter — ignore
                    return;
                }
                case 11, 12 -> {
                    // chunk_batch_start / finished — body ignored; LevelChunk packets follow individually
                    return;
                }
                case JavaPlayWire.CB_BLOCK_ENTITY_DATA -> {
                    if (client.listener != null) {
                        client.listener.onBlockEntityData(buf);
                    }
                    return;
                }
                case JavaPlayWire.CB_BLOCK_UPDATE -> {
                    long pos = buf.readLong();
                    int state = McCodec.readVarInt(buf);
                    if (client.listener != null) {
                        client.listener.onBlockUpdate(
                                JavaPlayWire.unpackBlockX(pos),
                                JavaPlayWire.unpackBlockY(pos),
                                JavaPlayWire.unpackBlockZ(pos),
                                state);
                    }
                    return;
                }
                case JavaPlayWire.CB_SECTION_BLOCKS_UPDATE -> {
                    long section = buf.readLong();
                    int sectionX = (int) (section >> 42);
                    int sectionY = (int) (section << 44 >> 44);
                    int sectionZ = (int) (section << 22 >> 42);
                    if (client.listener != null) {
                        client.listener.onSectionBlocksUpdate(sectionX, sectionY, sectionZ);
                    }
                    return;
                }
                case JavaPlayWire.CB_PLAYER_POSITION -> {
                    // Proto 776: teleportId, PositionMoveRotation (pos+delta+yaw/pitch), relatives int
                    int teleportId = McCodec.readVarInt(buf);
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    if (buf.readableBytes() >= 24) {
                        buf.readDouble();
                        buf.readDouble();
                        buf.readDouble();
                    }
                    float yaw = buf.isReadable() ? buf.readFloat() : 0f;
                    float pitch = buf.readableBytes() >= 4 ? buf.readFloat() : 0f;
                    if (buf.readableBytes() >= 4) {
                        buf.readInt(); // Relative flags bitmask
                    }
                    // Geyser: accept once per id + echo pos/rot with onGround=false.
                    // A second AcceptTeleport with the same id after Folia cleared
                    // awaitingPositionFromClient → "Invalid move player packet".
                    if (teleportId == client.lastAcceptedTeleportId) {
                        return;
                    }
                    client.lastAcceptedTeleportId = teleportId;
                    ctx.writeAndFlush(JavaPlayWire.acceptTeleport(teleportId));
                    ctx.writeAndFlush(JavaPlayWire.movePosRot(x, y, z, yaw, pitch, false));
                    if (client.listener != null) {
                        client.listener.onPlayerPosition(x, y, z, yaw, pitch, teleportId);
                    }
                    return;
                }
                case JavaPlayWire.CB_OPEN_SCREEN -> {
                    int windowId = McCodec.readVarInt(buf);
                    int menuType = McCodec.readVarInt(buf);
                    JavaDownstreamParse.tryPlainFromComponent(buf); // skip title
                    if (client.listener != null) {
                        client.listener.onOpenScreen(windowId, menuType);
                    }
                    return;
                }
                case JavaPlayWire.CB_COMMANDS -> {
                    java.util.List<String> literals = JavaDownstreamNbt.extractCommandLiterals(buf);
                    if (client.listener != null && !literals.isEmpty()) {
                        client.listener.onCommands(literals);
                    }
                    return;
                }
                case JavaPlayWire.CB_CONTAINER_SET_CONTENT -> {
                    int windowId = McCodec.readVarInt(buf);
                    int stateId = McCodec.readVarInt(buf);
                    client.lastContainerStateId = Math.max(0, stateId);
                    int count = McCodec.readVarInt(buf);
                    count = Math.max(0, Math.min(count, 128));
                    JeItemStackCodec.Stack[] stacks = new JeItemStackCodec.Stack[count];
                    boolean ok = true;
                    for (int i = 0; i < count; i++) {
                        try {
                            stacks[i] = JeItemStackCodec.readSlot(buf);
                        } catch (Exception e) {
                            LOG.fine("JE container_set_content slot " + i + " parse fail: " + e.getMessage());
                            stacks[i] = JeItemStackCodec.Stack.AIR;
                            ok = false;
                            // Fill remaining as air — wire may be desynced.
                            for (int j = i + 1; j < count; j++) {
                                stacks[j] = JeItemStackCodec.Stack.AIR;
                            }
                            break;
                        }
                    }
                    if (ok && buf.isReadable()) {
                        try {
                            JeItemStackCodec.readSlot(buf); // carried item
                        } catch (Exception ignored) {
                            // leave
                        }
                    }
                    if (client.listener != null) {
                        client.listener.onContainerSetContent(windowId, stacks);
                    }
                    return;
                }
                case JavaPlayWire.CB_CONTAINER_SET_SLOT -> {
                    int windowId = buf.readByte();
                    int stateId = McCodec.readVarInt(buf);
                    client.lastContainerStateId = Math.max(0, stateId);
                    short slot = buf.readShort();
                    JeItemStackCodec.Stack stack = JeItemStackCodec.Stack.AIR;
                    try {
                        stack = JeItemStackCodec.readSlot(buf);
                    } catch (Exception e) {
                        LOG.fine("JE container_set_slot parse fail: " + e.getMessage());
                    }
                    if (client.listener != null) {
                        client.listener.onContainerSetSlot(windowId, slot, stack);
                    }
                    return;
                }
                case JavaPlayWire.CB_SET_HEALTH -> {
                    float health = buf.readFloat();
                    int food = McCodec.readVarInt(buf);
                    float saturation = buf.isReadable() ? buf.readFloat() : 0f;
                    if (client.listener != null) {
                        client.listener.onSetHealth(health, food, saturation);
                    }
                    return;
                }
                case JavaPlayWire.CB_SYSTEM_CHAT -> {
                    String plain = JavaDownstreamParse.tryPlainFromComponent(buf);
                    boolean overlay = buf.isReadable() && buf.readBoolean();
                    if (overlay) {
                        if (client.listener != null && plain != null) {
                            client.listener.onActionBar(plain);
                        }
                        return;
                    }
                    if (client.listener != null && plain != null && !plain.isBlank()) {
                        client.listener.onSystemChat(plain);
                    }
                    return;
                }
                case JavaPlayWire.CB_PLAYER_CHAT -> {
                    // Proto 776: globalIndex, sender UUID, index, optional signature,
                    // SignedMessageBody (utf content + instant + salt + last-seen), …
                    try {
                        McCodec.readVarInt(buf); // globalIndex
                        UUID sender = McCodec.readUuid(buf);
                        McCodec.readVarInt(buf); // index
                        if (buf.isReadable() && buf.readBoolean()) {
                            buf.skipBytes(Math.min(256, buf.readableBytes())); // signature
                        }
                        String content = buf.isReadable()
                                ? McCodec.readString(buf, 256) : "";
                        // Skip timestamp/salt/last-seen/unsigned/filter/chatType — content is enough.
                        String source = sender != null ? sender.toString() : "player";
                        if (client.listener != null && content != null && !content.isBlank()) {
                            client.listener.onPlayerChat(source, content);
                        }
                    } catch (Exception e) {
                        LOG.fine("JE player_chat parse skip: " + e.getMessage());
                    }
                    return;
                }
                case JavaPlayWire.CB_LEVEL_EVENT -> {
                    // event VarInt, BlockPos long, data Int
                    int eventId = McCodec.readVarInt(buf);
                    long pos = buf.readableBytes() >= 8 ? buf.readLong() : 0L;
                    int data = buf.readableBytes() >= 4 ? buf.readInt() : 0;
                    int x = (int) (pos >> 38);
                    int y = (int) (pos << 52 >> 52);
                    int z = (int) (pos << 26 >> 38);
                    if (client.listener != null) {
                        client.listener.onLevelEvent(eventId, x, y, z, data);
                    }
                    return;
                }
                case JavaPlayWire.CB_SOUND, JavaPlayWire.CB_SOUND_ENTITY -> {
                    // Best-effort: sound id as VarInt (registry) or string depending on packet.
                    // Proto 776 sound: sound holder/id, source, pos floats or entity, volume, pitch.
                    try {
                        int soundId = McCodec.readVarInt(buf);
                        McCodec.readVarInt(buf); // source
                        double x;
                        double y;
                        double z;
                        if (packetId == JavaPlayWire.CB_SOUND_ENTITY) {
                            McCodec.readVarInt(buf); // entity id
                            x = 0;
                            y = 0;
                            z = 0;
                        } else {
                            x = buf.readInt() / 8.0;
                            y = buf.readInt() / 8.0;
                            z = buf.readInt() / 8.0;
                        }
                        if (client.listener != null) {
                            // Registry id unknown without mappings — use numeric token for hit/break heuristics.
                            client.listener.onSound("je_sound_" + soundId, x, y, z);
                        }
                    } catch (Exception ignored) {
                        // leave unread
                    }
                    return;
                }
                case JavaPlayWire.CB_PLAYER_INFO -> {
                    JavaDownstreamNbt.parsePlayerInfo(client, buf);
                    return;
                }
                case JavaPlayWire.CB_PLAYER_REMOVE -> {
                    int n = McCodec.readVarInt(buf);
                    for (int i = 0; i < n && buf.isReadable(); i++) {
                        UUID id = McCodec.readUuid(buf);
                        if (client.listener != null) {
                            client.listener.onPlayerInfoRemove(id);
                        }
                    }
                    return;
                }
                default -> {
                    if (JavaDownstreamPlayEntities.handle(client, packetId, buf)) {
                        return;
                    }
                    if (JavaDownstreamHud.handle(client, packetId, buf)) {
                        return;
                    }
                    if (JavaDownstreamEntityExtras.handle(client, packetId, buf)) {
                        return;
                    }
                    // accept / ignore
                }
            }
        } finally {
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    /**
     * Catch BungeeCord Connect when custom_payload packet id drifted off {@link JavaPlayWire#CB_CUSTOM_PAYLOAD}.
     * @return true if Connect was handled (caller must release {@code buf})
     */
    private boolean trySniffBungeeConnect(ByteBuf buf) {
        if (buf == null || !buf.isReadable() || client.listener == null) {
            return false;
        }
        buf.markReaderIndex();
        try {
            int packetId = McCodec.readVarInt(buf);
            // Candidate custom_payload ids across 1.20.5–26.2 (never probe add_entity = 1).
            if (packetId != JavaPlayWire.CB_CUSTOM_PAYLOAD
                    && packetId != 0x17 && packetId != 0x19 && packetId != 0x1A
                    && packetId != 0x6E && packetId != 0x6B) {
                return false;
            }
            byte[] hay = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), hay);
            var target = BedrockBungeeConnect.sniffTarget(hay);
            if (target.isEmpty()) {
                return false;
            }
            LOG.info("JE BungeeCord Connect sniff → " + target.get()
                    + " user=" + client.username + " packetId=" + packetId);
            client.listener.onBungeeConnect(target.get());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            buf.resetReaderIndex();
        }
    }

}
