package com.yapcore.dungeons.event;

import com.yapcore.dungeons.DungeonRun;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a run fails (lives exhausted or admin stop). */
public final class DungeonFailEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DungeonRun run;
    private final String reason;

    public DungeonFailEvent(DungeonRun run, String reason) {
        super(true);
        this.run = run;
        this.reason = reason == null ? "" : reason;
    }

    public DungeonRun run() {
        return run;
    }

    public String reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
