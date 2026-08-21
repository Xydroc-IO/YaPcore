package com.yapcore.crossplay.bedrock;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies Bedrock BREAK/PLACE (and light MOVE) onto the Paper world when
 * {@code game-authority=paper}. Uses Paper's classloader + main-thread schedule.
 */
public final class BedrockPaperWorldSync {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockPaper");

    private final AtomicReference<URLClassLoader> paperLoader = new AtomicReference<>();
    private final BedrockPaperPlayerInject playerInject = new BedrockPaperPlayerInject(paperLoader);
    private final BedrockPaperInventoryInject inventoryInject = new BedrockPaperInventoryInject();
    private volatile boolean enabled;
    /** Chunk column cache: key = ((long)cx << 32) ^ (cz & 0xffffffffL) */
    private final java.util.concurrent.ConcurrentHashMap<Long, int[][]> columnCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void attach(URLClassLoader loader) {
        paperLoader.set(loader);
        enabled = loader != null;
        columnCache.clear();
        if (enabled) {
            LOG.info("Bedrock→Paper world sync attached");
        }
    }

    public void detach() {
        paperLoader.set(null);
        enabled = false;
        columnCache.clear();
    }

    /** Inject a real Paper Player for a Bedrock Floodgate identity. */
    public boolean injectPlayer(String username, java.util.UUID uuid, double x, double y, double z) {
        inventoryInject.inject(uuid, username);
        boolean ok = isEnabled() && playerInject.inject(username, uuid, x, y, z);
        if (ok) {
            inventoryInject.flushToLivePlayer(username, this);
        }
        return ok;
    }

    /** Remove Paper presence when a Bedrock session disconnects. */
    public boolean ejectPlayer(String username) {
        inventoryInject.pullFromLivePlayer(username, this);
        boolean ok = playerInject.eject(username);
        inventoryInject.eject(username);
        return ok;
    }

    public boolean hasInjectedPlayer(String username) {
        return playerInject.isInjected(username);
    }

    private static long columnKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public void invalidateColumn(int cx, int cz) {
        columnCache.remove(columnKey(cx, cz));
    }

    /**
     * Snapshot one overworld column as Bedrock hashed state ids
     * ({@link BedrockBlockRuntimeIds} catalog). Y −64..319 → 24×4096.
     * Returns null if Paper is unavailable. Cached until BREAK/PLACE invalidates.
     */
    public int[][] snapshotColumnHashedStates(int chunkX, int chunkZ) {
        if (!isEnabled()) {
            return null;
        }
        long key = columnKey(chunkX, chunkZ);
        int[][] cached = columnCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            int[][] column = readColumnHashedStates(chunkX, chunkZ);
            if (column != null) {
                columnCache.put(key, column);
            }
            return column;
        } catch (Exception e) {
            LOG.log(Level.FINE, "column snapshot failed", e);
            return null;
        }
    }

    private void attackEntity(String attacker, String targetRuntime, String targetName, String targetUuid) {
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object attackerPlayer = findPlayer(bukkit, attacker);
            if (attackerPlayer == null) {
                return;
            }
            Object victim = null;
            if (targetUuid != null && !targetUuid.isBlank()) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(targetUuid.trim());
                    victim = bukkit.getMethod("getEntity", java.util.UUID.class).invoke(null, uuid);
                } catch (Exception ignored) {
                }
            }
            if (victim == null && targetName != null && !targetName.isBlank()) {
                victim = findPlayer(bukkit, targetName);
            }
            if (victim == null) {
                victim = nearestLivingTarget(attackerPlayer, cl, attacker);
            }
            if (victim == null) {
                return;
            }
            try {
                attackerPlayer.getClass().getMethod("attack",
                        Class.forName("org.bukkit.entity.Entity", true, cl))
                        .invoke(attackerPlayer, victim);
            } catch (NoSuchMethodException e) {
                victim.getClass().getMethod("damage", double.class).invoke(victim, 1.0);
            }
            final Object v = victim;
            LOG.fine(() -> "Paper ATTACK " + attacker + " → " + v.getClass().getSimpleName());
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper ATTACK failed", e);
        }
    }

    /** Closest LivingEntity within 4.5 blocks (prefers non-players). */
    private Object nearestLivingTarget(Object attackerPlayer, ClassLoader cl, String attackerName)
            throws ReflectiveOperationException {
        Object loc = attackerPlayer.getClass().getMethod("getLocation").invoke(attackerPlayer);
        Object world = loc.getClass().getMethod("getWorld").invoke(loc);
        if (world == null) {
            return null;
        }
        Class<?> living = Class.forName("org.bukkit.entity.LivingEntity", true, cl);
        @SuppressWarnings("unchecked")
        java.util.Collection<Object> nearby = (java.util.Collection<Object>) world.getClass()
                .getMethod("getNearbyEntities",
                        loc.getClass(), double.class, double.class, double.class)
                .invoke(world, loc, 4.5, 4.5, 4.5);
        Object bestMob = null;
        Object bestAny = null;
        double bestMobD = Double.MAX_VALUE;
        double bestAnyD = Double.MAX_VALUE;
        Class<?> playerCl = Class.forName("org.bukkit.entity.Player", true, cl);
        for (Object e : nearby) {
            if (e == null || !living.isInstance(e)) {
                continue;
            }
            try {
                String name = (String) e.getClass().getMethod("getName").invoke(e);
                if (name != null && name.equalsIgnoreCase(attackerName)) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            Object el = e.getClass().getMethod("getLocation").invoke(e);
            double d = ((Number) loc.getClass().getMethod("distance", el.getClass())
                    .invoke(loc, el)).doubleValue();
            boolean isPlayer = playerCl.isInstance(e);
            if (!isPlayer && d < bestMobD) {
                bestMobD = d;
                bestMob = e;
            }
            if (d < bestAnyD) {
                bestAnyD = d;
                bestAny = e;
            }
        }
        return bestMob != null ? bestMob : bestAny;
    }

    private void applyInventory(String playerName, int hotbar, int slot, String itemHint) {
        try {
            if (playerName == null || playerName.isBlank()) {
                return;
            }
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, playerName);
            if (player == null) {
                return;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            if (hotbar >= 0 && hotbar <= 8) {
                inv.getClass().getMethod("setHeldItemSlot", int.class).invoke(inv, hotbar);
            }
            if (slot >= 0 && itemHint != null && !itemHint.isBlank() && !"air".equalsIgnoreCase(itemHint)) {
                Object mat = materialValue(itemHint.toUpperCase().replace("MINECRAFT:", ""));
                if (mat != null) {
                    Class<?> isClass = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
                    Object stack = isClass.getConstructor(materialClass()).newInstance(mat);
                    inv.getClass().getMethod("setItem", int.class, isClass).invoke(inv, slot, stack);
                }
            }
            LOG.fine(() -> "Paper INV " + playerName + " hotbar=" + hotbar + " slot=" + slot);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper INV failed", e);
        }
    }

    public boolean isEnabled() {
        return enabled && paperLoader.get() != null;
    }

    /** Live Paper Bukkit classloader (may differ from Paperclip host loader). */
    private ClassLoader liveLoader() {
        return com.yapcore.paper.PaperCommandBridge.resolvePaperLoader(paperLoader.get());
    }

    ClassLoader liveLoaderPublic() {
        return liveLoader();
    }

    public void apply(String action, Map<String, String> payload) {
        if (!isEnabled() || payload == null) {
            return;
        }
        String act = action == null ? "" : action.trim().toUpperCase();
        switch (act) {
            case "BREAK" -> runOnMain(() -> {
                int x = parse(payload.get("x"), 0);
                int y = parse(payload.get("y"), 0);
                int z = parse(payload.get("z"), 0);
                breakBlock(x, y, z);
                invalidateColumn(x >> 4, z >> 4);
            });
            case "PLACE" -> runOnMain(() -> {
                int x = parse(payload.get("x"), 0);
                int y = parse(payload.get("y"), 0);
                int z = parse(payload.get("z"), 0);
                placeBlock(x, y, z, payload.getOrDefault("block", "stone"));
                invalidateColumn(x >> 4, z >> 4);
            });
            case "ATTACK" -> runOnMain(() -> attackEntity(
                    payload.get("attacker"),
                    payload.get("target"),
                    payload.get("targetName"),
                    payload.get("targetUuid")));
            case "OPEN_CONTAINER", "CONTAINER" -> runOnMain(() -> openContainer(
                    payload.get("player"),
                    parse(payload.get("type"), BedrockContainerBridge.TYPE_CHEST),
                    parse(payload.get("x"), 0),
                    parse(payload.get("y"), 0),
                    parse(payload.get("z"), 0)));
            case "CLOSE_CONTAINER" -> runOnMain(() -> closeContainer(payload.get("player")));
            case "INV", "HOTBAR" -> runOnMain(() -> applyInventory(
                    payload.get("player"),
                    parse(payload.get("hotbar"), -1),
                    parse(payload.get("slot"), -1),
                    payload.getOrDefault("item", "")));
            default -> {
            }
        }
    }

    public void openContainer(String playerName, int type, int x, int y, int z) {
        try {
            if (playerName == null || playerName.isBlank() || !isEnabled()) {
                return;
            }
            ClassLoader cl = liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, playerName);
            if (player == null) {
                return;
            }
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return;
            }
            // InventoryHolder.getInventory() then player.openInventory
            try {
                Object inv = state.getClass().getMethod("getInventory").invoke(state);
                if (inv != null) {
                    player.getClass().getMethod("openInventory",
                            Class.forName("org.bukkit.inventory.Inventory", true, cl))
                            .invoke(player, inv);
                    LOG.fine(() -> "Paper openInventory " + playerName + " @" + x + "," + y + "," + z);
                    return;
                }
            } catch (NoSuchMethodException ignored) {
                // not an inventory holder
            }
            if (type == BedrockContainerBridge.TYPE_ENCHANT) {
                // EnchantingTable — openEnchanting if available
                try {
                    Object loc = block.getClass().getMethod("getLocation").invoke(block);
                    player.getClass().getMethod("openEnchanting",
                                    Class.forName("org.bukkit.Location", true, cl), boolean.class)
                            .invoke(player, loc, true);
                } catch (NoSuchMethodException ignored) {
                    // optional
                }
            } else if (type == BedrockContainerBridge.TYPE_WORKBENCH) {
                try {
                    Object loc = block.getClass().getMethod("getLocation").invoke(block);
                    player.getClass().getMethod("openWorkbench",
                                    Class.forName("org.bukkit.Location", true, cl), boolean.class)
                            .invoke(player, loc, true);
                } catch (NoSuchMethodException ignored) {
                    // optional
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper openContainer failed", e);
        }
    }

    public void closeContainer(String playerName) {
        try {
            if (playerName == null || !isEnabled()) {
                return;
            }
            ClassLoader cl = liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, playerName);
            if (player != null) {
                player.getClass().getMethod("closeInventory").invoke(player);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper closeContainer failed", e);
        }
    }

    private void breakBlock(int x, int y, int z) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            Object air = materialValue("AIR");
            setType.invoke(block, air);
            LOG.fine(() -> "Paper BREAK @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "Paper BREAK failed", e);
        }
    }

    private void placeBlock(int x, int y, int z, String blockName) {
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return;
            }
            String matName = blockName == null ? "STONE" : blockName.trim().toUpperCase()
                    .replace("MINECRAFT:", "").replace(' ', '_');
            Object mat = materialValue(matName);
            if (mat == null) {
                mat = materialValue("STONE");
            }
            Method setType = block.getClass().getMethod("setType", materialClass());
            setType.invoke(block, mat);
            LOG.fine(() -> "Paper PLACE " + matName + " @" + x + "," + y + "," + z);
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.FINE, "Paper PLACE failed", e);
        }
    }

    public Object findOnlinePlayer(String username) {
        if (!isEnabled() || username == null) {
            return null;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            return findPlayer(bukkit, username);
        } catch (Exception e) {
            return null;
        }
    }

    public String materialAt(int x, int y, int z) {
        if (!isEnabled()) {
            return null;
        }
        try {
            Object block = blockAt(x, y, z);
            if (block == null) {
                return null;
            }
            Object type = block.getClass().getMethod("getType").invoke(block);
            return type == null ? null : String.valueOf(type);
        } catch (Exception e) {
            return null;
        }
    }

    private Object blockAt(int x, int y, int z) throws ReflectiveOperationException {
        ClassLoader cl = liveLoader();
        if (cl == null) {
            cl = paperLoader.get();
        }
        if (cl == null) {
            return null;
        }
        Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
        Method getWorlds = bukkit.getMethod("getWorlds");
        @SuppressWarnings("unchecked")
        java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
        if (worlds == null || worlds.isEmpty()) {
            return null;
        }
        Object world = worlds.get(0);
        Method getBlockAt = world.getClass().getMethod("getBlockAt", int.class, int.class, int.class);
        return getBlockAt.invoke(world, x, y, z);
    }

    private Class<?> materialClass() throws ClassNotFoundException {
        return Class.forName("org.bukkit.Material", true, paperLoader.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object materialValue(String name) {
        try {
            Class<?> mat = materialClass();
            Method match = mat.getMethod("matchMaterial", String.class);
            Object matched = match.invoke(null, name);
            if (matched != null) {
                return matched;
            }
            return Enum.valueOf((Class<? extends Enum>) mat, name);
        } catch (Exception e) {
            return null;
        }
    }

    private void runOnMain(Runnable task) {
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
            Object plugin = findAnyPlugin(bukkit);
            if (plugin == null) {
                // Fallback: run inline (may be wrong thread — still better than drop)
                task.run();
                return;
            }
            Method runTask = scheduler.getClass().getMethod("runTask",
                    Class.forName("org.bukkit.plugin.Plugin", true, cl), Runnable.class);
            runTask.invoke(scheduler, plugin, task);
        } catch (Exception e) {
            LOG.log(Level.FINE, "schedule Paper sync failed; running inline", e);
            try {
                task.run();
            } catch (Exception ex) {
                LOG.log(Level.FINE, "inline Paper sync failed", ex);
            }
        }
    }

    /** Paper overworld spawn (block coords), or null if unavailable. */
    public double[] spawnPosition() {
        if (!isEnabled()) {
            return null;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return null;
            }
            Object world = worlds.get(0);
            Object spawn = world.getClass().getMethod("getSpawnLocation").invoke(world);
            double x = ((Number) spawn.getClass().getMethod("getX").invoke(spawn)).doubleValue();
            double y = ((Number) spawn.getClass().getMethod("getY").invoke(spawn)).doubleValue();
            double z = ((Number) spawn.getClass().getMethod("getZ").invoke(spawn)).doubleValue();
            return new double[]{x, y, z};
        } catch (Exception e) {
            LOG.log(Level.FINE, "Paper spawn lookup failed", e);
            return null;
        }
    }

    /** Highest solid block Y at chunk center, or -1. */
    public int sampleGroundY(int chunkX, int chunkZ) {
        if (!isEnabled()) {
            return -1;
        }
        try {
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return -1;
            }
            Object world = worlds.get(0);
            Method highest = world.getClass().getMethod("getHighestBlockYAt", int.class, int.class);
            return ((Number) highest.invoke(world, x, z)).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    public record OnlinePlayer(String name, java.util.UUID uuid, double x, double y, double z) {
    }

    public record NearbyLiving(String name, java.util.UUID uuid, String entityType,
                               double x, double y, double z) {
    }

    /** Nearby non-player living entities around a point (for BE entity mirror + combat). */
    public java.util.List<NearbyLiving> listNearbyLiving(double x, double y, double z, double radius) {
        java.util.List<NearbyLiving> out = new java.util.ArrayList<>();
        if (!isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Method getWorlds = bukkit.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds == null || worlds.isEmpty()) {
                return out;
            }
            Object world = worlds.get(0);
            Class<?> locCl = Class.forName("org.bukkit.Location", true, cl);
            Object loc = locCl.getConstructor(
                    Class.forName("org.bukkit.World", true, cl),
                    double.class, double.class, double.class)
                    .newInstance(world, x, y, z);
            Class<?> living = Class.forName("org.bukkit.entity.LivingEntity", true, cl);
            Class<?> playerCl = Class.forName("org.bukkit.entity.Player", true, cl);
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> nearby = (java.util.Collection<Object>) world.getClass()
                    .getMethod("getNearbyEntities", locCl, double.class, double.class, double.class)
                    .invoke(world, loc, radius, radius, radius);
            for (Object e : nearby) {
                if (e == null || !living.isInstance(e) || playerCl.isInstance(e)) {
                    continue;
                }
                java.util.UUID uuid = (java.util.UUID) e.getClass().getMethod("getUniqueId").invoke(e);
                String name = String.valueOf(e.getClass().getMethod("getName").invoke(e));
                String type = e.getClass().getSimpleName();
                try {
                    Object et = e.getClass().getMethod("getType").invoke(e);
                    if (et != null) {
                        type = "minecraft:" + String.valueOf(et).toLowerCase(java.util.Locale.ROOT);
                    }
                } catch (Exception ignored) {
                }
                Object el = e.getClass().getMethod("getLocation").invoke(e);
                double ex = ((Number) el.getClass().getMethod("getX").invoke(el)).doubleValue();
                double ey = ((Number) el.getClass().getMethod("getY").invoke(el)).doubleValue();
                double ez = ((Number) el.getClass().getMethod("getZ").invoke(el)).doubleValue();
                out.add(new NearbyLiving(name, uuid, type, ex, ey, ez));
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "listNearbyLiving failed", e);
        }
        return out;
    }

    /** Snapshot of Paper online players (JE) for BE entity mirror. */
    public java.util.List<OnlinePlayer> listOnlinePlayers() {
        java.util.List<OnlinePlayer> out = new java.util.ArrayList<>();
        if (!isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object coll = bukkit.getMethod("getOnlinePlayers").invoke(null);
            if (coll instanceof java.util.Collection<?> c) {
                for (Object p : c) {
                    out.add(readPlayer(p));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "listOnlinePlayers failed", e);
        }
        return out;
    }

    private OnlinePlayer readPlayer(Object p) throws ReflectiveOperationException {
        String name = (String) p.getClass().getMethod("getName").invoke(p);
        java.util.UUID uuid = (java.util.UUID) p.getClass().getMethod("getUniqueId").invoke(p);
        Object loc = p.getClass().getMethod("getLocation").invoke(p);
        double x = ((Number) loc.getClass().getMethod("getX").invoke(loc)).doubleValue();
        double y = ((Number) loc.getClass().getMethod("getY").invoke(loc)).doubleValue();
        double z = ((Number) loc.getClass().getMethod("getZ").invoke(loc)).doubleValue();
        return new OnlinePlayer(name, uuid, x, y, z);
    }

    private int[][] readColumnHashedStates(int chunkX, int chunkZ) throws ReflectiveOperationException {
        final int sections = 24;
        final int minY = -64;
        int[][] out = new int[sections][4096];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int s = 0; s < sections; s++) {
            int y0 = minY + s * 16;
            boolean anyNonAir = false;
            for (int ly = 0; ly < 16; ly++) {
                int y = y0 + ly;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int state = materialToHashedState(blockAt(baseX + x, y, baseZ + z));
                        out[s][(x << 8) | (z << 4) | ly] = state;
                        if (state != BedrockPacketCodec.hashedAir()) {
                            anyNonAir = true;
                        }
                    }
                }
            }
            if (!anyNonAir) {
                // leave as air (default 0 fill → remap)
                java.util.Arrays.fill(out[s], BedrockPacketCodec.hashedAir());
            }
        }
        return out;
    }

    private int materialToHashedState(Object block) throws ReflectiveOperationException {
        if (block == null) {
            return BedrockPacketCodec.hashedAir();
        }
        Object type = block.getClass().getMethod("getType").invoke(block);
        String material = type == null ? null : String.valueOf(type);
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            if (data != null) {
                Object asString = data.getClass().getMethod("getAsString").invoke(data);
                if (asString != null) {
                    return BedrockBlockRuntimeIds.hashedForJeBlockData(String.valueOf(asString), material);
                }
            }
        } catch (NoSuchMethodException ignored) {
            // older API — Material only
        }
        return BedrockBlockRuntimeIds.hashedForMaterial(material);
    }

    private static Object findAnyPlugin(Class<?> bukkit) {
        try {
            Object pm = bukkit.getMethod("getPluginManager").invoke(null);
            Object[] plugins = (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm);
            if (plugins != null && plugins.length > 0) {
                return plugins[0];
            }
        } catch (Exception ignored) {
            // none
        }
        return null;
    }

    /**
     * Snapshot Bukkit player inventory as Bedrock item network ids (air=0).
     * Prefers live Paper player; falls back to P4.4 inventory vault for pure BE.
     */
    public int[] snapshotInventoryNetworkIds(String username, int slots) {
        int[] live = snapshotInventoryNetworkIdsLiveOnly(username, slots);
        if (live != null) {
            return live;
        }
        return inventoryInject.snapshotNetworkIds(username, slots);
    }

    /** Live Paper player only — null if not online (does not consult vault). */
    public int[] snapshotInventoryNetworkIdsLiveOnly(String username, int slots) {
        int[][] full = snapshotInventoryStacksLiveOnly(username, slots);
        return full == null ? null : full[0];
    }

    /**
     * Live Paper storage: {@code [0]=networkIds, [1]=counts}. Null if player offline.
     */
    public int[][] snapshotInventoryStacksLiveOnly(String username, int slots) {
        if (!isEnabled() || username == null || username.isBlank()) {
            return null;
        }
        try {
            ClassLoader cl = liveLoader();
            if (cl == null) {
                return null;
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, username);
            if (player == null) {
                return null;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            Object[] contents = (Object[]) inv.getClass().getMethod("getStorageContents").invoke(inv);
            int n = Math.max(0, slots);
            int[] ids = new int[n];
            int[] counts = new int[n];
            for (int i = 0; i < n; i++) {
                Object stack = contents != null && i < contents.length ? contents[i] : null;
                ids[i] = itemStackToNetworkId(stack);
                counts[i] = itemStackAmount(stack);
            }
            return new int[][]{ids, counts};
        } catch (Exception e) {
            LOG.log(Level.FINE, "inventory snapshot failed for " + username, e);
            return null;
        }
    }

    /**
     * Block inventory at world coords → {@code [0]=networkIds, [1]=counts} (padded to {@code slots}).
     */
    public int[][] snapshotBlockInventory(int x, int y, int z, int slots) {
        if (!isEnabled() || slots <= 0) {
            return null;
        }
        try {
            ClassLoader cl = liveLoader();
            if (cl == null) {
                return null;
            }
            Object block = blockAt(x, y, z);
            if (block == null) {
                return null;
            }
            Object state = block.getClass().getMethod("getState").invoke(block);
            if (state == null) {
                return null;
            }
            Object inv = state.getClass().getMethod("getInventory").invoke(state);
            if (inv == null) {
                return null;
            }
            Object[] contents = (Object[]) inv.getClass().getMethod("getContents").invoke(inv);
            int[] ids = new int[slots];
            int[] counts = new int[slots];
            for (int i = 0; i < slots; i++) {
                Object stack = contents != null && i < contents.length ? contents[i] : null;
                ids[i] = itemStackToNetworkId(stack);
                counts[i] = itemStackAmount(stack);
            }
            return new int[][]{ids, counts};
        } catch (Exception e) {
            LOG.log(Level.FINE, "block inventory snapshot failed @" + x + "," + y + "," + z, e);
            return null;
        }
    }

    /** Register Paper inventory vault for a pure-BE join. */
    public void injectBedrockPlayer(java.util.UUID uuid, String username) {
        inventoryInject.inject(uuid, username);
    }

    public void ejectBedrockPlayer(String username) {
        inventoryInject.eject(username);
    }

    public boolean clearInventory(String username) {
        inventoryInject.clear(username);
        boolean live = runPlayerInv(username, (player, inv, cl) -> {
            inv.getClass().getMethod("clear").invoke(inv);
            return true;
        });
        return live || inventoryInject.has(username);
    }

    public boolean giveItem(String username, String materialName, int amount) {
        if (amount <= 0) {
            return false;
        }
        inventoryInject.give(username, materialName, amount);
        boolean live = runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
            if (mat == null) {
                mat = matCl.getMethod("valueOf", String.class).invoke(null,
                        materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            }
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            Object[] arr = (Object[]) java.lang.reflect.Array.newInstance(stackCl, 1);
            arr[0] = stack;
            inv.getClass().getMethod("addItem", arr.getClass()).invoke(inv, (Object) arr);
            return true;
        });
        return live || inventoryInject.has(username);
    }

    public boolean setStorageSlot(String username, int slot, String materialName, int amount) {
        if (slot < 0 || slot >= 36) {
            return false;
        }
        inventoryInject.setSlot(username, slot, materialName, amount);
        boolean live = runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack;
            if (amount <= 0 || "AIR".equalsIgnoreCase(materialName)) {
                stack = null;
            } else {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod("setItem", int.class, stackCl).invoke(inv, slot, stack);
            return true;
        });
        return live || inventoryInject.has(username);
    }

    public boolean setHeldItemSlot(String username, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        inventoryInject.setHeld(username, hotbarSlot);
        boolean live = runPlayerInv(username, (player, inv, cl) -> {
            inv.getClass().getMethod("setHeldItemSlot", int.class).invoke(inv, hotbarSlot);
            return true;
        });
        return live || inventoryInject.has(username);
    }

    public boolean setOffhand(String username, String materialName, int amount) {
        return runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod("setItemInOffHand", stackCl).invoke(inv, stack);
            return true;
        });
    }

    /** Armor index: 0=helmet, 1=chest, 2=legs, 3=boots. */
    public boolean setArmorSlot(String username, int armorIndex, String materialName, int amount) {
        if (armorIndex < 0 || armorIndex > 3) {
            return false;
        }
        String[] methods = {"setHelmet", "setChestplate", "setLeggings", "setBoots"};
        return runPlayerInv(username, (player, inv, cl) -> {
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            inv.getClass().getMethod(methods[armorIndex], stackCl).invoke(inv, stack);
            return true;
        });
    }

    /** Best-effort craft matrix slot on open workbench (top inventory). */
    public boolean setCraftSlot(String username, int slot, String materialName, int amount) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        return runPlayerInv(username, (player, inv, cl) -> {
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return false;
            }
            Object top;
            try {
                top = open.getClass().getMethod("getTopInventory").invoke(open);
            } catch (NoSuchMethodException e) {
                return false;
            }
            if (top == null) {
                return false;
            }
            int size = ((Number) top.getClass().getMethod("getSize").invoke(top)).intValue();
            if (slot >= size) {
                return false;
            }
            Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
            Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
            Object stack = null;
            if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                if (mat == null) {
                    mat = matCl.getMethod("valueOf", String.class).invoke(null,
                            materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                }
                stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
            }
            top.getClass().getMethod("setItem", int.class, stackCl).invoke(top, slot, stack);
            return true;
        });
    }

    /** Open merchant UI for nearest villager / named entity when possible. */
    public boolean openMerchant(String playerName, String villagerNameHint) {
        if (!isEnabled() || playerName == null) {
            return false;
        }
        try {
            runOnMain(() -> {
                try {
                    ClassLoader cl = liveLoader();
                    Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
                    Object player = findPlayer(bukkit, playerName);
                    if (player == null) {
                        return;
                    }
                    Object loc = player.getClass().getMethod("getLocation").invoke(player);
                    Object world = loc.getClass().getMethod("getWorld").invoke(loc);
                    if (world == null) {
                        return;
                    }
                    Class<?> villagerCl = Class.forName("org.bukkit.entity.Villager", true, cl);
                    @SuppressWarnings("unchecked")
                    java.util.Collection<Object> nearby = (java.util.Collection<Object>) world.getClass()
                            .getMethod("getNearbyEntities",
                                    loc.getClass(), double.class, double.class, double.class)
                            .invoke(world, loc, 6.0, 4.0, 6.0);
                    Object best = null;
                    for (Object e : nearby) {
                        if (e == null || !villagerCl.isInstance(e)) {
                            continue;
                        }
                        if (villagerNameHint != null && !villagerNameHint.isBlank()) {
                            String n = String.valueOf(e.getClass().getMethod("getName").invoke(e));
                            if (n != null && n.toLowerCase(java.util.Locale.ROOT)
                                    .contains(villagerNameHint.toLowerCase(java.util.Locale.ROOT))) {
                                best = e;
                                break;
                            }
                        }
                        if (best == null) {
                            best = e;
                        }
                    }
                    if (best == null) {
                        return;
                    }
                    try {
                        player.getClass().getMethod("openMerchant",
                                        Class.forName("org.bukkit.inventory.Merchant", true, cl),
                                        boolean.class)
                                .invoke(player, best, true);
                    } catch (NoSuchMethodException e) {
                        // Merchant is Villager in some API versions
                        player.getClass().getMethod("openInventory",
                                        Class.forName("org.bukkit.inventory.InventoryView", true, cl))
                                .invoke(player, best.getClass().getMethod("getInventory").invoke(best));
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "openMerchant failed", e);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Snapshot merchant offers as [buyA_id, buyA_count, buyB_id, buyB_count, sell_id, sell_count]×N. */
    public java.util.List<int[]> snapshotMerchantOffers(String playerName, int max) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (!isEnabled()) {
            return out;
        }
        try {
            ClassLoader cl = liveLoader();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, playerName);
            if (player == null) {
                return out;
            }
            Object open = player.getClass().getMethod("getOpenInventory").invoke(player);
            if (open == null) {
                return out;
            }
            Object top = open.getClass().getMethod("getTopInventory").invoke(open);
            Object holder = top != null ? top.getClass().getMethod("getHolder").invoke(top) : null;
            Object merchant = holder;
            if (merchant == null || !Class.forName("org.bukkit.inventory.Merchant", true, cl).isInstance(merchant)) {
                return out;
            }
            @SuppressWarnings("unchecked")
            java.util.List<Object> recipes = (java.util.List<Object>) merchant.getClass()
                    .getMethod("getRecipes").invoke(merchant);
            if (recipes == null) {
                return out;
            }
            int n = Math.min(max, recipes.size());
            for (int i = 0; i < n; i++) {
                Object r = recipes.get(i);
                Object buy = r.getClass().getMethod("getIngredients").invoke(r);
                Object result = r.getClass().getMethod("getResult").invoke(r);
                int[] row = new int[6];
                if (buy instanceof java.util.List<?> list && !list.isEmpty()) {
                    fillStackIds(list.get(0), row, 0);
                    if (list.size() > 1) {
                        fillStackIds(list.get(1), row, 2);
                    }
                }
                fillStackIds(result, row, 4);
                out.add(row);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "snapshotMerchantOffers failed", e);
        }
        return out;
    }

    private void fillStackIds(Object stack, int[] row, int offset) throws ReflectiveOperationException {
        if (stack == null) {
            return;
        }
        Object type = stack.getClass().getMethod("getType").invoke(stack);
        int amount = ((Number) stack.getClass().getMethod("getAmount").invoke(stack)).intValue();
        String mat = type == null ? "air" : String.valueOf(type).toLowerCase(java.util.Locale.ROOT)
                .replace("minecraft:", "");
        int nid = 0;
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            String n = s.name().replace("minecraft:", "");
            if (n.equalsIgnoreCase(mat)) {
                nid = s.runtimeId() & 0xFFFF;
                break;
            }
        }
        row[offset] = nid;
        row[offset + 1] = amount;
    }

    public boolean setBlockInventorySlot(int x, int y, int z, int slot, String materialName, int amount) {
        if (!isEnabled() || slot < 0) {
            return false;
        }
        try {
            runOnMain(() -> {
                try {
                    Object block = blockAt(x, y, z);
                    if (block == null) {
                        return;
                    }
                    Object state = block.getClass().getMethod("getState").invoke(block);
                    if (state == null) {
                        return;
                    }
                    Object inv = state.getClass().getMethod("getInventory").invoke(state);
                    if (inv == null) {
                        return;
                    }
                    ClassLoader cl = liveLoader();
                    Class<?> matCl = Class.forName("org.bukkit.Material", true, cl);
                    Class<?> stackCl = Class.forName("org.bukkit.inventory.ItemStack", true, cl);
                    Object stack = null;
                    if (amount > 0 && materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                        Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, materialName);
                        if (mat == null) {
                            mat = matCl.getMethod("valueOf", String.class).invoke(null,
                                    materialName.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                        }
                        stack = stackCl.getConstructor(matCl, int.class).newInstance(mat, amount);
                    }
                    inv.getClass().getMethod("setItem", int.class, stackCl).invoke(inv, slot, stack);
                    try {
                        state.getClass().getMethod("update", boolean.class, boolean.class)
                                .invoke(state, true, false);
                    } catch (NoSuchMethodException ignored) {
                        state.getClass().getMethod("update").invoke(state);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "setBlockInventorySlot failed", e);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface InvOp {
        boolean run(Object player, Object inv, ClassLoader cl) throws Exception;
    }

    private boolean runPlayerInv(String username, InvOp op) {
        if (!isEnabled() || username == null || username.isBlank()) {
            return false;
        }
        try {
            ClassLoader cl = paperLoader.get();
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit", true, cl);
            Object player = findPlayer(bukkit, username);
            if (player == null) {
                return false;
            }
            Object inv = player.getClass().getMethod("getInventory").invoke(player);
            boolean primary = Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null));
            if (primary) {
                return op.run(player, inv, cl);
            }
            java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            Object server = bukkit.getMethod("getServer").invoke(null);
            Object plugin = findAnyPlugin(bukkit);
            Runnable r = () -> {
                try {
                    ok.set(op.run(player, inv, cl));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "inv op", e);
                } finally {
                    done.countDown();
                }
            };
            if (plugin != null) {
                Object scheduler = server.getClass().getMethod("getScheduler").invoke(server);
                Class<?> pluginCl = Class.forName("org.bukkit.plugin.Plugin", true, cl);
                scheduler.getClass().getMethod("runTask", pluginCl, Runnable.class)
                        .invoke(scheduler, plugin, r);
                done.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                r.run();
            }
            return ok.get();
        } catch (Exception e) {
            LOG.log(Level.FINE, "runPlayerInv " + username, e);
            return false;
        }
    }

    private static Object findPlayer(Class<?> bukkit, String username) throws Exception {
        Object player = bukkit.getMethod("getPlayerExact", String.class).invoke(null, username);
        if (player == null) {
            player = bukkit.getMethod("getPlayer", String.class).invoke(null, username);
        }
        return player;
    }

    private static int itemStackToNetworkId(Object stack) {
        if (stack == null) {
            return 0;
        }
        try {
            Object type = stack.getClass().getMethod("getType").invoke(stack);
            if (type == null) {
                return 0;
            }
            String name = String.valueOf(type);
            int matDot = name.lastIndexOf('.');
            if (matDot >= 0) {
                name = name.substring(matDot + 1);
            }
            String key = "minecraft:" + name.toLowerCase(java.util.Locale.ROOT);
            for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
                if (s.name().equals(key) || s.name().equalsIgnoreCase(key)) {
                    return s.runtimeId() & 0xFFFF;
                }
            }
            // Material AIR
            if ("AIR".equalsIgnoreCase(name) || "CAVE_AIR".equalsIgnoreCase(name)) {
                return 0;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int itemStackAmount(Object stack) {
        if (stack == null) {
            return 0;
        }
        try {
            Object amt = stack.getClass().getMethod("getAmount").invoke(stack);
            return amt instanceof Number n ? Math.max(0, n.intValue()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parse(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
