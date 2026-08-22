package com.yapcore.guilds.db;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRelationKey;
import com.yapcore.guilds.GuildRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuildRepository {

    private final GuildDatabase database;

    public GuildRepository(GuildDatabase database) {
        this.database = database;
    }

    public long create(Guild guild) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_guilds (name, tag, leader_uuid, level, xp, description, motd, join_mode)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, guild.name());
            ps.setString(2, guild.tag());
            ps.setString(3, guild.leaderId().toString());
            ps.setInt(4, guild.level());
            ps.setLong(5, guild.xp());
            ps.setString(6, guild.description());
            ps.setString(7, guild.motd());
            ps.setString(8, guild.joinMode().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("no guild id");
                }
                return keys.getLong(1);
            }
        }
    }

    public void deleteGuild(long guildId) throws SQLException {
        try (Connection c = database.connection()) {
            deleteInvites(c, guildId);
            deleteRelations(c, guildId);
            deleteMembers(c, guildId);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guilds WHERE id = ?")) {
                ps.setLong(1, guildId);
                ps.executeUpdate();
            }
        }
    }

    public Optional<Guild> get(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Guild> findByName(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Guild> findByTag(String tag) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds WHERE tag = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public List<Guild> listAll() throws SQLException {
        List<Guild> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapGuild(rs));
            }
        }
        return out;
    }

    public List<Guild> topByLevel(int offset, int limit) throws SQLException {
        List<Guild> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM yap_guilds ORDER BY level DESC, xp DESC, name ASC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapGuild(rs));
                }
            }
        }
        return out;
    }

    public void updateLevelXp(long guildId, int level, long xp) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET level = ?, xp = ? WHERE id = ?")) {
            ps.setInt(1, level);
            ps.setLong(2, xp);
            ps.setLong(3, guildId);
            ps.executeUpdate();
        }
    }

    public void updateDescription(long guildId, String description) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET description = ? WHERE id = ?")) {
            ps.setString(1, description);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    public void updateMotd(long guildId, String motd) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET motd = ? WHERE id = ?")) {
            ps.setString(1, motd);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    public void updateJoinMode(long guildId, GuildJoinMode mode) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET join_mode = ? WHERE id = ?")) {
            ps.setString(1, mode.name());
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    public void updateLeader(long guildId, UUID leaderId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET leader_uuid = ? WHERE id = ?")) {
            ps.setString(1, leaderId.toString());
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    public void updateHome(long guildId, GuildHome home) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE yap_guilds
                     SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ?
                     WHERE id = ?
                     """)) {
            if (home.isSet()) {
                ps.setString(1, home.world());
                ps.setDouble(2, home.x());
                ps.setDouble(3, home.y());
                ps.setDouble(4, home.z());
                ps.setFloat(5, home.yaw());
                ps.setFloat(6, home.pitch());
            } else {
                ps.setString(1, null);
                ps.setNull(2, java.sql.Types.DOUBLE);
                ps.setNull(3, java.sql.Types.DOUBLE);
                ps.setNull(4, java.sql.Types.DOUBLE);
                ps.setNull(5, java.sql.Types.FLOAT);
                ps.setNull(6, java.sql.Types.FLOAT);
            }
            ps.setLong(7, guildId);
            ps.executeUpdate();
        }
    }

    public void updateBank(long guildId, double balance) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE yap_guilds SET bank_balance = ? WHERE id = ?")) {
            ps.setDouble(1, balance);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    public void addMember(GuildMember member) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_guild_members (guild_id, player_uuid, role, contribution_xp)
                     VALUES (?, ?, ?, ?)
                     """)) {
            ps.setLong(1, member.guildId());
            ps.setString(2, member.playerId().toString());
            ps.setString(3, member.role().name());
            ps.setLong(4, member.contributionXp());
            ps.executeUpdate();
        }
    }

    public void removeMember(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_guild_members WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void updateMemberRole(long guildId, UUID playerId, GuildRole role) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_guild_members SET role = ? WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setString(1, role.name());
            ps.setLong(2, guildId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void addContribution(long guildId, UUID playerId, long amount) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_guild_members SET contribution_xp = contribution_xp + ? "
                             + "WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, amount);
            ps.setLong(2, guildId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<GuildMember> member(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, role, contribution_xp FROM yap_guild_members WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
            }
        }
    }

    public List<GuildMember> members(long guildId) throws SQLException {
        List<GuildMember> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, role, contribution_xp FROM yap_guild_members WHERE guild_id = ? "
                             + "ORDER BY contribution_xp DESC, FIELD(role, 'LEADER', 'OFFICER', 'VETERAN', 'MEMBER', 'RECRUIT')")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMember(rs));
                }
            }
        }
        return out;
    }

    public int memberCount(long guildId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_guild_members WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void upsertInvite(GuildInvite invite) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_guild_invites (guild_id, player_uuid, invited_by, expires_at)
                     VALUES (?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE invited_by = VALUES(invited_by), expires_at = VALUES(expires_at)
                     """)) {
            ps.setLong(1, invite.guildId());
            ps.setString(2, invite.playerId().toString());
            ps.setString(3, invite.invitedBy().toString());
            ps.setTimestamp(4, Timestamp.from(invite.expiresAt()));
            ps.executeUpdate();
        }
    }

    public void deleteInvite(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM yap_guild_invites WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        }
    }

    public void deleteInvitesForPlayer(UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        }
    }

    public Optional<GuildInvite> invite(long guildId, UUID playerId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_guild_invites WHERE guild_id = ? AND player_uuid = ?")) {
            ps.setLong(1, guildId);
            ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInvite(rs)) : Optional.empty();
            }
        }
    }

    public List<GuildInvite> invitesForPlayer(UUID playerId) throws SQLException {
        List<GuildInvite> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, player_uuid, invited_by, created_at, expires_at "
                             + "FROM yap_guild_invites WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapInvite(rs));
                }
            }
        }
        return out;
    }

    public void setRelation(long guildA, long guildB, GuildRelation relation) throws SQLException {
        GuildRelationKey.Pair pair = GuildRelationKey.of(guildA, guildB);
        if (relation == GuildRelation.NEUTRAL) {
            clearRelation(pair.lowId(), pair.highId());
            return;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO yap_guild_relations (guild_id_a, guild_id_b, relation)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE relation = VALUES(relation)
                     """)) {
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

    private static void deleteInvites(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_invites WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        }
    }

    private static void deleteRelations(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM yap_guild_relations WHERE guild_id_a = ? OR guild_id_b = ?")) {
            ps.setLong(1, guildId);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
    }

    private static void deleteMembers(Connection c, long guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM yap_guild_members WHERE guild_id = ?")) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        }
    }

    private static Guild mapGuild(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new Guild(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getInt("level"),
                rs.getLong("xp"),
                rs.getString("description"),
                rs.getString("motd"),
                GuildJoinMode.parse(rs.getString("join_mode")).orElse(GuildJoinMode.OPEN),
                rs.getDouble("bank_balance"),
                mapHome(rs),
                created == null ? Instant.EPOCH : created.toInstant());
    }

    private static GuildHome mapHome(ResultSet rs) throws SQLException {
        String world = rs.getString("home_world");
        if (world == null || world.isBlank()) {
            return GuildHome.unset();
        }
        return new GuildHome(
                world,
                rs.getDouble("home_x"),
                rs.getDouble("home_y"),
                rs.getDouble("home_z"),
                rs.getFloat("home_yaw"),
                rs.getFloat("home_pitch"));
    }

    private static GuildMember mapMember(ResultSet rs) throws SQLException {
        return new GuildMember(
                rs.getLong("guild_id"),
                UUID.fromString(rs.getString("player_uuid")),
                GuildRole.parse(rs.getString("role")).orElse(GuildRole.MEMBER),
                rs.getLong("contribution_xp"));
    }

    private static GuildInvite mapInvite(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp expires = rs.getTimestamp("expires_at");
        return new GuildInvite(
                rs.getLong("guild_id"),
                UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("invited_by")),
                created == null ? Instant.EPOCH : created.toInstant(),
                expires == null ? Instant.EPOCH : expires.toInstant());
    }
}
