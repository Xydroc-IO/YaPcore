package org.bukkit.event;

public abstract class Event {
    private String name;
    private final boolean async;

    protected Event() {
        this(false);
    }

    protected Event(boolean isAsync) {
        this.async = isAsync;
    }

    public String getEventName() {
        if (name == null) {
            name = getClass().getSimpleName();
        }
        return name;
    }

    public abstract HandlerList getHandlers();

    public boolean isAsynchronous() {
        return async;
    }
}
