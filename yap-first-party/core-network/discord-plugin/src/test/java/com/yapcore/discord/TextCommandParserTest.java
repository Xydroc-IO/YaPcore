package com.yapcore.discord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextCommandParserTest {

    @Test
    void parsesPlayerlistTokens() {
        assertEquals(TextCommandParser.Kind.PLAYERLIST,
                TextCommandParser.parse("playerlist", true, "!c").kind());
        assertEquals(TextCommandParser.Kind.PLAYERLIST,
                TextCommandParser.parse("!playerlist", true, "!c").kind());
        assertEquals(TextCommandParser.Kind.PLAYERLIST,
                TextCommandParser.parse("  PlayerList  ", true, "!c").kind());
        assertEquals(TextCommandParser.Kind.NONE,
                TextCommandParser.parse("playerlist", false, "!c").kind());
    }

    @Test
    void parsesConsolePrefix() {
        var parsed = TextCommandParser.parse("!c tps", true, "!c");
        assertEquals(TextCommandParser.Kind.CONSOLE, parsed.kind());
        assertEquals("tps", parsed.consoleCommand());

        var withArgs = TextCommandParser.parse("!c list", true, "!c");
        assertEquals("list", withArgs.consoleCommand());

        assertEquals(TextCommandParser.Kind.NONE,
                TextCommandParser.parse("!ctps", true, "!c").kind());
        assertEquals(TextCommandParser.Kind.NONE,
                TextCommandParser.parse("!c", true, "!c").kind());
        assertEquals(TextCommandParser.Kind.NONE,
                TextCommandParser.parse("!c tps", true, "").kind());
    }

    @Test
    void consoleWhitelistChecksFirstToken() {
        assertTrue(TextCommandParser.isConsoleCommandAllowed("tps", List.of("tps", "list")));
        assertTrue(TextCommandParser.isConsoleCommandAllowed("list uuids", List.of("tps", "list")));
        assertFalse(TextCommandParser.isConsoleCommandAllowed("stop", List.of("tps", "list")));
        assertFalse(TextCommandParser.isConsoleCommandAllowed("tps", List.of()));
        assertFalse(TextCommandParser.isConsoleCommandAllowed("", List.of("tps")));
    }

    @Test
    void roleAllowlistRequiresConfiguredRoles() {
        assertFalse(TextCommandParser.hasAnyRole(Set.of("1"), List.of()));
        assertFalse(TextCommandParser.hasAnyRole(Set.of(), List.of("1")));
        assertTrue(TextCommandParser.hasAnyRole(Set.of("1", "2"), List.of("2")));
        assertFalse(TextCommandParser.hasAnyRole(Set.of("1"), List.of("9")));
    }

    @Test
    void truncateAddsEllipsis() {
        assertEquals("ab…", TextCommandParser.truncate("abcd", 3));
        assertEquals("hi", TextCommandParser.truncate("hi", 10));
        assertEquals("", TextCommandParser.truncate(null, 5));
    }

    @Test
    void channelTopicTemplateReplacesPlaceholders() {
        String out = TextCommandParser.applyTopicTemplate(
                "Online: {online} | MSPT: {mspt} / {max}", 7, "12.50", 60);
        assertEquals("Online: 7 | MSPT: 12.50 / 60", out);
    }
}
