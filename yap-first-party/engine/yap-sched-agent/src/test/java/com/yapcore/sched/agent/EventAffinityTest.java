package com.yapcore.sched.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EventAffinityTest {

    @Test
    void playerGetterIsEntityAffinity() {
        Object player = new Object();
        assertSame(player, EventAffinity.entityOf(new Playerish(player)));
    }

    @Test
    void locationFallsBackToEntityLocation() {
        Object loc = new Object();
        Object entity = new Located(loc);
        assertSame(loc, EventAffinity.locationOf(new Playerish(entity)));
    }

    @Test
    void unknownEventHasNoAffinity() {
        assertNull(EventAffinity.entityOf(new Object()));
        assertNull(EventAffinity.locationOf(new Object()));
    }

    @Test
    void namedPlayerEventWalksSuperclass() {
        Object player = new Object();
        assertSame(player, EventAffinity.entityOf(new org.bukkit.event.player.PlayerJoinEvent(player)));
    }

    static final class Playerish {
        private final Object player;

        Playerish(Object player) {
            this.player = player;
        }

        public Object getPlayer() {
            return player;
        }
    }

    static final class Located {
        private final Object loc;

        Located(Object loc) {
            this.loc = loc;
        }

        public Object getLocation() {
            return loc;
        }
    }
}
