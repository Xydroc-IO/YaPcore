package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.via.catalog.CatalogStore;

/**
 * Item ID remaps via {@link CatalogStore} name bridges (full band catalogs).
 */
public final class ItemRemapper {

    private final ProtocolBand from;
    private final ProtocolBand to;
    private final CatalogStore catalogs = CatalogStore.get();

    public ItemRemapper(ProtocolBand from, ProtocolBand to) {
        this.from = from;
        this.to = to;
    }

    public int remapToServer(int clientItemId) {
        if (from == to) {
            return clientItemId;
        }
        return catalogs.remapItem(from, to, clientItemId);
    }

    public int remapToClient(int serverItemId) {
        if (from == to) {
            return serverItemId;
        }
        CatalogStore.BandCatalog client = catalogs.band(from);
        CatalogStore.BandCatalog server = catalogs.band(to);
        if (client == null || server == null) {
            return serverItemId;
        }
        return client.itemToLegacy(serverItemId, server);
    }
}
