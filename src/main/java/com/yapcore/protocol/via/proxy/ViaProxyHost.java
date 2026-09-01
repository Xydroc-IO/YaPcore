package com.yapcore.protocol.via.proxy;

import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.transform.PacketTransformer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * Callback surface from {@link com.yapcore.protocol.via.ViaProxyHandler} for backend S2C handling.
 */
public interface ViaProxyHost {

    ViaSession session();

    PacketTransformer transformer();

    boolean compressionInstalled();

    void setCompressionInstalled(boolean installed);

    void enqueueC2S(ByteBuf out, boolean keepAlive);

    ChannelHandlerContext inboundCtx();

    /**
     * When false, config/play resource_pack_push is auto-acked toward Paper so mid clients
     * are not blocked waiting for a pack download YaP is not serving.
     */
    boolean resourcePackForwardEnabled();
}
