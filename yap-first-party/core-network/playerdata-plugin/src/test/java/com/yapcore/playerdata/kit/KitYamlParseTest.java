package com.yapcore.playerdata.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitYamlParseTest {

    @Test
    void formatsCooldown() {
        assertEquals("ready", CooldownFormat.formatSeconds(0));
        assertEquals("1d2h", CooldownFormat.formatSeconds(86400 + 7200));
        assertEquals("45s", CooldownFormat.formatSeconds(45));
    }
}
