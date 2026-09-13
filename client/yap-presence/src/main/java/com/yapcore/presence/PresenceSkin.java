package com.yapcore.presence;

import com.yapcore.presence.geo.GeometryLoader;
import com.yapcore.presence.geo.GeometryModel;

import java.util.Objects;
import java.util.UUID;

/** Per-player presence skin + ported geometry held on the client. */
public final class PresenceSkin {

    private final UUID playerUuid;
    private final boolean slim;
    private final String skinPngUrl;
    private final String geometryJson;
    private final GeometryModel geometry;

    public PresenceSkin(UUID playerUuid, boolean slim, String skinPngUrl, String geometryJson) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.slim = slim;
        this.skinPngUrl = skinPngUrl == null ? "" : skinPngUrl;
        this.geometryJson = geometryJson == null ? "" : geometryJson;
        this.geometry = GeometryLoader.load(this.geometryJson);
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public boolean slim() {
        return slim;
    }

    public String skinPngUrl() {
        return skinPngUrl;
    }

    public String geometryJson() {
        return geometryJson;
    }

    public GeometryModel geometry() {
        return geometry;
    }

    public boolean hasRenderableGeometry() {
        return geometry != null && geometry.hasCubes();
    }
}
