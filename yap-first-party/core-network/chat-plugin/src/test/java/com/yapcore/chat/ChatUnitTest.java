package com.yapcore.chat;

import com.yapcore.chat.service.ChatFilterService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatUnitTest {

    @Test
    void colorConvertsAmpersand() {
        assertEquals("§aHi", ChatFormat.color("&aHi"));
        assertEquals("", ChatFormat.color(null));
    }

    @Test
    void channelPermissionFlags() {
        ChatConfig.ChannelDef open = new ChatConfig.ChannelDef("global", "{player}: {message}", -1, "");
        ChatConfig.ChannelDef staff = new ChatConfig.ChannelDef("staff", "{player}: {message}", -1, "yapchat.staff");
        assertFalse(open.requiresPermission());
        assertTrue(staff.requiresPermission());
    }

    @Test
    void filterReplaceAndBlock() {
        ChatConfig config = new ChatConfig(null);
        config.applyFilterForTest(true, false, Set.of("bad"), "***");
        ChatFilterService filter = new ChatFilterService(config);
        var replaced = filter.filter("say BAD word");
        assertTrue(replaced.matched());
        assertFalse(replaced.blocked());
        assertEquals("say *** word", replaced.message());

        config.applyFilterForTest(true, true, Set.of("bad"), "***");
        var blocked = filter.filter("bad news");
        assertTrue(blocked.blocked());
        assertTrue(blocked.matched());
    }

    @Test
    void formatNetworkFillsPlayerAndMessage() {
        ChatConfig config = new ChatConfig(null);
        config.applyChannelsForTest("global", Map.of(
                "global", new ChatConfig.ChannelDef("global", "{player}&7: {message}", -1, "")));
        var component = ChatFormat.formatNetwork(config, "global", "lobby", "Alex", "&ahello");
        String plain = LegacyComponentSerializer.legacySection().serialize(component);
        assertTrue(plain.contains("Alex"));
        assertTrue(plain.contains("hello"));
        assertTrue(plain.contains("lobby"));
    }
}
