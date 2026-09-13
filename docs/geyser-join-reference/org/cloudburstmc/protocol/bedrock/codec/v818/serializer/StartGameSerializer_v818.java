/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper
 *  org.cloudburstmc.protocol.bedrock.codec.v776.serializer.StartGameSerializer_v776
 *  org.cloudburstmc.protocol.bedrock.packet.StartGamePacket
 *  org.cloudburstmc.protocol.common.util.VarInts
 */
package org.cloudburstmc.protocol.bedrock.codec.v818.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v776.serializer.StartGameSerializer_v776;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.common.util.VarInts;

public class StartGameSerializer_v818
extends StartGameSerializer_v776 {
    public static final StartGameSerializer_v818 INSTANCE = new StartGameSerializer_v818();

    protected void writeLevelSettings(ByteBuf buffer, BedrockCodecHelper helper, StartGamePacket packet) {
        super.writeLevelSettings(buffer, helper, packet);
        helper.writeString(buffer, packet.getOwnerId());
    }

    protected void readLevelSettings(ByteBuf buffer, BedrockCodecHelper helper, StartGamePacket packet) {
        super.readLevelSettings(buffer, helper, packet);
        packet.setOwnerId(helper.readString(buffer));
    }

    protected void writeSyncedPlayerMovementSettings(ByteBuf buffer, StartGamePacket packet) {
        VarInts.writeInt((ByteBuf)buffer, (int)packet.getRewindHistorySize());
        buffer.writeBoolean(packet.isServerAuthoritativeBlockBreaking());
    }

    protected void readSyncedPlayerMovementSettings(ByteBuf buffer, StartGamePacket packet) {
        packet.setRewindHistorySize(VarInts.readInt((ByteBuf)buffer));
        packet.setServerAuthoritativeBlockBreaking(buffer.readBoolean());
    }
}
