package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.via.catalog.CatalogStore;

/**
 * Entity type id remaps across bands via catalog name bridges.
 * Missing names use {@link CatalogStore.BandCatalog#standInEntityType()} (pig / armor_stand).
 */
public final class EntityRemapper {

    private final ProtocolBand from;
    private final ProtocolBand to;
    private final CatalogStore catalogs = CatalogStore.get();

    public EntityRemapper(ProtocolBand from, ProtocolBand to) {
        this.from = from;
        this.to = to;
    }

    public int toServerType(int clientType) {
        if (from == to) {
            return clientType;
        }
        return catalogs.remapEntity(from, to, clientType);
    }

    public int toClientType(int serverType) {
        if (from == to) {
            return serverType;
        }
        CatalogStore.BandCatalog client = catalogs.band(from);
        CatalogStore.BandCatalog server = catalogs.band(to);
        if (client == null || server == null) {
            return serverType;
        }
        return client.entityToLegacy(serverType, server);
    }
}
