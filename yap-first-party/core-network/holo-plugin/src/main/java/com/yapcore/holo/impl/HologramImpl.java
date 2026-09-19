package com.yapcore.holo.impl;

import com.yapcore.holo.Hologram;
import com.yapcore.holo.HologramAttach;
import com.yapcore.holo.HologramClick;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HologramImpl implements Hologram {

    private final String id;
    private final HologramRuntime rt;
    private final JavaPlugin plugin;
    private final HologramPages pages;
    private final HologramView view;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final List<HologramClick> clicks = new CopyOnWriteArrayList<>();
    private volatile HologramAttach attach = HologramAttach.none();
    private volatile String seePermission = "";
    private volatile UUID cachedFollow;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private double viewDistance;
    private boolean spawned = true;

    public HologramImpl(String id, Location location, List<String> lines, double viewDistance,
                        HologramRuntime runtime) {
        this.id = id;
        this.rt = runtime;
        this.plugin = runtime.plugin;
        this.viewDistance = viewDistance;
        this.pages = new HologramPages(lines);
        this.view = new HologramView(runtime, pages, runtime.spacing);
        setLocation(location);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Location location() {
        World world = Bukkit.getWorld(worldName);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String worldName() {
        return worldName;
    }

    @Override
    public List<String> lines() {
        return pages.pageZero();
    }

    @Override
    public void setLines(List<String> lines) {
        pages.replacePageZero(lines);
        respawnViewers();
    }

    @Override
    public void setLine(int index, String text) {
        List<String> cur = new ArrayList<>(pages.pageZero());
        cur.set(index, text);
        setLines(cur);
    }

    @Override
    public void addLine(String text) {
        List<String> cur = new ArrayList<>(pages.pageZero());
        cur.add(text);
        setLines(cur);
    }

    @Override
    public void insertLine(int index, String text) {
        List<String> cur = new ArrayList<>(pages.pageZero());
        cur.add(index, text);
        setLines(cur);
    }

    @Override
    public void removeLine(int index) {
        List<String> cur = new ArrayList<>(pages.pageZero());
        cur.remove(index);
        setLines(cur);
    }

    @Override
    public List<List<String>> pages() {
        return pages.all();
    }

    @Override
    public void setPages(List<List<String>> next) {
        pages.setAll(next);
        respawnViewers();
    }

    @Override
    public int pageCount() {
        return pages.count();
    }

    @Override
    public int pageOf(Player player) {
        return player == null ? 0 : pages.pageOf(player.getUniqueId());
    }

    @Override
    public void setPage(Player player, int page) {
        if (player == null) {
            return;
        }
        pages.setPage(player.getUniqueId(), page);
        if (view.sees(player.getUniqueId())) {
            view.remove(player);
            view.spawn(player, location());
        }
    }

    @Override
    public void nextPage(Player player) {
        if (player == null) {
            return;
        }
        pages.next(player.getUniqueId());
        setPage(player, pages.pageOf(player.getUniqueId()));
    }

    @Override
    public void prevPage(Player player) {
        if (player == null) {
            return;
        }
        pages.prev(player.getUniqueId());
        setPage(player, pages.pageOf(player.getUniqueId()));
    }

    @Override
    public void teleport(Location location) {
        setLocation(location);
        for (Player player : view.viewers()) {
            view.teleport(player, this.location());
        }
    }

    @Override
    public void show(Player player) {
        if (player == null || !spawned || !rt.nms.ready()) {
            return;
        }
        hidden.remove(player.getUniqueId());
        view.spawn(player, location());
    }

    @Override
    public void hide(Player player) {
        if (player == null) {
            return;
        }
        hidden.add(player.getUniqueId());
        view.remove(player);
    }

    @Override
    public boolean visibleTo(Player player) {
        if (player == null || !spawned) {
            return false;
        }
        if (hidden.contains(player.getUniqueId())) {
            return false;
        }
        if (!seePermission.isBlank() && !player.hasPermission(seePermission)) {
            return false;
        }
        Location here = location();
        if (here.getWorld() == null || player.getWorld() == null || !here.getWorld().equals(player.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(here) <= viewDistance * viewDistance;
    }

    @Override
    public Set<UUID> hiddenPlayers() {
        return Set.copyOf(hidden);
    }

    @Override
    public double viewDistance() {
        return viewDistance;
    }

    @Override
    public void setViewDistance(double blocks) {
        this.viewDistance = Math.max(8.0, blocks);
    }

    @Override
    public void attach(HologramAttach attach) {
        this.attach = attach == null ? HologramAttach.none() : attach;
        this.cachedFollow = null;
    }

    @Override
    public HologramAttach attachment() {
        return attach;
    }

    @Override
    public void setClicks(List<HologramClick> next) {
        clicks.clear();
        if (next != null) {
            clicks.addAll(next);
        }
    }

    @Override
    public List<HologramClick> clicks() {
        return List.copyOf(clicks);
    }

    @Override
    public void handleClick(Player player, boolean left) {
        if (player == null || clicks.isEmpty()) {
            return;
        }
        HologramClick.Side side = left ? HologramClick.Side.LEFT : HologramClick.Side.RIGHT;
        for (HologramClick click : clicks) {
            if (click.side() == side) {
                runClick(player, click);
            }
        }
    }

    @Override
    public void setSeePermission(String permission) {
        this.seePermission = permission == null ? "" : permission.trim();
    }

    @Override
    public String seePermission() {
        return seePermission;
    }

    @Override
    public boolean matchesEntityId(int entityId) {
        return view.matchesEntityId(entityId);
    }

    @Override
    public void despawn() {
        spawned = false;
        view.despawnAll();
    }

    @Override
    public boolean spawned() {
        return spawned;
    }

    public void followTick() {
        if (attach.kind() == HologramAttach.Kind.NONE) {
            return;
        }
        Entity entity = cachedEntity();
        if (entity == null) {
            entity = HologramFollow.target(plugin, attach, location());
            cachedFollow = entity == null ? null : entity.getUniqueId();
        }
        if (entity == null || !entity.isValid()) {
            cachedFollow = null;
            return;
        }
        Location next = HologramFollow.followLocation(entity, attach.offsetY());
        if (next != null) {
            teleport(next);
        }
    }

    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        boolean should = visibleTo(player);
        boolean is = view.sees(player.getUniqueId());
        if (should && !is) {
            view.spawn(player, location());
        } else if (!should && is) {
            view.remove(player);
        } else if (should) {
            view.update(player, location());
        }
    }

    public void forget(UUID playerId) {
        view.forget(playerId);
        pages.forget(playerId);
    }

    public void applyWorldName(String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            this.worldName = worldName;
        }
    }

    public String clicksSerialized() {
        List<String> out = new ArrayList<>();
        for (HologramClick click : clicks) {
            out.add(click.serialize());
        }
        return String.join(" ", out);
    }

    public String pagesSerialized() {
        return pages.serializePages();
    }

    public void addPage() {
        pages.addPage();
        respawnViewers();
    }

    private Entity cachedEntity() {
        if (cachedFollow == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(cachedFollow);
        return entity != null && entity.isValid() ? entity : null;
    }

    private void runClick(Player player, HologramClick click) {
        String value = rt.placeholders.apply(player, click.value());
        switch (click.action()) {
            case NEXT -> nextPage(player);
            case PREV -> prevPage(player);
            case PAGE -> {
                try {
                    setPage(player, Integer.parseInt(value.trim()) - 1);
                } catch (NumberFormatException ignored) {
                    nextPage(player);
                }
            }
            case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
            case PLAYER, COMMAND -> player.performCommand(stripSlash(value));
        }
    }

    private static String stripSlash(String command) {
        if (command == null) {
            return "";
        }
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private void respawnViewers() {
        view.rebuildSlots();
        List<Player> viewers = view.viewers();
        view.despawnAll();
        for (Player player : viewers) {
            show(player);
        }
    }

    private void setLocation(Location location) {
        World world = location.getWorld();
        this.worldName = world == null ? "world" : world.getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
}
