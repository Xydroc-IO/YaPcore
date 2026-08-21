package me.clip.placeholderapi.builtin;

import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Built-in server expansion — placeholder set aligned with upstream Server expansion.
 * Clean-room YaP implementation.
 */
public final class ServerExpansion extends PlaceholderExpansion implements Configurable, Cacheable {

    private static final int MiB = 1_048_576;

    private String serverName = "A Minecraft Server";
    private String tpsHigh = "&a";
    private String tpsMedium = "&e";
    private String tpsLow = "&c";
    private String suffixWeek = "w";
    private String suffixDay = "d";
    private String suffixHour = "h";
    private String suffixMinute = "m";
    private String suffixSecond = "s";
    private ZoneId zone = ZoneId.systemDefault();
    private Locale locale = Locale.getDefault();

    private final Map<String, CachedInt> cache = new HashMap<>();

    @Override
    @NotNull
    public String getIdentifier() {
        return "server";
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
        defaults.put("server_name", "A Minecraft Server");
        defaults.put("time.locale", Locale.getDefault().toLanguageTag());
        defaults.put("time.zone", ZoneId.systemDefault().getId());
        defaults.put("time.suffix.week", "w");
        defaults.put("time.suffix.day", "d");
        defaults.put("time.suffix.hour", "h");
        defaults.put("time.suffix.minute", "m");
        defaults.put("time.suffix.second", "s");
        defaults.put("tps_color.high", "&a");
        defaults.put("tps_color.medium", "&e");
        defaults.put("tps_color.low", "&c");
        return defaults;
    }

    @Override
    @NotNull
    public List<String> getPlaceholders() {
        return List.of(
                "%server_name%", "%server_online%", "%server_max_players%", "%server_version%",
                "%server_tps%", "%server_uptime%", "%server_ram_used%", "%server_unique_joins%",
                "%server_has_whitelist%", "%server_total_entities%");
    }

    @Override
    public boolean canRegister() {
        serverName = getString("server_name", "A Minecraft Server");
        tpsHigh = getString("tps_color.high", "&a");
        tpsMedium = getString("tps_color.medium", "&e");
        tpsLow = getString("tps_color.low", "&c");
        suffixWeek = getString("time.suffix.week", "w");
        suffixDay = getString("time.suffix.day", "d");
        suffixHour = getString("time.suffix.hour", "h");
        suffixMinute = getString("time.suffix.minute", "m");
        suffixSecond = getString("time.suffix.second", "s");
        try {
            locale = Locale.forLanguageTag(getString("time.locale", Locale.getDefault().toLanguageTag()));
        } catch (Exception e) {
            locale = Locale.getDefault();
        }
        try {
            zone = ZoneId.of(getString("time.zone", ZoneId.systemDefault().getId()));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        return true;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params) {
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players", "max" -> String.valueOf(Bukkit.getMaxPlayers());
            case "unique_joins" -> String.valueOf(Bukkit.getOfflinePlayers().length);
            case "name" -> serverName;
            case "variant" -> variant();
            case "version" -> Bukkit.getBukkitVersion();
            case "bukkit_version" -> Bukkit.getBukkitVersion();
            case "build" -> build();
            case "version_build", "version_full" -> Bukkit.getBukkitVersion() + '-' + build();
            case "ram_used" -> String.valueOf((Runtime.getRuntime().totalMemory()
                    - Runtime.getRuntime().freeMemory()) / MiB);
            case "ram_free" -> String.valueOf(Runtime.getRuntime().freeMemory() / MiB);
            case "ram_total" -> String.valueOf(Runtime.getRuntime().totalMemory() / MiB);
            case "ram_max" -> String.valueOf(Runtime.getRuntime().maxMemory() / MiB);
            case "tps" -> formatTps(0, false);
            case "tps_1" -> formatTps(0, false);
            case "tps_5" -> formatTps(1, false);
            case "tps_15" -> formatTps(2, false);
            case "tps_1_colored" -> formatTps(0, true);
            case "tps_5_colored" -> formatTps(1, true);
            case "tps_15_colored" -> formatTps(2, true);
            case "uptime" -> formatUptime(ManagementFactory.getRuntimeMXBean().getUptime());
            case "total_chunks" -> cached("chunks",
                    () -> Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum());
            case "total_living_entities" -> cached("living",
                    () -> Bukkit.getWorlds().stream().mapToInt(w -> w.getLivingEntities().size()).sum());
            case "total_entities" -> cached("entities",
                    () -> Bukkit.getWorlds().stream().mapToInt(w -> w.getEntities().size()).sum());
            case "has_whitelist" -> bool(Bukkit.hasWhitelist());
            case "time" -> PlaceholderAPIPlugin.getDateFormat().format(new Date());
            case "java_version" -> System.getProperty("java.version", "");
            case "os_name" -> System.getProperty("os.name", "");
            default -> prefixed(player, params);
        };
    }

    @Nullable
    private String prefixed(OfflinePlayer player, String params) {
        if (params.startsWith("tps_")) {
            String rest = params.substring(4);
            boolean colored = rest.endsWith("_colored") || rest.contains("colored");
            int idx = 0;
            if (rest.startsWith("1")) {
                idx = 0;
            } else if (rest.startsWith("5")) {
                idx = 1;
            } else if (rest.startsWith("15")) {
                idx = 2;
            }
            return formatTps(idx, colored);
        }
        if (params.startsWith("online_")) {
            World world = Bukkit.getWorld(params.substring(7));
            return world == null ? "-1" : String.valueOf(world.getPlayers().size());
        }
        if (params.startsWith("time_")) {
            String pattern = params.substring(5);
            try {
                return DateTimeFormatter.ofPattern(pattern, locale)
                        .withZone(zone)
                        .format(Instant.now());
            } catch (Exception e) {
                return PlaceholderAPIPlugin.getDateFormat().format(new Date());
            }
        }
        if (params.startsWith("time:")) {
            try {
                return new SimpleDateFormat(params.substring(5).trim()).format(new Date());
            } catch (Exception e) {
                return PlaceholderAPIPlugin.getDateFormat().format(new Date());
            }
        }
        if (params.startsWith("countdown_raw_")) {
            return countdown(params.substring("countdown_raw_".length()), true, true);
        }
        if (params.startsWith("countdown_")) {
            return countdown(params.substring("countdown_".length()), true, false);
        }
        if (params.startsWith("countup_raw_")) {
            return countdown(params.substring("countup_raw_".length()), false, true);
        }
        if (params.startsWith("countup_")) {
            return countdown(params.substring("countup_".length()), false, false);
        }
        return null;
    }

    private String countdown(String spec, boolean countDown, boolean raw) {
        try {
            long targetEpoch;
            if (spec.matches("\\d+")) {
                targetEpoch = Long.parseLong(spec);
                if (targetEpoch < 1_000_000_000_000L) {
                    targetEpoch *= 1000L;
                }
            } else {
                ZonedDateTime zdt = ZonedDateTime.parse(spec);
                targetEpoch = zdt.toInstant().toEpochMilli();
            }
            long now = System.currentTimeMillis();
            long delta = countDown ? (targetEpoch - now) : (now - targetEpoch);
            if (delta < 0) {
                delta = 0;
            }
            if (raw) {
                return String.valueOf(delta / 1000L);
            }
            return formatUptime(delta);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatTps(int index, boolean colored) {
        double value = 20.0;
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > index) {
                value = Math.min(20.0, tps[index]);
            }
        } catch (Throwable ignored) {
            // facade
        }
        String num = String.format(Locale.US, "%.2f", value);
        if (!colored) {
            return num;
        }
        String color = value >= 18 ? tpsHigh : value >= 15 ? tpsMedium : tpsLow;
        return ChatColor.translateAlternateColorCodes('&', color) + num;
    }

    private String formatUptime(long ms) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        long weeks = seconds / 604800L;
        seconds %= 604800L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        StringBuilder sb = new StringBuilder();
        if (weeks > 0) {
            sb.append(weeks).append(suffixWeek).append(' ');
        }
        if (days > 0 || weeks > 0) {
            sb.append(days).append(suffixDay).append(' ');
        }
        if (hours > 0 || days > 0 || weeks > 0) {
            sb.append(hours).append(suffixHour).append(' ');
        }
        sb.append(minutes).append(suffixMinute).append(' ');
        sb.append(seconds).append(suffixSecond);
        return sb.toString().trim();
    }

    private String cached(String key, IntSupplier supplier) {
        CachedInt entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry == null || now - entry.at > 60_000L) {
            entry = new CachedInt(supplier.getAsInt(), now);
            cache.put(key, entry);
        }
        return String.valueOf(entry.value);
    }

    private static String variant() {
        String name = Bukkit.getName();
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        return name;
    }

    private static String build() {
        String ver = Bukkit.getVersion();
        if (ver == null) {
            return "unknown";
        }
        int idx = ver.lastIndexOf("(MC:");
        return idx >= 0 ? ver.substring(0, idx).trim() : ver;
    }

    private static String bool(boolean value) {
        return value ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse();
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt();
    }

    private record CachedInt(int value, long at) {
    }
}
