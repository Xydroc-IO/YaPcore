package com.yapcore.protect;

import java.util.List;

/** One page of protect lookup rows plus optional cursor for the next page. */
public record ProtectLookupPage(
        List<BlockChangeRecord> rows,
        ProtectLookupCursor nextCursor,
        boolean hasMore
) {
    public static ProtectLookupPage of(List<BlockChangeRecord> rows, int pageSize) {
        if (rows == null || rows.isEmpty()) {
            return new ProtectLookupPage(List.of(), null, false);
        }
        boolean more = rows.size() > pageSize;
        List<BlockChangeRecord> page = more ? List.copyOf(rows.subList(0, pageSize)) : List.copyOf(rows);
        ProtectLookupCursor next = null;
        if (more && !page.isEmpty()) {
            BlockChangeRecord last = page.get(page.size() - 1);
            next = new ProtectLookupCursor(last.epochMs(), last.id());
        }
        return new ProtectLookupPage(page, next, more);
    }
}
