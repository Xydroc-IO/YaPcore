package com.yapcore.regions;

import org.bukkit.Location;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Admin-defined regions (staff / server templates). Lookup is read-mostly; write methods
 * mutate MariaDB/SQLite and refresh the in-memory snapshot.
 * <p>
 * Spatial queries scan all regions for this server (O(n)). That is intentional and fine for
 * typical admin-region counts (tens to low hundreds). No spatial index ships in this release.
 */
public interface RegionService {

    /**
     * Region covering the location, if any.
     * Overlaps: highest {@link AdminRegion#priority()} wins; ties break by smallest volume.
     */
    Optional<AdminRegion> at(Location location);

    Optional<AdminRegion> named(String name);

    FlagValue flagAt(Location location, RegionFlag flag);

    /** All admin regions loaded for this server (immutable snapshot). */
    List<AdminRegion> listRegions();

    /** Greeting / farewell text for a region, if set. */
    Optional<String> message(long regionId, RegionMessageKind kind);

    /** Define a cuboid region from inclusive corner coordinates. */
    AdminRegion define(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException;

    /**
     * Define a polygonal region (XZ vertices, inclusive Y range). Requires ≥ 3 vertices.
     * Bounding box is derived from the vertices.
     */
    AdminRegion definePolygon(String name, String world, int minY, int maxY, List<RegionVertex> vertices)
            throws SQLException;

    /** Replace cuboid bounds; polygon regions become cuboids. */
    AdminRegion redefine(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2)
            throws SQLException;

    /** Replace polygon vertices and Y range; cuboid regions become polygons. */
    AdminRegion redefinePolygon(String name, String world, int minY, int maxY, List<RegionVertex> vertices)
            throws SQLException;

    void remove(String name) throws SQLException;

    void setFlag(String name, RegionFlag flag, FlagValue value) throws SQLException;

    void setPriority(String name, int priority) throws SQLException;

    void setMessage(String name, RegionMessageKind kind, String text) throws SQLException;

    void clearMessage(String name, RegionMessageKind kind) throws SQLException;

    /** Persist the region's current flags + messages as a named template. */
    void saveTemplate(String templateName, String fromRegion) throws SQLException;

    /** Apply a saved template's flags + messages onto an existing region. */
    void applyTemplate(String regionName, String templateName) throws SQLException;

    /** Named templates available on this server (flag + message presets). */
    List<String> listTemplates();
}
