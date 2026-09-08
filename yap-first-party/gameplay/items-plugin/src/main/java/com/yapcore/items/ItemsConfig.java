package com.yapcore.items;

import org.bukkit.configuration.file.FileConfiguration;

/** Tunables from config.yml. */
public final class ItemsConfig {

    private final int cmdMin;
    private final int cmdMax;
    private final boolean registerCombatService;
    private final int furnitureMaxPerChunk;
    private final boolean placeRequiresSneak;
    private final String msgUnknown;
    private final String msgGiven;
    private final String msgTaken;
    private final String msgCooldown;
    private final String msgNoPerm;
    private final String msgPlaced;
    private final String msgBroke;
    private final String msgCreated;
    private final String msgDeleted;
    private final String msgReloaded;

    public ItemsConfig(FileConfiguration cfg) {
        this.cmdMin = cfg.getInt("cmd-min", 12000);
        this.cmdMax = cfg.getInt("cmd-max", 12999);
        this.registerCombatService = cfg.getBoolean("register-combat-service", true);
        this.furnitureMaxPerChunk = cfg.getInt("furniture.max-per-chunk", 64);
        this.placeRequiresSneak = cfg.getBoolean("furniture.place-requires-sneak", true);
        this.msgUnknown = cfg.getString("messages.unknown-item", "&cUnknown item: &f{id}");
        this.msgGiven = cfg.getString("messages.given", "&aGave &f{amount}x {id}&a to &f{player}");
        this.msgTaken = cfg.getString("messages.taken", "&aRemoved &f{amount}x {id}&a from &f{player}");
        this.msgCooldown = cfg.getString("messages.cooldown", "&cAbility on cooldown (&f{seconds}s&c)");
        this.msgNoPerm = cfg.getString("messages.no-permission", "&cYou cannot use this item.");
        this.msgPlaced = cfg.getString("messages.placed-furniture", "&aPlaced &f{id}");
        this.msgBroke = cfg.getString("messages.broke-furniture", "&aRemoved furniture &f{id}");
        this.msgCreated = cfg.getString("messages.created", "&aCreated item &f{id}");
        this.msgDeleted = cfg.getString("messages.deleted", "&aDeleted item &f{id}");
        this.msgReloaded = cfg.getString("messages.reloaded", "&aYaPItems reloaded (&f{count}&a items).");
    }

    public int cmdMin() {
        return cmdMin;
    }

    public int cmdMax() {
        return cmdMax;
    }

    public boolean registerCombatService() {
        return registerCombatService;
    }

    public int furnitureMaxPerChunk() {
        return furnitureMaxPerChunk;
    }

    public boolean placeRequiresSneak() {
        return placeRequiresSneak;
    }

    public String msgUnknown() {
        return msgUnknown;
    }

    public String msgGiven() {
        return msgGiven;
    }

    public String msgTaken() {
        return msgTaken;
    }

    public String msgCooldown() {
        return msgCooldown;
    }

    public String msgNoPerm() {
        return msgNoPerm;
    }

    public String msgPlaced() {
        return msgPlaced;
    }

    public String msgBroke() {
        return msgBroke;
    }

    public String msgCreated() {
        return msgCreated;
    }

    public String msgDeleted() {
        return msgDeleted;
    }

    public String msgReloaded() {
        return msgReloaded;
    }

    public boolean inCmdRange(int cmd) {
        return cmd >= cmdMin && cmd <= cmdMax;
    }
}
