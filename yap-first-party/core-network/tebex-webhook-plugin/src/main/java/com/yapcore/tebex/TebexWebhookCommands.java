package com.yapcore.tebex;

import java.util.ArrayList;
import java.util.List;

/** Package command placeholder substitution. */
public final class TebexWebhookCommands {

    private TebexWebhookCommands() {
    }

    public static List<String> substitute(List<String> templates, String username,
                                          String transaction, String packageId) {
        List<String> out = new ArrayList<>();
        if (templates == null) {
            return out;
        }
        String user = username == null ? "" : username;
        String tx = transaction == null ? "" : transaction;
        String pkg = packageId == null ? "" : packageId;
        for (String raw : templates) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String cmd = raw
                    .replace("{username}", user)
                    .replace("{transaction}", tx)
                    .replace("{packageId}", pkg)
                    .trim();
            if (!cmd.isEmpty()) {
                out.add(cmd);
            }
        }
        return out;
    }
}
