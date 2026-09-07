package com.yapcore.staff.ranks;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** YaPPerms starter ladder + helpers. Custom ranks still work via typed names. */
public final class RankDefs {

    public record Rank(String id, int weight, String label, String hint, boolean onTrack) {
    }

    /** Matches shipped YaPPerms track {@code yap}. */
    public static final List<Rank> STARTER = List.of(
            new Rank("default", 0, "Default", "New players", true),
            new Rank("vip", 10, "VIP", "Kits & QoL extras", true),
            new Rank("mod", 45, "Mod", "Legacy staff alias (not on track)", false),
            new Rank("staff", 50, "Staff", "Moderation lite + admin menu", true),
            new Rank("admin", 100, "Admin", "Full tools + yapperm.admin", true),
            new Rank("owner", 200, "Owner", "Wildcard / promote / demote", true)
    );

    public static final String DEFAULT_TRACK = "yap";

    private RankDefs() {
    }

    public static Optional<Rank> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT).trim();
        return STARTER.stream().filter(r -> r.id().equals(key)).findFirst();
    }

    public static List<Rank> onTrack() {
        return STARTER.stream().filter(Rank::onTrack).toList();
    }

    public static String pretty(String id) {
        return find(id).map(Rank::label).orElse(id);
    }
}
