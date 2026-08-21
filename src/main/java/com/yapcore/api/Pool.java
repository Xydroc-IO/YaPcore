package com.yapcore.api;

/**
 * Execution pool for next-gen YaP plugins.
 * All-in-one plugins declare where work runs so TPS and UI never stall on DB I/O.
 */
public enum Pool {
    /** Cores 5–8 — GUI, menus, animations, click routing */
    UI,
    /** Cores 9–12 — DB, HTTP, files, proxy sync */
    HEAVY,
    /** Compatibility Bridge → GameCore tick handoff (world mutations) */
    SYNC
}
