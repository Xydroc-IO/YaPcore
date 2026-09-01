package com.yapcore.pregen.shape;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.WorldServices;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * YaPWorld selection → chunk shape (preferred on Folia product path).
 */
public final class YapWorldShape {

    private YapWorldShape() {
    }

    public static ChunkShape fromPlayer(Player player, Logger log) throws Exception {
        var service = WorldServices.selection();
        if (service.isEmpty()) {
            throw new IllegalStateException("YaPWorld is not loaded");
        }
        CuboidSelection sel = service.get().selection(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException(
                        "No YaPWorld selection — use /yapworld tool or /yapworld gui"));
        if (!sel.world().equals(player.getWorld().getName())) {
            throw new IllegalStateException("Selection is in world " + sel.world()
                    + " but you are in " + player.getWorld().getName());
        }
        log.info("Using YaPWorld selection for pregen: " + sel.minX() + "," + sel.minZ()
                + " → " + sel.maxX() + "," + sel.maxZ());
        return new RectShape(sel.minX(), sel.minZ(), sel.maxX(), sel.maxZ());
    }
}
