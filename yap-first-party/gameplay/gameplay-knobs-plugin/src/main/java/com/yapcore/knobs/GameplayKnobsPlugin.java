package com.yapcore.knobs;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YaP gameplay + mob encyclopedia (Paper plugin, Folia-safe).
 * Original YaP implementation — not a Purpur port.
 */
public final class GameplayKnobsPlugin extends JavaPlugin {

    private KnobsConfig knobs;
    private YapTask ridableTask;
    private int handlersRegistered;

    @Override
    public void onEnable() {
        knobs = new KnobsConfig(this);
        knobs.reload();
        getServer().getPluginManager().registerEvents(new KnobsListener(this, knobs), this);
        getServer().getPluginManager().registerEvents(new BlockKnobsListener(knobs), this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this, knobs), this);
        getServer().getPluginManager().registerEvents(new MobSpecialsListener(knobs), this);
        handlersRegistered = 4;
        ridableTask = YapSched.globalTimer(this, this::tickRidables, 1L, 1L);
        applyServerBrand();
        EncyclopediaNms.syncFromConfig(knobs);
        EncyclopediaNms.warnIfMisconfigured(getLogger(), knobs);
        getLogger().info("YaP Encyclopedia online — mobs=" + knobs.mobs().size()
                + " specials=" + knobs.specialsWired()
                + " attrKeys=" + AttributeApplier.supportedAttributeKeys()
                + " nmsHooks=" + EncyclopediaNms.hooksPresent());
        getLogger().info("Edit plugins/YaPGameplayKnobs/knobs.yml · /yapknobs reload|status");
        getLogger().info("Crop/fluid NMS needs YaP-Folia patch 0025 — defaults off; see docs/ops/TUNE.md");
    }

    @Override
    public void onDisable() {
        if (ridableTask != null) {
            ridableTask.cancel();
            ridableTask = null;
        }
        EncyclopediaNms.clear();
    }

    private void tickRidables() {
        if (!knobs.enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof LivingEntity mount)) {
                continue;
            }
            KnobsConfig.MobKnobs mk = knobs.mob(mount.getType().name());
            if (mk != null && mk.ridable() && mk.controllable()) {
                YapSched.entity(this, mount, () -> RidableController.tickMount(mount, player, mk));
            }
        }
    }

    private void applyServerBrand() {
        String brand = knobs.serverModName();
        if (brand == null || brand.isBlank()) {
            return;
        }
        try {
            // Paper/Folia: Bukkit.getServer() brand via reflection when API present
            var server = Bukkit.getServer();
            try {
                var m = server.getClass().getMethod("setServerModName", String.class);
                m.invoke(server, brand);
            } catch (NoSuchMethodException ignored) {
                System.setProperty("yap.encyclopedia.server-mod-name", brand);
            }
        } catch (ReflectiveOperationException e) {
            System.setProperty("yap.encyclopedia.server-mod-name", brand);
            getLogger().fine("server-mod-name via system property: " + brand);
        }
    }

    void reapplyLoadedMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (living instanceof Player) {
                    continue;
                }
                KnobsConfig.MobKnobs mk = knobs.mob(living.getType().name());
                if (mk == null || !mk.enabled()) {
                    continue;
                }
                YapSched.entity(this, living, () -> KnobsListener.applyMob(living, mk));
            }
        }
    }

    public KnobsConfig knobs() {
        return knobs;
    }

    public int handlersRegistered() {
        return handlersRegistered;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapknobs")) {
            return false;
        }
        if (args.length == 0) {
            sender.sendMessage("YaP Encyclopedia — mobs=" + knobs.mobs().size()
                    + " enabled=" + knobs.enabled()
                    + " specials=" + knobs.specialsWired());
            sender.sendMessage("Usage: /yapknobs reload | status");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("yapknobs.reload")) {
                sender.sendMessage("No permission.");
                return true;
            }
            knobs.reload();
            applyServerBrand();
            EncyclopediaNms.syncFromConfig(knobs);
            EncyclopediaNms.warnIfMisconfigured(getLogger(), knobs);
            reapplyLoadedMobs();
            sender.sendMessage("Encyclopedia reloaded (" + knobs.mobs().size()
                    + " mobs, specials=" + knobs.specialsWired() + ") — re-applied to loaded entities.");
            sender.sendMessage("nmsHooks: " + EncyclopediaNms.statusLine());
            return true;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("enabled=" + knobs.enabled()
                    + " mobs=" + knobs.mobs().size()
                    + " specialsWired=" + knobs.specialsWired()
                    + " attrKeys=" + AttributeApplier.supportedAttributeKeys()
                    + " handlers=" + handlersRegistered
                    + " brand=" + knobs.serverModName());
            sender.sendMessage("gameplay: blindness×" + knobs.gameplay().entityBlindnessMultiplier()
                    + " cropMod=" + knobs.gameplay().cropGrowthModifier()
                    + " cropNms=" + knobs.gameplay().cropGrowthNms()
                    + " tickFluids=" + knobs.gameplay().tickFluids()
                    + " voidFix=" + knobs.gameplay().useVoidDamageFix()
                    + " netheriteFR=" + knobs.gameplay().netheriteFireResistance()
                    + " totemVoid=" + knobs.gameplay().totemWorksInVoid());
            sender.sendMessage("blocks: barrelRows=" + knobs.barrelRows()
                    + " beehiveMax=" + knobs.beehiveMaxBees()
                    + " lightningRodRange=" + knobs.lightningRodRange()
                    + " giveDropSuppress=" + knobs.disableGiveDropping()
                    + " projBypassGrief=" + knobs.projectilesBypassMobGriefing());
            sender.sendMessage("nmsHooks: " + EncyclopediaNms.statusLine());
            return true;
        }
        return false;
    }
}
