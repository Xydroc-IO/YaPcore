package com.yapcore.link.bedrock.downstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JavaPlayWireHudIdsTest {

    @Test
    void hudAndEquipmentIdsMatchProto776() {
        assertEquals(9, JavaPlayWire.CB_BOSS_EVENT);
        assertEquals(14, JavaPlayWire.CB_CLEAR_TITLES);
        assertEquals(87, JavaPlayWire.CB_SET_ACTION_BAR_TEXT);
        assertEquals(98, JavaPlayWire.CB_SET_DISPLAY_OBJECTIVE);
        assertEquals(102, JavaPlayWire.CB_SET_EQUIPMENT);
        assertEquals(106, JavaPlayWire.CB_SET_OBJECTIVE);
        assertEquals(110, JavaPlayWire.CB_SET_SCORE);
        assertEquals(112, JavaPlayWire.CB_SET_SUBTITLE_TEXT);
        assertEquals(114, JavaPlayWire.CB_SET_TITLE_TEXT);
        assertEquals(115, JavaPlayWire.CB_SET_TITLES_ANIMATION);
        assertEquals(79, JavaPlayWire.CB_RESET_SCORE);
        assertTrue(JavaPlayWire.CB_SET_ENTITY_MOTION == 101);
        assertTrue(JavaPlayWire.CB_UPDATE_ATTRIBUTES == 131);
    }
}
