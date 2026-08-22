package com.yapcore.npcs;

import org.bukkit.entity.Player;

import java.util.List;

/** Quest definitions and per-player progress tracking. */
public interface QuestService {

    List<String> questIds();

    List<QuestProgress> progressFor(Player player);

    QuestProgress objectiveProgress(Player player, String questId, String objectiveId);

    boolean isQuestComplete(Player player, String questId);

    boolean tryComplete(Player player, String questId);
}
