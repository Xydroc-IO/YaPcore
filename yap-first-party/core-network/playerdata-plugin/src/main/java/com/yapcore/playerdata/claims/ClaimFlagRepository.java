package com.yapcore.playerdata.claims;

import com.yapcore.playerdata.db.Database;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimFlagRepository {

    private final Database database;

    public ClaimFlagRepository(Database database) {
        this.database = database;
    }

    public Map<RegionFlag, FlagValue> load(long claimId) throws SQLException {
        Map<RegionFlag, FlagValue> out = new EnumMap<>(RegionFlag.class);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT flag_name, flag_value FROM yap_claim_flags WHERE claim_id = ?")) {
            ps.setLong(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RegionFlag flag = RegionFlag.parse(rs.getString("flag_name")).orElse(null);
                    if (flag != null) {
                        out.put(flag, FlagValue.parse(rs.getString("flag_value")));
                    }
                }
            }
        }
        return out;
    }

    public void set(long claimId, RegionFlag flag, FlagValue value) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_claim_flags (claim_id, flag_name, flag_value)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
                     """)) {
            ps.setLong(1, claimId);
            ps.setString(2, flag.name());
            ps.setString(3, value.name());
            ps.executeUpdate();
        }
    }

    public void clearCache() {
        // no-op — ClaimFlagService owns cache
    }
}
