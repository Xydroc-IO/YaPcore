package com.yapcore.link.chat;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.PluginMessageEvent;

import java.util.logging.Logger;

/** Relays {@code yap:chat} plugin messages between backends (native YaP Link API). */
public final class ChatBridgePlugin implements LinkPlugin {

    public static final ChannelIdentifier CHANNEL = ChannelIdentifier.of("yap", "chat");

    private LinkProxy proxy;
    private Logger logger;

    @Override
    public void onLoad(LinkPluginContext context) {
        this.proxy = context.proxy();
        this.logger = context.logger();
    }

    @Override
    public void onEnable() {
        proxy.registerChannel(CHANNEL);
        logger.info("YaP Link Chat Bridge ready on channel yap:chat");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.channel().equals(CHANNEL)) {
            return;
        }
        if (event.sourceKind() != PluginMessageEvent.SourceKind.BACKEND) {
            return;
        }
        event.setResult(PluginMessageEvent.Result.HANDLED);
        RegisteredServer source = event.server().orElse(null);
        if (source == null) {
            return;
        }
        for (RegisteredServer target : proxy.servers()) {
            if (target.name().equalsIgnoreCase(source.name())) {
                continue;
            }
            proxy.broadcastPluginMessage(target, CHANNEL, event.data(), source);
        }
    }
}
