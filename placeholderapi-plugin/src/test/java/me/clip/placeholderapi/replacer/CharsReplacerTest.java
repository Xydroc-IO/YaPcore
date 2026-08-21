package me.clip.placeholderapi.replacer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CharsReplacerTest {

    @Test
    void replacesPercentPlaceholders() {
        Map<String, String> values = Map.of("name", "Steve", "world", "world");
        String out = new CharsReplacer(Replacer.Closure.PERCENT)
                .apply("Hi %player_name% in %player_world%!", null, lookup("player", values));
        assertEquals("Hi Steve in world!", out);
    }

    @Test
    void leavesUnknownIntact() {
        String raw = "Hello %missing_thing%";
        String out = new CharsReplacer(Replacer.Closure.PERCENT)
                .apply(raw, null, id -> null);
        assertEquals(raw, out);
    }

    @Test
    void bracketPlaceholders() {
        String out = new CharsReplacer(Replacer.Closure.BRACKET)
                .apply("X={server_online}", null, lookup("server", Map.of("online", "12")));
        assertEquals("X=12", out);
    }

    @Test
    void containsHelpers() {
        assertTrue(me.clip.placeholderapi.PlaceholderAPI.containsPlaceholders("%a_b%"));
        assertTrue(me.clip.placeholderapi.PlaceholderAPI.containsBracketPlaceholders("{a_b}"));
    }

    private static Function<String, PlaceholderExpansion> lookup(
            String expectedId, Map<String, String> params) {
        PlaceholderExpansion expansion = new PlaceholderExpansion() {
            @Override
            public @NotNull String getIdentifier() {
                return expectedId;
            }

            @Override
            public @NotNull String getAuthor() {
                return "test";
            }

            @Override
            public @NotNull String getVersion() {
                return "1";
            }

            @Override
            public @Nullable String onRequest(OfflinePlayer player, @NotNull String p) {
                return params.get(p);
            }

            @Override
            public @Nullable String onPlaceholderRequest(Player player, @NotNull String p) {
                return params.get(p);
            }
        };
        Map<String, PlaceholderExpansion> map = new HashMap<>();
        map.put(expectedId, expansion);
        return map::get;
    }
}
