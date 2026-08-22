package com.yapcore.smoke;

import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal Paper plugin for compatibility smoke — must enable on <b>real</b> Paper
 * (CraftServer), not YaP facade stubs.
 */
public final class CompatSmokePlugin extends JavaPlugin {

    /** Marker service so we know ServicesManager is the real Paper one. */
    public interface SmokeMarker {
        String id();
    }

    @Override
    public void onEnable() {
        String serverClass = Bukkit.getServer().getClass().getName();
        getLogger().info("YaP-COMPAT-SMOKE enabled api=" + Bukkit.getVersion()
                + " bukkit=" + Bukkit.getBukkitVersion()
                + " serverClass=" + serverClass
                + " players=" + Bukkit.getOnlinePlayers().size());

        // Product path: real CraftBukkit — not YaPBukkitServer / stub Server
        if (!serverClass.contains("CraftServer") && !serverClass.contains("craftbukkit")) {
            throw new IllegalStateException(
                    "YaP-COMPAT-SMOKE expected real Paper CraftServer, got: " + serverClass);
        }
        getLogger().info("YaP-COMPAT-SMOKE craftserver-ok");

        // Common API surfaces plugins rely on
        YapSched.global(this, () ->
                getLogger().info("YaP-COMPAT-SMOKE scheduler-ok"));

        Bukkit.getPluginManager().registerEvents(new Listener() {
        }, this);
        getLogger().info("YaP-COMPAT-SMOKE event-register-ok");

        Bukkit.getServicesManager().register(
                SmokeMarker.class,
                () -> "yap-compat-smoke",
                this,
                ServicePriority.Normal);
        SmokeMarker marker = Bukkit.getServicesManager().load(SmokeMarker.class);
        if (marker == null || !"yap-compat-smoke".equals(marker.id())) {
            throw new IllegalStateException("YaP-COMPAT-SMOKE ServicesManager failed");
        }
        getLogger().info("YaP-COMPAT-SMOKE services-ok");

        ItemStack stack = new ItemStack(Material.DIAMOND);
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("YaP smoke"));
            stack.setItemMeta(meta);
        }
        var inv = Bukkit.createInventory(null, 9, Component.text("YaP smoke"));
        inv.setItem(0, stack);
        getLogger().info("YaP-COMPAT-SMOKE inventory-adventure-ok size=" + inv.getSize());

        getLogger().info("YaP-COMPAT-SMOKE paper-api-ok");
    }
}
