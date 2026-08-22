package com.yapcore.mmo;

import org.bukkit.entity.Player;

/** Optional hook for mirroring skill XP feedback to Bedrock UI (M5). */
public interface SkillFeedbackBridge {

    void onXpGain(Player player, SkillId skillId, double amount, String label);
}
