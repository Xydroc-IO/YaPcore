package com.yapcore.skills.service;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.XpTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillXpGrantTest {

    @Test
    void threeConcurrentGrantsDoNotAllNotifyOneToTwo() throws Exception {
        UUID player = UUID.randomUUID();
        SkillId excavation = SkillId.of("excavation");
        XpTable table = XpTable.runescape(120, 1.0);
        double perBreak = table.xpForLevel(2);
        SkillXpLocks locks = new SkillXpLocks();
        AtomicReference<SkillProgress> row =
                new AtomicReference<>(new SkillProgress(player, excavation, 0, 1));
        List<Integer> notifiedFrom = java.util.Collections.synchronizedList(new ArrayList<>());

        int n = 3;
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    locks.withPlayer(player, () -> {
                        SkillProgress cur = row.get();
                        SkillXpGrant.Outcome out = SkillXpGrant.apply(cur, perBreak, table);
                        row.set(out.progress());
                        if (out.leveled()) {
                            notifiedFrom.add(out.oldLevel());
                        }
                        return out;
                    });
                    done.countDown();
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        long ones = notifiedFrom.stream().filter(level -> level == 1).count();
        assertEquals(1, ones, "racy applyXp would notify 1→2 three times: " + notifiedFrom);
        assertFalse(notifiedFrom.isEmpty());
        assertTrue(row.get().level() >= 2);
    }

    @Test
    void maxedSkillDoesNotMove() {
        UUID player = UUID.randomUUID();
        SkillId id = SkillId.of("excavation");
        XpTable table = XpTable.runescape(120, 1.0);
        SkillProgress maxed = new SkillProgress(player, id, table.xpForLevel(120), 120);
        SkillXpGrant.Outcome out = SkillXpGrant.apply(maxed, 10_000, table);
        assertFalse(out.leveled());
        assertEquals(120, out.progress().level());
        assertEquals(maxed.xp(), out.progress().xp(), 0.0);
    }
}
