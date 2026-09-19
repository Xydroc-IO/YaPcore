package com.yapcore.holo.nms;

import java.util.concurrent.atomic.AtomicInteger;

/** Packet-only entity ids. Prefers vanilla Entity counter when present. */
public final class EntityIdAllocator {

    private final AtomicInteger local = new AtomicInteger(1_100_000_000);
    private final AtomicInteger vanilla;

    public EntityIdAllocator(NmsLookup nms) {
        AtomicInteger found = null;
        Class<?> entity = nms.clazz("net.minecraft.world.entity.Entity");
        if (entity != null) {
            Object counter = nms.staticField(entity, "ENTITY_COUNTER", "entityCounter", "ENTITY_ID");
            if (counter instanceof AtomicInteger ai) {
                found = ai;
            }
        }
        this.vanilla = found;
    }

    public int next() {
        if (vanilla != null) {
            return vanilla.incrementAndGet();
        }
        return local.incrementAndGet();
    }
}
