package com.yapcore.holo.impl;

import com.yapcore.lib.PacketService;
import com.yapcore.holo.nms.NmsHologramPackets;
import org.bukkit.plugin.java.JavaPlugin;

public final class HologramRuntime {

    public final JavaPlugin plugin;
    public final PacketService packets;
    public final NmsHologramPackets nms;
    public final HologramPlaceholders placeholders;
    public final HologramAnimations animations;
    public final double spacing;

    public HologramRuntime(JavaPlugin plugin, PacketService packets, NmsHologramPackets nms,
                           HologramPlaceholders placeholders, HologramAnimations animations, double spacing) {
        this.plugin = plugin;
        this.packets = packets;
        this.nms = nms;
        this.placeholders = placeholders;
        this.animations = animations;
        this.spacing = spacing;
    }
}
