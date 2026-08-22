window.YapDashRegisterNetworkPanels = function ({ $, api, netPost }) {
  function renderProtectLookup(rows) {
    const tbody = $("protLookupBody");
    const empty = $("protLookupEmpty");
    tbody.innerHTML = "";
    if (!rows || !rows.length) {
      empty.classList.remove("hidden");
      return;
    }
    empty.classList.add("hidden");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${row.id ?? ""}</td><td>${row.changeType ?? ""}</td><td>${row.actorName ?? ""}</td>`
        + `<td>${row.world ?? ""}</td><td>${row.x ?? ""},${row.y ?? ""},${row.z ?? ""}</td>`
        + `<td><span class="mono">${row.blockBefore ?? ""}</span> → <span class="mono">${row.blockAfter ?? ""}</span></td>`;
      tbody.appendChild(tr);
    });
  }

  async function refreshProtect() {
    try {
      const r = await api("/api/protect");
      $("protInstalled").textContent = r.installed ? "yes" : "no";
      $("protLogging").textContent = r.loggingEnabled ? "on" : "off";
      $("protPrune").textContent = String(r.pruneDays || "—");
      $("protOut").textContent = r.status || "";
    } catch (e) { $("protOut").textContent = e.message; }
  }
  $("protRefresh").onclick = () => refreshProtect();
  $("protReload").onclick = async () => { $("protOut").textContent = (await netPost("/api/protect", { action: "reload" })).result || "ok"; refreshProtect(); };
  $("protPruneBtn").onclick = async () => {
    if (!confirm("Prune old protect rows?")) return;
    $("protOut").textContent = (await netPost("/api/protect", { action: "prune", days: "30" })).result || "ok";
  };
  $("protLookupBtn").onclick = async () => {
    const p = $("protLookupPlayer").value.trim() || "Steve";
    try {
      const r = await netPost("/api/protect", { action: "lookup", player: p, limit: "25" });
      renderProtectLookup(r.lookupRows || []);
      $("protOut").textContent = r.result || (r.lookupRows?.length ? `${r.lookupRows.length} row(s)` : "No rows");
    } catch (e) {
      renderProtectLookup([]);
      $("protOut").textContent = e.message;
    }
  };

  async function refreshWorld() {
    try {
      const r = await api("/api/world");
      $("wldInstalled").textContent = r.installed ? "yes" : "no";
      $("wldSchems").textContent = String(r.schematicCount ?? "—");
      $("wldBrush").textContent = String(r.brushMaxRadius ?? "—");
      $("wldOut").textContent = r.status || "";
    } catch (e) { $("wldOut").textContent = e.message; }
  }
  $("wldRefresh").onclick = () => refreshWorld();
  $("wldReload").onclick = async () => { $("wldOut").textContent = (await netPost("/api/world", { action: "reload" })).result || "ok"; refreshWorld(); };
  $("wldLoad").onclick = async () => {
    $("wldOut").textContent = (await netPost("/api/world", { action: "load", world: $("wldWorld").value.trim() })).result || "";
  };
  $("wldUnload").onclick = async () => {
    $("wldOut").textContent = (await netPost("/api/world", { action: "unload", world: $("wldWorld").value.trim() })).result || "";
  };

  async function refreshChat() {
    try {
      const r = await api("/api/chat");
      $("chtInstalled").textContent = r.installed ? "yes" : "no";
      $("chtDefault").textContent = r.defaultChannel || "—";
      $("chtSlow").textContent = String(r.slowModeSeconds ?? 0) + "s";
      $("chtRelay").textContent = r.networkEnabled ? "on" : "off";
      $("chtOut").textContent = (r.channels || []).join(", ");
    } catch (e) { $("chtOut").textContent = e.message; }
  }
  $("chtRefresh").onclick = () => refreshChat();
  $("chtReload").onclick = async () => { $("chtOut").textContent = (await netPost("/api/chat", { action: "reload" })).result || "ok"; refreshChat(); };
  $("chtClear").onclick = async () => { $("chtOut").textContent = (await netPost("/api/chat", { action: "clearchat" })).result || "ok"; };

  async function refreshMod() {
    try {
      const r = await api("/api/moderation");
      $("modInstalled").textContent = r.installed ? "yes" : "no";
      $("modServer").textContent = r.serverId || "—";
      $("modOut").textContent = r.hint || "";
    } catch (e) { $("modOut").textContent = e.message; }
  }
  $("modRefresh").onclick = () => refreshMod();
  $("modReload").onclick = async () => { $("modOut").textContent = (await netPost("/api/moderation", { action: "reload" })).result || "ok"; };
  $("modHistory").onclick = async () => {
    $("modOut").textContent = (await netPost("/api/moderation", { action: "history", player: $("modPlayer").value.trim() || "Steve" })).result || "";
  };
  $("modUnban").onclick = async () => {
    $("modOut").textContent = (await netPost("/api/moderation", { action: "unban", player: $("modPlayer").value.trim() })).result || "";
  };

  async function refreshPerms() {
    try {
      const r = await api("/api/perms");
      $("prmInstalled").textContent = r.installed ? "yes" : "no";
      $("prmDefault").textContent = r.defaultGroup || "—";
      $("prmTrack").textContent = r.defaultTrack || "—";
      $("prmGroups").textContent = "Groups: " + (r.groups || []).join(" → ");
    } catch (e) { $("prmOut").textContent = e.message; }
  }
  $("prmRefresh").onclick = () => refreshPerms();
  $("prmReload").onclick = async () => { $("prmOut").textContent = (await netPost("/api/perms", { action: "reload" })).result || "ok"; refreshPerms(); };
  $("prmApplypack").onclick = async () => { $("prmOut").textContent = (await netPost("/api/perms", { action: "applypack" })).result || "ok"; };

  function renderDataFeatures(features) {
    const grid = $("datFeatures");
    grid.innerHTML = "";
    Object.keys(features || {}).sort().forEach((key) => {
      const lab = document.createElement("label");
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!features[key];
      cb.onchange = () => netPost("/api/playerdata", { action: "set-feature", feature: key, enabled: cb.checked ? "true" : "false" }).then(refreshData).catch((e) => alert(e.message));
      lab.appendChild(cb);
      lab.appendChild(document.createTextNode(key));
      grid.appendChild(lab);
    });
  }
  async function refreshData() {
    try {
      const r = await api("/api/playerdata");
      $("datInstalled").textContent = r.installed ? "yes" : "no";
      $("datEco").textContent = r.economyEnabled ? "on" : "off";
      $("datAuth").textContent = r.authEnabled ? "on" : "off";
      renderDataFeatures(r.features || {});
      $("datOut").textContent = r.status || "";
    } catch (e) { $("datOut").textContent = e.message; }
  }
  $("datRefresh").onclick = () => refreshData();
  $("datReload").onclick = async () => { $("datOut").textContent = (await netPost("/api/playerdata", { action: "reload" })).result || "ok"; refreshData(); };
  $("datSave").onclick = async () => { $("datOut").textContent = (await netPost("/api/playerdata", { action: "save" })).result || "ok"; };

  async function refreshDiscord() {
    try {
      const r = await api("/api/discord");
      $("dscInstalled").textContent = r.installed ? "yes" : "no";
      $("dscModHook").textContent = r.moderationConfigured ? "yes" : "no";
      $("dscChatHook").textContent = r.chatConfigured ? "yes" : "no";
      $("dscRelay").textContent = r.mcToDiscord ? "on" : "off";
      $("dscInbound").textContent = r.discordToMc ? "on" : "off";
      $("dscMcRelay").value = r.mcToDiscord ? "true" : "false";
      $("dscDiscordMc").value = r.discordToMc ? "true" : "false";
      $("dscOut").textContent = r.hint || "";
    } catch (e) { $("dscOut").textContent = e.message; }
  }
  $("dscRefresh").onclick = () => refreshDiscord();
  $("dscReload").onclick = async () => { $("dscOut").textContent = (await netPost("/api/discord", { action: "reload" })).result || "ok"; };
  $("dscSaveMod").onclick = async () => {
    await netPost("/api/discord", { action: "save-webhook", key: "moderation", url: $("dscModUrl").value.trim() });
    refreshDiscord();
  };
  $("dscSaveChat").onclick = async () => {
    await netPost("/api/discord", { action: "save-webhook", key: "chat", url: $("dscChatUrl").value.trim() });
    refreshDiscord();
  };
  $("dscTestMod").onclick = async () => {
    $("dscOut").textContent = (await netPost("/api/discord", { action: "test-webhook", key: "moderation" })).result || "sent";
  };
  $("dscTestChat").onclick = async () => {
    $("dscOut").textContent = (await netPost("/api/discord", { action: "test-webhook", key: "chat" })).result || "sent";
  };
  $("dscSaveRelay").onclick = async () => {
    await netPost("/api/discord", {
      action: "save-relay",
      mcToDiscord: $("dscMcRelay").value,
      discordToMc: $("dscDiscordMc").value,
    });
    refreshDiscord();
  };

  async function refreshTabPanel() {
    try {
      const r = await api("/api/tab");
      $("tabInstalled").textContent = r.installed ? "yes" : "no";
      $("tabSidebar").textContent = r.sidebarEnabled ? "on" : "off";
      $("tabNetworkSync").textContent = r.networkSyncEnabled ? "on" : "off";
      $("tabBossBar").textContent = r.bossBarEnabled ? "on" : "off";
      $("tabRefreshSec").textContent = String(r.refreshSeconds || "—") + "s";
      $("tabHeader").value = (r.header || []).join("\n");
      $("tabFooter").value = (r.footer || []).join("\n");
      $("tabSidebarLines").value = (r.sidebarLines || []).join("\n");
      $("tabBossTitle").value = r.bossBarTitle || "";
      $("tabBossSubtitle").value = r.bossBarSubtitle || "";
      $("tabBossEnabled").value = r.bossBarEnabled ? "true" : "false";
      $("tabOut").textContent = r.hint || "";
    } catch (e) { $("tabOut").textContent = e.message; }
  }
  $("tabPanelRefresh").onclick = () => refreshTabPanel();
  $("tabReload").onclick = async () => { $("tabOut").textContent = (await netPost("/api/tab", { action: "reload" })).result || "ok"; refreshTabPanel(); };
  $("tabSaveHeader").onclick = async () => {
    await netPost("/api/tab", { action: "save-header", text: $("tabHeader").value });
    refreshTabPanel();
  };
  $("tabSaveFooter").onclick = async () => {
    await netPost("/api/tab", { action: "save-footer", text: $("tabFooter").value });
    refreshTabPanel();
  };
  $("tabSaveSidebar").onclick = async () => {
    await netPost("/api/tab", { action: "save-sidebar", text: $("tabSidebarLines").value });
    refreshTabPanel();
  };
  $("tabSaveBoss").onclick = async () => {
    await netPost("/api/tab", {
      action: "save-bossbar",
      enabled: $("tabBossEnabled").value,
      title: $("tabBossTitle").value,
      subtitle: $("tabBossSubtitle").value,
    });
    refreshTabPanel();
  };

  async function refreshMap() {
    try {
      const r = await api("/api/map");
      $("mapInstalled").textContent = r.installed ? "yes" : "no";
      $("mapTiles").textContent = String(r.tileCount ?? 0);
      $("mapRender").textContent = String(r.renderIntervalMinutes ?? "—") + "m";
      $("mapWorlds").textContent = (r.worlds || []).join(", ") || "—";
      const url = r.mapUrl || "#";
      $("mapOpenLink").href = url;
      const frame = $("mapFrame");
      if (r.installed && url !== "#") {
        frame.src = url;
        frame.classList.remove("hidden");
      } else {
        frame.removeAttribute("src");
        frame.classList.add("hidden");
      }
      $("mapOut").textContent = r.hint || "";
    } catch (e) { $("mapOut").textContent = e.message; }
  }
  $("mapRefresh").onclick = () => refreshMap();
  $("mapReload").onclick = async () => { $("mapOut").textContent = (await netPost("/api/map", { action: "reload" })).result || "ok"; refreshMap(); };
  $("mapRenderBtn").onclick = async () => { $("mapOut").textContent = (await netPost("/api/map", { action: "render" })).result || "queued"; setTimeout(refreshMap, 2000); };

  async function refreshGuard() {
    try {
      const r = await api("/api/guard");
      $("grdInstalled").textContent = r.installed ? "yes" : "no";
      $("grdFly").textContent = r.flyEnabled ? "on" : "off";
      $("grdSpeed").textContent = r.speedEnabled ? "on" : "off";
      $("grdReach").textContent = r.reachEnabled ? "on" : "off";
      $("grdKick").textContent = String(r.maxViolationsBeforeKick ?? "—");
      $("grdAlerts").textContent = r.alertsEnabled ? "on" : "off";
      $("grdOut").textContent = r.status || r.hint || "";
    } catch (e) { $("grdOut").textContent = e.message; }
  }
  $("grdRefresh").onclick = () => refreshGuard();
  $("grdReload").onclick = async () => { $("grdOut").textContent = (await netPost("/api/guard", { action: "reload" })).result || "ok"; refreshGuard(); };
  $("grdAlertsOn").onclick = async () => { $("grdOut").textContent = (await netPost("/api/guard", { action: "alerts-on" })).result || "ok"; refreshGuard(); };
  $("grdAlertsOff").onclick = async () => { $("grdOut").textContent = (await netPost("/api/guard", { action: "alerts-off" })).result || "ok"; refreshGuard(); };
  $("grdPlayerBtn").onclick = async () => {
    const p = $("grdPlayer").value.trim();
    if (!p) return;
    $("grdOut").textContent = (await netPost("/api/guard", { action: "player-status", player: p })).result || "";
  };

  async function refreshRegions() {
    try {
      const r = await api("/api/regions");
      $("regInstalled").textContent = r.installed ? "yes" : "no";
      $("regServer").textContent = r.serverId || "—";
      $("regFlags").textContent = "Flags: " + (r.flags || []).join(", ");
      const ul = $("regList");
      ul.innerHTML = "";
      (r.regionLines || []).forEach((line) => {
        if (!line || line.startsWith("Admin regions")) return;
        const li = document.createElement("li");
        li.textContent = line.replace(/\u00a7./g, "");
        ul.appendChild(li);
      });
      $("regOut").textContent = r.status || "";
    } catch (e) { $("regOut").textContent = e.message; }
  }
  $("regRefresh").onclick = () => refreshRegions();

  async function refreshNpcs() {
    try {
      const r = await api("/api/npcs");
      $("npcInstalled").textContent = r.installed ? "yes" : "no";
      $("npcQuests").textContent = String(r.questPackCount ?? 0);
      $("npcServer").textContent = r.serverId || "—";
      $("npcOut").textContent = r.npcList || r.hint || "";
    } catch (e) { $("npcOut").textContent = e.message; }
  }
  $("npcRefresh").onclick = () => refreshNpcs();
  $("npcListBtn").onclick = async () => {
    $("npcOut").textContent = (await netPost("/api/npcs", { action: "list" })).result || "";
  };

  async function boot() {
    try {
      await refreshStatus();
      connectConsole();
      pollTimer = setInterval(() => refreshStatus().catch(() => {}), 2000);
      renderVehicles();
    } catch (e) {
      alert("Login failed: " + e.message);
      logout(true);
    }
  }

  if (token) {
    $("tokenInput").value = token;
    showApp();
  }


};
