package com.yapcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server-wide Brigadier dispatcher for Paper-style command registration.
 */
public final class BrigadierGateway {

    private static final Logger LOG = Logger.getLogger("YaPcore.Brigadier");
    private static final BrigadierGateway INSTANCE = new BrigadierGateway();

    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final ConcurrentHashMap<String, LiteralCommandNode<CommandSourceStack>> literals =
            new ConcurrentHashMap<>();

    private BrigadierGateway() {
    }

    public static BrigadierGateway get() {
        return INSTANCE;
    }

    public CommandDispatcher<CommandSourceStack> dispatcher() {
        return dispatcher;
    }

    public void register(LiteralCommandNode<CommandSourceStack> node) {
        dispatcher.getRoot().addChild(node);
        literals.put(node.getLiteral().toLowerCase(), node);
        LOG.info("Brigadier registered /" + node.getLiteral());
    }

    public int execute(CommandSender sender, String input) throws CommandSyntaxException {
        CommandSourceStack stack = new YaPCommandSourceStack(sender);
        String line = input.startsWith("/") ? input.substring(1) : input;
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(line, stack);
        return dispatcher.execute(parsed);
    }

    public CompletableFuture<Suggestions> suggest(CommandSender sender, String input) {
        CommandSourceStack stack = new YaPCommandSourceStack(sender);
        String line = input.startsWith("/") ? input.substring(1) : input;
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(line, stack);
        return dispatcher.getCompletionSuggestions(parsed);
    }
}
