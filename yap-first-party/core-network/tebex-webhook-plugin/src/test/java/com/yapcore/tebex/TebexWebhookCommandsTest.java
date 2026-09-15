package com.yapcore.tebex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TebexWebhookCommandsTest {

    @Test
    void substitutesPlaceholders() {
        List<String> out = TebexWebhookCommands.substitute(
                List.of(
                        "yapperm user {username} parent set vip",
                        "kit grant {username} vip",
                        "say {transaction} {packageId}"
                ),
                ".BedrockUser",
                "tbx-99",
                "12345"
        );
        assertEquals(List.of(
                "yapperm user .BedrockUser parent set vip",
                "kit grant .BedrockUser vip",
                "say tbx-99 12345"
        ), out);
    }

    @Test
    void skipsBlankTemplates() {
        assertEquals(List.of("ok Steve"),
                TebexWebhookCommands.substitute(List.of(" ", "ok {username}"), "Steve", "", ""));
        assertEquals(List.of(), TebexWebhookCommands.substitute(null, "Steve", "", ""));
    }
}
