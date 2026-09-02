package com.yapcore.mechanics.service;

import com.yapcore.mechanics.MechanicsConfig;
import com.yapcore.mechanics.MechanicsService;
import com.yapcore.mechanics.StaminaState;
import com.yapcore.mechanics.farming.FarmingLoader;
import com.yapcore.mechanics.node.ResourceNodeLoader;
import com.yapcore.mechanics.physics.PhysicsLoader;
import com.yapcore.mechanics.stamina.StaminaTracker;
import com.yapcore.mechanics.tool.ToolRuleLoader;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class MechanicsServiceImpl implements MechanicsService {

    private final MechanicsConfig config;
    private final ToolRuleLoader tools;
    private final StaminaTracker stamina;
    private final ResourceNodeLoader nodes;
    private final PhysicsLoader physics;

    public MechanicsServiceImpl(
            MechanicsConfig config,
            ToolRuleLoader tools,
            StaminaTracker stamina,
            ResourceNodeLoader nodes,
            PhysicsLoader physics) {
        this.config = config;
        this.tools = tools;
        this.stamina = stamina;
        this.nodes = nodes;
        this.physics = physics;
    }

    public MechanicsConfig config() {
        return config;
    }

    public ToolRuleLoader tools() {
        return tools;
    }

    public StaminaTracker staminaTracker() {
        return stamina;
    }

    public ResourceNodeLoader nodes() {
        return nodes;
    }

    public PhysicsLoader physics() {
        return physics;
    }

    @Override
    public boolean canBreak(Player player, Material block, ItemStack tool) {
        return breakDeniedReason(player, block, tool).isEmpty();
    }

    @Override
    public Optional<String> breakDeniedReason(Player player, Material block, ItemStack tool) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        if (StaffBypass.mmo(player)) {
            return Optional.empty();
        }
        if (config.toolsEnabled() && config.toolsEnforce()) {
            ToolRuleLoader.ToolRule rule = tools.ruleFor(block);
            if (rule != null) {
                if (!ToolRuleLoader.matchesTool(tool, rule.tool())) {
                    return Optional.of("You need a " + rule.tool().name().toLowerCase() + " for this block.");
                }
                if (ToolRuleLoader.tierOf(tool) < rule.minTier()) {
                    return Optional.of("Your tool is too weak for this block.");
                }
            }
        }
        if (config.staminaEnabled() && stamina.state(player.getUniqueId()).exhausted()) {
            return Optional.of("You are exhausted — wait for stamina to recover.");
        }
        return Optional.empty();
    }

    @Override
    public StaminaState stamina(Player player) {
        return stamina.state(player.getUniqueId());
    }

    @Override
    public boolean consumeStamina(Player player, double amount) {
        return stamina.consume(player.getUniqueId(), amount);
    }

    @Override
    public void regenStamina(Player player, double amount) {
        stamina.regen(player.getUniqueId(), amount);
    }

    @Override
    public double fishingXpMultiplier(Player player) {
        if (!config.enabled()) {
            return 1.0;
        }
        ResourceNodeLoader.ResourceNode node = nodes.fishingBonusAt(player.getLocation());
        if (node == null) {
            return 1.0;
        }
        return node.fishingBonus();
    }

    @Override
    public double fallDamageMultiplier(Player player) {
        if (!config.physicsEnabled()) {
            return 1.0;
        }
        return physics.fallMultiplier();
    }

    @Override
    public double projectileDamageMultiplier(Player player) {
        if (!config.physicsEnabled()) {
            return 1.0;
        }
        return physics.projectileMultiplier();
    }
}
