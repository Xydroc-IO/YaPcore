package com.yapcore.combat.projectile;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatProjectileKeys {

    public static final String NAMESPACE = "yapcombat";

    private final NamespacedKey shooter;
    private final NamespacedKey style;
    private final NamespacedKey power;
    private final NamespacedKey pierce;
    private final NamespacedKey launchY;
    private final NamespacedKey managed;

    public CombatProjectileKeys(JavaPlugin plugin) {
        this.shooter = new NamespacedKey(plugin, "proj_shooter");
        this.style = new NamespacedKey(plugin, "proj_style");
        this.power = new NamespacedKey(plugin, "proj_power");
        this.pierce = new NamespacedKey(plugin, "proj_pierce");
        this.launchY = new NamespacedKey(plugin, "proj_launch_y");
        this.managed = new NamespacedKey(plugin, "proj_managed");
    }

    public NamespacedKey shooter() {
        return shooter;
    }

    public NamespacedKey style() {
        return style;
    }

    public NamespacedKey power() {
        return power;
    }

    public NamespacedKey pierce() {
        return pierce;
    }

    public NamespacedKey launchY() {
        return launchY;
    }

    public NamespacedKey managed() {
        return managed;
    }
}
