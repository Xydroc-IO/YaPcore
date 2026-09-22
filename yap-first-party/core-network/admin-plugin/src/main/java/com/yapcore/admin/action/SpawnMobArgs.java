package com.yapcore.admin.action;

import java.util.Locale;

/**
 * Parsed {@code /yapadmin spawnmob} args after the entity type:
 * {@code [amount] [level] [player]} with optional {@code level|lvl|l <n>}.
 */
public final class SpawnMobArgs {

    public final int amount;
    public final Integer level; // null = leave unleveled / natural
    public final String playerName; // null = admin self
    public final String error;

    SpawnMobArgs(int amount, Integer level, String playerName, String error) {
        this.amount = amount;
        this.level = level;
        this.playerName = playerName;
        this.error = error;
    }

    public static SpawnMobArgs parse(String[] argsFromType) {
        int amount = 1;
        Integer level = null;
        String playerName = null;
        boolean amountSet = false;
        boolean levelSet = false;

        for (int i = 0; i < argsFromType.length; i++) {
            String raw = argsFromType[i];
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.equals("level") || lower.equals("lvl") || lower.equals("l")) {
                if (i + 1 >= argsFromType.length) {
                    return err("Usage: … level <n>");
                }
                try {
                    level = Integer.parseInt(argsFromType[++i]);
                    levelSet = true;
                } catch (NumberFormatException e) {
                    return err("Bad level: " + argsFromType[i]);
                }
                continue;
            }
            try {
                int n = Integer.parseInt(raw);
                if (!amountSet) {
                    amount = n;
                    amountSet = true;
                } else if (!levelSet) {
                    level = n;
                    levelSet = true;
                } else {
                    return err("Too many numbers. Use: <type> [amount] [level] [player]");
                }
                continue;
            } catch (NumberFormatException ignored) {
                // player name
            }
            if (playerName != null) {
                return err("Only one player target allowed.");
            }
            playerName = raw;
        }
        return new SpawnMobArgs(Math.max(1, Math.min(64, amount)), level, playerName, null);
    }

    private static SpawnMobArgs err(String message) {
        return new SpawnMobArgs(1, null, null, message);
    }
}
