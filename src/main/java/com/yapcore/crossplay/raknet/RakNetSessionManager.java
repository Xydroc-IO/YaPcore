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
            List<RakNetReliability.Frame> frames = RakNetReliability.decodeFrameSet(content, peer.state());
            List<ByteBuf> replies = new java.util.ArrayList<>();
            for (RakNetReliability.Frame frame : frames) {
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
                        // Bedrock game packet batch marker
                        payload.readUnsignedByte();
                        gamePacketHandler.accept(peer, payload.retainedDuplicate());
                    } else {
                        gamePacketHandler.accept(peer, payload.retainedDuplicate());
                    }
                } finally {
                    payload.release();
                }
            }
            return replies;
        }

        if (id == RakNetReliability.ID_ACK || id == RakNetReliability.ID_NACK) {
            // consume — retransmit scheduling later
            return List.of();
        }

        return List.of();
    }

    public ByteBuf encapsulateGame(RakNetPeer peer, ByteBuf gameBatch) {
        ByteBuf wrapped = Unpooled.buffer(gameBatch.readableBytes() + 1);
        wrapped.writeByte(0xfe);
        wrapped.writeBytes(gameBatch);
        return RakNetReliability.wrapFrameSet(peer.state(),
                RakNetReliability.reliableOrdered(peer.state(), wrapped));
    }

    private static String key(InetSocketAddress a) {
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }
}
