package com.yapcore.npcs.service;

import com.yapcore.npcs.NpcService;
import com.yapcore.npcs.NpcsConfig;
import com.yapcore.npcs.db.NpcRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class NpcServiceImpl implements NpcService {

    private final JavaPlugin plugin;
    private final NpcsConfig config;
    private final NpcRepository repository;
    private final NamespacedKey npcKey;
    private final NpcEntitySpawnOps spawnOps;

    public NpcServiceImpl(JavaPlugin plugin, NpcsConfig config, NpcRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.npcKey = new NamespacedKey(plugin, "npc_id");
        this.spawnOps = new NpcEntitySpawnOps(this);
    }

    JavaPlugin plugin() {
        return plugin;
    }

    NpcsConfig config() {
        return config;
    }

    NpcRepository repository() {
        return repository;
    }

    public NamespacedKey npcKey() {
        return npcKey;
    }

    @Override
    public boolean create(Player player, String id, String displayName) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}")) {
            player.sendMessage("§cInvalid NPC id (use letters, numbers, _, -).");
            return false;
        }
        Location loc = player.getLocation();
        if (createAt(id, displayName, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw())) {
            player.sendMessage("§aCreated NPC §f" + id + " §aat your location.");
            return true;
        }
        player.sendMessage("§cDatabase error creating NPC.");
        return false;
    }

    @Override
    public boolean createAt(String id, String displayName, String world, double x, double y, double z, float yaw) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}")) {
            return false;
        }
        if (world == null || world.isBlank()) {
            return false;
        }
        String dialogue = null;
        String questId = null;
        String action = null;
        String skinUrl = null;
        boolean skinSlim = false;
        UUID entityUuid = null;
        try {
            var existing = repository.get(config.serverId(), id);
            if (existing.isPresent()) {
                var old = existing.get();
                dialogue = old.dialogue();
                questId = old.questId();
                action = old.action();
                skinUrl = old.skinUrl();
                skinSlim = old.skinSlim();
                entityUuid = old.entityUuid();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "npc createAt lookup", e);
        }
        var record = new NpcRepository.NpcRecord(
                id,
                config.serverId(),
                displayName == null || displayName.isBlank() ? id : displayName,
                world,
                x,
                y,
                z,
                yaw,
                entityUuid,
                dialogue,
                questId,
                action,
                skinUrl,
                skinSlim);
        try {
            repository.upsert(record);
            spawnOps.spawnOrRefresh(record);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc create", e);
            return false;
        }
    }

    @Override
    public boolean setQuestId(String id, String questId) {
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                old.dialogue(),
                questId == null || questId.isBlank() ? null : questId.trim(),
                old.action(), old.skinUrl(), old.skinSlim()), "npc setquest");
    }

    @Override
    public boolean setDialogue(String id, String dialogue) {
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                dialogue == null || dialogue.isBlank() ? null : dialogue,
                old.questId(), old.action(), old.skinUrl(), old.skinSlim()), "npc setdialogue");
    }

    @Override
    public boolean setDisplayName(String id, String displayName) {
        String name = displayName == null || displayName.isBlank() ? id : displayName.trim();
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        final String finalName = name;
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), finalName, old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                old.dialogue(), old.questId(), old.action(),
                old.skinUrl(), old.skinSlim()), "npc setname");
    }

    @Override
    public boolean moveTo(String id, String world, double x, double y, double z, float yaw) {
        if (world == null || world.isBlank() || Bukkit.getWorld(world) == null) {
            return false;
        }
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), world,
                x, y, z, yaw, old.entityUuid(),
                old.dialogue(), old.questId(), old.action(),
                old.skinUrl(), old.skinSlim()), "npc move");
    }

    @Override
    public boolean setAction(String id, String action) {
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                old.dialogue(), old.questId(),
                action == null || action.isBlank() ? null : action.trim(),
                old.skinUrl(), old.skinSlim()), "npc setaction");
    }

    @Override
    public boolean setSkinUrl(String id, String skinUrl) {
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                old.dialogue(), old.questId(), old.action(),
                skinUrl == null || skinUrl.isBlank() ? null : skinUrl.trim(),
                old.skinSlim()), "npc setskin");
    }

    @Override
    public boolean setSkinSlim(String id, boolean slim) {
        return updateField(id, old -> new NpcRepository.NpcRecord(
                old.id(), old.serverId(), old.displayName(), old.world(),
                old.x(), old.y(), old.z(), old.yaw(), old.entityUuid(),
                old.dialogue(), old.questId(), old.action(),
                old.skinUrl(), slim), "npc setskinslim");
    }

    private boolean updateField(String id, java.util.function.Function<NpcRepository.NpcRecord, NpcRepository.NpcRecord> map,
                                String logLabel) {
        try {
            var opt = repository.get(config.serverId(), id);
            if (opt.isEmpty()) {
                return false;
            }
            var updated = map.apply(opt.get());
            repository.upsert(updated);
            spawnOps.spawnOrRefresh(updated);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, logLabel, e);
            return false;
        }
    }

    @Override
    public void reloadConfig() {
        config.reload();
    }

    public List<NpcRepository.NpcRecord> listRecords() {
        try {
            return repository.listForServer(config.serverId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc list", e);
            return List.of();
        }
    }

    @Override
    public boolean remove(String id) {
        try {
            var opt = repository.get(config.serverId(), id);
            if (opt.isEmpty()) {
                return false;
            }
            spawnOps.despawn(opt.get());
            NpcHologramNametags.remove(plugin, id);
            return repository.delete(config.serverId(), id);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc remove", e);
            return false;
        }
    }

    @Override
    public List<String> listIds() {
        try {
            return repository.listForServer(config.serverId()).stream().map(NpcRepository.NpcRecord::id).toList();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc list", e);
            return List.of();
        }
    }

    @Override
    public void respawnAll() {
        try {
            for (var npc : repository.listForServer(config.serverId())) {
                spawnOps.spawnOrRefresh(npc);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc respawn", e);
        }
    }

    /** Respawn a single NPC by id (e.g. after an unexpected death). */
    public void respawn(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            var opt = repository.get(config.serverId(), id);
            if (opt.isEmpty()) {
                return;
            }
            spawnOps.spawnOrRefresh(opt.get());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc respawn " + id, e);
        }
    }

    public Optional<NpcRepository.NpcRecord> get(String id) {
        try {
            return repository.get(config.serverId(), id);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc get", e);
            return Optional.empty();
        }
    }

    public Optional<String> npcIdFromEntity(Entity entity) {
        if (!entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

}
