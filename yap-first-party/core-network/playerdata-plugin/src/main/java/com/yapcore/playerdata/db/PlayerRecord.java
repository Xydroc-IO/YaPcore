package com.yapcore.playerdata.db;

import java.util.UUID;

/**
 * Account + inventory-profile row used by sync.
 */
public final class PlayerRecord {

    private final UUID uuid;
    private String profile = "global";
    private String name;
    private double balance;
    private int xp;
    private int level;
    private double health;
    private int food;
    private float saturation;
    private byte[] inventory;
    private byte[] enderchest;
    private String lockServer;
    private java.sql.Timestamp lockUntil;

    public PlayerRecord(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String profile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double balance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public int xp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double health() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public int food() {
        return food;
    }

    public void setFood(int food) {
        this.food = food;
    }

    public float saturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public byte[] inventory() {
        return inventory;
    }

    public void setInventory(byte[] inventory) {
        this.inventory = inventory;
    }

    public byte[] enderchest() {
        return enderchest;
    }

    public void setEnderchest(byte[] enderchest) {
        this.enderchest = enderchest;
    }

    public String lockServer() {
        return lockServer;
    }

    public void setLockServer(String lockServer) {
        this.lockServer = lockServer;
    }

    public java.sql.Timestamp lockUntil() {
        return lockUntil;
    }

    public void setLockUntil(java.sql.Timestamp lockUntil) {
        this.lockUntil = lockUntil;
    }
}
