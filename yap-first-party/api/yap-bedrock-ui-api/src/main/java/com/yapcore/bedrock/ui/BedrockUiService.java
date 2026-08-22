package com.yapcore.bedrock.ui;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Bedrock UI surface: forms, action bar, and scoreboard sidebar mirroring. */
public interface BedrockUiService {

    boolean isBedrock(Player player);

    boolean hasNativeSession(Player player);

    void sendActionBar(Player player, String text);

    void updateSidebar(Player player, String objectiveId, String title, List<String> lines);

    int sendSimpleForm(
            Player player,
            String title,
            String content,
            Consumer<BedrockFormResult> onResult,
            String... buttons);

    int sendCustomForm(
            Player player,
            String title,
            String jsonContentArray,
            Consumer<BedrockFormResult> onResult);
}
