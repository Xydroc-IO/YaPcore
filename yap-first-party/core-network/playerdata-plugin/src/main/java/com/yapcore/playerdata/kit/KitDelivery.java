package com.yapcore.playerdata.kit;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.cmd.Perms;
import com.yapcore.playerdata.db.KitRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

/** Shared give/claim path for /kit, grants, first-join, and signs. */
public final class KitDelivery {

    public enum Mode {
        PLAYER, ADMIN, ADMIN_FORCE, FIRST_JOIN, GRANT
    }

    public enum Outcome {
        OK, UNKNOWN, NO_PERM, COOLDOWN, MAX_USES, CANT_AFFORD, NOT_READY
    }

    public record Result(Outcome outcome, long secondsLeft, String detail) {
        public static Result of(Outcome outcome) {
            return new Result(outcome, 0, "");
        }
    }

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final KitRepository kits;
    private final BalanceStore balances;

    public KitDelivery(JavaPlugin plugin, PlayerDataConfig config, KitRepository kits, BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.balances = balances;
    }

    public Result claim(Player player, String kitId, Mode mode) throws Exception {
        KitDef def = config.kits().get(kitId.toLowerCase());
        if (def == null) {
            return Result.of(Outcome.UNKNOWN);
        }
        boolean skipGates = mode == Mode.ADMIN || mode == Mode.ADMIN_FORCE || mode == Mode.GRANT;
        boolean skipCooldown = mode == Mode.ADMIN_FORCE || mode == Mode.GRANT || mode == Mode.FIRST_JOIN;
        boolean skipCost = skipGates || mode == Mode.FIRST_JOIN;
        boolean skipUses = skipGates;

        if (!skipGates && !Perms.hasKit(player, def.id())) {
            return Result.of(Outcome.NO_PERM);
        }
        var last = kits.lastClaim(player.getUniqueId(), def.id());
        int uses = kits.uses(player.getUniqueId(), def.id());
        if (!skipUses && def.maxUses() > 0 && uses >= def.maxUses()) {
            return new Result(Outcome.MAX_USES, 0, String.valueOf(def.maxUses()));
        }
        if (!skipCooldown && last.isPresent() && def.delaySeconds() > 0) {
            Instant next = last.get().plusSeconds(def.delaySeconds());
            if (Instant.now().isBefore(next)) {
                long secs = Duration.between(Instant.now(), next).getSeconds();
                return new Result(Outcome.COOLDOWN, secs, CooldownFormat.formatSeconds(secs));
            }
        }
        if (!skipCost && def.cost() > 0 && config.economyEnabled()) {
            double bal = balances.getBalance(player.getUniqueId());
            if (bal < def.cost()) {
                return new Result(Outcome.CANT_AFFORD, 0, String.format("%.2f", def.cost()));
            }
            balances.setBalance(player.getUniqueId(), bal - def.cost());
        }
        giveContents(player, def);
        runCommands(player, def);
        if (mode != Mode.ADMIN_FORCE) {
            kits.markClaimed(player.getUniqueId(), def.id());
        }
        return Result.of(Outcome.OK);
    }

    public void giveContents(Player player, KitDef def) {
        PlayerInventory inv = player.getInventory();
        equipOrStore(player, inv.getHelmet(), def.helmet(), inv::setHelmet);
        equipOrStore(player, inv.getChestplate(), def.chestplate(), inv::setChestplate);
        equipOrStore(player, inv.getLeggings(), def.leggings(), inv::setLeggings);
        equipOrStore(player, inv.getBoots(), def.boots(), inv::setBoots);
        if (def.offhand() != null) {
            ItemStack current = inv.getItemInOffHand();
            if (current == null || current.getType().isAir()) {
                inv.setItemInOffHand(def.offhand().clone());
            } else {
                addOrDrop(player, def.offhand().clone());
            }
        }
        for (ItemStack stack : def.items()) {
            if (stack != null && !stack.getType().isAir()) {
                addOrDrop(player, stack.clone());
            }
        }
    }

    private static void equipOrStore(Player player, ItemStack worn, ItemStack incoming,
                                     java.util.function.Consumer<ItemStack> setter) {
        if (incoming == null || incoming.getType().isAir()) {
            return;
        }
        if (worn == null || worn.getType().isAir()) {
            setter.accept(incoming.clone());
        } else {
            addOrDrop(player, incoming.clone());
        }
    }

    static void addOrDrop(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    public void runCommands(Player player, KitDef def) {
        if (def.commands().isEmpty()) {
            return;
        }
        for (String raw : def.commands()) {
            String cmd = raw.replace("{player}", player.getName())
                    .replace("{name}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
