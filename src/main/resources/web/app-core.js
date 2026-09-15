(() => {
  const $ = (id) => document.getElementById(id);
  let token = localStorage.getItem("yap_token") || "";
  let es = null;
  let pollTimer = null;

  function headers(json = true) {
    const h = { Authorization: "Bearer " + token };
    if (json) h["Content-Type"] = "application/json";
    return h;
  }

  async function api(path, opts = {}) {
    const res = await fetch(path, {
      ...opts,
      headers: { ...headers(!!opts.body), ...(opts.headers || {}) },
    });
    if (res.status === 401) {
      logout(false);
      throw new Error("unauthorized");
    }
    const text = await res.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text }; }
    if (!res.ok) throw new Error(data.error || data.message || res.statusText);
    return data;
  }

  function bindClick(id, fn) {
    const el = $(id);
    if (el) el.onclick = fn;
  }

  function activateTab(tab) {
    if (window.YapShell?.switchTab) {
      window.YapShell.switchTab(tab);
      return;
    }
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    const panel = $("tab-" + tab);
    if (panel) panel.classList.add("active");
    const load = window.YapDash?.tabLoads?.[tab];
    if (load) Promise.resolve(load()).catch((e) => console.error("tab load " + tab, e));
  }

  function showApp() {
    $("login").classList.add("hidden");
    $("app").classList.remove("hidden");
    document.cookie = "yap_token=" + encodeURIComponent(token) + "; path=/; SameSite=Strict";
    boot();
  }

  function logout(clear = true) {
    if (clear) localStorage.removeItem("yap_token");
    token = "";
    if (es) { es.close(); es = null; }
    if (pollTimer) clearInterval(pollTimer);
    $("app").classList.add("hidden");
    $("login").classList.remove("hidden");
  }

  $("loginBtn").onclick = () => {
    token = $("tokenInput").value.trim();
    if (!token) return;
    localStorage.setItem("yap_token", token);
    showApp();
  };
  $("tokenInput").addEventListener("keydown", (e) => {
    if (e.key === "Enter") $("loginBtn").click();
  });
  $("btnLogout").onclick = () => logout(true);

  const menuToggle = $("menuToggle");
  if (menuToggle) {
    menuToggle.onclick = () => $("sidebar")?.classList.toggle("open");
  }

  async function netPost(path, body) {
    return api(path, { method: "POST", body: JSON.stringify(body) });
  }

  async function refreshStatus() {
    const s = await api("/api/status");
    const badge = $("runBadge");
    const label = s.runLabel || (s.running ? "RUNNING" : "STOPPED");
    badge.textContent = label;
    badge.className = "badge " + (s.running ? "on" : "off");
    badge.title = s.fleetEnabled
      ? ("Chassis DualStack: " + (s.chassisRunning ? "on" : "off")
        + " · fleet " + (s.fleetRunningCount || 0) + "/" + (s.fleetInstanceCount || 0))
      : (s.chassisRunning ? "Chassis running" : "Chassis stopped");
    const startBtn = $("btnStart");
    const stopBtn = $("btnStop");
    if (startBtn) {
      const sl = startBtn.querySelector(".label") || startBtn;
      sl.textContent = s.fleetEnabled ? "Start network" : "Start";
      startBtn.title = s.fleetEnabled
        ? "Start chassis + primary fleet server (lobby)"
        : "Start YaPcore / Folia";
    }
    if (stopBtn) {
      const sl = stopBtn.querySelector(".label") || stopBtn;
      sl.textContent = s.fleetEnabled ? "Stop network" : "Stop";
      stopBtn.title = s.fleetEnabled
        ? "Stop all local fleet servers and chassis"
        : "Stop YaPcore / Folia";
    }
    $("stPlayers").textContent = s.players + " / " + s.maxPlayers;
    $("stHeap").textContent = s.heapUsedMb + " / " + s.heapMaxMb + " MB";
    $("stJava").textContent = s.javaClients;
    $("stBe").textContent = s.bedrockClients;
    $("stPack").textContent = s.activePack || "none";
    $("stPorts").textContent = "JE " + s.port + " · BE " + s.bedrockPort;
    $("stTicks").textContent = s.ticks != null ? String(s.ticks) : "—";
    $("stLinkProc").textContent = s.linkProcessRunning ? "running" : "stopped";
    $("stPid").textContent = s.pid != null ? String(s.pid) : "—";
    $("stReport").textContent = s.statusText || "";
    const bf = s.bedrockFeel || {};
    if ($("stParitySummary")) {
      $("stParitySummary").textContent = bf.summary || "—";
      $("bfEnabled").textContent = bf.enabled ? "ON" : "off";
      $("bfBand").textContent = bf.band || "—";
      $("bfMods").textContent = bf.clientModsPresent ? "yes" : "missing";
      $("bfPack").textContent = bf.blockPackExtractOk ? "ok" : "—";
      $("bfProv").textContent = bf.provenancePresent ? "ok" : "—";
      if ($("bfToggle")) $("bfToggle").value = bf.enabled ? "true" : "false";
      if ($("bfBandSelect") && bf.band) {
        const sel = $("bfBandSelect");
        if (![...sel.options].some((o) => o.value === bf.band)) {
          const opt = document.createElement("option");
          opt.value = bf.band;
          opt.textContent = bf.band;
          sel.appendChild(opt);
        }
        sel.value = bf.band;
      }
    }
    const nh = s.networkHealth || {};
    $("stNetworkSummary").textContent = nh.summary || "—";
    $("nhFolia").textContent = nh.foliaRunning ? "running" : "stopped";
    $("nhBedrock").textContent = nh.bedrockEnabled ? "on" : "off";
    $("nhLink").textContent = nh.linkSuiteComplete === true ? "complete" : nh.linkSuiteComplete === false ? "incomplete" : "—";
    $("nhPlugins").textContent = nh.pluginCount != null ? String(nh.pluginCount) : "—";
    $("nhCompat").textContent = nh.compatWarnings != null ? String(nh.compatWarnings) : "0";
    const smoke = nh.lastNetworkSmoke && nh.lastNetworkSmoke !== "never" ? nh.lastNetworkSmoke : nh.lastBedrockPlaySmoke;
    $("nhSmoke").textContent = smoke && smoke !== "never" ? smoke.replace("T", " ").slice(0, 19) : "never";
    const bh = nh.backendHealth || [];
    if ($("nhBackends")) {
      if (!bh.length) {
        $("nhBackends").textContent = "—";
      } else {
        const up = bh.filter((b) => b.up).length;
        $("nhBackends").textContent = up + "/" + bh.length + " up";
      }
    }
    const ops = nh.opsPlugins || {};
    $("stOpsSummary").textContent = ops.summary || "—";
    const opIds = {
      Protect: "opProtect",
      Chat: "opChat",
      Moderation: "opModeration",
      "Player data": "opPlayerdata",
      Map: "opMap",
      Discord: "opDiscord",
      Tebex: "opTebex",
    };
    (ops.plugins || []).forEach((p) => {
      const id = opIds[p.label];
      if (!id || !$(id)) return;
      $(id).textContent = p.installed ? (p.detail || "ready") : "missing";
    });
  }

  async function loadConnect() {
    const c = await api("/api/connect");
    $("connectCard").innerHTML = `
      <div><strong>Java</strong><br/><code>${c.javaJoin || "—"}</code></div>
      <div style="margin-top:10px"><strong>Bedrock</strong><br/><code>${c.bedrockJoin || "—"}</code></div>
      <div style="margin-top:10px"><strong>Crossplay</strong><br/><code>${c.crossplayJoin || "—"}</code></div>
      <div style="margin-top:10px"><strong>Local</strong><br/><code>${c.localhost || "—"}</code></div>
      <div style="margin-top:10px"><strong>Packs</strong><br/><code>${c.packUrl || "—"}</code></div>
      <div style="margin-top:10px" class="muted">Exposed: ${c.exposed ? "yes" : "no"} · host ${c.publicHost || "—"}</div>`;
  }

  function connectConsole() {
    if (es) es.close();
    const url = "/api/console/stream?token=" + encodeURIComponent(token);
    es = new EventSource(url);
    const out = $("consoleOut");
    es.onmessage = (ev) => {
      out.textContent += (out.textContent ? "\n" : "") + ev.data;
      out.scrollTop = out.scrollHeight;
    };
    api("/api/console").then((d) => {
      if (d.text) out.textContent = d.text;
      out.scrollTop = out.scrollHeight;
    }).catch(() => {});
  }

  $("cmdForm").onsubmit = async (e) => {
    e.preventDefault();
    const cmd = $("cmdInput").value.trim();
    if (!cmd) return;
    $("cmdInput").value = "";
    try {
      const ctx = window.YapFleetContext?.get?.();
      const body = { command: cmd };
      if (ctx?.type === "instance" && ctx.instanceId) {
        body.instanceId = ctx.instanceId;
      }
      const r = await api("/api/command", { method: "POST", body: JSON.stringify(body) });
      if (r.result) {
        const scope = r.instanceId && r.instanceId !== "primary" ? " [" + r.instanceId + "]" : "";
        $("consoleOut").textContent += "\n> " + cmd + scope + "\n" + r.result;
        $("consoleOut").scrollTop = $("consoleOut").scrollHeight;
      }
    } catch (err) {
      alert(err.message);
    }
  };

  bindClick("btnStart", async () => {
    try { await api("/api/server/start", { method: "POST", body: "{}" }); await refreshStatus(); }
    catch (e) { alert(e.message); }
  });
  bindClick("btnStop", async () => {
    if (!confirm("Stop the game network (fleet servers + chassis)?")) return;
    try { await api("/api/server/stop", { method: "POST", body: "{}" }); await refreshStatus(); }
    catch (e) { alert(e.message); }
  });

  window.YapDash = {
    $, api, netPost, bindClick,
    tabLoads: { connect: loadConnect },
    refreshStatus, connectConsole, activateTab,
  };

  $("bfSave")?.addEventListener("click", async () => {
    const msg = $("bfSaveMsg");
    try {
      await api("/api/config", {
        method: "POST",
        body: JSON.stringify({
          "parity.bedrock-feel": $("bfToggle")?.value || "false",
          "parity.bedrock-band": $("bfBandSelect")?.value || "band_26_50",
        }),
      });
      if (msg) {
        msg.hidden = false;
        msg.className = "easy-save-msg ok";
        msg.textContent = "Saved";
      }
      await refreshStatus();
    } catch (e) {
      if (msg) {
        msg.hidden = false;
        msg.className = "easy-save-msg err";
        msg.textContent = e.message;
      }
    }
  });

  $("stOpenLink")?.addEventListener("click", () => {
    if (window.YapShell?.openLink) window.YapShell.openLink();
    else if (window.YapShell?.switchTab) window.YapShell.switchTab("link");
    else document.querySelector('[data-tab="link"]')?.click();
  });

  if (window.YapDashRegisterAccessPanels) window.YapDashRegisterAccessPanels(window.YapDash);
  if (window.YapDashRegisterPlayersPanels) window.YapDashRegisterPlayersPanels(window.YapDash);
  if (window.YapDashRegisterAdminPanels) window.YapDashRegisterAdminPanels(window.YapDash);
  if (window.YapDashRegisterSetupPanels) window.YapDashRegisterSetupPanels(window.YapDash);
  if (window.YapDashRegisterOpsPanels) window.YapDashRegisterOpsPanels(window.YapDash);
  if (window.YapDashRegisterNetworkPanels) window.YapDashRegisterNetworkPanels(window.YapDash);
  if (window.YapDashRegisterFullPanels) window.YapDashRegisterFullPanels(window.YapDash);
  if (window.YapDashRegisterNpcPanels) window.YapDashRegisterNpcPanels(window.YapDash);
  if (window.YapDashRegisterSkillsPanels) window.YapDashRegisterSkillsPanels(window.YapDash);
  if (window.YapDashRegisterDisastersPanels) window.YapDashRegisterDisastersPanels(window.YapDash);
  if (window.YapDashRegisterSocialPanels) window.YapDashRegisterSocialPanels(window.YapDash);
  if (window.YapDashRegisterPluginEditors) window.YapDashRegisterPluginEditors(window.YapDash);
  if (window.YapDashRegisterKitsPanels) window.YapDashRegisterKitsPanels(window.YapDash);
  window.YapDashTabLoads = window.YapDash.tabLoads;

  async function boot() {
    try {
      await refreshStatus();
      connectConsole();
      pollTimer = setInterval(() => refreshStatus().catch(() => {}), 2000);
      if (window.YapDash.onBoot) await window.YapDash.onBoot();
    } catch (e) {
      alert("Login failed: " + e.message);
      logout(true);
    }
  }

  const params = new URLSearchParams(window.location.search);
  const urlToken = params.get("token");
  if (urlToken && urlToken.trim()) {
    token = urlToken.trim();
    localStorage.setItem("yap_token", token);
    history.replaceState({}, "", window.location.pathname);
    $("tokenInput").value = token;
    showApp();
  } else if (token) {
    $("tokenInput").value = token;
    showApp();
  }
})();
