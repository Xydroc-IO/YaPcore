package com.yapcore.npcs.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Helpers to merge hub action tokens without wiping unrelated kinds. */
public final class NpcActionMutator {

    private NpcActionMutator() {
    }

    public static Optional<Long> shopId(String actionRaw) {
        for (NpcActions.Action a : NpcActions.parse(actionRaw)) {
            if (a.kind() == NpcActions.Kind.SHOP) {
                try {
                    String v = a.value().startsWith("#") ? a.value().substring(1) : a.value();
                    return Optional.of(Long.parseLong(v.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** Replace all tokens of {@code kind}, keep others, append {@code token} if non-null. */
    public static String replaceKind(String existing, NpcActions.Kind kind, String tokenOrNull) {
        List<String> kept = new ArrayList<>();
        for (NpcActions.Action a : NpcActions.parse(existing)) {
            if (a.kind() != kind) {
                kept.add(toToken(a));
            }
        }
        if (tokenOrNull != null && !tokenOrNull.isBlank()) {
            kept.add(tokenOrNull.trim());
        }
        return String.join(";", kept);
    }

    private static String toToken(NpcActions.Action a) {
        String prefix = switch (a.kind()) {
            case SHOP -> "shop";
            case WARP -> "warp";
            case COMMAND -> "command";
            case PLAYER -> "player";
        };
        return prefix + ":" + a.value();
    }

    public static String kindLabel(NpcActions.Kind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
