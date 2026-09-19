package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.db.ShopRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Console JSON for dashboard chest-shop control. */
final class ShopJson {

    static final String PREFIX = "YAPCHESTSHOP_JSON:";

    private ShopJson() {
    }

    static String list(List<ShopRepository.Shop> shops) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shops.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(one(shops.get(i), -1));
        }
        return sb.append(']').toString();
    }

    static String one(ShopRepository.Shop s, int stock) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(s.id()).append(',');
        sb.append("\"owner\":").append(q(s.owner().toString())).append(',');
        sb.append("\"ownerName\":").append(q(ownerName(s.owner()))).append(',');
        sb.append("\"serverId\":").append(q(s.serverId())).append(',');
        sb.append("\"world\":").append(q(s.world())).append(',');
        sb.append("\"x\":").append(s.x()).append(',');
        sb.append("\"y\":").append(s.y()).append(',');
        sb.append("\"z\":").append(s.z()).append(',');
        sb.append("\"material\":").append(q(s.material().name())).append(',');
        sb.append("\"amount\":").append(s.amount()).append(',');
        sb.append("\"price\":").append(String.format(Locale.ROOT, "%.2f", s.price()));
        if (stock >= 0) {
            sb.append(",\"stock\":").append(stock);
        }
        return sb.append('}').toString();
    }

    static String q(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    static String ownerName(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        if (ShopAdminOps.CONSOLE_OWNER.equals(uuid)) {
            return "Console";
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        String name = p.getName();
        return name == null ? uuid.toString() : name;
    }
}
