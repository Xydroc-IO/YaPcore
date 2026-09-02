package com.yapcore.guard;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuardConfig {

    private final JavaPlugin plugin;

    private boolean flyEnabled = true;
    private double flySensitivity = 0.6;
    private boolean speedEnabled = true;
    private double speedSensitivity = 0.7;
    private double maxBlocksPerTick = 0.85;
    private boolean reachEnabled = true;
    private double reachSensitivity = 0.8;
    private double maxReachDistance = 3.5;
    private boolean scaffoldEnabled = true;
    private double scaffoldSensitivity = 0.7;
    private int maxPlacesPerSecond = 8;
    private boolean scaffoldRequireAirBelow = true;
    private int maxViolationsBeforeKick = 8;
    private int violationDecaySeconds = 45;
    private boolean alertsEnabled = true;
    private int joinGraceSeconds = 10;
    private int flyMinAirborneChecks = 4;

    public GuardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        flyEnabled = c.getBoolean("checks.fly.enabled", true);
        flySensitivity = clamp01(c.getDouble("checks.fly.sensitivity", flySensitivity));
        speedEnabled = c.getBoolean("checks.speed.enabled", true);
        speedSensitivity = clamp01(c.getDouble("checks.speed.sensitivity", speedSensitivity));
        maxBlocksPerTick = Math.max(0.1, c.getDouble("checks.speed.max-blocks-per-tick", maxBlocksPerTick));
        reachEnabled = c.getBoolean("checks.reach.enabled", true);
        reachSensitivity = clamp01(c.getDouble("checks.reach.sensitivity", reachSensitivity));
        maxReachDistance = Math.max(3.0, c.getDouble("checks.reach.max-distance", maxReachDistance));
        scaffoldEnabled = c.getBoolean("checks.scaffold.enabled", true);
        scaffoldSensitivity = clamp01(c.getDouble("checks.scaffold.sensitivity", scaffoldSensitivity));
        maxPlacesPerSecond = Math.max(1, c.getInt("checks.scaffold.max-places-per-second", maxPlacesPerSecond));
        scaffoldRequireAirBelow = c.getBoolean("checks.scaffold.require-air-below", true);
        maxViolationsBeforeKick = Math.max(1, c.getInt("max-violations-before-kick", maxViolationsBeforeKick));
        violationDecaySeconds = Math.max(5, c.getInt("violation-decay-seconds", violationDecaySeconds));
        alertsEnabled = c.getBoolean("alerts-enabled", true);
        joinGraceSeconds = Math.max(0, c.getInt("join-grace-seconds", joinGraceSeconds));
        flyMinAirborneChecks = Math.max(1, c.getInt("checks.fly.min-airborne-checks", flyMinAirborneChecks));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public boolean flyEnabled() {
        return flyEnabled;
    }

    public double flySensitivity() {
        return flySensitivity;
    }

    public boolean speedEnabled() {
        return speedEnabled;
    }

    public double speedSensitivity() {
        return speedSensitivity;
    }

    public double maxBlocksPerTick() {
        return maxBlocksPerTick;
    }

    public boolean reachEnabled() {
        return reachEnabled;
    }

    public double reachSensitivity() {
        return reachSensitivity;
    }

    public double maxReachDistance() {
        return maxReachDistance;
    }

    public boolean scaffoldEnabled() {
        return scaffoldEnabled;
    }

    public double scaffoldSensitivity() {
        return scaffoldSensitivity;
    }

    public int maxPlacesPerSecond() {
        return maxPlacesPerSecond;
    }

    public boolean scaffoldRequireAirBelow() {
        return scaffoldRequireAirBelow;
    }

    public int maxViolationsBeforeKick() {
        return maxViolationsBeforeKick;
    }

    public int violationDecaySeconds() {
        return violationDecaySeconds;
    }

    public boolean alertsEnabled() {
        return alertsEnabled;
    }

    public int joinGraceSeconds() {
        return joinGraceSeconds;
    }

    public int flyMinAirborneChecks() {
        return flyMinAirborneChecks;
    }

    public void setAlertsEnabled(boolean alertsEnabled) {
        this.alertsEnabled = alertsEnabled;
    }
}
