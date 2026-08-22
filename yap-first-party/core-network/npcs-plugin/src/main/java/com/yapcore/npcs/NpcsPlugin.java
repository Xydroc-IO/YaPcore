package com.yapcore.npcs;

import com.yapcore.npcs.cmd.NpcCommands;
import com.yapcore.npcs.cmd.QuestCommands;
import com.yapcore.npcs.db.NpcDatabase;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.npcs.db.QuestRepository;
import com.yapcore.npcs.listener.NpcInteractListener;
import com.yapcore.npcs.listener.QuestListener;
import com.yapcore.npcs.quest.QuestPackLoader;
import com.yapcore.npcs.service.NpcServiceImpl;
import com.yapcore.npcs.service.QuestServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NpcsPlugin extends JavaPlugin {

    private NpcsConfig config;
    private NpcDatabase database;
    private NpcRepository npcRepository;
    private QuestRepository questRepository;
    private QuestPackLoader questLoader;
    private NpcServiceImpl npcService;
    private QuestServiceImpl questService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadNpcs();

        getServer().getPluginManager().registerEvents(new QuestListener(questService), this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(this, config, npcService, questService),
                this);

        PluginCommand npcCmd = getCommand("npc");
        if (npcCmd != null) {
            NpcCommands npcCommands = new NpcCommands(npcService);
            npcCmd.setExecutor(npcCommands);
            npcCmd.setTabCompleter(npcCommands);
        }
        PluginCommand questCmd = getCommand("quests");
        if (questCmd != null) {
            QuestCommands questCommands = new QuestCommands(questService);
            questCmd.setExecutor(questCommands);
            questCmd.setTabCompleter(questCommands);
        }

        var sm = getServer().getServicesManager();
        sm.register(NpcService.class, npcService, this, ServicePriority.Normal);
        sm.register(QuestService.class, questService, this, ServicePriority.Normal);

        npcService.respawnAll();
        getLogger().info("YaPNpcs ready — server=" + config.serverId()
                + " npcs=" + npcService.listIds().size()
                + " quests=" + questService.questIds().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (npcService != null) {
            sm.unregister(NpcService.class, npcService);
        }
        if (questService != null) {
            sm.unregister(QuestService.class, questService);
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadNpcs() {
        if (config == null) {
            config = new NpcsConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new NpcDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPNpcs database failed: " + e.getMessage());
            return;
        }

        if (npcRepository == null) {
            npcRepository = new NpcRepository(database);
        }
        if (questRepository == null) {
            questRepository = new QuestRepository(database);
        }

        Path questsDir = getDataFolder().toPath().resolve("quests");
        try {
            Files.createDirectories(questsDir);
        } catch (Exception e) {
            getLogger().warning("Could not create quests dir: " + e.getMessage());
        }
        questLoader = new QuestPackLoader(questsDir);
        Path mmoQuests = getDataFolder().getParentFile().toPath().resolve("yap-mmo-content/quests");
        questLoader.registerDirectory(mmoQuests);
        questLoader.reload();

        npcService = new NpcServiceImpl(this, config, npcRepository);
        questService = new QuestServiceImpl(this, questLoader, questRepository);
    }

    public NpcsConfig config() {
        return config;
    }
}
