package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.WorldServices;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.db.MemberRepository;
import com.yapcore.yapblock.gen.SchematicIslandPaster;
import com.yapcore.yapblock.grid.GridAllocator;
import com.yapcore.yapblock.grid.IslandGrid;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class IslandCreateOps {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandGrid grid;
    private final GridAllocator allocator;
    private final IslandIndex index;
    private final IslandRepository islands;
    private final MemberRepository members;
    private final IslandRoleCache roles;
    private final SchematicIslandPaster paster;

    public IslandCreateOps(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandGrid grid,
            GridAllocator allocator,
            IslandIndex index,
            IslandRepository islands,
            MemberRepository members,
            IslandRoleCache roles,
            SchematicIslandPaster paster) {
        this.plugin = plugin;
        this.config = config;
        this.grid = grid;
        this.allocator = allocator;
        this.index = index;
        this.islands = islands;
        this.members = members;
        this.roles = roles;
        this.paster = paster;
    }

    public CompletableFuture<Optional<IslandSnapshot>> create(Player player) {
        if (index.ofPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(Component.text("You already have an island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<IslandSnapshot>> future = new CompletableFuture<>();
        ensureWorld().thenAccept(worldOk -> {
            if (!Boolean.TRUE.equals(worldOk)) {
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Could not create skyblock world.", NamedTextColor.RED)));
                future.complete(Optional.empty());
                return;
            }
            YapSched.async(plugin, () -> {
                try {
                    int[] slot = allocator.allocate();
                    int wx = grid.worldX(slot[0]);
                    int wz = grid.worldZ(slot[1]);
                    int y = grid.pasteY();
                    double homeX = wx + 0.5;
                    double homeY = y + 1.0;
                    double homeZ = wz + 0.5;
                    String name = player.getName() + "'s Island";
                    IslandSnapshot snap = islands.insert(
                            player.getUniqueId(),
                            name,
                            slot[0],
                            slot[1],
                            homeX,
                            homeY,
                            homeZ,
                            config.defaultSizeRadius(),
                            config.defaultMaxMembers(),
                            config.defaultGenTier());
                    members.upsert(snap.id(), player.getUniqueId(), IslandRole.OWNER);
                    roles.put(snap.id(), player.getUniqueId(), IslandRole.OWNER);
                    index.put(snap);
                    index.bindMember(player.getUniqueId(), snap.id());

                    World world = Bukkit.getWorld(config.worldName());
                    if (world == null) {
                        future.complete(Optional.of(snap));
                        return;
                    }
                    paster.pasteIsland(world, wx, y, wz).whenComplete((v, err) -> {
                        if (err != null) {
                            plugin.getLogger().log(Level.WARNING, "Island paste issue", err);
                        }
                        LocationTeleport.teleport(plugin, player, new Location(world, homeX, homeY, homeZ), ok -> {
                            if (Boolean.TRUE.equals(ok)) {
                                player.sendMessage(Component.text("Island created! Welcome home.", NamedTextColor.GREEN));
                            } else {
                                player.sendMessage(Component.text("Island created, but teleport failed. Use /is home.",
                                        NamedTextColor.YELLOW));
                            }
                        });
                        future.complete(Optional.of(snap));
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Island create failed", e);
                    YapSched.entity(plugin, player, () ->
                            player.sendMessage(Component.text("Island creation failed.", NamedTextColor.RED)));
                    future.complete(Optional.empty());
                }
            });
        });
        return future;
    }

    public CompletableFuture<Boolean> ensureWorld() {
        World existing = Bukkit.getWorld(config.worldName());
        if (existing != null) {
            return CompletableFuture.completedFuture(true);
        }
        var mgr = WorldServices.worldManager();
        if (mgr.isPresent()) {
            WorldCreateOptions opts = WorldCreateOptions.builder()
                    .type("NORMAL")
                    .environment("NORMAL")
                    .generator("YaPblock")
                    .generateStructures(false)
                    .build();
            return mgr.get().createWorld(config.worldName(), opts);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.global(plugin, () -> {
            try {
                WorldCreator creator = new WorldCreator(config.worldName());
                creator.generator(plugin.getName());
                creator.generateStructures(false);
                World world = creator.createWorld();
                future.complete(world != null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Fallback world create failed", e);
                future.complete(false);
            }
        });
        return future;
    }
}
