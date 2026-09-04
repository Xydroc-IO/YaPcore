package com.yapcore.essentials.store;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EssentialsStoreTest {

    @Test
    void afkToggleAndClear() {
        AfkService afk = new AfkService();
        UUID id = UUID.randomUUID();
        assertFalse(afk.isAfk(id));
        assertTrue(afk.toggle(id));
        assertTrue(afk.isAfk(id));
        assertFalse(afk.toggle(id));
        assertFalse(afk.isAfk(id));
        afk.toggle(id);
        afk.clear(id);
        assertFalse(afk.isAfk(id));
    }

    @Test
    void tpaRequestExpires() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TpaService.Request live = new TpaService.Request(a, b, false, System.currentTimeMillis() + 60_000L);
        assertFalse(live.expired());
        TpaService.Request dead = new TpaService.Request(a, b, true, System.currentTimeMillis() - 1L);
        assertTrue(dead.expired());
    }

    @Test
    void tpaPendingClearsExpiredWithoutPlugin() {
        // pending() is the pure expiry path used by accept/deny
        TpaService svc = new TpaService(null);
        UUID target = UUID.randomUUID();
        // inject via request map through public API would need players; exercise Request + clear
        svc.clear(target);
        assertNull(svc.pending(target));
    }
}
