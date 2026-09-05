package com.yapcore.discord.slash;

import com.yapcore.discord.api.SlashCommandProvider;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Pure merge of built-in slash {@link CommandData} with extension providers.
 * Built-ins win on name collision; first provider wins among extensions.
 */
public final class SlashCommandMerge {

    private SlashCommandMerge() {
    }

    /**
     * @param onCollision optional callback (commandName, providerId) when a provider command is skipped
     */
    public static List<CommandData> merge(
            List<? extends CommandData> builtins,
            Collection<? extends SlashCommandProvider> providers,
            BiConsumer<String, String> onCollision) {
        Map<String, CommandData> byName = new LinkedHashMap<>();
        if (builtins != null) {
            for (CommandData cmd : builtins) {
                if (cmd == null || cmd.getName() == null || cmd.getName().isBlank()) {
                    continue;
                }
                byName.put(cmd.getName().toLowerCase(Locale.ROOT), cmd);
            }
        }
        if (providers == null) {
            return new ArrayList<>(byName.values());
        }
        for (SlashCommandProvider provider : providers) {
            if (provider == null || provider.commands() == null) {
                continue;
            }
            for (CommandData cmd : provider.commands()) {
                if (cmd == null || cmd.getName() == null || cmd.getName().isBlank()) {
                    continue;
                }
                String key = cmd.getName().toLowerCase(Locale.ROOT);
                if (byName.containsKey(key)) {
                    if (onCollision != null) {
                        onCollision.accept(cmd.getName(), provider.id());
                    }
                    continue;
                }
                byName.put(key, cmd);
            }
        }
        return new ArrayList<>(byName.values());
    }

    public static List<CommandData> merge(
            List<? extends CommandData> builtins,
            Collection<? extends SlashCommandProvider> providers) {
        return merge(builtins, providers, null);
    }
}
