package com.yapcore.npcs.listener;

import com.yapcore.npcs.NpcsConfig;
import com.yapcore.npcs.action.NpcActionDispatcher;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.npcs.service.QuestServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NpcInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final NpcsConfig config;
    private final NpcServiceImpl npcs;
    private final QuestServiceImpl quests;
    private final NpcActionDispatcher actions;

    public NpcInteractListener(JavaPlugin plugin, NpcsConfig config,
                               NpcServiceImpl npcs, QuestServiceImpl quests) {
        this.plugin = plugin;
        this.config = config;
        this.npcs = npcs;
        this.quests = quests;
        this.actions = new NpcActionDispatcher(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        var npcIdOpt = npcs.npcIdFromEntity(clicked);
        if (npcIdOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String npcId = npcIdOpt.get();
        YapSched.entity(plugin, clicked, () -> handleInteract(player, npcId));
    }

    private void handleInteract(Player player, String npcId) {
        var npcOpt = npcs.get(npcId);
        if (npcOpt.isEmpty()) {
            return;
        }
        var npc = npcOpt.get();
        String dialogue = npc.dialogue();
        if (dialogue == null || dialogue.isBlank()) {
            dialogue = config.defaultDialogue();
        } else {
            dialogue = ChatColor.translateAlternateColorCodes('&', dialogue);
        }
        player.sendMessage(dialogue);

        quests.onTalk(player, npcId);

        if (npc.questId() != null && !npc.questId().isBlank()) {
            if (quests.isQuestComplete(player, npc.questId())) {
                quests.tryComplete(player, npc.questId());
            } else {
                player.sendMessage("§7Quest §f" + npc.questId() + " §7— use §e/quests progress "
                        + npc.questId());
            }
        }

        if (npc.action() != null && !npc.action().isBlank()) {
            actions.dispatch(player, npc.action());
        }
    }
}
