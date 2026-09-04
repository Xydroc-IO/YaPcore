package com.yapcore.world.cmd;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldPlugin;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.service.SelectionServiceImpl;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Selection fill/walls/shell/outline/hollow/replace handlers for {@link WorldCommands}. */
final class WorldCommandsSelectionEdit {

    private final WorldPlugin plugin;
    private final SelectionServiceImpl selection;
    private final SelectionEditService selectionEdit;

    WorldCommandsSelectionEdit(WorldPlugin plugin, SelectionServiceImpl selection,
                               SelectionEditService selectionEdit) {
        this.plugin = plugin;
        this.selection = selection;
        this.selectionEdit = selectionEdit;
    }

    boolean fill(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "fill", (player, sel, mat) ->
                selectionEdit.fill(player, sel, mat));
    }

    boolean walls(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "walls", (player, sel, mat) ->
                selectionEdit.walls(player, sel, mat));
    }

    boolean shell(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "shell", (player, sel, mat) ->
                selectionEdit.shell(player, sel, mat));
    }

    boolean outline(CommandSender sender, String[] args) {
        return runSelectionEdit(sender, args, "outline", (player, sel, mat) ->
                selectionEdit.outline(player, sel, mat));
    }

    boolean hollow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first — §f/yapworld tool §cor §f/yapworld gui");
            return true;
        }
        player.sendMessage("§7Hollowing selection…");
        selectionEdit.hollow(player, opt.get()).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aHollowed §f" + count + " §ablocks.")));
        return true;
    }

    boolean replace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§e/yapworld replace <from> <to>");
            return true;
        }
        Material from = Material.matchMaterial(args[1]);
        Material to = Material.matchMaterial(args[2]);
        if (from == null || to == null || !from.isBlock() || !to.isBlock()) {
            player.sendMessage("§cUnknown block material.");
            return true;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first.");
            return true;
        }
        player.sendMessage("§7Replacing §f" + from.name() + " §7→ §f" + to.name() + "§7…");
        selectionEdit.replace(player, opt.get(), from, to).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§aReplaced §f" + count + " §ablocks.")));
        return true;
    }

    @FunctionalInterface
    private interface EditOp {
        java.util.concurrent.CompletableFuture<Integer> run(Player player, CuboidSelection sel, Material mat);
    }

    private boolean runSelectionEdit(CommandSender sender, String[] args, String label, EditOp op) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("yapworld.selection")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        Material mat = Material.STONE;
        if (args.length >= 2) {
            Material parsed = Material.matchMaterial(args[1]);
            if (parsed == null || !parsed.isBlock()) {
                player.sendMessage("§cUnknown block material.");
                return true;
            }
            mat = parsed;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            player.sendMessage("§cSet pos1 and pos2 first — §f/yapworld tool §cor open §f/yapworld gui");
            return true;
        }
        player.sendMessage("§7Running §f" + label + " §7with §f" + mat.name() + "§7…");
        Material finalMat = mat;
        op.run(player, opt.get(), finalMat).thenAccept(count ->
                YapSched.global(plugin, () -> player.sendMessage("§a" + label + " §f" + count + " §ablocks.")));
        return true;
    }
}
