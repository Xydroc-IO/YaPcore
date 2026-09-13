package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Miss-swing deferral: entity attack must cancel a pending air-miss so JE swing does not
 * reset AttackStrengthTicker before {@code minecraft:attack}.
 */
class BedrockCombatMissSwingTest {

    @Test
    @SuppressWarnings("unchecked")
    void cancelMissSwingClearsPending() throws Exception {
        Field pending = BedrockCombat.class.getDeclaredField("PENDING_MISS_SWING");
        pending.setAccessible(true);
        ConcurrentHashMap<Long, Boolean> map =
                (ConcurrentHashMap<Long, Boolean>) pending.get(null);

        long guid = 0xC0FFEEL;
        LinkBedrockSession session = LinkBedrockSession.open(
                guid, 1L, "tester", new UUID(0L, 9L), 818, null, packets -> { });
        map.put(guid, Boolean.TRUE);
        assertTrue(map.containsKey(guid));

        BedrockCombat.cancelMissSwing(session);
        assertFalse(map.containsKey(guid), "entity attack must drop deferred air-miss");
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushWithoutPendingIsNoOp() throws Exception {
        Field pending = BedrockCombat.class.getDeclaredField("PENDING_MISS_SWING");
        pending.setAccessible(true);
        ConcurrentHashMap<Long, Boolean> map =
                (ConcurrentHashMap<Long, Boolean>) pending.get(null);
        long guid = 0xBEEFL;
        map.remove(guid);
        LinkBedrockSession session = LinkBedrockSession.open(
                guid, 2L, "tester2", new UUID(0L, 10L), 818, null, packets -> { });
        BedrockCombat.flushMissSwing(session);
        assertFalse(map.containsKey(guid));
    }
}
