package me.clip.placeholderapi.builtin;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.builtin.util.PlayerUtil;
import me.clip.placeholderapi.builtin.util.PlayerUtil.Cardinal;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Built-in player expansion — placeholder set aligned with upstream Player expansion.
 * Clean-room YaP implementation (not GPL Player-Expansion source).
 */
public final class PlayerExpansion extends PlaceholderExpansion implements Configurable {

    private String low = "&a";
    private String medium = "&e";
    private String high = "&c";
    private int mediumValue = 50;
    private int highValue = 100;
    private String north = "N";
    private String northEast = "NE";
    private String east = "E";
    private String southEast = "SE";
    private String south = "S";
    private String southWest = "SW";
    private String west = "W";
    private String northWest = "NW";

    @Override
    @NotNull
    public String getIdentifier() {
        return "player";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "YapLabs";
    }

    @Override
    @NotNull
    public String getVersion() {
        return "2.0.0-yap";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("ping_color.high", "&c");
        defaults.put("ping_color.medium", "&e");
        defaults.put("ping_color.low", "&a");
        defaults.put("ping_value.medium", 50);
        defaults.put("ping_value.high", 100);
        defaults.put("direction.north", "N");
        defaults.put("direction.north_east", "NE");
        defaults.put("direction.east", "E");
        defaults.put("direction.south_east", "SE");
        defaults.put("direction.south", "S");
        defaults.put("direction.south_west", "SW");
        defaults.put("direction.west", "W");
        defaults.put("direction.north_west", "NW");
        return defaults;
    }

    @Override
    @NotNull
    public List<String> getPlaceholders() {
        return List.of(
                "%player_name%", "%player_displayname%", "%player_uuid%", "%player_health%",
                "%player_max_health%", "%player_food_level%", "%player_level%", "%player_exp%",
                "%player_gamemode%", "%player_world%", "%player_x%", "%player_y%", "%player_z%",
                "%player_ping%", "%player_colored_ping%", "%player_is_op%", "%player_online%",
                "%player_biome%", "%player_direction%", "%player_ip%", "%player_locale%");
    }

    @Override
    public boolean register() {
        low = getString("ping_color.low", "&a");
        medium = getString("ping_color.medium", "&e");
        high = getString("ping_color.high", "&c");
        mediumValue = getInt("ping_value.medium", 50);
        highValue = getInt("ping_value.high", 100);
        north = getString("direction.north", "N");
        northEast = getString("direction.north_east", "NE");
        east = getString("direction.east", "E");
        southEast = getString("direction.south_east", "SE");
        south = getString("direction.south", "S");
        southWest = getString("direction.south_west", "SW");
        west = getString("direction.west", "W");
        northWest = getString("direction.north_west", "NW");
        return super.register();
    }

    @Override
    @Nullable
    public String onRequest(final OfflinePlayer player, @NotNull final String identifier) {
        if (identifier.startsWith("ping_") || identifier.startsWith("colored_ping_")) {
            boolean colored = identifier.startsWith("colored_ping_");
            String name = identifier.substring(colored ? 13 : 5);
            Player target = Bukkit.getPlayerExact(name);
            return target == null ? "0" : retrievePing(target, colored);
        }

        if (player == null) {
            return "";
        }

        switch (identifier) {
            case "name" -> {
                return player.getName();
            }
            case "uuid" -> {
                return player.getUniqueId().toString();
            }
            case "has_played_before" -> {
                return bool(player.hasPlayedBefore());
            }
            case "online" -> {
                return bool(player.isOnline());
            }
            case "is_whitelisted" -> {
                return bool(player.isWhitelisted());
            }
            case "is_banned" -> {
                return bool(player.isBanned());
            }
            case "is_op" -> {
                return bool(player.isOp());
            }
            case "first_played", "first_join" -> {
                return String.valueOf(player.getFirstPlayed());
            }
            case "first_played_formatted", "first_join_date" -> {
                return PlaceholderAPIPlugin.getDateFormat().format(new Date(player.getFirstPlayed()));
            }
            case "last_played", "last_join" -> {
                return String.valueOf(player.getLastPlayed());
            }
            case "last_played_formatted", "last_join_date" -> {
                return PlaceholderAPIPlugin.getDateFormat().format(new Date(player.getLastPlayed()));
            }
            case "bed_x" -> {
                return player.getBedSpawnLocation() != null
                        ? String.valueOf(player.getBedSpawnLocation().getBlockX()) : "";
            }
            case "bed_y" -> {
                return player.getBedSpawnLocation() != null
                        ? String.valueOf(player.getBedSpawnLocation().getBlockY()) : "";
            }
            case "bed_z" -> {
                return player.getBedSpawnLocation() != null
                        ? String.valueOf(player.getBedSpawnLocation().getBlockZ()) : "";
            }
            case "bed_world" -> {
                return player.getBedSpawnLocation() != null && player.getBedSpawnLocation().getWorld() != null
                        ? player.getBedSpawnLocation().getWorld().getName() : "";
            }
            default -> {
                // continue to online
            }
        }

        if (!player.isOnline()) {
            return "";
        }
        Player p = player.getPlayer();
        if (p == null) {
            return "";
        }

        if (identifier.startsWith("has_permission_")) {
            String perm = identifier.substring("has_permission_".length());
            return bool(!perm.isEmpty() && p.hasPermission(perm));
        }
        if (identifier.startsWith("has_potioneffect_")) {
            String effect = identifier.substring("has_potioneffect_".length());
            PotionEffectType type = PotionEffectType.getByName(effect);
            return bool(type != null && p.hasPotionEffect(type));
        }
        if (identifier.startsWith("item_in_hand_level_")) {
            String ench = identifier.substring("item_in_hand_level_".length());
            Enchantment enchantment = Enchantment.getByName(ench);
            return String.valueOf(enchantment == null ? 0
                    : PlayerUtil.itemInHand(p).getEnchantmentLevel(enchantment));
        }
        if (identifier.startsWith("item_in_offhand_level_")) {
            String ench = identifier.substring("item_in_offhand_level_".length());
            Enchantment enchantment = Enchantment.getByName(ench);
            return String.valueOf(enchantment == null ? 0
                    : p.getInventory().getItemInOffHand().getEnchantmentLevel(enchantment));
        }
        if (identifier.startsWith("locale")) {
            return locale(p, identifier);
        }

        return switch (identifier) {
            case "absorption" -> String.valueOf((int) p.getAbsorptionAmount());
            case "has_empty_slot" -> bool(p.getInventory().firstEmpty() > -1);
            case "empty_slots" -> String.valueOf(PlayerUtil.getEmptySlots(p));
            case "server", "servername" -> "see %server_name%";
            case "displayname" -> p.getDisplayName();
            case "list_name" -> p.getPlayerListName();
            case "gamemode" -> p.getGameMode().name();
            case "direction" -> directionLabel(PlayerUtil.getDirection(p));
            case "direction_xz" -> PlayerUtil.getXZDirection(p);
            case "world" -> p.getWorld().getName();
            case "world_type" -> worldType(p.getWorld().getEnvironment());
            case "x" -> String.valueOf(p.getLocation().getBlockX());
            case "x_long" -> String.valueOf(p.getLocation().getX());
            case "y" -> String.valueOf(p.getLocation().getBlockY());
            case "y_long" -> String.valueOf(p.getLocation().getY());
            case "z" -> String.valueOf(p.getLocation().getBlockZ());
            case "z_long" -> String.valueOf(p.getLocation().getZ());
            case "yaw" -> String.valueOf(p.getLocation().getYaw());
            case "pitch" -> String.valueOf(p.getLocation().getPitch());
            case "biome" -> PlayerUtil.getBiome(p);
            case "biome_capitalized" -> PlayerUtil.getCapitalizedBiome(p);
            case "light_level" -> String.valueOf(p.getLocation().getBlock().getLightLevel());
            case "ip", "ip_address" -> p.getAddress() == null ? ""
                    : p.getAddress().getAddress().getHostAddress();
            case "allow_flight" -> bool(p.getAllowFlight());
            case "can_pickup_items" -> bool(p.getCanPickupItems());
            case "compass_x" -> String.valueOf(p.getCompassTarget().getBlockX());
            case "compass_y" -> String.valueOf(p.getCompassTarget().getBlockY());
            case "compass_z" -> String.valueOf(p.getCompassTarget().getBlockZ());
            case "compass_world" -> p.getCompassTarget().getWorld() != null
                    ? p.getCompassTarget().getWorld().getName() : "";
            case "block_underneath" -> p.getLocation().clone().subtract(0, 1, 0).getBlock().getType().name();
            case "custom_name" -> p.getCustomName() != null ? p.getCustomName() : p.getName();
            case "exp" -> String.valueOf(p.getExp());
            case "current_exp" -> String.valueOf(PlayerUtil.getTotalExperience(p));
            case "total_exp" -> String.valueOf(p.getTotalExperience());
            case "exp_to_level" -> String.valueOf(p.getExpToLevel());
            case "level" -> String.valueOf(p.getLevel());
            case "fly_speed" -> String.valueOf(p.getFlySpeed());
            case "food_level", "food" -> String.valueOf(p.getFoodLevel());
            case "health" -> String.valueOf(p.getHealth());
            case "health_rounded" -> String.valueOf(Math.round(p.getHealth()));
            case "health_scale" -> String.valueOf(p.getHealthScale());
            case "has_health_boost" -> bool(p.hasPotionEffect(PotionEffectType.HEALTH_BOOST));
            case "health_boost" -> p.getHealthScale() > 20
                    ? Double.toString(p.getHealthScale() - 20) : "0";
            case "item_in_hand" -> PlayerUtil.itemInHand(p).getType().name();
            case "item_in_hand_name" -> PlayerUtil.itemName(PlayerUtil.itemInHand(p));
            case "item_in_hand_data", "item_in_hand_durability" ->
                    String.valueOf(PlayerUtil.durability(PlayerUtil.itemInHand(p)));
            case "item_in_offhand" -> p.getInventory().getItemInOffHand().getType().name();
            case "item_in_offhand_name" -> PlayerUtil.itemName(p.getInventory().getItemInOffHand());
            case "item_in_offhand_data", "item_in_offhand_durability" ->
                    String.valueOf(PlayerUtil.durability(p.getInventory().getItemInOffHand()));
            case "last_damage" -> String.valueOf(p.getLastDamage());
            case "max_health" -> String.valueOf(p.getMaxHealth());
            case "max_health_rounded" -> String.valueOf(Math.round(p.getMaxHealth()));
            case "max_air" -> String.valueOf(p.getMaximumAir());
            case "max_no_damage_ticks" -> String.valueOf(p.getMaximumNoDamageTicks());
            case "no_damage_ticks" -> String.valueOf(p.getNoDamageTicks());
            case "armor_helmet_name" -> armorName(p.getInventory().getHelmet());
            case "armor_helmet_data", "armor_helmet_durability" ->
                    String.valueOf(PlayerUtil.durability(p.getInventory().getHelmet()));
            case "armor_chestplate_name" -> armorName(p.getInventory().getChestplate());
            case "armor_chestplate_data", "armor_chestplate_durability" ->
                    String.valueOf(PlayerUtil.durability(p.getInventory().getChestplate()));
            case "armor_leggings_name" -> armorName(p.getInventory().getLeggings());
            case "armor_leggings_data", "armor_leggings_durability" ->
                    String.valueOf(PlayerUtil.durability(p.getInventory().getLeggings()));
            case "armor_boots_name" -> armorName(p.getInventory().getBoots());
            case "armor_boots_data", "armor_boots_durability" ->
                    String.valueOf(PlayerUtil.durability(p.getInventory().getBoots()));
            case "ping" -> retrievePing(p, false);
            case "colored_ping" -> retrievePing(p, true);
            case "time" -> String.valueOf(p.getPlayerTime());
            case "time_offset" -> String.valueOf(p.getPlayerTimeOffset());
            case "remaining_air" -> String.valueOf(p.getRemainingAir());
            case "saturation" -> String.valueOf(p.getSaturation());
            case "sleep_ticks" -> String.valueOf(p.getSleepTicks());
            case "thunder_duration" -> String.valueOf(p.getWorld().getThunderDuration());
            case "ticks_lived" -> String.valueOf(p.getTicksLived());
            case "seconds_lived" -> String.valueOf(p.getTicksLived() / 20);
            case "minutes_lived" -> String.valueOf((p.getTicksLived() / 20) / 60);
            case "walk_speed" -> String.valueOf(p.getWalkSpeed());
            case "weather_duration" -> String.valueOf(p.getWorld().getWeatherDuration());
            case "world_time" -> String.valueOf(p.getWorld().getTime());
            case "world_time_12" -> PlayerUtil.format12(p.getWorld().getTime());
            case "world_time_24" -> PlayerUtil.format24(p.getWorld().getTime());
            case "is_flying" -> bool(p.isFlying());
            case "is_sleeping" -> bool(p.isSleeping());
            case "is_conversing" -> bool(p.isConversing());
            case "is_dead" -> bool(p.isDead());
            case "is_sneaking" -> bool(p.isSneaking());
            case "is_sprinting" -> bool(p.isSprinting());
            case "is_leashed" -> bool(p.isLeashed());
            case "is_inside_vehicle" -> bool(p.isInsideVehicle());
            default -> null;
        };
    }

    private String locale(Player p, String identifier) {
        String localeStr = PlayerUtil.getLocale(p);
        String iso = localeStr.replace('_', '-');
        return switch (identifier) {
            case "locale" -> localeStr;
            case "locale_country" -> Locale.forLanguageTag(iso).getCountry();
            case "locale_display_country" -> Locale.forLanguageTag(iso).getDisplayCountry();
            case "locale_display_name" -> Locale.forLanguageTag(iso).getDisplayName();
            case "locale_short" -> {
                int idx = localeStr.indexOf('_');
                yield idx > 0 ? localeStr.substring(0, idx) : localeStr;
            }
            default -> localeStr;
        };
    }

    private String directionLabel(Cardinal dir) {
        return switch (dir) {
            case NORTH -> north;
            case NORTH_EAST -> northEast;
            case EAST -> east;
            case SOUTH_EAST -> southEast;
            case SOUTH -> south;
            case SOUTH_WEST -> southWest;
            case WEST -> west;
            case NORTH_WEST -> northWest;
        };
    }

    private static String worldType(World.Environment environment) {
        return switch (environment) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            case NORMAL -> "Overworld";
            default -> environment.name();
        };
    }

    private static String armorName(ItemStack stack) {
        return Optional.ofNullable(stack)
                .map(ItemStack::getItemMeta)
                .filter(ItemMeta::hasDisplayName)
                .map(ItemMeta::getDisplayName)
                .orElse("");
    }

    private String retrievePing(Player player, boolean colored) {
        int ping = PlayerUtil.getPing(player);
        if (!colored) {
            return String.valueOf(ping);
        }
        String color = ping > highValue ? high : ping > mediumValue ? medium : low;
        return ChatColor.translateAlternateColorCodes('&', color) + ping;
    }

    private static String bool(boolean value) {
        return value ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse();
    }
}
