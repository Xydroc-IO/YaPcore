package com.yapcore.items.furniture;

import com.yapcore.items.ItemsConfig;
import com.yapcore.items.ItemsKeys;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemFactory;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Place / break / persist ItemDisplay furniture keyed by world+block. */
public final class FurnitureService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ItemsPlugin plugin;
    private final ItemsConfig config;
    private final ItemsKeys keys;
    private final ItemFactory factory;
    private final Map<String, FurnitureRecord> records = new LinkedHashMap<>();

    public FurnitureService(ItemsPlugin plugin, ItemsConfig config, ItemsKeys keys, ItemFactory factory) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.factory = factory;
    }

    public void load() {
        records.clear();
        File file = dataFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("furniture");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            FurnitureRecord rec = FurnitureRecord.from(sec);
            if (rec != null) {
                records.put(rec.key(), rec);
            }
        }
        plugin.getLogger().info("Loaded " + records.size() + " furniture records");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (FurnitureRecord rec : records.values()) {
            String path = "furniture." + rec.key();
            yaml.set(path + ".world", rec.world());
            yaml.set(path + ".x", rec.x());
            yaml.set(path + ".y", rec.y());
            yaml.set(path + ".z", rec.z());
            yaml.set(path + ".item-id", rec.itemId());
            yaml.set(path + ".yaw", rec.yaw());
            if (rec.entityUuid() != null) {
                yaml.set(path + ".entity", rec.entityUuid().toString());
            }
        }
        try {
            yaml.save(dataFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed saving furniture.yml", e);
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "furniture.yml");
    }

    public boolean tryPlace(Player player, ItemStack stack, Block clicked, BlockFace face) {
        var defOpt = factory.definitionOf(stack);
        if (defOpt.isEmpty() || !defOpt.get().isFurniture()) {
            return false;
        }
        ItemDefinition def = defOpt.get();
        if (config.placeRequiresSneak() && !player.isSneaking()) {
            return false;
        }
        Block placeAt = clicked.getRelative(face);
        if (!placeAt.getType().isAir()) {
            return false;
        }
        String key = FurnitureRecord.keyOf(placeAt.getWorld().getName(), placeAt.getX(), placeAt.getY(), placeAt.getZ());
        if (records.containsKey(key)) {
            return true;
        }
        long inChunk = records.values().stream()
                .filter(r -> r.world().equals(placeAt.getWorld().getName())
                        && (r.x() >> 4) == (placeAt.getX() >> 4)
                        && (r.z() >> 4) == (placeAt.getZ() >> 4))
                .count();
        if (inChunk >= config.furnitureMaxPerChunk()) {
            player.sendMessage(LEGACY.deserialize("&cToo much furniture in this chunk."));
            return true;
        }
        Location loc = placeAt.getLocation().add(0.5, 0.5, 0.5);
        loc.setYaw(player.getLocation().getYaw());
        YapSched.region(plugin, loc, () -> {
            ItemDisplay display = placeAt.getWorld().spawn(loc, ItemDisplay.class, ent -> {
                ItemStack visual = factory.build(def, 1);
                ent.setItemStack(visual);
                float scale = def.furniture().scale();
                ent.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f()));
                ent.getPersistentDataContainer().set(keys.furnitureId(), PersistentDataType.STRING, def.id());
                ent.setPersistent(true);
            });
            FurnitureRecord rec = new FurnitureRecord(
                    placeAt.getWorld().getName(),
                    placeAt.getX(),
                    placeAt.getY(),
                    placeAt.getZ(),
                    def.id(),
                    loc.getYaw(),
                    display.getUniqueId());
            records.put(rec.key(), rec);
            save();
            playPlaceSound(loc, def.furniture().placeSound());
            String msg = config.msgPlaced().replace("{id}", def.id());
            player.sendMessage(LEGACY.deserialize(msg));
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && factory.idOf(hand).filter(id -> id.equals(def.id())).isPresent()) {
                hand.setAmount(hand.getAmount() - 1);
            }
        });
        return true;
    }

    public boolean tryBreak(Player player, Entity entity) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }
        String itemId = display.getPersistentDataContainer().get(keys.furnitureId(), PersistentDataType.STRING);
        if (itemId == null) {
            return false;
        }
        Location loc = display.getLocation();
        String key = FurnitureRecord.keyOf(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
        FurnitureRecord rec = records.get(key);
        if (rec == null) {
            // fallback scan
            for (Map.Entry<String, FurnitureRecord> e : records.entrySet()) {
                if (e.getValue().entityUuid() != null && e.getValue().entityUuid().equals(display.getUniqueId())) {
                    key = e.getKey();
                    rec = e.getValue();
                    break;
                }
            }
        }
        final String removeKey = key;
        final FurnitureRecord removeRec = rec;
        YapSched.region(plugin, loc, () -> {
            display.remove();
            if (removeRec != null) {
                records.remove(removeKey);
                save();
                boolean drops = plugin.registry().get(itemId)
                        .map(d -> d.furniture() == null || d.furniture().breakDrops())
                        .orElse(true);
                if (drops && player != null) {
                    factory.create(itemId, 1).ifPresent(stack ->
                            loc.getWorld().dropItemNaturally(loc, stack));
                }
            }
            if (player != null) {
                player.sendMessage(LEGACY.deserialize(config.msgBroke().replace("{id}", itemId)));
            }
        });
        return true;
    }

    public boolean removeLookingAt(Player player) {
        Entity target = player.getTargetEntity(6);
        if (target == null) {
            player.sendMessage(LEGACY.deserialize("&cLook at furniture to remove."));
            return false;
        }
        return tryBreak(player, target);
    }

    public void respawnChunk(World world, int chunkX, int chunkZ) {
        for (FurnitureRecord rec : records.values()) {
            if (!rec.world().equals(world.getName())) {
                continue;
            }
            if ((rec.x() >> 4) != chunkX || (rec.z() >> 4) != chunkZ) {
                continue;
            }
            Location loc = new Location(world, rec.x() + 0.5, rec.y() + 0.5, rec.z() + 0.5, rec.yaw(), 0);
            YapSched.region(plugin, loc, () -> spawnIfMissing(rec, loc));
        }
    }

    private void spawnIfMissing(FurnitureRecord rec, Location loc) {
        if (rec.entityUuid() != null) {
            Entity existing = Bukkit.getEntity(rec.entityUuid());
            if (existing != null && !existing.isDead()) {
                return;
            }
        }
        // clear stray displays at block
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 0.6, 0.6, 0.6)) {
            if (nearby instanceof ItemDisplay display) {
                String id = display.getPersistentDataContainer().get(keys.furnitureId(), PersistentDataType.STRING);
                if (rec.itemId().equals(id)) {
                    nearby.remove();
                }
            }
        }
        var defOpt = plugin.registry().get(rec.itemId());
        if (defOpt.isEmpty()) {
            return;
        }
        ItemDefinition def = defOpt.get();
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, ent -> {
            ent.setItemStack(factory.build(def, 1));
            float scale = def.furniture() != null ? def.furniture().scale() : 1f;
            ent.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            ent.getPersistentDataContainer().set(keys.furnitureId(), PersistentDataType.STRING, def.id());
            ent.setPersistent(true);
        });
        FurnitureRecord updated = new FurnitureRecord(
                rec.world(), rec.x(), rec.y(), rec.z(), rec.itemId(), rec.yaw(), display.getUniqueId());
        records.put(updated.key(), updated);
        save();
    }

    public void shutdown() {
        save();
    }

    private static void playPlaceSound(Location loc, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
            loc.getWorld().playSound(loc, sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public record FurnitureRecord(
            String world,
            int x,
            int y,
            int z,
            String itemId,
            float yaw,
            UUID entityUuid) {

        public String key() {
            return keyOf(world, x, y, z);
        }

        public static String keyOf(String world, int x, int y, int z) {
            return world + ";" + x + ";" + y + ";" + z;
        }

        public static FurnitureRecord from(ConfigurationSection sec) {
            String world = sec.getString("world");
            if (world == null) {
                return null;
            }
            UUID entity = null;
            String entityRaw = sec.getString("entity");
            if (entityRaw != null && !entityRaw.isBlank()) {
                try {
                    entity = UUID.fromString(entityRaw);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new FurnitureRecord(
                    world,
                    sec.getInt("x"),
                    sec.getInt("y"),
                    sec.getInt("z"),
                    sec.getString("item-id", ""),
                    (float) sec.getDouble("yaw", 0),
                    entity);
        }
    }
}
