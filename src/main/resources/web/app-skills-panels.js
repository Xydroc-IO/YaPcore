window.YapDashRegisterSkillsPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  function setOut(text) {
    const el = $("skillsOut");
    if (el) el.textContent = text || "";
  }

  function setText(id, value) {
    const el = $(id);
    if (el) el.replaceChildren(document.createTextNode(value == null ? "—" : String(value)));
  }

  function renderSkillsList(skills) {
    const tbody = $("skillsListBody");
    const empty = $("skillsListEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    const rows = Array.isArray(skills) ? skills : [];
    if (!rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      const enabled = row.enabled === true || row.enabled === "true" ? "yes" : "no";
      tr.innerHTML = `<td><strong>${row.id || "—"}</strong></td>`
        + `<td>${row.display || row.id || "—"}</td>`
        + `<td>${enabled}</td>`;
      tbody.appendChild(tr);
    });
  }

  function renderLeaderboard(preview) {
    const tbody = $("skillsLbBody");
    const empty = $("skillsLbEmpty");
    const skillEl = $("skillsLbSkill");
    if (!tbody) return;
    tbody.innerHTML = "";
    const map = preview && typeof preview === "object" ? preview : {};
    const skillIds = Object.keys(map);
    if (!skillIds.length) {
      empty?.classList.remove("hidden");
      if (skillEl) skillEl.textContent = "—";
      return;
    }
    const skillId = skillIds.includes("mining") ? "mining" : skillIds[0];
    if (skillEl) skillEl.textContent = skillId;
    const rows = map[skillId] || [];
    if (!rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((row, i) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>#${i + 1}</td>`
        + `<td>${row.player || row.playerId || "—"}</td>`
        + `<td>${row.level ?? "—"}</td>`
        + `<td>${row.xp != null ? Math.floor(row.xp) : "—"}</td>`;
      tbody.appendChild(tr);
    });
  }

  function renderOverallLeaderboard(rows) {
    const tbody = $("skillsOverallLbBody");
    const empty = $("skillsOverallLbEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    const list = Array.isArray(rows) ? rows : [];
    if (!list.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    list.forEach((row, i) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>#${i + 1}</td>`
        + `<td>${row.player || row.playerId || "—"}</td>`
        + `<td>${row.level ?? "—"}</td>`
        + `<td>${row.xp != null ? Math.floor(row.xp) : "—"}</td>`;
      tbody.appendChild(tr);
    });
  }

  async function refreshSkillsPanel() {
    try {
      const data = await api("/api/skills");
      setText("skillsInstalled", data.installed ? "yap-skills" : "—");
      setText("skillsEnabled", data.enabled === false ? "no" : (data.installed ? "yes" : "—"));
      setText("skillsCount", data.skillCount ?? 0);
      setText("skillsMaxLevel", data.maxLevel ?? "—");
      setText("skillsOverallMax", data.overallMaxLevel ?? "—");
      renderSkillsList(data.skills);
      renderLeaderboard(data.leaderboardPreview);
      renderOverallLeaderboard(data.overallLeaderboardPreview);
      setOut(data.error || "");
    } catch (e) {
      setOut(e.message);
    }
  }

  async function reloadSkills() {
    try {
      const r = await netPost("/api/skills", { action: "reload" });
      setOut(r.result || "Skills reloaded.");
      await refreshSkillsPanel();
    } catch (e) {
      setOut(e.message);
    }
  }

  $("skillsRefresh")?.addEventListener("click", refreshSkillsPanel);
  $("skillsReload")?.addEventListener("click", reloadSkills);
  YapDash.tabLoads.skills = refreshSkillsPanel;
};
