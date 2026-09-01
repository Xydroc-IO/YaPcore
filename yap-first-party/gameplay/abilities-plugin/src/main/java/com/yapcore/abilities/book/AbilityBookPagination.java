package com.yapcore.abilities.book;

import java.util.List;

public final class AbilityBookPagination {

    private AbilityBookPagination() {
    }

    public record Page<T>(List<T> items, int page, int totalPages, boolean hasPrev, boolean hasNext) {
    }

    public static <T> Page<T> slice(List<T> all, int page, int pageSize) {
        if (all.isEmpty()) {
            return new Page<>(List.of(), 1, 1, false, false);
        }
        int totalPages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int p = Math.min(Math.max(1, page), totalPages);
        int start = (p - 1) * pageSize;
        int end = Math.min(all.size(), start + pageSize);
        return new Page<>(all.subList(start, end), p, totalPages, p > 1, p < totalPages);
    }
}
