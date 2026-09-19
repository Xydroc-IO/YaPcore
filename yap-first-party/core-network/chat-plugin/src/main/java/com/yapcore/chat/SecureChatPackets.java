package com.yapcore.chat;

import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketTypes;
import org.bukkit.plugin.Plugin;

/** YaPLib path for unsigned login flag + player-chat → system chat. */
public final class SecureChatPackets extends PacketAdapter {

    public SecureChatPackets(Plugin plugin) {
        super(plugin, PacketTypes.Play.Server.LOGIN, PacketTypes.Play.Server.PLAYER_CHAT);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Object rewritten = SecureChatRewriter.rewrite(event.packet().handle());
        if (rewritten != event.packet().handle()) {
            event.setHandle(rewritten);
        }
    }
}
