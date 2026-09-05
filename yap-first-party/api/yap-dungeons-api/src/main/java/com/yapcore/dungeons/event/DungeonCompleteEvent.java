package com.yapcore.dungeons.event;

import com.yapcore.dungeons.DungeonRun;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the global region after a dungeon boss is defeated. */
public final class DungeonCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DungeonRun run;
    private final long clearTimeMs;

    public DungeonCompleteEvent(DungeonRun run, long clearTimeMs) {
        super(true);
        this.run = run;
        this.clearTimeMs = clearTimeMs;
    }

    public DungeonRun run() {
        return run;
    }

    public long clearTimeMs() {
        return clearTimeMs;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
