package me.clip.placeholderapi.replacer;

import java.util.Locale;
import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scans for {@code %id_params%} / {@code {id_params}} and resolves via expansion lookup.
 * Clean-room implementation matching PlaceholderAPI placeholder grammar.
 */
public final class CharsReplacer implements Replacer {

    @NotNull
    private final Closure closure;

    public CharsReplacer(@NotNull final Closure closure) {
        this.closure = closure;
    }

    @Override
    @NotNull
    public String apply(
            @NotNull final String text,
            @Nullable final OfflinePlayer player,
            @NotNull final Function<String, @Nullable PlaceholderExpansion> lookup) {
        final char head = closure.head;
        final char tail = closure.tail;
        int start = text.indexOf(head);
        if (start < 0) {
            return text;
        }

        final int length = text.length();
        final StringBuilder out = new StringBuilder(length + (length >> 3));
        int cursor = 0;

        while (start >= 0) {
            if (start > cursor) {
                out.append(text, cursor, start);
            }

            final int end = text.indexOf(tail, start + 1);
            if (end < 0) {
                out.append(text, start, length);
                return out.toString();
            }

            // Spaces before first '_' invalidate the token — treat head as literal.
            int underscore = -1;
            boolean invalid = false;
            for (int i = start + 1; i < end; i++) {
                final char c = text.charAt(i);
                if (c == ' ' && underscore < 0) {
                    invalid = true;
                    break;
                }
                if (c == '_' && underscore < 0) {
                    underscore = i;
                }
            }

            if (invalid) {
                out.append(head);
                cursor = start + 1;
                start = text.indexOf(head, cursor);
                continue;
            }

            if (underscore < 0) {
                out.append(text, start, end + 1);
                cursor = end + 1;
                start = text.indexOf(head, cursor);
                continue;
            }

            final String identifier = text.substring(start + 1, underscore);
            final String params = underscore + 1 < end
                    ? text.substring(underscore + 1, end)
                    : "";

            final PlaceholderExpansion expansion = lookup.apply(identifier.toLowerCase(Locale.ROOT));
            final String replacement = expansion != null ? expansion.onRequest(player, params) : null;
            if (replacement != null) {
                out.append(replacement);
            } else {
                out.append(head).append(identifier).append('_').append(params).append(tail);
            }

            cursor = end + 1;
            start = text.indexOf(head, cursor);
        }

        if (cursor < length) {
            out.append(text, cursor, length);
        }
        return out.toString();
    }
}
