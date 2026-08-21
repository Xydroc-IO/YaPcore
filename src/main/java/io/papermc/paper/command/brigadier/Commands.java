package io.papermc.paper.command.brigadier;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.yapcore.command.BrigadierGateway;

/**
 * Paper Commands registrar — plugins call this during lifecycle to attach Brigadier nodes.
 */
public final class Commands {

    private Commands() {
    }

    public static void register(LiteralCommandNode<CommandSourceStack> node) {
        BrigadierGateway.get().register(node);
    }

    public static com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher() {
        return BrigadierGateway.get().dispatcher();
    }
}
