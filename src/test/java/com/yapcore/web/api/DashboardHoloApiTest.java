package com.yapcore.web.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardHoloApiTest {

    @Test
    void buildsConsoleCommands() {
        assertEquals("yapholo list json", DashboardHoloApi.holoCommand("list", Map.of()));
        assertEquals("yapholo delete spawn", DashboardHoloApi.holoCommand("delete", Map.of("id", "spawn")));
        assertEquals("yapholo create spawn at world 0 66 8 &6Welcome",
                DashboardHoloApi.holoCommand("create", Map.of(
                        "id", "spawn", "world", "world", "x", "0", "y", "66", "z", "8", "text", "&6Welcome")));
        assertEquals("yapholo move spawn at world 1 2 3",
                DashboardHoloApi.holoCommand("move", Map.of(
                        "id", "spawn", "world", "world", "x", "1", "y", "2", "z", "3")));
        assertEquals("yapholo setlines spawn &6A|&7B",
                DashboardHoloApi.holoCommand("setlines", Map.of("id", "spawn", "lines", "&6A\n&7B")));
        assertEquals("yapholo attach spawn npc:shop:0.25",
                DashboardHoloApi.holoCommand("attach", Map.of("id", "spawn", "attach", "npc:shop:0.25")));
        assertEquals("yapholo click spawn LEFT:NEXT RIGHT:CONSOLE:say hi",
                DashboardHoloApi.holoCommand("click", Map.of("id", "spawn", "clicks", "LEFT:NEXT RIGHT:CONSOLE:say hi")));
        assertEquals("yapholo see spawn yapholo.see.vip",
                DashboardHoloApi.holoCommand("see", Map.of("id", "spawn", "perm", "yapholo.see.vip")));
        assertEquals("&6A;;#ICON:DIAMOND", DashboardHoloApi.encodeLines("&6A\n\n#ICON:DIAMOND"));
        assertNull(DashboardHoloApi.holoCommand("delete", Map.of("id", "bad id")));
        assertTrue(DashboardHoloApi.holoCommand("nope", Map.of()) == null);
    }
}
