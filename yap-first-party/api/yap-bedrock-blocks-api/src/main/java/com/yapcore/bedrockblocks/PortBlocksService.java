package com.yapcore.bedrockblocks;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** First-party Folia placement API for Bedrock catalog port blocks. */
public interface PortBlocksService {

    /**
     * Place a port block at {@code block} (replacing air / existing port).
     * For frames, {@code face} is the attach face; ignored for other kinds.
     */
    boolean place(Block block, String portIdOrShort, BlockFace face);

    /** Break a port block at {@code block} if one is present; optionally drop the item. */
    boolean breakPort(Block block, boolean dropItem);

    /** Build a give-stack tagged with {@code yapbedrock:port_id}. */
    ItemStack giveItem(String portIdOrShort, int amount);

    /** Give the stack to {@code player}'s inventory (overflow drops at feet). */
    boolean giveItem(Player player, String portIdOrShort, int amount);

    Optional<PortBlockDefinition> resolve(String portIdOrShort);

    /** Resolve a placed port at a world block (PDC / native heuristics). */
    Optional<PortBlockDefinition> resolveAt(Block block);

    List<PortBlockDefinition> list();
}
