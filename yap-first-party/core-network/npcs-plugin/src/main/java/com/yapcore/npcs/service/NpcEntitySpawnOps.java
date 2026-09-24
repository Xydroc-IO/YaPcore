package com.yapcore.npcs.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yapcore.npcs.NpcsConfig;
import com.yapcore.npcs.db.NpcRepository;
import com.yapcore.sched.YapSched;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/** Spawn, refresh, and despawn NPC entities in the world. */
final class NpcEntitySpawnOps {

    private final NpcServiceImpl host;
    private final JavaPlugin plugin;
    private final NpcsConfig config;
    private final NpcRepository repository;

    NpcEntitySpawnOps(NpcServiceImpl host) {
        this.host = host;
        this.plugin = host.plugin();
        this.config = host.config();
        this.repository = host.repository();
    }

    void spawnOrRefresh(NpcRepository.NpcRecord npc) throws SQLException {
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

    void despawn(NpcRepository.NpcRecord npc) {
        if (npc.entityUuid() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(npc.entityUuid());
        if (entity != null) {
            YapSched.entity(plugin, entity, entity::remove);
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
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCollidable(false);
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
        mannequin.setPersistent(true);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setCollidable(false);
        mannequin.setGravity(false);
        mannequin.setDescription(Component.empty());
        tag(mannequin, npc.id());
        if (!NpcHologramNametags.apply(plugin, config, npc.id(), npc.displayName(), mannequin)) {
            mannequin.customName(Component.text(npc.displayName(), NamedTextColor.GOLD));
            mannequin.setCustomNameVisible(true);
        }
        try {
            UUID profileUuid = UUID.nameUUIDFromBytes(("yap-npc:" + npc.id()).getBytes(StandardCharsets.UTF_8));
            String profileName = profileNameFor(npc.id());
            PlayerProfile profile = Bukkit.createProfile(profileUuid, profileName);
            URL skin = URI.create(npc.skinUrl()).toURL();
            PlayerTextures.SkinModel model = npc.skinSlim()
                    ? PlayerTextures.SkinModel.SLIM
                    : PlayerTextures.SkinModel.CLASSIC;
            if (isMojangTextureHost(skin.getHost())) {
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(skin, model);
                profile.setTextures(textures);
            }
            String value = buildTexturesValue(profileUuid, profileName, npc.skinUrl(), npc.skinSlim());
            profile.setProperty(new ProfileProperty("textures", value));
            mannequin.setProfile(ResolvableProfile.resolvableProfile(profile));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to apply NPC skin for " + npc.id(), e);
        }
    }

    private static boolean isMojangTextureHost(String host) {
        return host != null && host.equalsIgnoreCase("textures.minecraft.net");
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

    private static String profileNameFor(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return "NPC";
        }
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < npcId.length() && sb.length() < 16; i++) {
            char c = npcId.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "NPC" : sb.toString();
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
            case "farming", "tractor_supply", "farmer" -> Villager.Profession.FARMER;
            case "fishing", "tackle_shack", "fisherman" -> Villager.Profession.FISHERMAN;
            default -> Villager.Profession.NITWIT;
        };
    }

    private void despawnTaggedNear(Location loc, String id) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Entity e : world.getNearbyEntities(loc, 8.0, 4.0, 8.0)) {
            String tagged = e.getPersistentDataContainer().get(host.npcKey(), PersistentDataType.STRING);
            if (id.equals(tagged)) {
                e.remove();
            }
        }
    }

    private void tag(Entity entity, String id) {
        entity.getPersistentDataContainer().set(host.npcKey(), PersistentDataType.STRING, id);
    }
}
