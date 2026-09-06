package com.yapcore.protect.cmd;

import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.service.ProtectServiceImpl;
import org.bukkit.command.CommandSender;

/** Lookup / rollback / restore / prune handlers for ProtectCommands. */
final class ProtectCommandOps {

    private final ProtectLookupOps lookupOps;
    private final ProtectRollbackOps rollbackOps;
    private final ProtectExportOps exportOps;

    ProtectCommandOps(ProtectServiceImpl service, ProtectConfig config) {
        this.lookupOps = new ProtectLookupOps(service, config);
        this.rollbackOps = new ProtectRollbackOps(service);
        this.exportOps = new ProtectExportOps(service, config);
    }

    void setConfig(ProtectConfig config) {
        lookupOps.setConfig(config);
        exportOps.setConfig(config);
    }

    boolean lookup(CommandSender sender, String[] args) {
        return lookupOps.lookup(sender, args);
    }

    boolean dashLookup(CommandSender sender, String[] args) {
        return lookupOps.dashLookup(sender, args);
    }

    boolean rollback(CommandSender sender, String[] args) {
        return rollbackOps.rollback(sender, args);
    }

    boolean restore(CommandSender sender, String[] args) {
        return rollbackOps.restore(sender, args);
    }

    boolean prune(CommandSender sender, String[] args) {
        return exportOps.prune(sender, args);
    }

    boolean export(CommandSender sender, String[] args) {
        return exportOps.export(sender, args);
    }
}
