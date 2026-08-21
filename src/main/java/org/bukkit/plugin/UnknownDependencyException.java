package org.bukkit.plugin;

/** Thrown when a hard plugin dependency is missing. */
public class UnknownDependencyException extends RuntimeException {
    public UnknownDependencyException(String message) {
        super(message);
    }

    public UnknownDependencyException(Throwable cause) {
        super(cause);
    }
}
