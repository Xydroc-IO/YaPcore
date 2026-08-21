package com.yapcore.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares what plugin/module authors can rely on today.
 */
public final class ApiCoverage {

    private ApiCoverage() {
    }

    public enum Status { FULL, PARTIAL, STUB, PLANNED }

    public record Entry(String area, Status status, String notes) {
    }

    public static List<Entry> snapshot() {
        return List.of(
                new Entry("Paper API type surface", Status.FULL, "~1.5k+ stubs from paper-api 1.21.4 on classpath"),
                new Entry("JavaPlugin + plugin.yml", Status.FULL, "Paper: paper-dir/plugins; facade: soft-fail loader"),
                new Entry("PluginClassLoader isolation", Status.PARTIAL, "Facade parent-first; real Paper uses Paper’s loader"),
                new Entry("ServicesManager", Status.FULL, "Vault-style register/get"),
                new Entry("YamlConfiguration", Status.FULL, "reload/save + defaults merge"),
                new Entry("PluginCommand from plugin.yml", Status.FULL, "aliases + JavaPlugin executor"),
                new Entry("YaPPlugin + yap.yml", Status.FULL, "Native dual-pool plugins"),
                new Entry("YaPModule + module.yml", Status.FULL, "Fine-tune modules in modules/"),
                new Entry("Scheduler SYNC/async", Status.FULL, "Bridge + Heavy I/O"),
                new Entry("YaPScheduler UI/HEAVY/SYNC", Status.FULL, "ThreadPools ownership"),
                new Entry("Adventure Component/Audience", Status.FULL, "Kyori Adventure"),
                new Entry("Brigadier commands", Status.FULL, "Mojang brigadier + Paper Commands registrar"),
                new Entry("Paper/Bukkit events", Status.FULL, "430+ Event stubs; HandlerList + soft-fail listeners"),
                new Entry("NMS / CraftBukkit", Status.PARTIAL, "CraftPlayer/World/Server + MinecraftServer facades"),
                new Entry("Inventory + InventoryHolder", Status.PARTIAL, "GUI open/click/close/drag"),
                new Entry("ItemMeta / lore / display", Status.PARTIAL, "Common meta fields"),
                new Entry("Player / OfflinePlayer", Status.PARTIAL, "Online + CraftPlayer.getHandle()"),
                new Entry("Permissions + attachments", Status.PARTIAL, "Basic Permissible"),
                new Entry("Sounds", Status.PARTIAL, "Common Sound enum + playSound"),
                new Entry("World / Block", Status.PARTIAL, "Bridged + CraftWorld.getHandle()"),
                new Entry("Plugin messaging", Status.PARTIAL, "Messenger + channels"),
                new Entry("Material catalog", Status.PARTIAL, "Expanded common set"),
                new Entry("Bit-identical Paper method bodies", Status.PARTIAL, "Stubs load; deepen hot paths as plugins demand"),
                new Entry("Full Mojang NMS bytecode", Status.PLANNED, "Facades cover casts; not obfuscated jar"),
                new Entry("Every Event field getter", Status.PARTIAL, "Stubs fire/listen; deepen payloads as needed")
        );
    }

    public static Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Entry e : snapshot()) {
            m.put(e.area(), e.status() + " — " + e.notes());
        }
        return m;
    }
}
