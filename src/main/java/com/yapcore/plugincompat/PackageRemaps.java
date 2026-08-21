package com.yapcore.plugincompat;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Light package rewrites: versioned CraftBukkit → unversioned Paper packages.
 */
public final class PackageRemaps {

    private static final Pattern VERSIONED_CRAFT =
            Pattern.compile("^org/bukkit/craftbukkit/v1_2[01]_R[0-9]+/");

    private PackageRemaps() {
    }

    /** Internal binary names that should be rewritten (prefixes). */
    public static List<String> versionedCraftPrefixes() {
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

    public static String rewriteInternalName(String name) {
        if (name == null) {
            return null;
        }
        for (String prefix : versionedCraftPrefixes()) {
            if (name.startsWith(prefix)) {
                return "org/bukkit/craftbukkit/" + name.substring(prefix.length());
            }
        }
        // Also catch any v1_20_R* / v1_21_R* we missed
        if (VERSIONED_CRAFT.matcher(name).find()) {
            return name.replaceFirst("org/bukkit/craftbukkit/v1_2[01]_R[0-9]+/",
                    "org/bukkit/craftbukkit/");
        }
        return name;
    }

    public static String rewriteDescriptor(String desc) {
        if (desc == null || desc.indexOf("craftbukkit/v1_") < 0) {
            return desc;
        }
        StringBuilder sb = new StringBuilder(desc.length());
        int i = 0;
        while (i < desc.length()) {
            if (desc.startsWith("Lorg/bukkit/craftbukkit/v1_", i)) {
                int end = desc.indexOf(';', i);
                if (end > i) {
                    String internal = desc.substring(i + 1, end);
                    sb.append('L').append(rewriteInternalName(internal)).append(';');
                    i = end + 1;
                    continue;
                }
            }
            sb.append(desc.charAt(i));
            i++;
        }
        return sb.toString();
    }
}
