package com.yapcore.portals;

import java.util.Locale;
import java.util.Objects;

/** Named fleet portal definition on one Folia backend. */
public final class Portal {

    private final String name;
    private final String world;
    private final PortalCuboid cuboid;
    private final String targetServer;
    private final String permission;
    private final int cooldownSeconds;
    private final boolean enabled;
    private final String enterMessage;
    private final String color;

    public Portal(
            String name,
            String world,
            PortalCuboid cuboid,
            String targetServer,
            String permission,
            int cooldownSeconds,
            boolean enabled,
            String enterMessage
    ) {
        this(name, world, cuboid, targetServer, permission, cooldownSeconds, enabled, enterMessage,
                PortalColors.DEFAULT);
    }

    public Portal(
            String name,
            String world,
            PortalCuboid cuboid,
            String targetServer,
            String permission,
            int cooldownSeconds,
            boolean enabled,
            String enterMessage,
            String color
    ) {
        this.name = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
        this.world = Objects.requireNonNull(world, "world").trim();
        this.cuboid = Objects.requireNonNull(cuboid, "cuboid");
        this.targetServer = Objects.requireNonNull(targetServer, "targetServer").trim();
        this.permission = permission == null ? "" : permission.trim();
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.enabled = enabled;
        this.enterMessage = enterMessage == null ? "" : enterMessage;
        this.color = PortalColors.normalize(color);
    }

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public PortalCuboid cuboid() {
        return cuboid;
    }

    public String targetServer() {
        return targetServer;
    }

    public String permission() {
        return permission;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean enabled() {
        return enabled;
    }

    public String enterMessage() {
        return enterMessage;
    }

    /** Dye color name (purple, lime, red, …) for walk-through portal visuals. */
    public String color() {
        return color;
    }

    public Portal withTarget(String server) {
        return copy(server, permission, cooldownSeconds, enabled, enterMessage, color, cuboid);
    }

    public Portal withPermission(String perm) {
        return copy(targetServer, perm, cooldownSeconds, enabled, enterMessage, color, cuboid);
    }

    public Portal withCooldown(int seconds) {
        return copy(targetServer, permission, seconds, enabled, enterMessage, color, cuboid);
    }

    public Portal withEnabled(boolean on) {
        return copy(targetServer, permission, cooldownSeconds, on, enterMessage, color, cuboid);
    }

    public Portal withMessage(String msg) {
        return copy(targetServer, permission, cooldownSeconds, enabled, msg, color, cuboid);
    }

    public Portal withColor(String next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, next, cuboid);
    }

    public Portal withCuboid(PortalCuboid next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, color, next);
    }

    private Portal copy(
            String target,
            String perm,
            int cooldown,
            boolean on,
            String msg,
            String col,
            PortalCuboid box
    ) {
        return new Portal(name, world, box, target, perm, cooldown, on, msg, col);
    }
}
