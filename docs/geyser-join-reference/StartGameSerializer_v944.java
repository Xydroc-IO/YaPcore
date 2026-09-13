/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper
 *  org.cloudburstmc.protocol.bedrock.codec.v924.serializer.StartGameSerializer_v924
 *  org.cloudburstmc.protocol.bedrock.data.ClientStoreEntrypointConfiguration
 *  org.cloudburstmc.protocol.bedrock.data.GatheringsConfigurationJoinInfo
 *  org.cloudburstmc.protocol.bedrock.data.PresenceConfiguration
 *  org.cloudburstmc.protocol.bedrock.data.ServerConfigurationJoinInfo
 */
package org.cloudburstmc.protocol.bedrock.codec.v944.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v924.serializer.StartGameSerializer_v924;
import org.cloudburstmc.protocol.bedrock.data.ClientStoreEntrypointConfiguration;
import org.cloudburstmc.protocol.bedrock.data.GatheringsConfigurationJoinInfo;
import org.cloudburstmc.protocol.bedrock.data.PresenceConfiguration;
import org.cloudburstmc.protocol.bedrock.data.ServerConfigurationJoinInfo;

public class StartGameSerializer_v944
extends StartGameSerializer_v924 {
    public static final StartGameSerializer_v944 INSTANCE = new StartGameSerializer_v944();

    protected void writeServerJoinInfo(ByteBuf buffer, BedrockCodecHelper helper, ServerConfigurationJoinInfo info) {
        helper.writeOptionalNull(buffer, (Object)info.getGatheringsConfigurationJoinInfo(), (arg_0, arg_1, arg_2) -> ((BedrockCodecHelper)helper).writeGatheringsConfiguration(arg_0, arg_1, arg_2));
        helper.writeOptionalNull(buffer, (Object)info.getClientStoreEntrypointConfiguration(), this::writeClientStoreEntrypointConfiguration);
        helper.writeOptionalNull(buffer, (Object)info.getPresenceConfiguration(), (arg_0, arg_1) -> ((BedrockCodecHelper)helper).writePresenceConfiguration(arg_0, arg_1));
    }

    private void writeClientStoreEntrypointConfiguration(ByteBuf buf, BedrockCodecHelper h, ClientStoreEntrypointConfiguration store) {
        h.writeString(buf, store.getStoreId());
        h.writeString(buf, store.getStoreName());
    }

    protected ServerConfigurationJoinInfo readServerJoinInfo(ByteBuf buffer, BedrockCodecHelper helper) {
        GatheringsConfigurationJoinInfo gatheringsConfigurationJoinInfo = (GatheringsConfigurationJoinInfo)helper.readOptional(buffer, null, (arg_0, arg_1) -> ((BedrockCodecHelper)helper).readGatheringsConfiguration(arg_0, arg_1));
        ClientStoreEntrypointConfiguration clientStoreEntrypointConfiguration = (ClientStoreEntrypointConfiguration)helper.readOptional(buffer, null, this::readClientStoreEntrypointConfiguration);
        PresenceConfiguration presenceConfiguration = (PresenceConfiguration)helper.readOptional(buffer, null, arg_0 -> ((BedrockCodecHelper)helper).readPresenceConfiguration(arg_0));
        return new ServerConfigurationJoinInfo(gatheringsConfigurationJoinInfo, clientStoreEntrypointConfiguration, presenceConfiguration);
    }

    private ClientStoreEntrypointConfiguration readClientStoreEntrypointConfiguration(ByteBuf buf, BedrockCodecHelper h) {
        return new ClientStoreEntrypointConfiguration(h.readString(buf), h.readString(buf));
    }
}
