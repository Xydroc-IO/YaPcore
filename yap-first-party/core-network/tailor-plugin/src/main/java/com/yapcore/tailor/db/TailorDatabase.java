package com.yapcore.tailor.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.tailor.ActiveSkin;
import com.yapcore.tailor.SkinModel;
import com.yapcore.tailor.TailorConfig;
import com.yapcore.tailor.WardrobeSlot;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Map.entry;

public final class TailorDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final TailorConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.sqlite();

    public TailorDatabase(JavaPlugin plugin, TailorConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
        } catch (Exception e) {
            throw new SQLException("Cannot create tailor data folder", e);
        }

        YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
                "YaPTailor",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMinIdle(),
                config.connectionTimeoutMs(),
                config.useSharedYapDb());
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPTailor using shared YaPDB pool (" + shared.jdbcUrl() + ")");
            return;
        }
        usingShared = false;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Hikari may still resolve via DriverManager for mysql
        }
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().info("YaPTailor embedded pool ready (" + config.jdbcUrl() + ")");
    }

    private void migrate() throws SQLException {
        String uuidType = dialect.engine() == YapDbEngine.SQLITE ? "TEXT" : "VARCHAR(36)";
        String textType = dialect.longTextType();
        String autoPk = dialect.autoIncrementPk();
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tailor_active (
                      uuid %s PRIMARY KEY,
                      skin_url %s,
                      cape_url %s,
                      model VARCHAR(16) NOT NULL,
                      texture_value %s,
                      updated_at BIGINT NOT NULL,
                      bedrock_canonical %s,
                      geometry_name %s,
                      skin_id %s
                    )
                    """.formatted(uuidType, textType, textType, textType, textType, textType, textType));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tailor_wardrobe (
                      id %s,
                      uuid %s NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      skin_url %s,
                      cape_url %s,
                      model VARCHAR(16) NOT NULL,
                      created_at BIGINT NOT NULL,
                      updated_at BIGINT NOT NULL,
                      bedrock_canonical %s,
                      geometry_name %s,
                      skin_id %s
                    )
                    """.formatted(autoPk, uuidType, textType, textType, textType, textType, textType));
            tryAlter(st, "ALTER TABLE tailor_active ADD COLUMN bedrock_canonical " + textType);
            tryAlter(st, "ALTER TABLE tailor_active ADD COLUMN geometry_name " + textType);
            tryAlter(st, "ALTER TABLE tailor_active ADD COLUMN skin_id " + textType);
            tryAlter(st, "ALTER TABLE tailor_active ADD COLUMN active_slot_id BIGINT");
            tryAlter(st, "ALTER TABLE tailor_wardrobe ADD COLUMN bedrock_canonical " + textType);
            tryAlter(st, "ALTER TABLE tailor_wardrobe ADD COLUMN geometry_name " + textType);
            tryAlter(st, "ALTER TABLE tailor_wardrobe ADD COLUMN skin_id " + textType);
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_tailor_wardrobe_uuid ON tailor_wardrobe (uuid)");
            } catch (SQLException ignored) {
                // some engines already have the index
            }
        }
    }

    private static void tryAlter(Statement st, String sql) {
        try {
            st.execute(sql);
        } catch (SQLException ignored) {
            // column already exists
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        return embedded.getConnection();
    }

    public Optional<ActiveSkin> loadActive(UUID uuid) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT skin_url, cape_url, model, texture_value, updated_at, "
                             + "bedrock_canonical, geometry_name, skin_id, active_slot_id "
                             + "FROM tailor_active WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                SkinModel model = SkinModel.fromString(rs.getString("model"));
                String geometry = rs.getString("geometry_name");
                String skinId = rs.getString("skin_id");
                long slotRaw = rs.getLong("active_slot_id");
                Long activeSlotId = rs.wasNull() || slotRaw <= 0 ? null : slotRaw;
                if ((geometry == null || geometry.isBlank()) || (skinId == null || skinId.isBlank())) {
                    ActiveSkin filled = ActiveSkin.of(
                            uuid,
                            rs.getString("skin_url"),
                            rs.getString("cape_url"),
                            model,
                            rs.getString("texture_value"),
                            rs.getLong("updated_at"),
                            activeSlotId);
                    return Optional.of(new ActiveSkin(
                            filled.playerUuid(),
                            filled.sourceUrl(),
                            filled.capeUrl(),
                            filled.model(),
                            filled.textureValueBase64(),
                            filled.updatedAtMs(),
                            rs.getString("bedrock_canonical"),
                            geometry == null || geometry.isBlank() ? filled.geometryName() : geometry,
                            skinId == null || skinId.isBlank() ? filled.skinId() : skinId,
                            activeSlotId));
                }
                return Optional.of(new ActiveSkin(
                        uuid,
                        rs.getString("skin_url"),
                        rs.getString("cape_url"),
                        model,
                        rs.getString("texture_value"),
                        rs.getLong("updated_at"),
                        rs.getString("bedrock_canonical"),
                        geometry,
                        skinId,
                        activeSlotId));
            }
        }
    }

    public void saveActive(ActiveSkin skin) throws SQLException {
        String sql = dialect.upsert(
                "tailor_active",
                List.of("uuid"),
                List.of("uuid", "skin_url", "cape_url", "model", "texture_value", "updated_at",
                        "bedrock_canonical", "geometry_name", "skin_id", "active_slot_id"),
                Map.ofEntries(
                        entry("skin_url", "EXCLUDED.skin_url"),
                        entry("cape_url", "EXCLUDED.cape_url"),
                        entry("model", "EXCLUDED.model"),
                        entry("texture_value", "EXCLUDED.texture_value"),
                        entry("updated_at", "EXCLUDED.updated_at"),
                        entry("bedrock_canonical", "EXCLUDED.bedrock_canonical"),
                        entry("geometry_name", "EXCLUDED.geometry_name"),
                        entry("skin_id", "EXCLUDED.skin_id"),
                        entry("active_slot_id", "EXCLUDED.active_slot_id")));
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, skin.playerUuid().toString());
            ps.setString(2, skin.sourceUrl());
            ps.setString(3, skin.capeUrl());
            ps.setString(4, skin.model().name());
            ps.setString(5, skin.textureValueBase64());
            ps.setLong(6, skin.updatedAtMs());
            ps.setString(7, skin.bedrockCanonicalJson());
            ps.setString(8, skin.geometryName());
            ps.setString(9, skin.skinId());
            if (skin.activeSlotId() == null || skin.activeSlotId() <= 0) {
                ps.setObject(10, null);
            } else {
                ps.setLong(10, skin.activeSlotId());
            }
            ps.executeUpdate();
        }
    }

    public void deleteActive(UUID uuid) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tailor_active WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public List<WardrobeSlot> listWardrobe(UUID uuid) throws SQLException {
        List<WardrobeSlot> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, skin_url, cape_url, model, created_at, updated_at, "
                             + "bedrock_canonical, geometry_name, skin_id "
                             + "FROM tailor_wardrobe WHERE uuid=? ORDER BY id ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readWardrobeRow(rs, uuid));
                }
            }
        }
        return out;
    }

    public Optional<WardrobeSlot> findWardrobe(UUID uuid, long id) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, skin_url, cape_url, model, created_at, updated_at, "
                             + "bedrock_canonical, geometry_name, skin_id "
                             + "FROM tailor_wardrobe WHERE uuid=? AND id=?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readWardrobeRow(rs, uuid));
            }
        }
    }

    public Optional<WardrobeSlot> findWardrobeByName(UUID uuid, String name) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, skin_url, cape_url, model, created_at, updated_at, "
                             + "bedrock_canonical, geometry_name, skin_id "
                             + "FROM tailor_wardrobe WHERE uuid=? AND LOWER(name)=LOWER(?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readWardrobeRow(rs, uuid));
            }
        }
    }

    private static WardrobeSlot readWardrobeRow(ResultSet rs, UUID uuid) throws SQLException {
        return new WardrobeSlot(
                rs.getLong("id"),
                uuid,
                rs.getString("name"),
                rs.getString("skin_url"),
                rs.getString("cape_url"),
                SkinModel.fromString(rs.getString("model")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getString("bedrock_canonical"),
                rs.getString("geometry_name"),
                rs.getString("skin_id"));
    }

    public WardrobeSlot insertWardrobe(
            UUID uuid,
            String name,
            String skinUrl,
            String capeUrl,
            SkinModel model,
            String bedrockCanonical,
            String geometryName,
            String skinId) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tailor_wardrobe (uuid, name, skin_url, cape_url, model, created_at, updated_at, "
                             + "bedrock_canonical, geometry_name, skin_id) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, skinUrl);
            ps.setString(4, capeUrl);
            ps.setString(5, model.name());
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.setString(8, bedrockCanonical);
            ps.setString(9, geometryName);
            ps.setString(10, skinId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return new WardrobeSlot(
                            keys.getLong(1), uuid, name, skinUrl, capeUrl, model, now, now,
                            bedrockCanonical, geometryName, skinId);
                }
            }
        }
        // SQLite sometimes needs last_insert_rowid fallback
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM tailor_wardrobe WHERE uuid=? AND name=? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new WardrobeSlot(
                            rs.getLong(1), uuid, name, skinUrl, capeUrl, model, now, now,
                            bedrockCanonical, geometryName, skinId);
                }
            }
        }
        throw new SQLException("Failed to insert wardrobe slot");
    }

    public void updateWardrobe(WardrobeSlot slot) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE tailor_wardrobe SET name=?, skin_url=?, cape_url=?, model=?, updated_at=?, "
                             + "bedrock_canonical=?, geometry_name=?, skin_id=? "
                             + "WHERE id=? AND uuid=?")) {
            ps.setString(1, slot.name());
            ps.setString(2, slot.skinUrl());
            ps.setString(3, slot.capeUrl());
            ps.setString(4, slot.model().name());
            ps.setLong(5, slot.updatedAtMs());
            ps.setString(6, slot.bedrockCanonicalJson());
            ps.setString(7, slot.geometryName());
            ps.setString(8, slot.skinId());
            ps.setLong(9, slot.id());
            ps.setString(10, slot.playerUuid().toString());
            ps.executeUpdate();
        }
    }

    public void deleteWardrobe(UUID uuid, long id) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tailor_wardrobe WHERE uuid=? AND id=?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public int countWardrobe(UUID uuid) throws SQLException {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM tailor_wardrobe WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean isOpen() {
        return usingShared ? shared != null && shared.isOpen() : embedded != null && !embedded.isClosed();
    }

    @Override
    public void close() {
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
        shared = null;
        usingShared = false;
    }
}
