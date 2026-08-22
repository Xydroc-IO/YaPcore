package com.yapcore.mechanics.physics;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PhysicsLoader {

    private static final Logger LOG = Logger.getLogger("YaPMechanics");

    private double fallMultiplier = 1.0;
    private double projectileMultiplier = 1.0;
    private double knockbackMultiplier = 1.0;
    private Set<Material> softLanding = EnumSet.noneOf(Material.class);

    public void load(Path file) {
        fallMultiplier = 1.0;
        projectileMultiplier = 1.0;
        knockbackMultiplier = 1.0;
        softLanding = EnumSet.noneOf(Material.class);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            fallMultiplier = yaml.getDouble("fall-damage-multiplier", 1.0);
            projectileMultiplier = yaml.getDouble("projectile-damage-multiplier", 1.0);
            knockbackMultiplier = yaml.getDouble("knockback-multiplier", 1.0);
            for (String raw : yaml.getStringList("soft-landing")) {
                Material mat = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
                if (mat != null) {
                    softLanding.add(mat);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load physics.yml", e);
        }
    }

    public double fallMultiplier() {
        return fallMultiplier;
    }

    public double projectileMultiplier() {
        return projectileMultiplier;
    }

    public double knockbackMultiplier() {
        return knockbackMultiplier;
    }

    public boolean isSoftLanding(Material material) {
        return softLanding.contains(material);
    }
}
