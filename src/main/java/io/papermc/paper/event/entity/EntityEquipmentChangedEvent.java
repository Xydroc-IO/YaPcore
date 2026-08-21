package io.papermc.paper.event.entity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Paper/Bukkit event stub for YaPcore plugin compatibility (generated).
 */
public class EntityEquipmentChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public EntityEquipmentChangedEvent() {
        super(false);
    }

    public EntityEquipmentChangedEvent(Object... ctx) {
        super(false);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
