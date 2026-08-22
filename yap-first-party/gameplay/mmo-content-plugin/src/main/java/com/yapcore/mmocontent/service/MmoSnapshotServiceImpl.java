package com.yapcore.mmocontent.service;

import com.yapcore.mmo.HiscoreEntry;
import com.yapcore.mmo.MmoSnapshotService;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmocontent.MmoContentConfig;
import com.yapcore.mmocontent.area.SkillAreaLoader;
import com.yapcore.mmocontent.boss.BossPackLoader;
import com.yapcore.mmocontent.db.BossKillRepository;
import com.yapcore.mmocontent.db.HiscoreRepository;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MmoSnapshotServiceImpl implements MmoSnapshotService {

    private final MmoContentConfig config;
    private final HiscoreRepository hiscores;
    private final BossKillRepository bossKills;
    private final BossPackLoader bosses;
    private final SkillAreaLoader areas;

    public MmoSnapshotServiceImpl(MmoContentConfig config,
                                  HiscoreRepository hiscores,
                                  BossKillRepository bossKills,
                                  BossPackLoader bosses,
                                  SkillAreaLoader areas) {
        this.config = config;
        this.hiscores = hiscores;
        this.bossKills = bossKills;
        this.bosses = bosses;
        this.areas = areas;
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plugin", "yap-mmo-content");
        out.put("bossCount", bosses.bosses().size());
        out.put("areaCount", areas.areas().size());
        out.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        SkillServices.find().ifPresentOrElse(svc -> {
            out.put("skillCount", svc.definitions().size());
            List<Map<String, Object>> sample = new ArrayList<>();
            for (var player : Bukkit.getOnlinePlayers()) {
                if (sample.size() >= 5) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("player", player.getName());
                row.put("mining", levelOf(svc, player.getUniqueId(), "mining"));
                row.put("fishing", levelOf(svc, player.getUniqueId(), "fishing"));
                sample.add(row);
            }
            out.put("onlineSample", sample);
        }, () -> out.put("skillCount", 0));
        out.put("hiscorePreview", previewMap(SkillId.of("mining")));
        try {
            out.put("bossKills", bossKills.totalKillsByBoss());
        } catch (Exception e) {
            out.put("bossKills", Map.of());
        }
        return out;
    }

    @Override
    public List<HiscoreEntry> hiscorePreview(SkillId skillId, int limit) {
        try {
            return hiscores.top(skillId, limit, 0);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<HiscoreEntry> hiscorePage(SkillId skillId, int pageSize, int page) {
        try {
            int offset = Math.max(0, (Math.max(1, page) - 1) * Math.max(1, pageSize));
            return hiscores.top(skillId, Math.max(1, pageSize), offset);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> previewMap(SkillId skillId) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("skill", skillId.id());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HiscoreEntry entry : hiscorePreview(skillId, config.hiscorePreviewLimit())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", entry.rank());
            row.put("player", resolveName(entry.playerId(), entry.playerName()));
            row.put("level", entry.level());
            row.put("xp", entry.xp());
            rows.add(row);
        }
        preview.put("rows", rows);
        return preview;
    }

    private static int levelOf(SkillService svc, java.util.UUID uuid, String skill) {
        try {
            return svc.get(uuid, SkillId.of(skill)).join().level();
        } catch (Exception e) {
            return 1;
        }
    }

    private static String resolveName(java.util.UUID uuid, String fallback) {
        var offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : fallback;
    }
}
