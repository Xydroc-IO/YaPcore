package com.yapcore.guilds.db;

import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRelationKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GuildRelationQueries {

    private final GuildDatabase database;

    GuildRelationQueries(GuildDatabase database) {
        this.database = database;
    }

    public void setRelation(long guildA, long guildB, GuildRelation relation) throws SQLException {
        GuildRelationKey.Pair pair = GuildRelationKey.of(guildA, guildB);
        if (relation == GuildRelation.NEUTRAL) {
            clearRelation(pair.lowId(), pair.highId());
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().upsert(
                     "yap_guild_relations",
                     List.of("guild_id_a", "guild_id_b"),
                     List.of("guild_id_a", "guild_id_b", "relation"),
                     Map.of("relation", "EXCLUDED.relation")))) {
            ps.setLong(1, pair.lowId());
            ps.setLong(2, pair.highId());
            ps.setString(3, relation.name());
            ps.executeUpdate();
        }
    }

    public void clearRelation(long lowId, long highId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_guild_relations WHERE guild_id_a = ? AND guild_id_b = ?")) {
            ps.setLong(1, lowId);
            ps.setLong(2, highId);
            ps.executeUpdate();
        }
    }

    public Optional<GuildRelation> relation(long guildA, long guildB) throws SQLException {
        if (guildA == guildB) {
            return Optional.of(GuildRelation.NEUTRAL);
        }
        GuildRelationKey.Pair pair = GuildRelationKey.of(guildA, guildB);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT relation FROM yap_guild_relations WHERE guild_id_a = ? AND guild_id_b = ?")) {
            ps.setLong(1, pair.lowId());
            ps.setLong(2, pair.highId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.of(GuildRelation.NEUTRAL);
                }
                return GuildRelation.parse(rs.getString("relation"));
            }
        }
    }

    public Map<String, Integer> dashboardCounts() throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_guilds")) {
                rs.next();
                out.put("guilds", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_guild_members")) {
                rs.next();
                out.put("members", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM yap_guild_relations WHERE relation = 'ALLY'")) {
                rs.next();
                out.put("alliances", rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM yap_guild_invites")) {
                rs.next();
                out.put("invites", rs.getInt(1));
            }
        }
        return out;
    }

}
