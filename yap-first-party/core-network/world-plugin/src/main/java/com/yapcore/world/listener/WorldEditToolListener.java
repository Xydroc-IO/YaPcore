package com.yapcore.world.listener;

import com.yapcore.world.WorldConfig;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.tool.WorldEditSession;
import com.yapcore.world.tool.WorldEditTool;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** Golden axe world-edit tool: selection, brush, and in-game GUI opener. */
public final class WorldEditToolListener implements Listener {

    private final WorldConfig config;
    private final SelectionServiceImpl selection;
    private final BrushService brushService;
    private final WorldEditTool tool;
    private final Consumer<Player> openEditor;

    public WorldEditToolListener(WorldConfig config, SelectionServiceImpl selection,
                                 BrushService brushService, WorldEditTool tool,
                                 Consumer<Player> openEditor) {
        this.config = config;
        this.selection = selection;
        this.brushService = brushService;
        this.tool = tool;
        this.openEditor = openEditor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!tool.isTool(hand)) {
            return;
        }
        if (!player.hasPermission("yapworld.selection") && !player.hasPermission("yapworld.brush")) {
            return;
        }
        if (!config.selectionEnabled()) {
            player.sendMessage("§cWorld edit is disabled on this server.");
            return;
        }

        if (player.isSneaking()
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            openEditor.accept(player);
            return;
        }

        WorldEditSession session = WorldEditSession.of(player.getUniqueId());
        if (session.mode() == WorldEditSession.Mode.BRUSH && player.hasPermission("yapworld.brush")) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
                return;
            }
            event.setCancelled(true);
            brushService.setBrush(player.getUniqueId(), session.brushRadius(), session.material());
            var target = event.getClickedBlock() != null
                    ? event.getClickedBlock().getLocation()
                    : player.getLocation();
            brushService.apply(player, target).thenAccept(count ->
                    player.sendMessage("§aBrush placed §f" + count + " §ablocks."));
            return;
        }

        if (!player.hasPermission("yapworld.selection")) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        var block = event.getClickedBlock();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(player.getUniqueId(), block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            player.sendMessage("§aPos1 → §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.setPos2(player.getUniqueId(), block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            player.sendMessage("§aPos2 → §f" + block.getX() + ", " + block.getY() + ", " + block.getZ());
            event.setCancelled(true);
        }
    }
}
