package com.yapcore.fleet.model;

/** Runtime lifecycle of a fleet Folia JVM. */
public enum InstanceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED;

    public boolean isAlive() {
        return this == STARTING || this == RUNNING;
    }
}
