package com.yapcore.discord.slash;

import com.yapcore.discord.api.SlashCommandProvider;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandMergeTest {

    @Test
    void mergesProviderCommandsAfterBuiltins() {
        List<CommandData> builtins = List.of(
                Commands.slash("status", "built-in"),
                Commands.slash("players", "built-in"));
        SlashCommandProvider provider = provider("demo",
                Commands.slash("ping", "extension ping"));

        List<CommandData> merged = SlashCommandMerge.merge(builtins, List.of(provider));
        assertEquals(3, merged.size());
        assertEquals("status", merged.get(0).getName());
        assertEquals("players", merged.get(1).getName());
        assertEquals("ping", merged.get(2).getName());
    }

    @Test
    void builtinsWinOnNameCollision() {
        AtomicInteger collisions = new AtomicInteger();
        List<CommandData> builtins = List.of(Commands.slash("status", "built-in"));
        SlashCommandProvider provider = provider("collide",
                Commands.slash("status", "extension override"));

        List<CommandData> merged = SlashCommandMerge.merge(
                builtins, List.of(provider), (name, id) -> collisions.incrementAndGet());

        assertEquals(1, merged.size());
        assertEquals("status", merged.get(0).getName());
        assertEquals(1, collisions.get());
        // Built-in instance retained (identity), not the provider's colliding CommandData.
        assertTrue(merged.get(0) == builtins.get(0));
    }

    @Test
    void multipleProvidersMergeWithoutDuplicateNames() {
        SlashCommandProvider a = provider("a", Commands.slash("alpha", "a"));
        SlashCommandProvider b = provider("b",
                Commands.slash("beta", "b"),
                Commands.slash("alpha", "b-dup"));

        List<String> skipped = new ArrayList<>();
        List<CommandData> merged = SlashCommandMerge.merge(
                List.of(), List.of(a, b), (name, id) -> skipped.add(name + ":" + id));

        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(c -> "alpha".equals(c.getName())));
        assertTrue(merged.stream().anyMatch(c -> "beta".equals(c.getName())));
        assertEquals(List.of("alpha:b"), skipped);
    }

    private static SlashCommandProvider provider(String id, CommandData... cmds) {
        List<CommandData> list = List.of(cmds);
        return new SlashCommandProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Collection<? extends CommandData> commands() {
                return list;
            }

            @Override
            public void onSlashCommand(SlashCommandInteractionEvent event) {
                // no-op smoke
            }
        };
    }
}
