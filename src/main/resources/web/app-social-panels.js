window.YapDashRegisterSocialPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  function setOut(id, text, err) {
    const el = $(id);
    if (!el) return;
    el.textContent = text || "";
    el.className = "report muted-small" + (err ? " err" : "");
  }

  function setText(id, value) {
    const el = $(id);
    if (el) el.replaceChildren(document.createTextNode(value == null ? "—" : String(value)));
  }

  function yn(v) {
    return v === true || v === "true" ? "yes" : "no";
  }

  function checked(id, on) {
    const el = $(id);
    if (el) el.checked = !!on;
  }

  function num(id, value) {
    const el = $(id);
    if (el && value != null) el.value = value;
  }

  function text(id, value) {
    const el = $(id);
    if (el && value != null) el.value = value;
  }

  function paintPreview(tbodyId, emptyId, rows, cols) {
    const tbody = $(tbodyId);
    const empty = $(emptyId);
    if (!tbody) return;
    tbody.innerHTML = "";
    const list = Array.isArray(rows) ? rows : [];
    if (!list.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    list.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = cols.map((c) => `<td>${row[c] ?? "—"}</td>`).join("");
      tbody.appendChild(tr);
    });
  }

  function paintModes(selectId, modes) {
    const sel = $(selectId);
    if (!sel) return;
    const cur = sel.value;
    sel.innerHTML = "";
    (modes || []).forEach((m) => {
      const opt = document.createElement("option");
      opt.value = m;
      opt.textContent = m;
      sel.appendChild(opt);
    });
    if (cur && (modes || []).includes(cur)) sel.value = cur;
  }

  async function refreshFactions() {
    try {
      const d = await api("/api/factions");
      setText("facInstalled", d.installed ? "yap-factions" : "missing");
      setText("facCount", d.factions ?? 0);
      setText("facMembers", d.members ?? 0);
      setText("facClaims", d.claimOverlays ?? 0);
      setText("facAllies", d.alliances ?? 0);
      checked("facEnabled", d.enabled !== false);
      checked("facBank", d.bankEnabled !== false);
      checked("facAlliesBuild", d.alliesCanBuild !== false);
      checked("facEnemyPvp", d.enemyPvpOnly !== false);
      num("facBasePower", d.baseMaxPower ?? 50);
      num("facPowerMember", d.powerPerMember ?? 10);
      num("facClaimBlocks", d.claimBlocksPerPower ?? 100);
      paintPreview("facPreviewBody", "facPreviewEmpty", d.preview, ["name", "tag", "power", "maxPower"]);
      setOut("facOut", d.error || d.live || "");
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  }

  async function refreshGuilds() {
    try {
      const d = await api("/api/guilds");
      setText("gldInstalled", d.installed ? "yap-guilds" : "missing");
      setText("gldCount", d.guilds ?? 0);
      setText("gldMembers", d.members ?? 0);
      setText("gldAllies", d.alliances ?? 0);
      setText("gldInvites", d.invites ?? 0);
      checked("gldEnabled", d.enabled !== false);
      checked("gldBank", d.bankEnabled !== false);
      num("gldMaxLevel", d.maxLevel ?? 50);
      num("gldBaseMembers", d.baseMaxMembers ?? 5);
      paintPreview("gldPreviewBody", "gldPreviewEmpty", d.preview, ["name", "tag", "level", "xp"]);
      setOut("gldOut", d.error || d.live || "");
    } catch (e) {
      setOut("gldOut", e.message, true);
    }
  }

  async function refreshGames() {
    try {
      const d = await api("/api/games");
      setText("gmsInstalled", d.installed ? "yap-games" : "missing");
      setText("gmsModeCount", d.modeCount ?? 0);
      setText("gmsArenaCount", d.arenaCount ?? 0);
      setText("gmsEnabled", yn(d.enabled !== false));
      checked("gmsEnabledCb", d.enabled !== false);
      checked("gmsBlockSkill", d.blockSkillXp !== false);
      checked("gmsRewards", d.rewardsEnabled !== false);
      num("gmsCountdown", d.countdownSeconds ?? 10);
      text("gmsLobby", d.lobbyWorld || "world");
      paintModes("gmsForceMode", d.modes);
      const modesEl = $("gmsModesList");
      if (modesEl) modesEl.textContent = (d.modes || []).join(", ") || "—";
      const arenasEl = $("gmsArenasList");
      if (arenasEl) arenasEl.textContent = (d.arenas || []).join(", ") || "—";
      setOut("gmsOut", d.error || d.live || "");
    } catch (e) {
      setOut("gmsOut", e.message, true);
    }
  }

  $("facRefresh")?.addEventListener("click", refreshFactions);
  $("facReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", { action: "reload" });
      setOut("facOut", r.result || "Reloaded.");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSave")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "save-settings",
        enabled: $("facEnabled")?.checked ? "true" : "false",
        bankEnabled: $("facBank")?.checked ? "true" : "false",
        alliesCanBuild: $("facAlliesBuild")?.checked ? "true" : "false",
        enemyPvpOnly: $("facEnemyPvp")?.checked ? "true" : "false",
        baseMaxPower: $("facBasePower")?.value || "50",
        powerPerMember: $("facPowerMember")?.value || "10",
        claimBlocksPerPower: $("facClaimBlocks")?.value || "100",
      });
      setOut("facOut", r.reload || "Saved.");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSetPower")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "setpower",
        faction: $("facAdminName")?.value || "",
        power: $("facAdminPower")?.value || "0",
        max: $("facAdminMax")?.value || "",
      });
      setOut("facOut", r.result || r.command || "OK");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSetJoin")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "setjoin",
        faction: $("facAdminName")?.value || "",
        mode: $("facAdminJoin")?.value || "invite",
      });
      setOut("facOut", r.result || r.command || "OK");
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facDisband")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "disband",
        faction: $("facAdminName")?.value || "",
      });
      setOut("facOut", r.result || r.command || "OK");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });

  $("gldRefresh")?.addEventListener("click", refreshGuilds);
  $("gldReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/guilds", { action: "reload" });
      setOut("gldOut", r.result || "Reloaded.");
      await refreshGuilds();
    } catch (e) {
      setOut("gldOut", e.message, true);
    }
  });
  $("gldSave")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/guilds", {
        action: "save-settings",
        enabled: $("gldEnabled")?.checked ? "true" : "false",
        bankEnabled: $("gldBank")?.checked ? "true" : "false",
        maxLevel: $("gldMaxLevel")?.value || "50",
        baseMaxMembers: $("gldBaseMembers")?.value || "5",
      });
      setOut("gldOut", r.reload || "Saved.");
      await refreshGuilds();
    } catch (e) {
      setOut("gldOut", e.message, true);
    }
  });
  $("gldSetLevel")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/guilds", {
        action: "setlevel",
        guild: $("gldAdminName")?.value || "",
        level: $("gldAdminLevel")?.value || "1",
        xp: $("gldAdminXp")?.value || "",
      });
      setOut("gldOut", r.result || r.command || "OK");
      await refreshGuilds();
    } catch (e) {
      setOut("gldOut", e.message, true);
    }
  });
  $("gldDisband")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/guilds", {
        action: "disband",
        guild: $("gldAdminName")?.value || "",
      });
      setOut("gldOut", r.result || r.command || "OK");
      await refreshGuilds();
    } catch (e) {
      setOut("gldOut", e.message, true);
    }
  });

  $("gmsRefresh")?.addEventListener("click", refreshGames);
  $("gmsReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/games", { action: "reload" });
      setOut("gmsOut", r.result || "Reloaded.");
      await refreshGames();
    } catch (e) {
      setOut("gmsOut", e.message, true);
    }
  });
  $("gmsList")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/games", { action: "list" });
      setOut("gmsOut", r.result || "Listed.");
    } catch (e) {
      setOut("gmsOut", e.message, true);
    }
  });
  $("gmsSave")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/games", {
        action: "save-settings",
        enabled: $("gmsEnabledCb")?.checked ? "true" : "false",
        blockSkillXp: $("gmsBlockSkill")?.checked ? "true" : "false",
        rewardsEnabled: $("gmsRewards")?.checked ? "true" : "false",
        countdownSeconds: $("gmsCountdown")?.value || "10",
        lobbyWorld: $("gmsLobby")?.value || "world",
      });
      setOut("gmsOut", r.reload || "Saved.");
      await refreshGames();
    } catch (e) {
      setOut("gmsOut", e.message, true);
    }
  });
  $("gmsForceStart")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/games", {
        action: "forcestart",
        mode: $("gmsForceMode")?.value || "",
      });
      setOut("gmsOut", r.result || r.command || "OK");
    } catch (e) {
      setOut("gmsOut", e.message, true);
    }
  });

  Object.assign(YapDash.tabLoads, {
    factions: refreshFactions,
    guilds: refreshGuilds,
    games: refreshGames,
  });
};
