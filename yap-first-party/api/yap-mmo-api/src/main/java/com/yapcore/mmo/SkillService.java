package com.yapcore.mmo;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkillService {

    CompletableFuture<SkillProgress> get(UUID playerId, SkillId skillId);

    CompletableFuture<Collection<SkillProgress>> getAll(UUID playerId);

    CompletableFuture<SkillProgress> addXp(UUID playerId, SkillId skillId, double amount, XpSource source);

    CompletableFuture<SkillProgress> setLevel(UUID playerId, SkillId skillId, int level, XpSource source);

    int levelForXp(SkillId skillId, double xp);

    double xpForLevel(SkillId skillId, int level);

    Optional<SkillDefinition> definition(SkillId skillId);

    Collection<SkillDefinition> definitions();

    XpTable xpTable();

    /** Sum of XP across enabled skills (display / total_level helpers). */
    CompletableFuture<Double> combinedSkillXp(UUID playerId);

    /** Stored overall XP (feeds the overall level curve). */
    CompletableFuture<Double> overallXp(UUID playerId);

    /** Stored overall level (1..overall max). */
    CompletableFuture<Integer> overallLevel(UUID playerId);

    CompletableFuture<PlayerOverall> overall(UUID playerId);

    /** Sum of enabled skill levels (e.g. 3 skills at 50 → 150). */
    CompletableFuture<Integer> totalLevel(UUID playerId);

    XpTable overallXpTable();
}
