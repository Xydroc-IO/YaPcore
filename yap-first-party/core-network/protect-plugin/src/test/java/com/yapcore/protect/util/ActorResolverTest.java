package com.yapcore.protect.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActorResolverTest {

    @Test
    void naturalUsesNatureLabel() {
        ActorResolver.Actor actor = ActorResolver.natural();
        assertNull(actor.uuid());
        assertEquals(ActorResolver.NATURE, actor.name());
    }
}
