package com.yapcore.holo;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Packet-only floating text / items (no world entity). */
public interface Hologram {

    String id();

    Location location();

    /** Page 0 serialized lines ({@code #ICON:}, {@code #ANIM:}, or text). */
    List<String> lines();

    void setLines(List<String> lines);

    void setLine(int index, String text);

    void addLine(String text);

    void insertLine(int index, String text);

    void removeLine(int index);

    List<List<String>> pages();

    void setPages(List<List<String>> pages);

    int pageCount();

    int pageOf(Player player);

    void setPage(Player player, int page);

    void nextPage(Player player);

    void prevPage(Player player);

    void teleport(Location location);

    void show(Player player);

    void hide(Player player);

    boolean visibleTo(Player player);

    Set<UUID> hiddenPlayers();

    double viewDistance();

    void setViewDistance(double blocks);

    void attach(HologramAttach attach);

    HologramAttach attachment();

    void setClicks(List<HologramClick> clicks);

    List<HologramClick> clicks();

    void handleClick(Player player, boolean left);

    void setSeePermission(String permission);

    String seePermission();

    boolean matchesEntityId(int entityId);

    void despawn();

    boolean spawned();
}
