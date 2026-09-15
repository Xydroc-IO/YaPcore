package com.yapcore.crossplay.raknet;

import com.yapcore.crossplay.bedrock.crypto.BedrockEncryption;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import javax.crypto.SecretKey;

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
        /**
         * After ServerToClientHandshake (Geyser enableEncryption). Encrypts outbound batches
         * after compression; decrypts inbound before compression header.
         */
        private volatile BedrockEncryption.PeerCipher encryption;
        /** Outbound encrypt-applied log budget (prove S2C encryption on wire). */
        private final java.util.concurrent.atomic.AtomicInteger encryptAppliedLogs =
                new java.util.concurrent.atomic.AtomicInteger();

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

        public boolean encryptionEnabled() {
            return encryption != null;
        }

        /**
         * Cloudburst {@code BedrockPeer.enableEncryption}: AES/CTR when protocol ≥428.
         */
        public void enableEncryption(SecretKey key, int protocolVersion) throws Exception {
            if (encryption != null) {
                throw new IllegalStateException("Encryption is already enabled");
            }
            if (key == null || !"AES".equals(key.getAlgorithm())) {
                throw new IllegalArgumentException("Invalid key algorithm");
            }
            boolean useCtr = protocolVersion >= BedrockEncryption.CTR_PROTOCOL_FLOOR;
            encryption = new BedrockEncryption.PeerCipher(key, useCtr);
            LOG.info("RakNet encryption enabled " + address + " ctr=" + useCtr
                    + " proto=" + protocolVersion);
        }

        public BedrockEncryption.PeerCipher encryption() {
            return encryption;
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
    private java.util.function.Consumer<RakNetPeer> disconnectHandler = p -> {
    };

    public RakNetSessionManager(long serverGuid) {
        this.serverGuid = serverGuid;
    }

    public void setGamePacketHandler(BiConsumer<RakNetPeer, ByteBuf> handler) {
        this.gamePacketHandler = handler != null ? handler : (p, b) -> {
        };
    }

    public void setDisconnectHandler(java.util.function.Consumer<RakNetPeer> handler) {
        this.disconnectHandler = handler != null ? handler : p -> {
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
            LOG.info("RakNet OCR1 from " + sender + " mtu≈" + peer.state().mtu());
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
                LOG.info("RakNet OCR2 from " + sender + " guid=" + Long.toHexString(guid)
                        + " mtu=" + mtu);
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
                if (decoded.frames().isEmpty() && decoded.splitFragmentsAccepted() == 0 && peekLen > 8) {
                    StringBuilder hex = new StringBuilder(peek.length * 2);
                    for (byte b : peek) {
                        hex.append(String.format("%02x", b & 0xff));
                    }
                    LOG.warning("RakNet 0 frames after decode bytes=" + peekLen
                            + " splitsAccepted=" + decoded.splitFragmentsAccepted()
                            + " dg#" + decoded.datagramNumber()
                            + " hex=" + hex);
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
                            LOG.info("RakNet client disconnect " + sender
                                    + " guid=" + Long.toHexString(peer.state().clientGuid()));
                            try {
                                disconnectHandler.accept(peer);
                            } catch (Exception e) {
                                LOG.warning("RakNet disconnect handler: " + e.getMessage());
                            }
                            remove(sender);
                        } else if (inner == 0xfe) {
                            payload.readUnsignedByte();
                            ByteBuf afterFe = payload;
                            boolean decryptedOk = false;
                            // JOIN INVARIANT #2 (locked 22:18 empty-world path — do not regress to IC-90):
                            // Speculative decrypt + trailer verify. CTR is NOT advanced on trailer miss.
                            // On trailer miss: ALWAYS process as plaintext leftover (Login retransmit /
                            // mid-join race). NEVER drop when inboundSynchronized — that drop caused
                            // post-connect IC-90 (SetLocalPlayerAsInitialized never arrived).
                            // On unknown compression method AFTER decrypt: restore header (race).
                            // Do NOT re-add "drop unknown after decrypt".
                            if (peer.encryptionEnabled()) {
                                try {
                                    boolean wasSynced = peer.encryption().inboundSynchronized();
                                    afterFe = peer.encryption().decrypt(payload);
                                    decryptedOk = true;
                                    if (!wasSynced) {
                                        LOG.info("BE decrypt OK first inbound from " + sender
                                                + " bytes=" + afterFe.readableBytes()
                                                + " (ClientToServerHandshake / first enc batch)");
                                    }
                                } catch (BedrockEncryption.InvalidEncryptionTrailerException e) {
                                    // Speculative decrypt left CTR unmoved — plaintext race (22:18).
                                    boolean synced = peer.encryption().inboundSynchronized();
                                    LOG.info("BE enc trailer miss from " + sender
                                            + (synced
                                            ? " (plaintext race after sync; CTR unmoved — NOT drop)"
                                            : " (plaintext leftover before first enc batch) — no CTR advance"));
                                    afterFe = payload;
                                } catch (Exception e) {
                                    LOG.warning("BE decrypt failed from " + sender + ": " + e.getMessage());
                                    continue;
                                }
                            }
                            ByteBuf batch = afterFe;
                            if (peer.gameCompressionHeader() && afterFe.isReadable()) {
                                int method = afterFe.readUnsignedByte();
                                if (method == 0) {
                                    // ZLIB/raw deflate — Geyser CompressionCodec header 0
                                    batch = RakNetSessionCompression.inflateRaw(afterFe);
                                    if (batch == null) {
                                        LOG.warning("BE deflate batch inflate failed from " + sender);
                                        if (afterFe != payload) {
                                            afterFe.release();
                                        }
                                        continue;
                                    }
                                } else if (method == 0xff || method == 255) {
                                    // NONE — payload is plaintext batch
                                } else if (method == 1) {
                                    batch = RakNetSessionCompression.inflateSnappy(afterFe);
                                    if (batch == null) {
                                        LOG.warning("BE SNAPPY batch inflate failed from " + sender);
                                        if (afterFe != payload) {
                                            afterFe.release();
                                        }
                                        continue;
                                    }
                                } else {
                                    // Unknown method (commonly 6 pre-enc, or post-decrypt first
                                    // length-varint): restore byte and treat as uncompressed batch.
                                    // The 22:18 empty-world join relied on this AFTER AES decrypt —
                                    // dropping "unknown after decrypt" blocked SetLocalPlayerAsInitialized.
                                    afterFe.readerIndex(afterFe.readerIndex() - 1);
                                    String peekIds = RakNetSessionCompression.peekBatchPacketIds(afterFe, 4);
                                    LOG.info("BE batch method=" + method
                                            + " treated as uncompressed race (restored header byte)"
                                            + (decryptedOk ? " enc=on" : "")
                                            + " peekIds=[" + peekIds + "]");
                                }
                            }
                            LOG.info("RakNet game-batch from " + sender
                                    + " bytes=" + batch.readableBytes()
                                    + (peer.encryptionEnabled()
                                    ? (decryptedOk ? " enc=on" : " enc=arming-plain")
                                    : ""));
                            gamePacketHandler.accept(peer, batch.retainedDuplicate());
                            if (batch != afterFe) {
                                batch.release();
                            }
                            if (afterFe != payload) {
                                afterFe.release();
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
     * After login encryption handshake, encrypts (compression ‖ trailer) like Cloudburst.
     */
    public List<ByteBuf> encapsulateGameDatagrams(RakNetPeer peer, ByteBuf gameBatch) {
        boolean header = peer != null && peer.gameCompressionHeader();
        ByteBuf body = gameBatch;
        int method = 0xff;
        // Match NetworkSettings threshold (Geyser zlib threshold=512).
        if (header && gameBatch.readableBytes() >= 512) {
            ByteBuf deflated = RakNetSessionCompression.deflateRaw(gameBatch);
            if (deflated != null && deflated.readableBytes() < gameBatch.readableBytes()) {
                body = deflated;
                method = 0;
            } else if (deflated != null) {
                deflated.release();
            }
        }
        ByteBuf payload;
        if (header) {
            payload = Unpooled.buffer(body.readableBytes() + 1);
            payload.writeByte(method);
            payload.writeBytes(body);
            if (body != gameBatch) {
                body.release();
            }
        } else {
            payload = body.retainedDuplicate();
            if (body != gameBatch) {
                body.release();
            }
        }
        // Cloudburst: compress → encrypt → frame 0xfe (NetworkSettings compression header first)
        if (peer != null && peer.encryptionEnabled()) {
            try {
                int plainLen = payload.readableBytes();
                ByteBuf encrypted = peer.encryption().encrypt(payload);
                payload.release();
                payload = encrypted;
                // Prove S2C game packets are encrypted on the wire after enableEncryption.
                if (peer.encryptAppliedLogs.getAndIncrement() < 8) {
                    LOG.info("BE encrypt applied outbound " + peer.address()
                            + " plain=" + plainLen
                            + " cipher=" + payload.readableBytes()
                            + " method=" + (header ? method : -1)
                            + " (compress→encrypt→0xfe)");
                }
            } catch (Exception e) {
                payload.release();
                LOG.warning("BE encrypt failed: " + e.getMessage());
                return List.of();
            }
        }
        ByteBuf wrapped = Unpooled.buffer(payload.readableBytes() + 1);
        wrapped.writeByte(0xfe);
        wrapped.writeBytes(payload);
        payload.release();
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

    private static String key(InetSocketAddress a) {
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }
}
