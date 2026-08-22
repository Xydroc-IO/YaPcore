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
}
