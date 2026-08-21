package com.yaplabs.yapengine.sandbox.io;

/**
 * Named Heavy I/O roles — Threads 12–15.
 */
public enum HeavyIoRole {
    DATABASE(12, "yap-t12-io-database"),
    WORLD_SAVE(13, "yap-t13-io-world"),
    RESOURCE_PACK(14, "yap-t14-io-packs"),
    BEDROCK(15, "yap-t15-io-bedrock");

    private final int threadId;
    private final String threadName;

    HeavyIoRole(int threadId, String threadName) {
        this.threadId = threadId;
        this.threadName = threadName;
    }

    public int threadId() {
        return threadId;
    }

    public String threadName() {
        return threadName;
    }
}
