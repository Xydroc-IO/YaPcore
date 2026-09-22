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
    private final PortalShape shape;
    private final PortalArrival arrival;
    private final String homeName;

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
                PortalColors.DEFAULT, PortalShape.full(), PortalArrival.SPAWN, "home");
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
        this(name, world, cuboid, targetServer, permission, cooldownSeconds, enabled, enterMessage,
                color, PortalShape.full(), PortalArrival.SPAWN, "home");
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
            String color,
            PortalShape shape
    ) {
        this(name, world, cuboid, targetServer, permission, cooldownSeconds, enabled, enterMessage,
                color, shape, PortalArrival.SPAWN, "home");
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
            String color,
            PortalShape shape,
            PortalArrival arrival
    ) {
        this(name, world, cuboid, targetServer, permission, cooldownSeconds, enabled, enterMessage,
                color, shape, arrival, "home");
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
            String color,
            PortalShape shape,
            PortalArrival arrival,
            String homeName
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
        this.shape = shape == null ? PortalShape.full() : shape;
        this.arrival = arrival == null ? PortalArrival.SPAWN : arrival;
        this.homeName = normalizeHomeName(homeName);
    }

    private static String normalizeHomeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "home";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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

    public PortalShape shape() {
        return shape;
    }

    /** Spawn pad, wild RTP, or player home on the destination backend. */
    public PortalArrival arrival() {
        return arrival;
    }

    /** YaPPlayerData home name when {@link #arrival()} is {@link PortalArrival#HOME}. */
    public String homeName() {
        return homeName;
    }

    public boolean containsBlock(int x, int y, int z) {
        return shape.contains(cuboid, x, y, z);
    }

    public Portal withTarget(String server) {
        return copy(server, permission, cooldownSeconds, enabled, enterMessage, color, cuboid, shape, arrival, homeName);
    }

    public Portal withPermission(String perm) {
        return copy(targetServer, perm, cooldownSeconds, enabled, enterMessage, color, cuboid, shape, arrival, homeName);
    }

    public Portal withCooldown(int seconds) {
        return copy(targetServer, permission, seconds, enabled, enterMessage, color, cuboid, shape, arrival, homeName);
    }

    public Portal withEnabled(boolean on) {
        return copy(targetServer, permission, cooldownSeconds, on, enterMessage, color, cuboid, shape, arrival, homeName);
    }

    public Portal withMessage(String msg) {
        return copy(targetServer, permission, cooldownSeconds, enabled, msg, color, cuboid, shape, arrival, homeName);
    }

    public Portal withColor(String next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, next, cuboid, shape, arrival, homeName);
    }

    public Portal withCuboid(PortalCuboid next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, color, next, shape, arrival, homeName);
    }

    public Portal withShape(PortalShape next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, color, cuboid,
                next == null ? PortalShape.full() : next, arrival, homeName);
    }

    public Portal withArrival(PortalArrival next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, color, cuboid, shape,
                next == null ? PortalArrival.SPAWN : next, homeName);
    }

    public Portal withHomeName(String next) {
        return copy(targetServer, permission, cooldownSeconds, enabled, enterMessage, color, cuboid, shape,
                arrival, next);
    }

    /**
     * Add or remove one block inside the selection. Switches the portal to a custom mask.
     * Blocks outside the cuboid are ignored (returns this).
     */
    public Portal withMaskBlock(int x, int y, int z, boolean add) {
        if (!cuboid.containsBlock(x, y, z)) {
            return this;
        }
        PortalShape base = shape.kind() == PortalShape.Kind.CUSTOM
                ? shape
                : PortalShape.of(PortalShape.Kind.CUSTOM);
        return withShape(base.withBlock(x - cuboid.minX(), y - cuboid.minY(), z - cuboid.minZ(), add));
    }

    /** Freeze the current preset into an editable block mask (capped so huge boxes stay empty). */
    public Portal asCustomMask() {
        if (shape.kind() == PortalShape.Kind.CUSTOM) {
            return this;
        }
        if (cuboid.volumeBlocks() > 4096) {
            return withShape(PortalShape.of(PortalShape.Kind.CUSTOM));
        }
        java.util.Set<Long> packed = new java.util.LinkedHashSet<>();
        for (int x = cuboid.minX(); x <= cuboid.maxX(); x++) {
            for (int y = cuboid.minY(); y <= cuboid.maxY(); y++) {
                for (int z = cuboid.minZ(); z <= cuboid.maxZ(); z++) {
                    if (containsBlock(x, y, z)) {
                        packed.add(PortalShape.pack(x - cuboid.minX(), y - cuboid.minY(), z - cuboid.minZ()));
                    }
                }
            }
        }
        return withShape(PortalShape.customPacked(packed));
    }

    private Portal copy(
            String target,
            String perm,
            int cooldown,
            boolean on,
            String msg,
            String col,
            PortalCuboid box,
            PortalShape nextShape,
            PortalArrival nextArrival,
            String nextHome
    ) {
        return new Portal(name, world, box, target, perm, cooldown, on, msg, col, nextShape, nextArrival, nextHome);
    }
}
