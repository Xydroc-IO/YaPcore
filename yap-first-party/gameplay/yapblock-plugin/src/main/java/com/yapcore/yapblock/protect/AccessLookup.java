package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import com.yapcore.yapblock.service.IslandServiceImpl;

final class AccessLookup {

    private AccessLookup() {
    }

    static IslandAccess access(YapblockPlugin plugin) {
        IslandServiceImpl service = plugin.service();
        return service == null ? null : service.access();
    }
}
