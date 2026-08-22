package com.yapcore.link.tab;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.PluginMessageEvent;

import java.util.logging.Logger;

/** Relays {@code yap:tab} plugin messages between backends (header/footer/sidebar sync). */
public final class TabBridgePlugin implements LinkPlugin {

    public static final ChannelIdentifier CHANNEL = ChannelIdentifier.of("yap", "tab");

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
        logger.info("YaP Link Tab Bridge ready on channel yap:tab");
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
