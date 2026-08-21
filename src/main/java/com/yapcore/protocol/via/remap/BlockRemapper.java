package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.via.catalog.CatalogStore;

/** Block id remaps via catalog name bridges; legacy id|meta packed as (id&lt;&lt;4)|meta. */
public final class BlockRemapper {

    private final ProtocolBand from;
    private final ProtocolBand to;
    private final CatalogStore catalogs = CatalogStore.get();

    public BlockRemapper(ProtocolBand from, ProtocolBand to) {
        this.from = from;
        this.to = to;
    }

    public int toServerBlockState(int legacyIdMetaOrState) {
        if (from == to) {
            return legacyIdMetaOrState;
        }
        if (isLegacy(from)) {
            int id = (legacyIdMetaOrState >> 4) & 0xFFF;
            int remapped = catalogs.remapBlock(from, to, id);
            // Modern uses flat block state ids — return remapped block id as best-effort state
            return remapped;
        }
        return catalogs.remapBlock(from, to, legacyIdMetaOrState);
    }

    public int toClientLegacy(int modernStateOrId) {
        if (from == to) {
            return modernStateOrId;
        }
        CatalogStore.BandCatalog client = catalogs.band(from);
        CatalogStore.BandCatalog server = catalogs.band(to);
        if (client == null || server == null) {
            return modernStateOrId;
        }
        int legacyId = client.blockToLegacy(modernStateOrId, server);
        if (isLegacy(from)) {
            return (legacyId << 4); // meta 0 default
        }
        return legacyId;
    }

    private static boolean isLegacy(ProtocolBand b) {
        return b.ordinal() <= ProtocolBand.V1_12.ordinal();
    }

    public ProtocolBand from() {
        return from;
    }

    public ProtocolBand to() {
        return to;
    }

    public String sectionFormatFrom() {
        return catalogs.sectionFormat(from);
    }

    public String sectionFormatTo() {
        return catalogs.sectionFormat(to);
    }
}
