package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Per-address RakNet connection state machine (open → connected → frames).
 */
public final class RakNetSessionManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.RakNet");

    public final class RakNetPeer {
        private final InetSocketAddress address;
        private final RakNetReliability.SessionState state = new RakNetReliability.SessionState();
        private volatile Phase phase = Phase.UNCONNECTED;
        /** After NetworkSettings, modern BE batches need a compression-method byte. */
        private volatile boolean gameCompressionHeader = false;

        public RakNetPeer(InetSocketAddress address) {
            this.address = address;
        }

        public InetSocketAddress address() {
            return address;
        }

        public RakNetReliability.SessionState state() {
            return state;
        }

        public Phase phase() {
            return phase;
        }

        public void setPhase(Phase phase) {
            this.phase = phase;
        }

        public boolean gameCompressionHeader() {
            return gameCompressionHeader;
        }

        public void setGameCompressionHeader(boolean enabled) {
            this.gameCompressionHeader = enabled;
        }
    }

    public enum Phase {
        UNCONNECTED,
        OPENING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private final long serverGuid;
    private final ConcurrentHashMap<String, RakNetPeer> peers = new ConcurrentHashMap<>();
    private BiConsumer<RakNetPeer, ByteBuf> gamePacketHandler = (p, b) -> {
    };

    public RakNetSessionManager(long serverGuid) {
        this.serverGuid = serverGuid;
    }

    public void setGamePacketHandler(BiConsumer<RakNetPeer, ByteBuf> handler) {
        this.gamePacketHandler = handler != null ? handler : (p, b) -> {
        };
    }

    public long serverGuid() {
        return serverGuid;
    }

    public RakNetPeer peer(InetSocketAddress address) {
        return peers.computeIfAbsent(key(address), k -> new RakNetPeer(address));
    }

    public void remove(InetSocketAddress address) {
        peers.remove(key(address));
    }

    public int size() {
        return peers.size();
    }

    /**
     * Handle one UDP datagram. Returns reply buffers to send (may be empty).
     */
    public List<ByteBuf> handle(InetSocketAddress sender, ByteBuf content) {
        if (!content.isReadable()) {
            return List.of();
        }
        int id = content.getUnsignedByte(content.readerIndex());
        RakNetPeer peer = peer(sender);

        if (id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_1) {
            content.readUnsignedByte();
            // magic + protocol + pad for MTU
            if (content.readableBytes() >= RakNetUnconnected.MAGIC.length) {
                content.skipBytes(RakNetUnconnected.MAGIC.length);
            }
            if (content.isReadable()) {
                content.readUnsignedByte(); // protocol version
            }
            int mtu = 28 + content.readableBytes() + 1 + RakNetUnconnected.MAGIC.length + 8 + 1 + 2;
            peer.state().setMtu(mtu);
            peer.setPhase(Phase.OPENING);
            return List.of(RakNetReliability.openConnectionReply1(serverGuid, false, peer.state().mtu()));
        }

        if (id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_2) {
            content.readUnsignedByte();
            if (content.readableBytes() >= RakNetUnconnected.MAGIC.length) {
                content.skipBytes(RakNetUnconnected.MAGIC.length);
            }
            try {
                RakNetReliability.readAddress(content);
                int mtu = content.readUnsignedShort();
                long guid = content.readLong();
                peer.state().setMtu(mtu);
                peer.state().setClientGuid(guid);
                peer.setPhase(Phase.CONNECTING);
                return List.of(RakNetReliability.openConnectionReply2(
                        serverGuid,
                        RakNetReliability.InetAddrCookie.of(sender),
                        peer.state().mtu(),
                        false));
            } catch (Exception e) {
                LOG.fine("OCR2 parse fail: " + e.getMessage());
                return List.of();
            }
        }

        if (RakNetReliability.isFrameSet(id)) {
            try {
                int peekLen = content.readableBytes();
                byte[] peek = new byte[Math.min(64, peekLen)];
                content.getBytes(content.readerIndex(), peek);
                RakNetReliability.DecodedFrameSet decoded =
                        RakNetReliability.decodeFrameSetEx(content, peer.state());
                List<ByteBuf> replies = new java.util.ArrayList<>();
                // Always ACK the datagram — split fragments also need ACKs or the client
                // stalls retransmitting the same part forever.
                if (decoded.datagramNumber() >= 0) {
                    replies.add(RakNetReliability.buildAck(decoded.datagramNumber()));
                }
                if (decoded.frames().isEmpty() && peekLen > 8) {
                    StringBuilder hex = new StringBuilder(peek.length * 2);
                    for (byte b : peek) {
                        hex.append(String.format("%02x", b & 0xff));
                    }
                    LOG.warning("RakNet 0 frames after decode bytes=" + peekLen + " hex=" + hex);
                }
                for (RakNetReliability.Frame frame : decoded.frames()) {
                    ByteBuf payload = frame.payload();
                    try {
                        if (!payload.isReadable()) {
                            continue;
                        }
                        int inner = payload.getUnsignedByte(payload.readerIndex());
                        if (inner == RakNetReliability.ID_CONNECTION_REQUEST) {
                            payload.readUnsignedByte();
                            long guid = payload.readLong();
                            long time = payload.readableBytes() >= 8 ? payload.readLong() : System.currentTimeMillis();
                            peer.state().setClientGuid(guid);
                            ByteBuf accept = RakNetReliability.connectionRequestAccepted(
                                    RakNetReliability.InetAddrCookie.of(sender),
                                    (short) 0,
                                    new RakNetReliability.InetAddrCookie[0],
                                    time,
                                    System.currentTimeMillis());
                            replies.add(RakNetReliability.wrapFrameSet(peer.state(),
                                    RakNetReliability.reliableOrdered(peer.state(), accept)));
                            peer.setPhase(Phase.CONNECTED);
                            peer.state().setConnected(true);
                            LOG.info("RakNet connected " + sender + " guid=" + Long.toHexString(guid));
                        } else if (inner == RakNetReliability.ID_NEW_INCOMING_CONNECTION) {
                            peer.setPhase(Phase.CONNECTED);
                            peer.state().setConnected(true);
                            LOG.info("RakNet new-incoming " + sender);
                        } else if (inner == RakNetReliability.ID_CONNECTED_PING) {
                            payload.readUnsignedByte();
                            long ping = payload.readLong();
                            peer.state().touchPing();
                            ByteBuf pong = RakNetReliability.connectedPong(ping, System.currentTimeMillis());
                            replies.add(RakNetReliability.wrapFrameSet(peer.state(),
                                    RakNetReliability.unreliable(pong)));
                        } else if (inner == RakNetReliability.ID_DISCONNECT) {
                            peer.setPhase(Phase.DISCONNECTED);
                            peer.state().setConnected(false);
                            remove(sender);
                        } else if (inner == 0xfe) {
                            payload.readUnsignedByte();
                            ByteBuf batch = payload;
                            if (peer.gameCompressionHeader() && payload.isReadable()) {
                                int method = payload.readUnsignedByte();
                                if (method == 0) {
                                    // deflate (raw) — inflate remaining into a new buffer
                                    batch = inflateRaw(payload);
                                    if (batch == null) {
                                        LOG.warning("BE deflate batch inflate failed from " + sender);
                                        continue;
                                    }
                                } else if (method != 0xff && method != 255) {
                                    LOG.warning("BE compressed batch method=" + method
                                            + " not supported — drop");
                                    continue;
                                }
                            }
                            LOG.info("RakNet game-batch from " + sender
                                    + " bytes=" + batch.readableBytes());
                            gamePacketHandler.accept(peer, batch.retainedDuplicate());
                            if (batch != payload) {
                                batch.release();
                            }
                        } else if (payload.readableBytes() > 64) {
                            // Likely assembled split without leading 0xfe (shouldn't happen)
                            LOG.info("RakNet large inner id=0x" + Integer.toHexString(inner)
                                    + " from " + sender + " bytes=" + payload.readableBytes());
                            gamePacketHandler.accept(peer, payload.retainedDuplicate());
                        } else {
                            LOG.info("RakNet inner id=0x" + Integer.toHexString(inner)
                                    + " from " + sender + " bytes=" + payload.readableBytes());
                            gamePacketHandler.accept(peer, payload.retainedDuplicate());
                        }
                    } finally {
                        payload.release();
                    }
                }
                return replies;
            } catch (Exception e) {
                LOG.warning("RakNet frame-set decode fail from " + sender + ": " + e.getMessage());
                return List.of();
            }
        }

        if (id == RakNetReliability.ID_ACK || id == RakNetReliability.ID_NACK) {
            // consume — retransmit scheduling later
            return List.of();
        }

        return List.of();
    }

    /**
     * Encapsulate a game batch into one or more RakNet datagrams (MTU-safe splits).
     * After NetworkSettings, prefixes compression method (0=deflate when large, else 255=none).
     */
    public List<ByteBuf> encapsulateGameDatagrams(RakNetPeer peer, ByteBuf gameBatch) {
        boolean header = peer != null && peer.gameCompressionHeader();
        ByteBuf body = gameBatch;
        int method = 0xff;
        if (header && gameBatch.readableBytes() >= 256) {
            ByteBuf deflated = deflateRaw(gameBatch);
            if (deflated != null && deflated.readableBytes() < gameBatch.readableBytes()) {
                body = deflated;
                method = 0;
            } else if (deflated != null) {
                deflated.release();
            }
        }
        ByteBuf wrapped = Unpooled.buffer(body.readableBytes() + (header ? 2 : 1));
        wrapped.writeByte(0xfe);
        if (header) {
            wrapped.writeByte(method);
        }
        wrapped.writeBytes(body);
        if (body != gameBatch) {
            body.release();
        }
        return RakNetReliability.wrapReliableOrderedPossiblySplit(peer.state(), wrapped);
    }

    /** Backward-compatible single-datagram helper (may exceed MTU — prefer encapsulateGameDatagrams). */
    public ByteBuf encapsulateGame(RakNetPeer peer, ByteBuf gameBatch) {
        List<ByteBuf> dgs = encapsulateGameDatagrams(peer, gameBatch);
        if (dgs.size() == 1) {
            return dgs.get(0);
        }
        // Concatenate only for callers that ignore splits (tests); production uses Datagrams API.
        ByteBuf first = dgs.get(0);
        for (int i = 1; i < dgs.size(); i++) {
            dgs.get(i).release();
        }
        return first;
    }

    private static ByteBuf deflateRaw(ByteBuf plain) {
        byte[] in = new byte[plain.readableBytes()];
        plain.getBytes(plain.readerIndex(), in);
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(in);
            deflater.finish();
            byte[] buf = new byte[Math.max(64, in.length)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(in.length / 2);
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                } else {
                    break;
                }
            }
            return Unpooled.wrappedBuffer(baos.toByteArray());
        } catch (Exception e) {
            return null;
        } finally {
            deflater.end();
        }
    }

    private static String key(InetSocketAddress a) {
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    /** Minecraft Bedrock batch compression method 0 = raw deflate (no zlib wrapper). */
    private static ByteBuf inflateRaw(ByteBuf compressed) {
        byte[] in = new byte[compressed.readableBytes()];
        compressed.getBytes(compressed.readerIndex(), in);
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
        try {
            inflater.setInput(in);
            byte[] buf = new byte[Math.max(8192, in.length * 4)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(buf.length);
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                    if (baos.size() > 16 * 1024 * 1024) {
                        return null;
                    }
                    continue;
                }
                if (inflater.needsInput()) {
                    break;
                }
                if (n == 0) {
                    break;
                }
            }
            if (baos.size() == 0) {
                return null;
            }
            return Unpooled.wrappedBuffer(baos.toByteArray());
        } catch (Exception e) {
            LOG.warning("inflateRaw: " + e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }
}
