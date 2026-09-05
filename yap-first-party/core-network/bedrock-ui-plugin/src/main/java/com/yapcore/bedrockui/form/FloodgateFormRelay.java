package com.yapcore.bedrockui.form;

import com.yapcore.bedrock.ui.BedrockFormResult;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Floodgate-only form transport over plugin channel {@code floodgate:form}.
 * Used when the player is Bedrock ({@code YaPFloodgate}) but has no chassis native UDP session.
 * Stock Velocity+Geyser(+Floodgate) forwards this channel to the Bedrock client automatically.
 */
public final class FloodgateFormRelay implements PluginMessageListener, Listener {

    public static final String CHANNEL = "floodgate:form";

    private final JavaPlugin plugin;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(UUID playerId, String username, Consumer<BedrockFormResult> onResult) {
    }

    public FloodgateFormRelay(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        var messenger = plugin.getServer().getMessenger();
        try {
            messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
        pending.clear();
    }

    public int sendSimple(Player player, String title, String content,
                          Consumer<BedrockFormResult> onResult, String... buttons) {
        String json = FloodgateFormCodec.simpleJson(title, content, buttons);
        return send(player, FloodgateFormCodec.TYPE_SIMPLE, json, onResult);
    }

    public int sendModal(Player player, String title, String content,
                         String button1, String button2, Consumer<BedrockFormResult> onResult) {
        String json = FloodgateFormCodec.modalJson(title, content, button1, button2);
        return send(player, FloodgateFormCodec.TYPE_MODAL, json, onResult);
    }

    public int sendCustom(Player player, String title, String jsonContentArray,
                          Consumer<BedrockFormResult> onResult) {
        String json = FloodgateFormCodec.customJson(title, jsonContentArray);
        return send(player, FloodgateFormCodec.TYPE_CUSTOM, json, onResult);
    }

    private int send(Player player, byte typeOrdinal, String json, Consumer<BedrockFormResult> onResult) {
        if (player == null || !player.isOnline()) {
            return -1;
        }
        short formId = nextFormId();
        int id = formId & 0xFFFF;
        pending.put(id, new Pending(player.getUniqueId(), player.getName(), onResult));
        byte[] payload = FloodgateFormCodec.encodeOutbound(typeOrdinal, formId, json);
        try {
            player.sendPluginMessage(plugin, CHANNEL, payload);
            return id;
        } catch (Exception e) {
            pending.remove(id);
            plugin.getLogger().log(Level.FINE, "floodgate:form send failed", e);
            return -1;
        }
    }

    /** Backend form ids stay below 0x8000 so proxy Floodgate forwards responses to us. */
    private short nextFormId() {
        return (short) nextId.getAndUpdate(n -> {
            int next = n + 1;
            return next > Short.MAX_VALUE ? 1 : next;
        });
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || player == null || message == null) {
            return;
        }
        FloodgateFormCodec.InboundResponse inbound;
        try {
            inbound = FloodgateFormCodec.decodeInbound(message);
        } catch (IllegalArgumentException e) {
            return;
        }
        int id = inbound.formId() & 0xFFFF;
        Pending p = pending.remove(id);
        if (p == null) {
            return;
        }
        if (!p.playerId().equals(player.getUniqueId())) {
            // Reject spoofed responses; restore pending for the rightful owner.
            pending.put(id, p);
            plugin.getLogger().fine("Ignored floodgate:form response from wrong player for id=" + id);
            return;
        }
        BedrockFormResult result = new BedrockFormResult(
                id, p.username(), inbound.rawData(), inbound.closed());
        Consumer<BedrockFormResult> handler = p.onResult();
        if (handler == null) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            try {
                handler.accept(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "floodgate form handler", e);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> e = it.next();
            if (e.getValue().playerId().equals(id)) {
                it.remove();
                Consumer<BedrockFormResult> h = e.getValue().onResult();
                if (h != null) {
                    BedrockFormResult closed = new BedrockFormResult(
                            e.getKey(), e.getValue().username(), "null", true);
                    try {
                        h.accept(closed);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
