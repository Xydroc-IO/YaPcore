package com.yapcore.finetune;

import com.yapcore.api.module.YaPModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Shared helpers for thin operator packaging modules.
 * Engines stay in plugins/; modules declare {@code provides} and point at config.
 */
abstract class FineTuneModule extends YaPModule {

    protected abstract String guideTitle();

    protected abstract List<String> guideLines();

    /** Paper plugin name to verify, or null if chassis-only. */
    protected String requiredPaperPlugin() {
        return null;
    }

    @Override
    public final void onEnable() {
        getDataFolder().mkdirs();
        writeGuide();
        String paper = requiredPaperPlugin();
        if (paper != null) {
            checkPaperPlugin(paper);
        }
        onFineTuneEnable();
    }

    protected void onFineTuneEnable() {
        getLogger().info(guideTitle() + " — see " + getDataFolder().getName() + "/FINE_TUNE.txt");
    }

    private void checkPaperPlugin(String name) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                getLogger().info(guideTitle() + " OK — Paper plugin " + name + " is online");
                return;
            }
            // Folia game-authority: Paper plugins run in the Folia JVM, not chassis Bukkit.
            String home = System.getProperty("yapcore.home", ".");
            java.io.File pluginsDir = new java.io.File(home, "plugins");
            // YaPDB → yap-db.jar; YaPChat → yap-chat.jar; YaPPlayerData → yap-playerdata.jar
            String jarHint = switch (name) {
                case "YaPDB" -> "yap-db.jar";
                case "YaPPlayerData" -> "yap-playerdata.jar";
                case "YaPPerms" -> "yap-perms.jar";
                case "YaPChat" -> "yap-chat.jar";
                case "YaPModeration" -> "yap-moderation.jar";
                case "YaPEssentials" -> "yap-essentials.jar";
                case "YaPProtect" -> "yap-protect.jar";
                case "YaPWorld" -> "yap-world.jar";
                default -> null;
            };
            if (jarHint != null && new java.io.File(pluginsDir, jarHint).isFile()) {
                getLogger().info(guideTitle() + " OK — " + jarHint
                        + " present (Folia hosts the Paper plugin)");
                return;
            }
            getLogger().warning(
                    guideTitle() + ": Paper plugin " + name
                            + " missing or disabled. Install the matching jar into plugins/.");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            getLogger().warning(
                    guideTitle() + ": Bukkit not available — drop the Paper plugin into plugins/ "
                            + "under game-authority=paper.");
        }
    }

    private void writeGuide() {
        try {
            var path = getDataFolder().toPath().resolve("FINE_TUNE.txt");
            var body = new StringBuilder();
            body.append(guideTitle()).append('\n');
            body.append("=".repeat(Math.min(72, guideTitle().length()))).append('\n');
            body.append("Module: ").append(getName()).append('\n');
            body.append("Provides: ").append(String.join(", ", getDescription().provides())).append('\n');
            if (!getDescription().requires().isEmpty()) {
                body.append("Requires: ").append(String.join(", ", getDescription().requires())).append('\n');
            }
            body.append('\n');
            for (String line : guideLines()) {
                body.append(line).append('\n');
            }
            Files.writeString(path, body.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Could not write FINE_TUNE.txt: " + e.getMessage());
        }
    }
}
