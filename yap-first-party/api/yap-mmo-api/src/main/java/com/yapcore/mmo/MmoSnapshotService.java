package com.yapcore.mmo;

import java.util.List;
import java.util.Map;

/** Read-only MMO dashboard snapshot (hiscores, boss kills, skill counts). */
public interface MmoSnapshotService {

    Map<String, Object> snapshot();

    List<HiscoreEntry> hiscorePreview(SkillId skillId, int limit);

    List<HiscoreEntry> hiscorePage(SkillId skillId, int pageSize, int page);
}
