window.YapDashRegisterFullPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  function setOut(id, text) {
    const el = $(id);
    if (el) el.textContent = text || "";
  }

  function featGrid(containerId, features, onToggle) {
    const wrap = $(containerId);
    if (!wrap) return;
    wrap.innerHTML = "";
    Object.entries(features || {}).forEach(([key, on]) => {
      const lbl = document.createElement("label");
      lbl.innerHTML = `<input type="checkbox" ${on ? "checked" : ""}/> ${key}`;
      lbl.querySelector("input").onchange = (e) => onToggle(key, e.target.checked);
      wrap.appendChild(lbl);
    });
  }

  /* ——— Chat ——— */
  async function refreshChat() {
    try {
      const r = await api("/api/chat");
      $("chtInstalled").textContent = r.installed ? "yes" : "no";
      $("chtDefault").textContent = r.defaultChannel || "—";
      $("chtSlow").textContent = String(r.slowModeSeconds ?? 0) + "s";
      $("chtRelay").textContent = r.networkEnabled ? "on" : "off";
      if ($("chtDefaultSel")) {
        const ch = r.channels || ["global", "local", "staff"];
        $("chtDefaultSel").innerHTML = "";
        ch.forEach((c) => {
          const o = document.createElement("option");
          o.value = c; o.textContent = c;
          if (c === r.defaultChannel) o.selected = true;
          $("chtDefaultSel").appendChild(o);
        });
      }
      if ($("chtSlowInput")) $("chtSlowInput").value = r.slowModeSeconds ?? 0;
      if ($("chtFilterBox")) $("chtFilterBox").checked = !!r.filterEnabled;
      if ($("chtRelayBox")) $("chtRelayBox").checked = !!r.networkEnabled;
      $("chtChannels").textContent = (r.channels || []).join(", ") || "—";
      setOut("chtOut", "");
    } catch (e) { setOut("chtOut", e.message); }
  }
  $("chtSaveSettings")?.addEventListener("click", async () => {
    try {
      await netPost("/api/chat", {
        action: "save-settings",
        defaultChannel: $("chtDefaultSel")?.value,
        slowModeSeconds: $("chtSlowInput")?.value || "0",
        filterEnabled: $("chtFilterBox")?.checked ? "true" : "false",
        networkEnabled: $("chtRelayBox")?.checked ? "true" : "false",
      });
      setOut("chtOut", "Chat settings saved.");
      refreshChat();
    } catch (e) { setOut("chtOut", e.message); }
  });
  $("chtRefresh")?.addEventListener("click", () => refreshChat());
  $("chtReload")?.addEventListener("click", async () => {
    setOut("chtOut", (await netPost("/api/chat", { action: "reload" })).result || "ok");
    refreshChat();
  });
  $("chtClear")?.addEventListener("click", async () => {
    if (!confirm("Clear in-game chat for all players?")) return;
    setOut("chtOut", (await netPost("/api/chat", { action: "clearchat" })).result || "ok");
  });

  /* ——— Guard ——— */
  async function refreshGuard() {
    try {
      const r = await api("/api/guard");
      $("grdFly").textContent = r.flyEnabled ? "on" : "off";
      $("grdSpeed").textContent = r.speedEnabled ? "on" : "off";
      $("grdReach").textContent = r.reachEnabled ? "on" : "off";
      $("grdScaffold").textContent = r.scaffoldEnabled ? "on" : "off";
      $("grdKick").textContent = String(r.maxViolationsBeforeKick ?? "—");
      $("grdDecay").textContent = String(r.violationDecaySeconds ?? "—") + "s";
      $("grdAlerts").textContent = r.alertsEnabled ? "on" : "off";
      if ($("grdFlyBox")) $("grdFlyBox").checked = !!r.flyEnabled;
      if ($("grdSpeedBox")) $("grdSpeedBox").checked = !!r.speedEnabled;
      if ($("grdReachBox")) $("grdReachBox").checked = !!r.reachEnabled;
      if ($("grdScaffoldBox")) $("grdScaffoldBox").checked = !!r.scaffoldEnabled;
      if ($("grdAlertsBox")) $("grdAlertsBox").checked = !!r.alertsEnabled;
      if ($("grdKickInput")) $("grdKickInput").value = r.maxViolationsBeforeKick ?? 8;
      if ($("grdDecayInput")) $("grdDecayInput").value = r.violationDecaySeconds ?? 45;
      setOut("grdOut", r.status || "");
    } catch (e) { setOut("grdOut", e.message); }
  }
  $("grdSaveSettings")?.addEventListener("click", async () => {
    try {
      await netPost("/api/guard", {
        action: "save-settings",
        flyEnabled: $("grdFlyBox")?.checked ? "true" : "false",
        speedEnabled: $("grdSpeedBox")?.checked ? "true" : "false",
        reachEnabled: $("grdReachBox")?.checked ? "true" : "false",
        scaffoldEnabled: $("grdScaffoldBox")?.checked ? "true" : "false",
        alertsEnabled: $("grdAlertsBox")?.checked ? "true" : "false",
        maxViolations: $("grdKickInput")?.value || "8",
        decaySeconds: $("grdDecayInput")?.value || "45",
      });
      setOut("grdOut", "Guard settings saved.");
      refreshGuard();
    } catch (e) { setOut("grdOut", e.message); }
  });
  $("grdRefresh")?.addEventListener("click", () => refreshGuard());
  $("grdReload")?.addEventListener("click", async () => {
    setOut("grdOut", (await netPost("/api/guard", { action: "reload" })).result || "ok");
    refreshGuard();
  });
  $("grdPlayerBtn")?.addEventListener("click", async () => {
    const p = $("grdPlayer")?.value.trim() || "Steve";
    setOut("grdOut", (await netPost("/api/guard", { action: "player-status", player: p })).result || "");
  });

  /* ——— Protect ——— */
  function renderProtectLookup(rows) {
    const tbody = $("protLookupBody");
    const empty = $("protLookupEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    if (!rows || !rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${row.id ?? ""}</td><td>${row.changeType ?? ""}</td><td>${row.actorName ?? ""}</td>`
        + `<td>${row.world ?? ""}</td><td>${row.x ?? ""},${row.y ?? ""},${row.z ?? ""}</td>`
        + `<td><span class="mono">${row.blockBefore ?? ""}</span> → <span class="mono">${row.blockAfter ?? ""}</span></td>`
        + `<td><button type="button" class="warn ghost prot-rb" data-id="${row.id}">Rollback</button></td>`;
      tr.querySelector(".prot-rb")?.addEventListener("click", async () => {
        if (!confirm("Rollback change #" + row.id + "?")) return;
        try {
          const r = await netPost("/api/protect", { action: "rollback", id: String(row.id) });
          setOut("protOut", r.result || "Rollback sent.");
        } catch (e) { setOut("protOut", e.message); }
      });
      tbody.appendChild(tr);
    });
  }
  async function refreshProtect() {
    try {
      const r = await api("/api/protect");
      $("protInstalled").textContent = r.installed ? "yes" : "no";
      $("protLogging").textContent = r.loggingEnabled ? "on" : "off";
      $("protPrune").textContent = String(r.pruneDays || "—");
      if ($("protLogBox")) $("protLogBox").checked = !!r.loggingEnabled;
      if ($("protBlocksBox")) $("protBlocksBox").checked = !!r.logBlocks;
      if ($("protContainersBox")) $("protContainersBox").checked = !!r.logContainers;
      if ($("protPruneInput")) $("protPruneInput").value = r.pruneDays ?? 30;
      setOut("protOut", r.status || "");
    } catch (e) { setOut("protOut", e.message); }
  }
  $("protSaveSettings")?.addEventListener("click", async () => {
    try {
      await netPost("/api/protect", {
        action: "save-settings",
        loggingEnabled: $("protLogBox")?.checked ? "true" : "false",
        logBlocks: $("protBlocksBox")?.checked ? "true" : "false",
        logContainers: $("protContainersBox")?.checked ? "true" : "false",
        pruneDays: $("protPruneInput")?.value || "30",
      });
      setOut("protOut", "Protect settings saved.");
      refreshProtect();
    } catch (e) { setOut("protOut", e.message); }
  });
  $("protRefresh")?.addEventListener("click", () => refreshProtect());
  $("protReload")?.addEventListener("click", async () => {
    setOut("protOut", (await netPost("/api/protect", { action: "reload" })).result || "ok");
    refreshProtect();
  });
  $("protPruneBtn")?.addEventListener("click", async () => {
    if (!confirm("Prune old protect rows?")) return;
    setOut("protOut", (await netPost("/api/protect", { action: "prune", days: $("protPruneInput")?.value || "30" })).result || "ok");
  });
  $("protLookupBtn")?.addEventListener("click", async () => {
    const p = $("protLookupPlayer")?.value.trim() || "Steve";
    try {
      const r = await netPost("/api/protect", { action: "lookup", player: p, limit: "25" });
      renderProtectLookup(r.lookupRows || []);
      setOut("protOut", r.result || (r.lookupRows?.length ? `${r.lookupRows.length} row(s)` : "No rows"));
    } catch (e) {
      renderProtectLookup([]);
      setOut("protOut", e.message);
    }
  });

  /* ——— Regions ——— */
  let selectedRegion = "";
  function renderRegions(list) {
    const tbody = $("regBody");
    const empty = $("regEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    if (!list?.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    list.forEach((reg) => {
      const tr = document.createElement("tr");
      tr.className = reg.name === selectedRegion ? "selected" : "";
      tr.innerHTML = `<td><strong>${reg.name}</strong></td><td>${reg.world}</td>`
        + `<td>${reg.minX},${reg.minY},${reg.minZ} → ${reg.maxX},${reg.maxY},${reg.maxZ}</td>`
        + `<td>${reg.flagCount ?? 0}</td>`;
      tr.onclick = () => {
        selectedRegion = reg.name;
        $("regFlagName").value = reg.name;
        $("regDefineName").value = reg.name;
        renderRegions(list);
      };
      tbody.appendChild(tr);
    });
  }
  async function refreshRegions() {
    try {
      const r = await api("/api/regions");
      $("regInstalled").textContent = r.installed ? "yes" : "no";
      $("regServer").textContent = r.serverId || "—";
      $("regCount").textContent = String(r.regionCount ?? (r.regions || []).length);
      if ($("regFlags")) {
        $("regFlags").innerHTML = "";
        (r.flags || []).forEach((f) => {
          const o = document.createElement("option");
          o.value = f; o.textContent = f;
          $("regFlags").appendChild(o);
        });
      }
      renderRegions(r.regions || []);
      setOut("regOut", "");
    } catch (e) { setOut("regOut", e.message); }
  }
  $("regRefresh")?.addEventListener("click", () => refreshRegions());
  $("regDefineBtn")?.addEventListener("click", async () => {
    const name = $("regDefineName")?.value.trim();
    if (!name) return;
    try {
      const r = await netPost("/api/regions", {
        action: "define", name,
        world: $("regDefineWorld")?.value.trim() || "world",
        x1: $("regX1")?.value || "0", y1: $("regY1")?.value || "0", z1: $("regZ1")?.value || "0",
        x2: $("regX2")?.value || "0", y2: $("regY2")?.value || "255", z2: $("regZ2")?.value || "0",
      });
      renderRegions(r.regions || []);
      setOut("regOut", r.result || "Region defined.");
    } catch (e) { setOut("regOut", e.message); }
  });
  $("regFlagBtn")?.addEventListener("click", async () => {
    const name = $("regFlagName")?.value.trim();
    if (!name) return;
    try {
      const r = await netPost("/api/regions", {
        action: "flag-set", name,
        flag: $("regFlags")?.value,
        value: $("regFlagVal")?.value || "allow",
      });
      setOut("regOut", r.result || "Flag set.");
      refreshRegions();
    } catch (e) { setOut("regOut", e.message); }
  });

  /* ——— World ——— */
  async function refreshWorld() {
    try {
      const r = await api("/api/world");
      $("wldInstalled").textContent = r.installed ? "yes" : "no";
      $("wldSchems").textContent = String(r.schematicCount ?? "—");
      $("wldBrush").textContent = String(r.brushMaxRadius ?? "—");
      if ($("wldBrushInput")) $("wldBrushInput").value = r.brushMaxRadius ?? 16;
      $("wldAllowLoad").textContent = r.allowLoad ? "yes" : "no";
      $("wldAllowUnload").textContent = r.allowUnload ? "yes" : "no";
      setOut("wldOut", r.status || "");
    } catch (e) { setOut("wldOut", e.message); }
  }
  $("wldRefresh")?.addEventListener("click", () => refreshWorld());
  $("wldLoad")?.addEventListener("click", async () => {
    setOut("wldOut", (await netPost("/api/world", { action: "load", world: $("wldWorld")?.value.trim() })).result || "");
  });
  $("wldUnload")?.addEventListener("click", async () => {
    setOut("wldOut", (await netPost("/api/world", { action: "unload", world: $("wldWorld")?.value.trim() })).result || "");
  });
  $("wldReload")?.addEventListener("click", async () => {
    setOut("wldOut", (await netPost("/api/world", { action: "reload" })).result || "ok");
    refreshWorld();
  });
  $("wldSchemList")?.addEventListener("click", async () => {
    setOut("wldOut", (await netPost("/api/world", { action: "schem-list" })).result || "");
  });
  $("wldSaveBrush")?.addEventListener("click", async () => {
    try {
      await netPost("/api/world", { action: "save-brush", maxRadius: $("wldBrushInput")?.value || "16" });
      setOut("wldOut", "Brush max radius saved.");
      refreshWorld();
    } catch (e) { setOut("wldOut", e.message); }
  });

  /* ——— Map ——— */
  async function refreshMap() {
    try {
      const r = await api("/api/map");
      $("mapUrl").textContent = r.mapUrl || "—";
      $("mapTiles").textContent = String(r.tileCount ?? "—");
      $("mapWorlds").textContent = (r.worlds || []).join(", ") || "world";
      if ($("mapInterval")) $("mapInterval").value = r.renderIntervalMinutes ?? 15;
      if ($("mapWorldsInput")) $("mapWorldsInput").value = (r.worlds || []).join("\n");
      const iframe = $("mapFrame");
      if (iframe && r.mapUrl) iframe.src = r.mapUrl;
      setOut("mapOut", "");
    } catch (e) { setOut("mapOut", e.message); }
  }
  $("mapRefresh")?.addEventListener("click", () => refreshMap());
  $("mapReload")?.addEventListener("click", async () => {
    setOut("mapOut", (await netPost("/api/map", { action: "reload" })).result || "ok");
    refreshMap();
  });
  $("mapRender")?.addEventListener("click", async () => {
    setOut("mapOut", (await netPost("/api/map", { action: "render" })).result || "Render started.");
  });
  $("mapSaveSettings")?.addEventListener("click", async () => {
    try {
      await netPost("/api/map", {
        action: "save-settings",
        renderIntervalMinutes: $("mapInterval")?.value || "15",
        worlds: $("mapWorldsInput")?.value || "world",
      });
      setOut("mapOut", "Map settings saved.");
      refreshMap();
    } catch (e) { setOut("mapOut", e.message); }
  });

  Object.assign(YapDash.tabLoads, {
    chat: refreshChat,
    guard: refreshGuard,
    protect: refreshProtect,
    regions: refreshRegions,
    world: refreshWorld,
    map: refreshMap,
  });
};
