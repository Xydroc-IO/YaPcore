package com.yapcore.fleet.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Product jar sets seeded onto every fleet instance by default.
 * <p>
 * CORE+NETWORK always ships. Gameplay defaults ({@code yap-items}, {@code yap-qol}) also seed
 * when present in the root catalog — other gameplay jars stay opt-in via catalog install.
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

    /** Always-on gameplay suite (VIP tools + custom items). */
    private static final List<String> GAMEPLAY_DEFAULTS = List.of(
            "yap-items.jar",
            "yap-qol.jar");

    private static final Set<String> CORE_NETWORK_SET =
            Collections.unmodifiableSet(new LinkedHashSet<>(CORE_NETWORK));

    private static final Set<String> SEED_SET;

    static {
        LinkedHashSet<String> all = new LinkedHashSet<>(CORE_NETWORK);
        all.addAll(GAMEPLAY_DEFAULTS);
        SEED_SET = Collections.unmodifiableSet(all);
    }

    private FleetDefaultPlugins() {
    }

    /** Ordered CORE+NETWORK jar file names. */
    public static List<String> coreNetworkJars() {
        return CORE_NETWORK;
    }

    /** Ordered gameplay jars seeded by default when present in root {@code plugins/}. */
    public static List<String> gameplayDefaultJars() {
        return GAMEPLAY_DEFAULTS;
    }

    /** Full seed list (CORE+NETWORK + gameplay defaults). */
    public static List<String> seedJars() {
        List<String> out = new ArrayList<>(CORE_NETWORK.size() + GAMEPLAY_DEFAULTS.size());
        out.addAll(CORE_NETWORK);
        out.addAll(GAMEPLAY_DEFAULTS);
        return List.copyOf(out);
    }

    public static boolean isCoreNetwork(String fileName) {
        return matchesAny(fileName, CORE_NETWORK, CORE_NETWORK_SET);
    }

    public static boolean isSeedDefault(String fileName) {
        return matchesAny(fileName, seedJars(), SEED_SET);
    }

    /** True if {@code fileName} should be pre-checked in GUI "Core suite". */
    public static boolean isCoreDefault(String fileName) {
        return isSeedDefault(fileName);
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
        return seedJars().stream()
                .filter(j -> !have.contains(j.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static boolean matchesAny(String fileName, List<String> jars, Set<String> exact) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String n = fileName.trim();
        if (exact.contains(n)) {
            return true;
        }
        String lower = n.toLowerCase(Locale.ROOT);
        for (String jar : jars) {
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
}
