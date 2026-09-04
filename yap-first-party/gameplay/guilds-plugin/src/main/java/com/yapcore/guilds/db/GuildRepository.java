package com.yapcore.guilds.db;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuildRepository {

    private final GuildDatabase database;
    private final GuildMemberInviteQueries members;
    private final GuildRelationQueries relations;

    public GuildRepository(GuildDatabase database) {
        this.database = database;
        this.members = new GuildMemberInviteQueries(database);
        this.relations = new GuildRelationQueries(database);
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
            GuildSqlMapping.deleteInvites(c, guildId);
            GuildSqlMapping.deleteRelations(c, guildId);
            GuildSqlMapping.deleteMembers(c, guildId);
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
                return rs.next() ? Optional.of(GuildSqlMapping.mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Guild> findByName(String name) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(GuildSqlMapping.mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Guild> findByTag(String tag) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds WHERE tag = ?")) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(GuildSqlMapping.mapGuild(rs)) : Optional.empty();
            }
        }
    }

    public List<Guild> listAll() throws SQLException {
        List<Guild> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM yap_guilds ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(GuildSqlMapping.mapGuild(rs));
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
                    out.add(GuildSqlMapping.mapGuild(rs));
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
        members.addMember(member);
    }

    public void removeMember(long guildId, UUID playerId) throws SQLException {
        members.removeMember(guildId, playerId);
    }

    public void updateMemberRole(long guildId, UUID playerId, GuildRole role) throws SQLException {
        members.updateMemberRole(guildId, playerId, role);
    }

    public void addContribution(long guildId, UUID playerId, long amount) throws SQLException {
        members.addContribution(guildId, playerId, amount);
    }

    public Optional<GuildMember> member(UUID playerId) throws SQLException {
        return members.member(playerId);
    }

    public List<GuildMember> members(long guildId) throws SQLException {
        return members.members(guildId);
    }

    public int memberCount(long guildId) throws SQLException {
        return members.memberCount(guildId);
    }

    public void upsertInvite(GuildInvite invite) throws SQLException {
        members.upsertInvite(invite);
    }

    public void deleteInvite(long guildId, UUID playerId) throws SQLException {
        members.deleteInvite(guildId, playerId);
    }

    public void deleteInvitesForPlayer(UUID playerId) throws SQLException {
        members.deleteInvitesForPlayer(playerId);
    }

    public Optional<GuildInvite> invite(long guildId, UUID playerId) throws SQLException {
        return members.invite(guildId, playerId);
    }

    public List<GuildInvite> invitesForPlayer(UUID playerId) throws SQLException {
        return members.invitesForPlayer(playerId);
    }

    public void setRelation(long guildA, long guildB, GuildRelation relation) throws SQLException {
        relations.setRelation(guildA, guildB, relation);
    }

    public void clearRelation(long lowId, long highId) throws SQLException {
        relations.clearRelation(lowId, highId);
    }

    public Optional<GuildRelation> relation(long guildA, long guildB) throws SQLException {
        return relations.relation(guildA, guildB);
    }

    public Map<String, Integer> dashboardCounts() throws SQLException {
        return relations.dashboardCounts();
    }
}
