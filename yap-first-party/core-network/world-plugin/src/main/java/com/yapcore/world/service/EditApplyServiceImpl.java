package com.yapcore.world.service;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.EditApplyService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.SelectionEditService;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EditApplyServiceImpl implements EditApplyService {

    private final SelectionEditService edit;
    private final ClipboardService clipboard;
    private final SelectionServiceImpl selection;

    public EditApplyServiceImpl(SelectionEditService edit, ClipboardService clipboard,
                                SelectionServiceImpl selection) {
        this.edit = edit;
        this.clipboard = clipboard;
        this.selection = selection;
    }

    @Override
    public CompletableFuture<Integer> fillPattern(Player player, CuboidSelection sel, String pattern) {
        return edit.fillPattern(player, sel, pattern);
    }

    @Override
    public CompletableFuture<Integer> replaceMask(Player player, CuboidSelection sel, String fromMask, String toPattern) {
        return edit.replaceMask(player, sel, fromMask, toPattern);
    }

    @Override
    public Optional<CuboidSelection> selection(UUID playerUuid) {
        return selection.selection(playerUuid);
    }

    public ClipboardService clipboard() {
        return clipboard;
    }
}
