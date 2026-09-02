package com.yapcore.perms.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredNodeTest {

    @Test
    void globalNodeAppliesEverywhere() {
        StoredNode node = new StoredNode("essentials.fly", true, "", "", null);
        assertTrue(node.applies(Instant.now(), "world", "lobby"));
    }

    @Test
    void worldContextFilters() {
        StoredNode node = new StoredNode("essentials.fly", true, "world_nether", "", null);
        assertTrue(node.applies(Instant.now(), "world_nether", ""));
        assertFalse(node.applies(Instant.now(), "world", ""));
    }

    @Test
    void expiredNodeDoesNotApply() {
        StoredNode node = new StoredNode("essentials.fly", true, "", "", Instant.now().minusSeconds(5));
        assertFalse(node.applies(Instant.now(), "", ""));
        assertTrue(node.expired(Instant.now()));
    }
}
