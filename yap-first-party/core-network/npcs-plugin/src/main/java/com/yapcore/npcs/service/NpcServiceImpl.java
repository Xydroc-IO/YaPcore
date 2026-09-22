package com.yapcore.npcs.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yapcore.npcs.NpcService;
import com.yapcore.npcs.NpcsConfig;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.sched.YapSched;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class NpcServiceImpl implements NpcService {

    private final JavaPlugin plugin;
    private final NpcsConfig config;
    private final NpcRepository repository;
    private final NamespacedKey npcKey;

    public NpcServiceImpl(JavaPlugin plugin, NpcsConfig config, NpcRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.npcKey = new NamespacedKey(plugin, "npc_id");
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
            spawnOrRefresh(record);
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
            spawnOrRefresh(updated);
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
            despawn(opt.get());
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
                spawnOrRefresh(npc);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "npc respawn", e);
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

    private void spawnOrRefresh(NpcRepository.NpcRecord npc) throws SQLException {
        World world = Bukkit.getWorld(npc.world());
        if (world == null) {
            return;
        }
        Location loc = npc.toLocation(world);
        if (!ownedByCurrentRegion(world, loc)) {
            YapSched.region(plugin, loc, () -> {
                try {
                    spawnOrRefreshOnRegion(npc);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "npc spawn " + npc.id(), e);
                }
            });
            return;
        }
        try {
            spawnOrRefreshOnRegion(npc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "npc spawn " + npc.id(), e);
        }
    }

    private void spawnOrRefreshOnRegion(NpcRepository.NpcRecord npc) throws SQLException {
        World world = Bukkit.getWorld(npc.world());
        if (world == null) {
            return;
        }
        Location loc = npc.toLocation(world);
        loc.getChunk();
        if (npc.entityUuid() != null) {
            Entity existing = Bukkit.getEntity(npc.entityUuid());
            if (existing != null && !existing.isDead()) {
                YapSched.entity(plugin, existing, () -> {
                    existing.remove();
                    YapSched.region(plugin, loc, () -> spawnFreshSafe(npc));
                });
                return;
            }
        }
        spawnFresh(npc);
    }

    private void spawnFreshSafe(NpcRepository.NpcRecord npc) {
        try {
            spawnFresh(npc);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "npc spawn " + npc.id(), e);
        }
    }

    private void spawnFresh(NpcRepository.NpcRecord npc) throws SQLException {
        World world = Bukkit.getWorld(npc.world());
        if (world == null) {
            return;
        }
        Location loc = npc.toLocation(world);
        loc.getChunk();
        despawnTaggedNear(loc, npc.id());
        boolean useMannequin = npc.skinUrl() != null && !npc.skinUrl().isBlank();
        if (useMannequin) {
            Mannequin mannequin = (Mannequin) world.spawnEntity(loc, EntityType.MANNEQUIN);
            applyMannequin(mannequin, npc);
            repository.setEntityUuid(config.serverId(), npc.id(), mannequin.getUniqueId());
            return;
        }
        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setProfession(professionFor(npc.id()));
        tag(villager, npc.id());
        if (!NpcHologramNametags.apply(plugin, config, npc.id(), npc.displayName(), villager)) {
            villager.customName(Component.text(npc.displayName(), NamedTextColor.GOLD));
            villager.setCustomNameVisible(true);
        }
        repository.setEntityUuid(config.serverId(), npc.id(), villager.getUniqueId());
    }

    private static boolean ownedByCurrentRegion(World world, Location loc) {
        try {
            return Bukkit.isOwnedByCurrentRegion(loc);
        } catch (Throwable t) {
            return true;
        }
    }

    private void applyMannequin(Mannequin mannequin, NpcRepository.NpcRecord npc) {
        mannequin.setImmovable(true);
        mannequin.setInvulnerable(true);
        mannequin.setSilent(true);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setGravity(false);
        mannequin.setDescription(Component.empty());
        tag(mannequin, npc.id());
        if (!NpcHologramNametags.apply(plugin, config, npc.id(), npc.displayName(), mannequin)) {
            mannequin.customName(Component.text(npc.displayName(), NamedTextColor.GOLD));
            mannequin.setCustomNameVisible(true);
        }
        try {
            UUID profileUuid = UUID.nameUUIDFromBytes(("yap-npc:" + npc.id()).getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(profileUuid, truncateName(npc.displayName()));
            PlayerTextures textures = profile.getTextures();
            URL skin = URI.create(npc.skinUrl()).toURL();
            PlayerTextures.SkinModel model = npc.skinSlim()
                    ? PlayerTextures.SkinModel.SLIM
                    : PlayerTextures.SkinModel.CLASSIC;
            textures.setSkin(skin, model);
            profile.setTextures(textures);
            // Also set textures property for clients that read ProfileProperty
            String value = buildTexturesValue(profileUuid, npc.displayName(), npc.skinUrl(), npc.skinSlim());
            profile.setProperty(new ProfileProperty("textures", value));
            mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply NPC skin for " + npc.id(), e);
        }
    }

    private static String buildTexturesValue(UUID uuid, String name, String skinUrl, boolean slim) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"").append(uuid.toString().replace("-", ""))
                .append("\",\"profileName\":\"").append(escape(name))
                .append("\",\"textures\":{\"SKIN\":{\"url\":\"").append(escape(skinUrl)).append('"');
        if (slim) {
            json.append(",\"metadata\":{\"model\":\"slim\"}");
        }
        json.append("}}}");
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncateName(String name) {
        if (name == null || name.isBlank()) {
            return "NPC";
        }
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    private static Villager.Profession professionFor(String id) {
        if (id == null) {
            return Villager.Profession.NITWIT;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "armorer" -> Villager.Profession.ARMORER;
            case "weaponsmith" -> Villager.Profession.WEAPONSMITH;
            case "tools", "toolsmith" -> Villager.Profession.TOOLSMITH;
            case "enchant", "librarian" -> Villager.Profession.LIBRARIAN;
            case "chef", "butcher" -> Villager.Profession.BUTCHER;
            case "blocks", "mason" -> Villager.Profession.MASON;
            case "redstone", "cleric" -> Villager.Profession.CLERIC;
            default -> Villager.Profession.NITWIT;
        };
    }

    private void despawn(NpcRepository.NpcRecord npc) {
        if (npc.entityUuid() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(npc.entityUuid());
        if (entity != null) {
            YapSched.entity(plugin, entity, entity::remove);
        }
    }

    private void despawnTaggedNear(Location loc, String id) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Entity e : world.getNearbyEntities(loc, 8.0, 4.0, 8.0)) {
            String tagged = e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
            if (id.equals(tagged)) {
                e.remove();
            }
        }
    }

    private void tag(Entity entity, String id) {
        entity.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
    }
}
