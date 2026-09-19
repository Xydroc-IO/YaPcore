package com.yapcore.chat;

import com.yapcore.chat.service.ChatFilterService;
import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketPriority;
import com.yapcore.lib.packet.PacketThreadMode;
import com.yapcore.lib.packet.PacketTypes;
import com.yapcore.moderation.Punishment;
import com.yapcore.sched.StaffBypass;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Packet-layer mute + block-filter. Signed chat cannot be rewritten; cancel only.
 * Replacement / channels still run on {@code AsyncChatEvent}.
 */
public final class ChatPacketFilter extends PacketAdapter {

    private final ChatPlugin plugin;

    public ChatPacketFilter(ChatPlugin plugin) {
        super(plugin, PacketPriority.LOW, PacketThreadMode.REGION,
                PacketTypes.Play.Client.CHAT, PacketTypes.Play.Client.CHAT_COMMAND);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.player();
        if (player == null) {
            return;
        }
        String text = ChatPacketText.read(event.packet());
        if (text == null) {
            return;
        }
        boolean command = event.type() != null && "chat_command".equals(event.type().name());
        Optional<Punishment> mute = ChatFormat.activeMute(player.getUniqueId());
        if (mute.isPresent()) {
            if (command && ChatPacketText.commandAllowedWhenMuted(text)) {
                return;
            }
            event.setCancelled(true);
            plugin.chatConfig().messages().sendRaw(player, plugin.chatConfig().mutedMessage(),
                    "reason", mute.get().reason());
            return;
        }
        if (command) {
            return;
        }
        if (StaffBypass.chat(player) || player.hasPermission("yapchat.bypass.filter")) {
            return;
        }
        ChatFilterService.FilterResult filtered = plugin.filter().filter(text);
        if (filtered.blocked()) {
            event.setCancelled(true);
            plugin.chatConfig().messages().sendRaw(player, plugin.chatConfig().filteredMessage());
        }
    }
}
