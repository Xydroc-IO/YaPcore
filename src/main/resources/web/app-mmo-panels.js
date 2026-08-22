window.YapDashRegisterMmoPanels = function (YapDash) {
  const { $, api } = YapDash;

  function setOut(text) {
    const el = $("mmoOut");
    if (el) el.textContent = text || "";
  }

  function renderHiscorePreview(preview) {
    const tbody = $("mmoHiscoreBody");
    const empty = $("mmoHiscoreEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    const rows = preview && preview.rows ? preview.rows : [];
    if (!rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>#${row.rank || "—"}</td>`
        + `<td>${row.player || row.playerId || "—"}</td>`
        + `<td>${row.level ?? "—"}</td>`
        + `<td>${row.xp != null ? Math.floor(row.xp) : "—"}</td>`;
      tbody.appendChild(tr);
    });
    const skillEl = $("mmoHiscoreSkill");
    if (skillEl && preview.skill) skillEl.textContent = preview.skill;
  }

  function renderBossKills(totals) {
    const tbody = $("mmoBossBody");
    const empty = $("mmoBossEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    const entries = totals ? Object.entries(totals) : [];
    if (!entries.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    entries.forEach(([boss, kills]) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td><strong>${boss}</strong></td><td>${kills}</td>`;
      tbody.appendChild(tr);
    });
  }

  async function refreshMmoPanel() {
    try {
      const data = await api("/api/mmo");
      $("mmoSkillsInstalled")?.replaceChildren(document.createTextNode(data.installed ? "yap-skills" : "—"));
      $("mmoContentInstalled")?.replaceChildren(document.createTextNode(data.contentInstalled ? "yap-mmo-content" : "—"));
      $("mmoSkillCount")?.replaceChildren(document.createTextNode(String(data.skillCount ?? 0)));
      $("mmoBossCount")?.replaceChildren(document.createTextNode(String(data.bossCount ?? 0)));
      $("mmoAreaCount")?.replaceChildren(document.createTextNode(String(data.areaCount ?? 0)));
      const preview = (data.live && data.live.hiscorePreview) || data.hiscorePreview || {};
      renderHiscorePreview(preview);
      const kills = (data.live && data.live.bossKills) || data.bossKillTotals || {};
      renderBossKills(kills);
      setOut(data.error || "");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("mmoRefresh")?.addEventListener("click", refreshMmoPanel);
  YapDash.tabLoads.mmo = refreshMmoPanel;
};
