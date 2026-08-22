package com.yapcore.mmo.event;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.XpSource;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class SkillLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillId skillId;
    private final int oldLevel;
    private final int newLevel;
    private final double totalXp;
    private final XpSource source;

    public SkillLevelUpEvent(
            @NotNull Player player,
            @NotNull SkillId skillId,
            int oldLevel,
            int newLevel,
            double totalXp,
            @NotNull XpSource source) {
        super(player);
        this.skillId = skillId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.totalXp = totalXp;
        this.source = source;
    }

    public SkillId skillId() {
        return skillId;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    public double totalXp() {
        return totalXp;
    }

    public XpSource source() {
        return source;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
