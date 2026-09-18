(() => {
  const $ = (id) => document.getElementById(id);
  const api = (...args) => window.YapDash.api(...args);
  const toast = (m, k) => window.YapFleetContext?.toast?.(m, k);

  function esc(v) {
    return String(v == null ? "" : v)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  function stateBadge(state, running) {
    const s = String(state || "STOPPED").toUpperCase();
    if (running === true || s === "RUNNING") return `<span class="badge on">Running</span>`;
    if (s === "STARTING") return `<span class="badge warn">Starting</span>`;
    if (s === "STOPPING") return `<span class="badge warn">Stopping</span>`;
    if (s === "ERROR" || s === "FAILED") return `<span class="badge off">Error</span>`;
    if (s === "REMOTE") return `<span class="badge">Remote</span>`;
    return `<span class="badge off">Stopped</span>`;
  }

  let lastSnap = null;
  let busy = new Set();

  async function refreshFleet() {
    const r = await api("/api/fleet");
    lastSnap = r;
    window.YapFleetContext && (window.YapFleetContext._lastSnapshot = r);
    window.YapFleetContext?.renderChips?.(r);

    $("fleetEnabled").textContent = r.fleetEnabled ? "yes" : "no";
    $("fleetPrimary").textContent = r.primaryId || "—";
    const instances = r.instances || [];
    const nodes = r.nodes || [];
    $("fleetCount").textContent = String(instances.length);
    $("fleetNodeCount").textContent = String(nodes.length);

    const filter = $("fleetNodeFilter");
    const prev = filter.value;
    filter.innerHTML = `<option value="all">all</option><option value="local">local</option>`;
    nodes.forEach((n) => {
      const opt = document.createElement("option");
      opt.value = n.id;
      opt.textContent = n.displayName || n.id;
      filter.appendChild(opt);
    });
    if ([...filter.options].some((o) => o.value === prev)) filter.value = prev;

    const healthBy = {};
    (r.backendHealth || []).forEach((h) => {
      healthBy[String(h.name || "").toLowerCase()] = h;
    });

    const q = ($("fleetSearch")?.value || "").trim().toLowerCase();
    const nodeSel = filter.value;
    const shown = instances.filter((i) => {
      if (nodeSel !== "all" && i.nodeId !== nodeSel) return false;
      if (!q) return true;
      const hay = `${i.id} ${i.displayName || ""} ${i.port} ${i.state}`.toLowerCase();
      return hay.includes(q);
    });

    const cards = $("fleetCards");
    const empty = $("fleetEmpty");
    cards.innerHTML = "";
    empty.classList.toggle("hidden", shown.length > 0 || !r.fleetEnabled);

    shown.forEach((i) => {
      const h = healthBy[String(i.serverId || i.id).toLowerCase()] || {};
      const players = h.online != null ? `${h.online}/${h.max || "?"} players` : "— players";
      const lat = h.latencyMs != null && h.latencyMs >= 0 ? `${h.latencyMs} ms` : "—";
      const card = document.createElement("article");
      card.className = "fleet-card";
      card.innerHTML = `
        <div class="fleet-card-head">
          <div>
            <h3>${esc(i.displayName || i.id)}</h3>
            <div class="muted" style="font-size:12px;margin-top:2px">${esc(i.id)} · ${esc(i.nodeId)}</div>
          </div>
          ${stateBadge(i.state, i.running)}
        </div>
        <div class="fleet-card-meta">
          <div>Port<strong>${esc(i.port)}</strong></div>
          <div>RAM<strong>${esc(i.ramMb > 0 ? (i.ramMb >= 1024 ? (i.ramMb / 1024) + "G" : i.ramMb + "M") : "auto")}</strong></div>
          <div>Plugins<strong>${esc(i.pluginCount != null ? i.pluginCount : "—")}</strong></div>
          <div>Players<strong>${esc(players)}</strong></div>
          <div>Health<strong>${esc(h.up == null ? "—" : h.up ? "up" : "down")}</strong></div>
        </div>
        ${i.lastError ? `<div class="muted" style="font-size:11px;color:var(--danger)">${esc(i.lastError)}</div>` : ""}
        <div class="fleet-card-actions">
          <button type="button" class="primary" data-act="plugins">Plugins</button>
          <button type="button" data-act="start">Start</button>
          <button type="button" data-act="stop">Stop</button>
          <button type="button" data-act="restart">Restart</button>
          <button type="button" data-act="open">Setup</button>
          ${Number(i.pluginCount) === 0 ? `<button type="button" data-act="seed">Install defaults</button>` : ""}
          <button type="button" class="danger" data-act="delete">Delete</button>
        </div>`;
      card.querySelectorAll("button").forEach((btn) => {
        btn.onclick = () => onCardAction(i, btn.dataset.act, btn);
      });
      if (busy.has(i.id)) {
        card.querySelectorAll("button").forEach((b) => b.classList.add("busy"));
      }
      cards.appendChild(card);
    });

    const out = $("fleetOut");
    if (out) {
      out.textContent = JSON.stringify({
        players: (r.players || []).slice(0, 12),
        hint: "Fleet home · Start/Stop/Restart · Open scopes Setup/Plugins",
      }, null, 2);
    }
    paintSetupIfOpen();
    return r;
  }

  async function fleetAction(body) {
    try {
      const r = await api("/api/fleet", { method: "POST", body: JSON.stringify(body) });
      const out = $("fleetOut");
      if (out) out.textContent = JSON.stringify(r, null, 2);
      await refreshFleet();
      return r;
    } catch (e) {
      toast(String(e.message || e), "err");
      throw e;
    }
  }

  async function onCardAction(inst, act, btn) {
    if (act === "open" || act === "plugins") {
      window.YapFleetContext?.set?.({ type: "instance", instanceId: inst.id });
      if (act === "open") {
        $("fleetInstanceSetup")?.classList.remove("hidden");
        await loadSetup(inst.id);
      } else {
        document.querySelector('[data-tab="plugins"]')?.click();
        window.YapFleetContext?.notify?.();
      }
      toast((act === "plugins" ? "Plugins: " : "Setup: ") + (inst.displayName || inst.id));
      return;
    }
    if (act === "seed") {
      busy.add(inst.id);
      btn?.classList.add("busy");
      try {
        await fleetAction({ action: "install-core-network", id: inst.id });
        toast("CORE+NETWORK · " + inst.id, "ok");
      } finally {
        busy.delete(inst.id);
        await refreshFleet();
      }
      return;
    }
    if (act === "flat") {
      if (!confirm("Wipe " + inst.id + " world and regenerate as FLAT?\nServer must be stopped. This deletes the current world.")) {
        return;
      }
      busy.add(inst.id);
      btn?.classList.add("busy");
      try {
        if (inst.running) {
          await fleetAction({ action: "stop", id: inst.id });
          await new Promise((r) => setTimeout(r, 1500));
        }
        const r = await fleetAction({
          action: "world-swap-flat",
          id: inst.id,
          includeDims: "true",
          creativeMode: inst.id === "creative" ? "true" : "false",
        });
        toast("Flat world ready · " + inst.id + " — Start when ready", "ok");
        if (r?.note) console.info(r.note);
      } finally {
        busy.delete(inst.id);
        await refreshFleet();
      }
      return;
    }
    if (act === "delete") {
      if (!confirm("Delete instance " + inst.id + "?")) return;
    }
    busy.add(inst.id);
    btn?.classList.add("busy");
    try {
      await fleetAction({ action: act, id: inst.id });
      toast((act.charAt(0).toUpperCase() + act.slice(1)) + " · " + inst.id, "ok");
    } finally {
      busy.delete(inst.id);
      await refreshFleet();
    }
  }

  async function loadSetup(id) {
    const r = await api("/api/fleet/instances/" + encodeURIComponent(id) + "/settings");
    const inst = r.instance || {};
    const props = r.properties || {};
    const pd = r.playerData || {};
    $("fleetSetupTitle").textContent = inst.displayName || inst.id || id;
    $("fleetSetupDisplay").value = inst.displayName || "";
    $("fleetSetupServerId").value = inst.serverId || "";
    $("fleetSetupPort").value = inst.port || props["server-port"] || "";
    $("fleetSetupBind").value = inst.bind || props["server-ip"] || "";
    $("fleetSetupMax").value = props["max-players"] || "20";
    ensureSelectValue($("fleetSetupMax"), props["max-players"] || "20");
    const ram = inst.ramMb > 0 ? String(inst.ramMb) : "2048";
    ensureSelectValue($("fleetSetupRam"), ram);
    $("fleetSetupMotd").value = props.motd || "";
    ensureSelectValue($("fleetSetupGamemode"), props.gamemode || "survival");
    ensureSelectValue($("fleetSetupDifficulty"), props.difficulty || "easy");
    $("fleetSetupView").value = props["view-distance"] || "";
    $("fleetSetupAuto").value = String(inst.autoStart !== false);
    paintPlayerData(pd);
    $("fleetInstanceSetup")?.classList.remove("hidden");
  }

  function paintPlayerData(pd) {
    const profile = String(pd.inventoryProfile || "global").trim();
    const sel = $("fleetSetupInvProfile");
    const custom = $("fleetSetupInvCustom");
    const wrap = $("fleetSetupInvCustomWrap");
    if (!sel) return;
    if (profile === "global" || profile === "server") {
      sel.value = profile;
      if (custom) custom.value = "";
      wrap?.classList.add("hidden");
    } else {
      sel.value = "custom";
      if (custom) custom.value = profile;
      wrap?.classList.remove("hidden");
    }
    setBoolSelect($("fleetSetupSyncInv"), pd.syncInventory !== false);
    setBoolSelect($("fleetSetupSyncEnder"), pd.syncEnderchest !== false);
    setBoolSelect($("fleetSetupSyncXp"), pd.syncXp !== false);
    setBoolSelect($("fleetSetupSyncVitals"), pd.syncVitals !== false);
    setBoolSelect($("fleetSetupSyncEco"), pd.syncEconomy !== false);
  }

  function setBoolSelect(el, on) {
    if (el) el.value = on ? "true" : "false";
  }

  function syncInvProfileUi() {
    const mode = $("fleetSetupInvProfile")?.value;
    const wrap = $("fleetSetupInvCustomWrap");
    if (!wrap) return;
    wrap.classList.toggle("hidden", mode !== "custom");
  }

  function ensureSelectValue(el, value) {
    if (!el) return;
    const v = String(value == null ? "" : value);
    if (![...el.options].some((o) => o.value === v)) {
      const opt = document.createElement("option");
      opt.value = v;
      opt.textContent = v + " (custom)";
      el.appendChild(opt);
    }
    el.value = v;
  }

  function paintSetupIfOpen() {
    const ctx = window.YapFleetContext?.get?.();
    if (ctx?.type === "instance" && ctx.instanceId) {
      $("fleetInstanceSetup")?.classList.remove("hidden");
    }
  }

  function dashToken() {
    return localStorage.getItem("yap_token") || "";
  }

  function openAddServer() {
    const adv = $("fleetAdvanced");
    if (adv) adv.open = true;
    $("fleetNewId")?.focus();
  }

  function openLinkSetup() {
    const ok = window.YapShell?.openLink
      ? window.YapShell.openLink()
      : window.YapShell?.switchTab?.("link");
    if (!ok) {
      document.querySelector('[data-tab="link"]')?.click();
    }
    window.YapFleetContext?.toast?.("YaP Link setup", "ok");
  }

  $("fleetEnable").onclick = () => fleetAction({ action: "enable-fleet" }).then(() => toast("Fleet enabled"));
  $("fleetRefresh").onclick = () => refreshFleet();
  $("fleetSyncLink").onclick = () => fleetAction({ action: "sync-link" }).then(() => toast("Link synced"));
  $("fleetOpenLink")?.addEventListener("click", openLinkSetup);
  $("fleetCreate").onclick = async () => {
    const id = $("fleetNewId").value.trim();
    await fleetAction({
      action: "create",
      id,
      displayName: $("fleetNewName").value.trim(),
      port: $("fleetNewPort").value.trim(),
      autoStart: $("fleetNewAuto").value,
      ramMb: $("fleetNewRam")?.value || "2048",
    });
    const max = $("fleetNewMax")?.value?.trim();
    if (id && max) {
      await api("/api/fleet/instances/" + encodeURIComponent(id) + "/settings", {
        method: "POST",
        body: JSON.stringify({ "max-players": max }),
      });
    }
    toast("Created " + id);
  };
  $("fleetAddNode").onclick = () => fleetAction({
    action: "write-node-token",
    id: $("fleetNodeId").value.trim(),
    token: $("fleetNodeToken").value.trim(),
    baseUrl: $("fleetNodeUrl").value.trim(),
    displayName: $("fleetNodeName").value.trim(),
  });
  $("fleetProbeNode").onclick = () => fleetAction({
    action: "probe-node",
    id: $("fleetNodeId").value.trim(),
  });
  $("fleetDeploy").onclick = () => fleetAction({
    action: "deploy",
    sourcePath: $("fleetDeploySrc").value.trim(),
    target: $("fleetDeployTarget").value.trim(),
    instanceIds: $("fleetDeployIds").value.trim(),
    restartPolicy: $("fleetDeployRestart")?.value || "none",
  });
  $("fleetBootstrap").onclick = async () => {
    const engine = prompt(
      "Database engine: mysql | postgres | sqlite | skip\n(Or use the Database card above for Docker + setup)",
      "mysql"
    );
    if (engine === null) return;
    let jdbcUrl = "";
    const e = String(engine || "").trim().toLowerCase();
    if (e === "postgres" || e === "postgresql") {
      jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/yap_playerdata";
    } else if (e === "sqlite") {
      jdbcUrl = "jdbc:sqlite:data/yap.db";
    } else if (e === "skip" || e === "") {
      jdbcUrl = "";
    } else {
      jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap_playerdata?useSSL=false&allowPublicKeyRetrieval=true";
    }
    if (jdbcUrl) {
      const edited = prompt("JDBC URL (blank skips ensure-db)", jdbcUrl);
      if (edited === null) return;
      jdbcUrl = edited;
    }
    await fleetAction({
      action: "bootstrap",
      jdbcUrl: jdbcUrl || "",
      createSurvival: confirm("Create survival instance?"),
      enableVelocity: confirm("Enable Link velocity forwarding?"),
      startInstances: confirm("Start auto-start instances now?"),
    });
    toast("Bootstrap complete");
    if (window.YapDatabase?.refresh) window.YapDatabase.refresh();
  };
  $("fleetConsoleSend").onclick = async () => {
    const id = $("fleetConsoleId").value.trim() || "lobby";
    const command = $("fleetConsoleCmd").value.trim();
    const r = await fleetAction({ action: "command", id, command });
    $("fleetConsole").textContent += (r.result || JSON.stringify(r)) + "\n";
  };
  $("fleetConsoleStream").onclick = () => {
    const id = $("fleetConsoleId").value.trim() || "lobby";
    const url = `/api/fleet/console/stream?id=${encodeURIComponent(id)}&token=${encodeURIComponent(dashToken())}`;
    const es = new EventSource(url);
    es.onmessage = (ev) => {
      $("fleetConsole").textContent += ev.data + "\n";
    };
    es.onerror = () => {
      $("fleetConsole").textContent += "[SSE closed]\n";
      es.close();
    };
  };
  $("fleetNodeFilter").onchange = () => refreshFleet();
  $("fleetSearch")?.addEventListener("input", () => refreshFleet());
  $("fleetEmptyAdd")?.addEventListener("click", openAddServer);

  $("fleetSetupSave")?.addEventListener("click", async () => {
    const ctx = window.YapFleetContext?.get?.();
    const id = ctx?.type === "instance" ? ctx.instanceId : null;
    if (!id) {
      toast("Select a server first", "err");
      return;
    }
    const msg = $("fleetSetupMsg");
    try {
      const invMode = $("fleetSetupInvProfile")?.value || "global";
      const body = {
        displayName: $("fleetSetupDisplay").value.trim(),
        serverId: $("fleetSetupServerId").value.trim(),
        port: $("fleetSetupPort").value.trim(),
        bind: $("fleetSetupBind").value.trim(),
        "max-players": $("fleetSetupMax").value.trim(),
        ramMb: $("fleetSetupRam")?.value?.trim() || "",
        motd: $("fleetSetupMotd").value.trim(),
        gamemode: $("fleetSetupGamemode").value.trim(),
        difficulty: $("fleetSetupDifficulty").value.trim(),
        "view-distance": $("fleetSetupView").value.trim(),
        autoStart: $("fleetSetupAuto").value,
        inventoryProfile: invMode,
        inventoryProfileCustom: $("fleetSetupInvCustom")?.value?.trim() || "",
        syncInventory: $("fleetSetupSyncInv")?.value || "true",
        syncEnderchest: $("fleetSetupSyncEnder")?.value || "true",
        syncXp: $("fleetSetupSyncXp")?.value || "true",
        syncVitals: $("fleetSetupSyncVitals")?.value || "true",
        syncEconomy: $("fleetSetupSyncEco")?.value || "true",
      };
      const r = await api("/api/fleet/instances/" + encodeURIComponent(id) + "/settings", {
        method: "POST",
        body: JSON.stringify(body),
      });
      if (r.playerData) paintPlayerData(r.playerData);
      if (msg) {
        msg.hidden = false;
        msg.className = "easy-save-msg ok";
        let text = r.linkSynced ? "Saved · Link servers.* synced" : "Saved";
        if (r.playerDataChanged) {
          text += r.playerDataReloaded ? " · Player data reloaded" : " · Inventory settings saved";
        }
        msg.textContent = text;
      }
      toast("Settings saved for " + id);
      await refreshFleet();
    } catch (e) {
      if (msg) {
        msg.hidden = false;
        msg.className = "easy-save-msg err";
        msg.textContent = e.message;
      }
      toast(e.message, "err");
    }
  });

  $("fleetSetupInvProfile")?.addEventListener("change", syncInvProfileUi);

  window.addEventListener("yap-fleet-context", (ev) => {
    const ctx = ev.detail;
    if (ctx?.type === "instance" && ctx.instanceId) {
      loadSetup(ctx.instanceId).catch(() => {});
    } else {
      $("fleetInstanceSetup")?.classList.add("hidden");
    }
  });

  window.YapDash = window.YapDash || {};
  window.YapDash.tabLoads = Object.assign(window.YapDash.tabLoads || {}, {
    fleet: refreshFleet,
  });
})();
