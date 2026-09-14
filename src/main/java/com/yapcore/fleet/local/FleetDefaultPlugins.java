package com.yapcore.fleet.local;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Product CORE+NETWORK jar set — seeded onto every fleet instance by default.
 * Gameplay and third-party jars are opt-in via catalog install.
 */
public final class FleetDefaultPlugins {

    private static final List<String> CORE_NETWORK = List.of(
            "yap-folia-bridge.jar",
            "yap-placeholderapi.jar",
            "yap-plugin-compat.jar",
            "yap-pregen.jar",
            "yap-db.jar",
            "yap-perms.jar",
            "yap-playerdata.jar",
            "yap-moderation.jar",
            "yap-essentials.jar",
            "yap-admin.jar",
            "yap-protect.jar",
            "yap-world.jar",
            "WorldEdit.jar",
            "yap-regions.jar",
            "yap-npcs.jar",
            "yap-guard.jar",
            "yap-lagguard.jar",
            "yap-map.jar",
            "yap-factions.jar",
            "yap-conquest.jar",
            "yap-packs.jar",
            "yap-commands.jar",
            "yap-chat.jar",
            "yap-tab.jar",
            "yap-discord.jar",
            "yap-floodgate.jar",
            "yap-bedrock-ui.jar",
            "yap-tailor.jar",
            "yap-bedrock-blocks.jar");

    private static final Set<String> CORE_NETWORK_SET =
            Collections.unmodifiableSet(new LinkedHashSet<>(CORE_NETWORK));

    private FleetDefaultPlugins() {
    }

    /** Ordered CORE+NETWORK jar file names (product default suite). */
    public static List<String> coreNetworkJars() {
        return CORE_NETWORK;
    }

    public static boolean isCoreNetwork(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String n = fileName.trim();
        if (CORE_NETWORK_SET.contains(n)) {
            return true;
        }
        String lower = n.toLowerCase(Locale.ROOT);
        for (String jar : CORE_NETWORK) {
            if (lower.equals(jar.toLowerCase(Locale.ROOT))) {
                return true;
            }
            String base = jar.substring(0, jar.length() - 4);
            if (lower.startsWith(base.toLowerCase(Locale.ROOT)) && lower.endsWith(".jar")) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code fileName} should be pre-checked in GUI "Core suite". */
    public static boolean isCoreDefault(String fileName) {
        return isCoreNetwork(fileName);
    }

    public static List<String> missingFrom(Iterable<String> presentFileNames) {
        Set<String> have = new LinkedHashSet<>();
        if (presentFileNames != null) {
            for (String n : presentFileNames) {
                if (n != null) {
                    have.add(n.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return CORE_NETWORK.stream()
                .filter(j -> !have.contains(j.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
