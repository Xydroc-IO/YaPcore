package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RakNet reliability layer: frame sets, reliable sequencing, ACK/NACK.
 * Clean-room Geyser parity foundation (4.G1) — not Geyser/RakNet source.
 */
public final class RakNetReliability {

    public static final int ID_CONNECTED_PING = 0x00;
    public static final int ID_UNCONNECTED_PING = 0x01;
    public static final int ID_CONNECTED_PONG = 0x03;
    public static final int ID_OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final int ID_OPEN_CONNECTION_REPLY_1 = 0x06;
    public static final int ID_OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final int ID_OPEN_CONNECTION_REPLY_2 = 0x08;
    public static final int ID_CONNECTION_REQUEST = 0x09;
    public static final int ID_CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final int ID_NEW_INCOMING_CONNECTION = 0x13;
    public static final int ID_DISCONNECT = 0x15;
    public static final int ID_INCOMPATIBLE_PROTOCOL = 0x19;
    /** Datagram IDs 0x80–0x8f (ID_DATA_PACKET_0..F); must accept full range. */
    public static final int ID_FRAME_SET_RANGE_START = 0x80;
    public static final int ID_FRAME_SET_RANGE_END = 0x8f;
    public static final int ID_NACK = 0xa0;
    public static final int ID_ACK = 0xc0;

    public enum Reliability {
        UNRELIABLE(0),
        UNRELIABLE_SEQUENCED(1),
        RELIABLE(2),
        RELIABLE_ORDERED(3),
        RELIABLE_SEQUENCED(4),
        UNRELIABLE_WITH_ACK_RECEIPT(5),
        RELIABLE_WITH_ACK_RECEIPT(6),
        RELIABLE_ORDERED_WITH_ACK_RECEIPT(7);

        public final int code;

        Reliability(int code) {
            this.code = code;
        }

        public boolean isReliable() {
            return this == RELIABLE || this == RELIABLE_ORDERED || this == RELIABLE_SEQUENCED
                    || this == RELIABLE_WITH_ACK_RECEIPT || this == RELIABLE_ORDERED_WITH_ACK_RECEIPT;
        }

        public boolean isOrdered() {
            return this == RELIABLE_ORDERED || this == RELIABLE_ORDERED_WITH_ACK_RECEIPT;
        }

        /** Order index+channel present (ordered OR sequenced). */
        public boolean isSequencedOrOrdered() {
            return isOrdered()
                    || this == UNRELIABLE_SEQUENCED
                    || this == RELIABLE_SEQUENCED;
        }

        public boolean isSequenced() {
            return this == UNRELIABLE_SEQUENCED || this == RELIABLE_SEQUENCED;
        }

        public static Reliability of(int code) {
            for (Reliability r : values()) {
                if (r.code == code) {
                    return r;
                }
            }
            return UNRELIABLE;
        }
    }

    public record Frame(Reliability reliability, int reliableIndex, int sequenceIndex,
                        int orderIndex, int orderChannel, ByteBuf payload) {
    }

    public static final class SessionState {
        private final AtomicInteger sendReliable = new AtomicInteger();
        private final AtomicInteger sendSequence = new AtomicInteger();
        private final AtomicInteger sendOrder = new AtomicInteger();
        private final AtomicInteger sendDatagram = new AtomicInteger();
        private final AtomicInteger sendSplitId = new AtomicInteger();
        private final BitSet receivedReliable = new BitSet();
        private final java.util.concurrent.ConcurrentHashMap<Integer, SplitAssembler> splits =
                new java.util.concurrent.ConcurrentHashMap<>();
        private volatile boolean connected;
        private volatile int mtu = 1492;
        private volatile long clientGuid;
        private volatile long lastPingMs = System.currentTimeMillis();

        public int nextReliable() {
            return sendReliable.getAndIncrement();
        }

        public int nextSequence() {
            return sendSequence.getAndIncrement();
        }

        public int nextOrder() {
            return sendOrder.getAndIncrement();
        }

        public int nextSplitId() {
            return sendSplitId.getAndIncrement() & 0xFFFF;
        }

        public int nextDatagram() {
            return sendDatagram.getAndIncrement() & 0xFFFFFF;
        }

        public boolean markReceived(int reliableIndex) {
            synchronized (receivedReliable) {
                if (receivedReliable.get(reliableIndex)) {
                    return false;
                }
                receivedReliable.set(reliableIndex);
                return true;
            }
        }

        /** Returns assembled payload when complete, else null. Caller owns returned buffer. */
        public ByteBuf acceptSplit(int splitId, int splitCount, int splitIndex, ByteBuf fragment) {
            if (splitCount <= 0 || splitCount > 512 || splitIndex < 0 || splitIndex >= splitCount) {
                fragment.release();
                return null;
            }
            SplitAssembler asm = splits.computeIfAbsent(splitId, id -> new SplitAssembler(splitCount));
            ByteBuf done = asm.add(splitIndex, fragment);
            if (done != null) {
                splits.remove(splitId);
            }
            return done;
        }

        public boolean connected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public int mtu() {
            return mtu;
        }

        public void setMtu(int mtu) {
            this.mtu = Math.max(576, Math.min(mtu, 1400));
        }

        public long clientGuid() {
            return clientGuid;
        }

        public void setClientGuid(long clientGuid) {
            this.clientGuid = clientGuid;
        }

        public long lastPingMs() {
            return lastPingMs;
        }

        public void touchPing() {
            lastPingMs = System.currentTimeMillis();
        }
    }

    private static final class SplitAssembler {
        private final ByteBuf[] parts;
        private int received;

        SplitAssembler(int count) {
            this.parts = new ByteBuf[count];
        }

        synchronized ByteBuf add(int index, ByteBuf fragment) {
            if (parts[index] != null) {
                fragment.release();
                return null;
            }
            parts[index] = fragment;
            received++;
            if (received < parts.length) {
                return null;
            }
            int total = 0;
            for (ByteBuf p : parts) {
                total += p.readableBytes();
            }
            ByteBuf out = Unpooled.buffer(total);
            for (ByteBuf p : parts) {
                out.writeBytes(p);
                p.release();
            }
            return out;
        }
    }

    private RakNetReliability() {
    }

    public static boolean isFrameSet(int id) {
        return id >= ID_FRAME_SET_RANGE_START && id <= ID_FRAME_SET_RANGE_END;
    }

    public static boolean isOpenConnectionRequest1(ByteBuf buf) {
        return buf.isReadable() && buf.getUnsignedByte(buf.readerIndex()) == ID_OPEN_CONNECTION_REQUEST_1;
    }

    public static boolean isOpenConnectionRequest2(ByteBuf buf) {
        return buf.isReadable() && buf.getUnsignedByte(buf.readerIndex()) == ID_OPEN_CONNECTION_REQUEST_2;
    }

    public static ByteBuf openConnectionReply1(long serverGuid, boolean security, int mtu) {
        ByteBuf out = Unpooled.buffer(1 + RakNetUnconnected.MAGIC.length + 8 + 1 + 2);
        out.writeByte(ID_OPEN_CONNECTION_REPLY_1);
        out.writeBytes(RakNetUnconnected.MAGIC);
        out.writeLong(serverGuid);
        out.writeBoolean(security);
        out.writeShort(mtu);
        return out;
    }

    public static ByteBuf openConnectionReply2(long serverGuid, InetAddrCookie addr, int mtu, boolean security) {
        ByteBuf out = Unpooled.buffer(64);
        out.writeByte(ID_OPEN_CONNECTION_REPLY_2);
        out.writeBytes(RakNetUnconnected.MAGIC);
        out.writeLong(serverGuid);
        writeAddress(out, addr);
        out.writeShort(mtu);
        out.writeBoolean(security);
        return out;
    }

    /** Bedrock / modern RakNet expect 20 system addresses in CRA. */
    public static final int SYSTEM_ADDRESS_COUNT = 20;

    public static ByteBuf connectionRequestAccepted(InetAddrCookie internal, short systemIndex,
                                                    InetAddrCookie[] systemAddresses, long requestTime, long time) {
        ByteBuf out = Unpooled.buffer(512);
        out.writeByte(ID_CONNECTION_REQUEST_ACCEPTED);
        writeAddress(out, internal);
        out.writeShort(systemIndex);
        for (int i = 0; i < SYSTEM_ADDRESS_COUNT; i++) {
            writeAddress(out, i < systemAddresses.length && systemAddresses[i] != null
                    ? systemAddresses[i] : InetAddrCookie.loopback());
        }
        out.writeLong(requestTime);
        out.writeLong(time);
        return out;
    }

    public static ByteBuf connectedPong(long pingTime, long pongTime) {
        ByteBuf out = Unpooled.buffer(17);
        out.writeByte(ID_CONNECTED_PONG);
        out.writeLong(pingTime);
        out.writeLong(pongTime);
        return out;
    }

    public static ByteBuf wrapFrameSet(SessionState state, Frame frame) {
        return wrapFrameSet(state, frame, false, 0, 0, 0);
    }

    /**
     * Encode one RELIABLE_ORDERED frame into a datagram. When {@code split}, writes
     * splitCount/splitId/splitIndex (BE) after the order header.
     */
    public static ByteBuf wrapFrameSet(SessionState state, Frame frame,
                                       boolean split, int splitCount, int splitId, int splitIndex) {
        ByteBuf out = Unpooled.buffer(frame.payload().readableBytes() + 48);
        out.writeByte(ID_FRAME_SET_RANGE_START);
        writeTriadLe(out, state.nextDatagram());
        int flags = (frame.reliability().code << 5);
        if (split) {
            flags |= 0x10;
        }
        out.writeByte(flags);
        out.writeShort(frame.payload().readableBytes() << 3); // bit length
        if (frame.reliability().isReliable()) {
            writeTriadLe(out, frame.reliableIndex());
        }
        if (frame.reliability() == Reliability.UNRELIABLE_SEQUENCED
                || frame.reliability() == Reliability.RELIABLE_SEQUENCED) {
            writeTriadLe(out, frame.sequenceIndex());
        }
        if (frame.reliability().isOrdered()) {
            writeTriadLe(out, frame.orderIndex());
            out.writeByte(frame.orderChannel());
        }
        if (split) {
            out.writeInt(splitCount);
            out.writeShort(splitId);
            out.writeInt(splitIndex);
        }
        out.writeBytes(frame.payload());
        return out;
    }

    public static Frame reliableOrdered(SessionState state, ByteBuf payload) {
        return new Frame(Reliability.RELIABLE_ORDERED, state.nextReliable(), 0,
                state.nextOrder(), 0, payload);
    }

    /**
     * Split a large game payload across MTU-sized RELIABLE_ORDERED frames (shared order index).
     */
    public static List<ByteBuf> wrapReliableOrderedPossiblySplit(SessionState state, ByteBuf payload) {
        int overhead = 64; // frame-set + reliability + split headers + slack
        int maxPayload = Math.max(400, state.mtu() - overhead);
        if (payload.readableBytes() <= maxPayload) {
            ByteBuf dg = wrapFrameSet(state, reliableOrdered(state, payload.retain()));
            payload.release();
            return List.of(dg);
        }
        int total = payload.readableBytes();
        int splitCount = (total + maxPayload - 1) / maxPayload;
        if (splitCount > 512) {
            maxPayload = (total + 511) / 512;
            splitCount = (total + maxPayload - 1) / maxPayload;
        }
        int splitId = state.nextSplitId();
        int orderIndex = state.nextOrder();
        List<ByteBuf> out = new ArrayList<>(splitCount);
        for (int i = 0; i < splitCount; i++) {
            int from = i * maxPayload;
            int len = Math.min(maxPayload, total - from);
            ByteBuf part = payload.retainedSlice(payload.readerIndex() + from, len);
            Frame frame = new Frame(Reliability.RELIABLE_ORDERED, state.nextReliable(), 0,
                    orderIndex, 0, part);
            out.add(wrapFrameSet(state, frame, true, splitCount, splitId, i));
            part.release(); // wrapFrameSet copied bytes
        }
        payload.release();
        return out;
    }

    public static Frame unreliable(ByteBuf payload) {
        return new Frame(Reliability.UNRELIABLE, 0, 0, 0, 0, payload);
    }

    public static List<Frame> decodeFrameSet(ByteBuf buf, SessionState state) {
        return decodeFrameSetEx(buf, state).frames();
    }

    public record DecodedFrameSet(int datagramNumber, List<Frame> frames, int splitFragmentsAccepted) {
        public boolean madeProgress() {
            return !frames.isEmpty() || splitFragmentsAccepted > 0;
        }
    }

    public static DecodedFrameSet decodeFrameSetEx(ByteBuf buf, SessionState state) {
        List<Frame> frames = new ArrayList<>();
        int splits = 0;
        if (!buf.isReadable()) {
            return new DecodedFrameSet(-1, frames, 0);
        }
        int id = buf.readUnsignedByte();
        if (!isFrameSet(id)) {
            buf.readerIndex(buf.readerIndex() - 1);
            return new DecodedFrameSet(-1, frames, 0);
        }
        int datagramNumber = readTriadLe(buf);
        while (buf.isReadable()) {
            if (buf.readableBytes() < 3) {
                break; // flags + bitLen
            }
            int flags = buf.readUnsignedByte();
            Reliability rel = Reliability.of((flags >> 5) & 0x7);
            boolean split = (flags & 0x10) != 0;
            int bitLen = buf.readUnsignedShort();
            // Match jsp-raknet / Cloudburst: length in bits >> 3 (not round-up)
            int byteLen = bitLen >>> 3;
            if (byteLen <= 0) {
                break;
            }
            int reliableIndex = 0;
            int sequenceIndex = 0;
            int orderIndex = 0;
            int orderChannel = 0;
            if (rel.isReliable()) {
                if (buf.readableBytes() < 3) {
                    break;
                }
                reliableIndex = readTriadLe(buf);
                state.markReceived(reliableIndex);
            }
            if (rel.isSequenced()) {
                if (buf.readableBytes() < 3) {
                    break;
                }
                sequenceIndex = readTriadLe(buf);
            }
            if (rel.isSequencedOrOrdered()) {
                if (buf.readableBytes() < 4) {
                    break;
                }
                orderIndex = readTriadLe(buf);
                orderChannel = buf.readUnsignedByte();
            }
            if (split) {
                // splitCount(int BE) + splitId(short BE) + splitIndex(int BE)
                if (buf.readableBytes() < 10 + byteLen) {
                    break;
                }
                int splitCount = buf.readInt();
                int splitId = buf.readUnsignedShort();
                int splitIndex = buf.readInt();
                ByteBuf fragment = buf.readRetainedSlice(byteLen);
                splits++;
                ByteBuf assembled = state.acceptSplit(splitId, splitCount, splitIndex, fragment);
                if (assembled != null) {
                    frames.add(new Frame(rel, reliableIndex, sequenceIndex, orderIndex, orderChannel, assembled));
                }
                continue;
            }
            if (buf.readableBytes() < byteLen) {
                break;
            }
            ByteBuf payload = buf.readRetainedSlice(byteLen);
            frames.add(new Frame(rel, reliableIndex, sequenceIndex, orderIndex, orderChannel, payload));
        }
        return new DecodedFrameSet(datagramNumber, frames, splits);
    }

    public static ByteBuf buildAck(int... datagramNumbers) {
        ByteBuf out = Unpooled.buffer(32);
        out.writeByte(ID_ACK);
        out.writeShort(datagramNumbers.length);
        for (int n : datagramNumbers) {
            out.writeBoolean(true); // single
            writeTriadLe(out, n);
        }
        return out;
    }

    public record InetAddrCookie(byte version, byte[] address, int port) {
        public static InetAddrCookie loopback() {
            return new InetAddrCookie((byte) 4, new byte[]{127, 0, 0, 1}, 19132);
        }

        public static InetAddrCookie of(java.net.InetSocketAddress a) {
            byte[] addr = a.getAddress().getAddress();
            byte ver = (byte) (addr.length == 16 ? 6 : 4);
            return new InetAddrCookie(ver, addr, a.getPort());
        }
    }

    public static void writeAddress(ByteBuf out, InetAddrCookie addr) {
        out.writeByte(addr.version());
        if (addr.version() == 4) {
            for (int i = 0; i < 4; i++) {
                out.writeByte(~addr.address()[i]);
            }
            out.writeShort(addr.port());
        } else {
            out.writeBytes(addr.address(), 0, Math.min(16, addr.address().length));
            out.writeShort(addr.port());
        }
    }

    public static InetAddrCookie readAddress(ByteBuf in) {
        byte ver = in.readByte();
        if (ver == 4) {
            byte[] a = new byte[4];
            for (int i = 0; i < 4; i++) {
                a[i] = (byte) (~in.readByte());
            }
            int port = in.readUnsignedShort();
            return new InetAddrCookie(ver, a, port);
        }
        byte[] a = new byte[16];
        in.readBytes(a);
        int port = in.readUnsignedShort();
        return new InetAddrCookie(ver, a, port);
    }

    public static void writeTriadLe(ByteBuf out, int value) {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
    }

    public static int readTriadLe(ByteBuf in) {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16);
    }
}
