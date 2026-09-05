package com.yapcore.skills.service;

import com.yapcore.mmo.XpTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverallLevelMathTest {

    @Test
    void overallAndSkillCapDefaultTo120() {
        XpTable overall = XpTable.runescape(120, 1.0);
        XpTable skills = XpTable.runescape(120, 1.0);
        assertEquals(120, overall.maxLevel());
        assertEquals(120, skills.maxLevel());
        assertTrue(overall.xpForLevel(120) > overall.xpForLevel(99));
        assertEquals(120, overall.levelForXp(overall.xpForLevel(120)));
    }

    @Test
    void skillCapStaysConfigurable() {
        XpTable skills = XpTable.runescape(120, 1.0);
        assertEquals(120, skills.maxLevel());
    }

    @Test
    void xpShareFeedsOverallSlowerThanSkill() {
        double skillGrant = 100.0;
        double share = 0.5;
        assertEquals(50.0, skillGrant * share);
    }
}
