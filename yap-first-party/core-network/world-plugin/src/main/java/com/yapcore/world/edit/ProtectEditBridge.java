package com.yapcore.world.edit;

import com.yapcore.protect.ProtectEditBlock;
import com.yapcore.protect.ProtectService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Soft-depend bridge: attribute YaPWorld apply batches to YaPProtect edit sessions. */
public final class ProtectEditBridge {

    private static final Logger LOG = Logger.getLogger("YaP.World.Protect");

    private ProtectEditBridge() {
    }

    public static UUID logSession(Player player, EditSession session) {
        if (session == null || session.isEmpty()) {
            return null;
        }
        RegisteredServiceProvider<ProtectService> rsp =
                Bukkit.getServicesManager().getRegistration(ProtectService.class);
        if (rsp == null || rsp.getProvider() == null) {
            return null;
        }
        UUID editOpId = UUID.randomUUID();
        UUID actor = player != null ? player.getUniqueId() : null;
        String name = player != null ? player.getName() : "console";
        List<ProtectEditBlock> edits = new ArrayList<>(session.edits().size());
        for (EditSession.BlockEdit e : session.edits()) {
            edits.add(new ProtectEditBlock(
                    e.world(), e.x(), e.y(), e.z(), e.before(), e.after()));
        }
        try {
            rsp.getProvider().logEditBatch(editOpId, actor, name, edits);
            if (player != null) {
                LOG.fine("Protect edit session " + editOpId + " (" + edits.size()
                        + " blocks) for " + name);
            }
            return editOpId;
        } catch (Exception e) {
            LOG.warning("Protect edit-session bridge failed: " + e.getMessage());
            return null;
        }
    }
}
