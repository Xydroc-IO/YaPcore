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
    badge.textContent = s.running ? "RUNNING" : "STOPPED";
    badge.className = "badge " + (s.running ? "on" : "off");
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
    const nh = s.networkHealth || {};
    $("stNetworkSummary").textContent = nh.summary || "—";
    $("nhFolia").textContent = nh.foliaRunning ? "running" : "stopped";
    $("nhBedrock").textContent = nh.bedrockEnabled ? "on" : "off";
    $("nhLink").textContent = nh.linkSuiteComplete === true ? "complete" : nh.linkSuiteComplete === false ? "incomplete" : "—";
    $("nhPlugins").textContent = nh.pluginCount != null ? String(nh.pluginCount) : "—";
    $("nhCompat").textContent = nh.compatWarnings != null ? String(nh.compatWarnings) : "0";
    const smoke = nh.lastNetworkSmoke && nh.lastNetworkSmoke !== "never" ? nh.lastNetworkSmoke : nh.lastBedrockPlaySmoke;
    $("nhSmoke").textContent = smoke && smoke !== "never" ? smoke.replace("T", " ").slice(0, 19) : "never";
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
      const r = await api("/api/command", { method: "POST", body: JSON.stringify({ command: cmd }) });
      if (r.result) {
        $("consoleOut").textContent += "\n> " + cmd + "\n" + r.result;
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
    if (!confirm("Stop the server?")) return;
    try { await api("/api/server/stop", { method: "POST", body: "{}" }); await refreshStatus(); }
    catch (e) { alert(e.message); }
  });

  window.YapDash = {
    $, api, netPost, bindClick,
    tabLoads: { connect: loadConnect },
    refreshStatus, connectConsole, activateTab,
  };

  if (window.YapDashRegisterAccessPanels) window.YapDashRegisterAccessPanels(window.YapDash);
  if (window.YapDashRegisterPlayersPanels) window.YapDashRegisterPlayersPanels(window.YapDash);
  if (window.YapDashRegisterAdminPanels) window.YapDashRegisterAdminPanels(window.YapDash);
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
