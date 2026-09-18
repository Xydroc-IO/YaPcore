package org.bukkit.event.player;

/** Test stand-in so EventAffinity can match Bukkit class names. */
public class PlayerEvent {

    private final Object player;

    public PlayerEvent(Object player) {
        this.player = player;
    }

    public Object getPlayer() {
        return player;
    }
}
