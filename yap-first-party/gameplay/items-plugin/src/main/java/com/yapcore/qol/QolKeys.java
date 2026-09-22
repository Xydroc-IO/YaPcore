package com.yapcore.qol;

import org.bukkit.NamespacedKey;

/** PDC keys for timber / excavator tools. Namespace stays {@code yap-qol} after the items merge. */
public final class QolKeys {

    public final NamespacedKey toolType;
    public final NamespacedKey mineSize;

    public QolKeys() {
        this.toolType = ToolTags.TYPE;
        this.mineSize = ToolTags.SIZE;
    }
}
