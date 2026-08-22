package com.yapcore.mmobedrock.ui;

import java.util.List;

/** Simple page slicing for Bedrock form buttons. */
public final class Pagination {

    private Pagination() {
    }

    public record Page<T>(List<T> items, int page, int totalPages, boolean hasPrev, boolean hasNext) {
    }

    public static <T> Page<T> slice(List<T> all, int page, int pageSize) {
        if (all == null || all.isEmpty()) {
            return new Page<>(List.of(), 1, 1, false, false);
        }
        int size = Math.max(1, pageSize);
        int totalPages = Math.max(1, (all.size() + size - 1) / size);
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = (safePage - 1) * size;
        int to = Math.min(from + size, all.size());
        return new Page<>(all.subList(from, to), safePage, totalPages, safePage > 1, safePage < totalPages);
    }
}
