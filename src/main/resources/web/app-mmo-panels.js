window.YapDashRegisterMmoPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  function setOut(text) {
    const el = $("mmoOut");
    if (el) el.textContent = text || "";
  }

  function setText(id, value) {
    const el = $(id);
    if (el) el.replaceChildren(document.createTextNode(value == null ? "—" : String(value)));
  }

  function yesNo(val) {
    return val === true || val === "true" ? "yes" : "no";
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

  function renderBarBindings(preview) {
    const tbody = $("mmoBarBody");
    const empty = $("mmoBarEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    const rows = Array.isArray(preview) ? preview : [];
    if (!rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${row.player || "—"}</td>`
        + `<td class="muted-small">${row.bindings || "—"}</td>`
        + `<td>${row.boundCount ?? "—"}</td>`;
      tbody.appendChild(tr);
    });
  }

  function renderAbilities(abilities) {
    const ab = abilities || {};
    const installed = ab.abilitiesInstalled === true || ab.abilitiesInstalled === "true";
    setText("mmoAbilitiesInstalled", installed ? "yap-abilities" : "—");
    setText("mmoAbilityCount", ab.abilityCount ?? 0);
    setText("mmoHotbarKeys", ab.hotbarKeys || "4-9");
    setText("mmoBarPlayers", ab.barBindingPlayers ?? 0);
    setText("mmoDualHotbar", yesNo(ab.dualHotbar));
    setText("mmoAbilityBook", yesNo(ab.abilityBookEnabled));
    setText("mmoShiftFBook", yesNo(ab.shiftFBook));
    setText("mmoOnlineBindings", ab.onlineWithBindings ?? "—");
    renderBarBindings(ab.barBindingPreview);
  }

  async function refreshMmoPanel() {
    try {
      const data = await api("/api/mmo");
      setText("mmoSkillsInstalled", data.installed ? "yap-skills" : "—");
      setText("mmoContentInstalled", data.contentInstalled ? "yap-mmo-content" : "—");
      setText("mmoSkillCount", data.skillCount ?? 0);
      setText("mmoBossCount", data.bossCount ?? 0);
      setText("mmoAreaCount", data.areaCount ?? 0);
      renderAbilities(data.abilities);
      const preview = (data.live && data.live.hiscorePreview) || data.hiscorePreview || {};
      renderHiscorePreview(preview);
      const kills = (data.live && data.live.bossKills) || data.bossKillTotals || {};
      renderBossKills(kills);
      setOut(data.error || "");
    } catch (e) {
      setOut(e.message);
    }
  }

  async function reloadAbilities() {
    try {
      const r = await netPost("/api/mmo", { action: "reload-abilities" });
      setOut(r.result || "Abilities reloaded.");
      await refreshMmoPanel();
    } catch (e) {
      setOut(e.message);
    }
  }

  $("mmoRefresh")?.addEventListener("click", refreshMmoPanel);
  $("mmoReloadAbilities")?.addEventListener("click", reloadAbilities);
  YapDash.tabLoads.mmo = refreshMmoPanel;
};
