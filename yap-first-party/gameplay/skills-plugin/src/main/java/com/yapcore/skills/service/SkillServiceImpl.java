package com.yapcore.skills.service;

import com.yapcore.mmo.CombatLevelCalculator;
import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillFeedbackServices;
import com.yapcore.mmo.SkillId;
import com.yapcore.skills.db.SkillRepository;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.XpTable;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsConfig;
import com.yapcore.skills.skill.SkillPackLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class SkillServiceImpl implements SkillService {

    private final JavaPlugin plugin;
    private final SkillsConfig config;
    private final SkillRepository repository;
    private final SkillPackLoader loader;
    private final XpTable xpTable;

    public SkillServiceImpl(
            JavaPlugin plugin,
            SkillsConfig config,
            SkillRepository repository,
            SkillPackLoader loader,
            XpTable xpTable) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.loader = loader;
        this.xpTable = xpTable;
    }

    @Override
    public CompletableFuture<SkillProgress> get(UUID playerId, SkillId skillId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.get(playerId, skillId)
                        .orElse(new SkillProgress(playerId, skillId, 0, 1));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Collection<SkillProgress>> getAll(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<SkillProgress> stored = repository.list(playerId);
                if (!stored.isEmpty()) {
                    return List.copyOf(stored);
                }
                List<SkillProgress> defaults = new ArrayList<>();
                for (SkillDefinition def : loader.skills().values()) {
                    if (def.enabled()) {
                        defaults.add(new SkillProgress(playerId, def.id(), 0, 1));
                    }
                }
                return List.copyOf(defaults);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<SkillProgress> addXp(UUID playerId, SkillId skillId, double amount, XpSource source) {
        if (amount <= 0 || com.yapcore.games.GameServices.suppressesSkillXp(playerId)) {
            return get(playerId, skillId);
        }
        return CompletableFuture.supplyAsync(() -> applyXp(playerId, skillId, amount, source));
    }

    @Override
    public CompletableFuture<SkillProgress> setLevel(UUID playerId, SkillId skillId, int level, XpSource source) {
        int clamped = Math.max(1, Math.min(xpTable.maxLevel(), level));
        double xp = xpTable.xpForLevel(clamped);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return persist(playerId, skillId, xp, clamped, source, clamped);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "skill setLevel", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public int levelForXp(SkillId skillId, double xp) {
        return xpTable.levelForXp(xp);
    }

    @Override
    public double xpForLevel(SkillId skillId, int level) {
        return xpTable.xpForLevel(level);
    }

    @Override
    public Optional<SkillDefinition> definition(SkillId skillId) {
        return Optional.ofNullable(loader.get(skillId));
    }

    @Override
    public Collection<SkillDefinition> definitions() {
        return loader.skills().values();
    }

    @Override
    public XpTable xpTable() {
        return xpTable;
    }

    public Optional<SkillDefinition.BreakAction> breakAction(SkillId skillId, org.bukkit.Material material) {
        SkillDefinition def = loader.get(skillId);
        if (def == null || !def.enabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(def.breakActions().get(material));
    }

    public CompletableFuture<List<SkillRepository.LeaderboardEntry>> topBySkill(
            SkillId skillId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.topBySkill(skillId, offset, pageSize);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "skill top", e);
                throw new RuntimeException(e);
            }
        });
    }

    public int combatLevel(UUID playerId) {
        try {
            int attack = levelOrDefault(playerId, SkillId.of(config.combatAttackSkill()));
            int strength = levelOrDefault(playerId, SkillId.of(config.combatStrengthSkill()));
            int defence = levelOrDefault(playerId, SkillId.of(config.combatDefenceSkill()));
            int hitpoints = levelOrDefault(playerId, SkillId.of(config.combatHitpointsSkill()));
            int prayer = levelOrDefault(playerId, SkillId.of("prayer"));
            int ranged = levelOrDefault(playerId, SkillId.of("ranged"));
            int magic = levelOrDefault(playerId, SkillId.of("magic"));
            return CombatLevelCalculator.calculate(
                    attack, strength, defence, hitpoints, prayer, ranged, magic);
        } catch (Exception e) {
            return 3;
        }
    }

    private int levelOrDefault(UUID playerId, SkillId skillId) {
        try {
            return get(playerId, skillId).orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).join().level();
        } catch (Exception e) {
            return 1;
        }
    }

    private SkillProgress applyXp(UUID playerId, SkillId skillId, double amount, XpSource source) {
        try {
            SkillProgress cur = repository.get(playerId, skillId)
                    .orElse(new SkillProgress(playerId, skillId, 0, 1));
            int oldLevel = cur.level();
            double newXp = cur.xp() + amount;
            int newLevel = xpTable.levelForXp(newXp);
            if (newLevel > xpTable.maxLevel()) {
                newLevel = xpTable.maxLevel();
                newXp = xpTable.xpForLevel(newLevel);
            }
            return persist(playerId, skillId, newXp, newLevel, source, oldLevel);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "skill addXp", e);
            throw new RuntimeException(e);
        }
    }

    private SkillProgress persist(
            UUID playerId,
            SkillId skillId,
            double xp,
            int level,
            XpSource source,
            int oldLevel) throws SQLException {
        SkillProgress progress = new SkillProgress(playerId, skillId, xp, level);
        repository.upsert(progress);
        if (level > oldLevel) {
            notifyLevelUp(playerId, skillId, oldLevel, level, xp, source);
        }
        return progress;
    }

    private void notifyLevelUp(
            UUID playerId,
            SkillId skillId,
            int oldLevel,
            int newLevel,
            double totalXp,
            XpSource source) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        SkillDefinition def = loader.get(skillId);
        String name = def == null ? skillId.id() : def.display();
        YapSched.entity(plugin, player, () -> {
            SkillLevelUpEvent event = new SkillLevelUpEvent(player, skillId, oldLevel, newLevel, totalXp, source);
            Bukkit.getPluginManager().callEvent(event);
            if (config.levelUpChat()) {
                player.sendMessage("§aLevel up! §f" + name + " §7is now level §e" + newLevel);
            }
            if (config.levelUpTitle()) {
                player.showTitle(Title.title(
                        Component.text("Level Up!"),
                        Component.text(name + " → " + newLevel),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        });
    }

    public void showXpGain(Player player, SkillId skillId, double amount) {
        if (!config.actionBarXp()) {
            return;
        }
        SkillDefinition def = loader.get(skillId);
        String name = def == null ? skillId.id() : def.display();
        String label = "+" + formatXp(amount) + " " + name + " XP";
        player.sendActionBar(Component.text(label));
        SkillFeedbackServices.find().ifPresent(bridge ->
                bridge.onXpGain(player, skillId, amount, label));
    }

    public static String formatXp(double xp) {
        if (xp >= 1000) {
            return String.format("%.1fk", xp / 1000.0);
        }
        if (xp == Math.floor(xp)) {
            return String.format("%.0f", xp);
        }
        return String.format("%.1f", xp);
    }
}
