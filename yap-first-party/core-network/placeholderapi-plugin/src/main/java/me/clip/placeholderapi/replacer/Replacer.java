package me.clip.placeholderapi.replacer;

import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Placeholder text rewriter. */
public interface Replacer {

    @NotNull
    String apply(
            @NotNull String text,
            @Nullable OfflinePlayer player,
            @NotNull Function<String, @Nullable PlaceholderExpansion> lookup);

    enum Closure {
        PERCENT('%', '%'),
        BRACKET('{', '}');

        public final char head;
        public final char tail;

        Closure(char head, char tail) {
            this.head = head;
            this.tail = tail;
        }
    }
}
