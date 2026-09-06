package com.yapcore.plugincompat.paper;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure remap / target constants documented by YaPPluginCompat.
 * Mirrors the Tier A CraftBukkit versioned → unversioned rewrite story.
 */
final class PluginCompatTargets {

    static final String SOURCE_RANGE = "1.20–1.21";
    static final String TARGET_PAPER = "26.2";
    static final String BACKUP_DIR = ".yap-plugin-compat-backup";
    static final String UNVERSIONED_CRAFT = "org/bukkit/craftbukkit/";

    private static final Pattern VERSIONED_CRAFT =
            Pattern.compile("^org/bukkit/craftbukkit/v1_2[01]_R[0-9]+/");

    private PluginCompatTargets() {
    }

    static List<String> versionedCraftPrefixes() {
        return List.of(
                "org/bukkit/craftbukkit/v1_20_R1/",
                "org/bukkit/craftbukkit/v1_20_R2/",
                "org/bukkit/craftbukkit/v1_20_R3/",
                "org/bukkit/craftbukkit/v1_20_R4/",
                "org/bukkit/craftbukkit/v1_21_R1/",
                "org/bukkit/craftbukkit/v1_21_R2/",
                "org/bukkit/craftbukkit/v1_21_R3/",
                "org/bukkit/craftbukkit/v1_21_R4/",
                "org/bukkit/craftbukkit/v1_21_R5/",
                "org/bukkit/craftbukkit/v1_21_R6/"
        );
    }

    static String rewriteInternalName(String name) {
        if (name == null) {
            return null;
        }
        for (String prefix : versionedCraftPrefixes()) {
            if (name.startsWith(prefix)) {
                return UNVERSIONED_CRAFT + name.substring(prefix.length());
            }
        }
        if (VERSIONED_CRAFT.matcher(name).find()) {
            return name.replaceFirst("org/bukkit/craftbukkit/v1_2[01]_R[0-9]+/", UNVERSIONED_CRAFT);
        }
        return name;
    }
}
