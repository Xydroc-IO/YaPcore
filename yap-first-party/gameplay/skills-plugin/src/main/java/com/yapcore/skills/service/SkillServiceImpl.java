package com.yapcore.skills.service;

import com.yapcore.mmo.CombatLevelCalculator;
import com.yapcore.mmo.PlayerOverall;
import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillFeedbackServices;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.XpSource;
import com.yapcore.mmo.XpTable;
import com.yapcore.mmo.event.SkillLevelUpEvent;
import com.yapcore.sched.YapSched;
import com.yapcore.skills.SkillsConfig;
import com.yapcore.skills.db.SkillRepository;
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
    private final XpTable overallXpTable;

    public SkillServiceImpl(
            JavaPlugin plugin,
            SkillsConfig config,
            SkillRepository repository,
            SkillPackLoader loader,
            XpTable xpTable,
            XpTable overallXpTable) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.loader = loader;
        this.xpTable = xpTable;
        this.overallXpTable = overallXpTable;
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
        if (amount <= 0) {
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

    @Override
    public XpTable overallXpTable() {
        return overallXpTable;
    }

    @Override
    public CompletableFuture<Double> combinedSkillXp(UUID playerId) {
        return getAll(playerId).thenApply(this::sumXpEnabled);
    }

    @Override
    public CompletableFuture<Double> overallXp(UUID playerId) {
        return overall(playerId).thenApply(PlayerOverall::xp);
    }

    @Override
    public CompletableFuture<Integer> overallLevel(UUID playerId) {
        return overall(playerId).thenApply(PlayerOverall::level);
    }

    @Override
    public CompletableFuture<PlayerOverall> overall(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.getOverall(playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> totalLevel(UUID playerId) {
        return getAll(playerId).thenApply(this::totalLevelOf);
    }

    public int totalLevelOf(Collection<SkillProgress> progress) {
        int total = 0;
        int present = 0;
        for (SkillProgress p : progress) {
            if (isEnabledSkill(p.skillId())) {
                total += p.level();
                present++;
            }
        }
        int missing = Math.max(0, enabledSkillCount() - present);
        return total + missing;
    }

    public double sumXpEnabled(Collection<SkillProgress> progress) {
        double sum = 0;
        for (SkillProgress p : progress) {
            if (isEnabledSkill(p.skillId())) {
                sum += p.xp();
            }
        }
        return sum;
    }

    private int enabledSkillCount() {
        int n = 0;
        for (SkillDefinition def : loader.skills().values()) {
            if (def.enabled()) {
                n++;
            }
        }
        return Math.max(1, n);
    }

    private boolean isEnabledSkill(SkillId id) {
        SkillDefinition def = loader.get(id);
        return def != null && def.enabled();
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

    public CompletableFuture<List<SkillRepository.LeaderboardEntry>> topOverall(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.topOverall(offset, pageSize);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "overall top", e);
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
            boolean skillMaxed = cur.level() >= xpTable.maxLevel();
            int oldLevel = cur.level();
            SkillProgress progress = cur;
            if (!skillMaxed) {
                double newXp = cur.xp() + amount;
                int newLevel = xpTable.levelForXp(newXp);
                if (newLevel > xpTable.maxLevel()) {
                    newLevel = xpTable.maxLevel();
                    newXp = xpTable.xpForLevel(newLevel);
                }
                progress = persist(playerId, skillId, newXp, newLevel, source, oldLevel);
            }
            // Overall keeps progressing from skill actions even after that skill is maxed.
            double overallGrant = amount * config.overallXpShare();
            if (skillMaxed) {
                // Full share still applies; optional bonus so maxed skills remain meaningful.
                overallGrant = amount * Math.max(config.overallXpShare(), config.overallMaxedXpShare());
            }
            grantOverallXp(playerId, overallGrant);
            return progress;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "skill addXp", e);
            throw new RuntimeException(e);
        }
    }

    private void grantOverallXp(UUID playerId, double amount) throws SQLException {
        if (amount <= 0) {
            return;
        }
        PlayerOverall cur = repository.getOverall(playerId);
        int oldLevel = cur.level();
        double newXp = cur.xp() + amount;
        int newLevel = overallXpTable.levelForXp(newXp);
        if (newLevel > overallXpTable.maxLevel()) {
            newLevel = overallXpTable.maxLevel();
            newXp = overallXpTable.xpForLevel(newLevel);
        }
        repository.upsertOverall(new PlayerOverall(playerId, newXp, newLevel));
        if (newLevel > oldLevel) {
            notifyOverallLevelUp(playerId, oldLevel, newLevel);
        }
    }

    private void notifyOverallLevelUp(UUID playerId, int oldLevel, int newLevel) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            if (config.levelUpChat()) {
                player.sendMessage("§aOverall level up! §7You are now overall level §e" + newLevel
                        + "§7/§e" + overallXpTable.maxLevel());
            }
            if (config.levelUpTitle()) {
                player.showTitle(Title.title(
                        Component.text("Overall Level Up!"),
                        Component.text(oldLevel + " → " + newLevel),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        });
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
        boolean skillMaxed;
        try {
            skillMaxed = repository.get(player.getUniqueId(), skillId)
                    .map(p -> p.level() >= xpTable.maxLevel())
                    .orElse(false);
        } catch (SQLException e) {
            skillMaxed = false;
        }
        String label;
        if (skillMaxed) {
            double overallAmount = amount * Math.max(config.overallXpShare(), config.overallMaxedXpShare());
            label = "+" + formatXp(overallAmount) + " Overall XP";
        } else {
            label = "+" + formatXp(amount) + " " + name + " XP";
        }
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
