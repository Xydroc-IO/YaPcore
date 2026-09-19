package com.yapcore.holo.impl;

import com.yapcore.holo.HologramLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/** Page storage + per-player page index. */
public final class HologramPages {

    private final List<List<String>> pages = new ArrayList<>();
    private final Map<UUID, Integer> playerPage = new ConcurrentHashMap<>();

    public HologramPages(List<String> firstPage) {
        pages.add(copy(firstPage));
    }

    public List<String> pageZero() {
        return List.copyOf(pages.get(0));
    }

    public List<List<String>> all() {
        List<List<String>> out = new ArrayList<>(pages.size());
        for (List<String> page : pages) {
            out.add(List.copyOf(page));
        }
        return out;
    }

    public int count() {
        return pages.size();
    }

    public void setAll(List<List<String>> next) {
        pages.clear();
        if (next == null || next.isEmpty()) {
            pages.add(List.of("&f"));
            return;
        }
        for (List<String> page : next) {
            pages.add(copy(page));
        }
        playerPage.replaceAll((id, n) -> Math.min(n, pages.size() - 1));
    }

    public void replacePageZero(List<String> lines) {
        pages.set(0, copy(lines));
    }

    public void addPage() {
        pages.add(new ArrayList<>(List.of("&7New page")));
    }

    public List<HologramLine> linesFor(UUID playerId) {
        int index = pageOf(playerId);
        List<String> raw = pages.get(index);
        List<HologramLine> out = new ArrayList<>(raw.size());
        for (String row : raw) {
            out.add(HologramLine.parse(row));
        }
        return out;
    }

    public int pageOf(UUID playerId) {
        int n = playerPage.getOrDefault(playerId, 0);
        if (n < 0 || n >= pages.size()) {
            return 0;
        }
        return n;
    }

    public void setPage(UUID playerId, int page) {
        if (playerId == null) {
            return;
        }
        int max = pages.size() - 1;
        int n = Math.max(0, Math.min(max, page));
        playerPage.put(playerId, n);
    }

    public void next(UUID playerId) {
        int cur = pageOf(playerId);
        setPage(playerId, cur + 1 >= pages.size() ? 0 : cur + 1);
    }

    public void prev(UUID playerId) {
        int cur = pageOf(playerId);
        setPage(playerId, cur - 1 < 0 ? pages.size() - 1 : cur - 1);
    }

    public int maxLines() {
        int max = 1;
        for (List<String> page : pages) {
            max = Math.max(max, Math.max(1, page.size()));
        }
        return max;
    }

    public void forget(UUID playerId) {
        playerPage.remove(playerId);
    }

    public static List<List<String>> splitPages(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(List.of("&f"));
        }
        String[] chunks = raw.split(";;", -1);
        List<List<String>> out = new ArrayList<>();
        for (String chunk : chunks) {
            out.add(splitLines(chunk));
        }
        return out;
    }

    public static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("&f");
        }
        String[] parts = raw.split("\\|", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(part);
        }
        return out;
    }

    public String serializePages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                sb.append(";;");
            }
            sb.append(String.join("|", pages.get(i)));
        }
        return sb.toString();
    }

    private static List<String> copy(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>(List.of("&f"));
        }
        return new ArrayList<>(lines);
    }

    public static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
