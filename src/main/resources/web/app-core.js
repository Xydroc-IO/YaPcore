(() => {
  const $ = (id) => document.getElementById(id);
  let token = localStorage.getItem("yap_token") || "";
  let es = null;
  let pollTimer = null;

  const TYPES = [
    "chassis", "buggy", "hoverbike", "truck_4x4", "monster_truck",
    "sport_car", "hypercar", "lambo", "ferrari", "mclaren", "porsche"
  ];

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

  document.querySelectorAll(".tabs button").forEach((btn) => {
    btn.onclick = () => {
      document.querySelectorAll(".tabs button").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
      btn.classList.add("active");
      $("tab-" + btn.dataset.tab).classList.add("active");
      if (btn.dataset.tab === "connect") loadConnect();
    };
  });

  async function netPost(path, body) {
    const r = await api(path, { method: "POST", body: JSON.stringify(body) });
    return r;
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

  $("btnStart").onclick = async () => {
    try { await api("/api/server/start", { method: "POST", body: "{}" }); await refreshStatus(); }
    catch (e) { alert(e.message); }
  };
  $("btnStop").onclick = async () => {
    if (!confirm("Stop the server?")) return;
    try { await api("/api/server/stop", { method: "POST", body: "{}" }); await refreshStatus(); }
    catch (e) { alert(e.message); }
  };

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


  window.YapDash = { $, api, netPost };
  if (window.YapDashRegisterOpsPanels) window.YapDashRegisterOpsPanels(window.YapDash);
  if (window.YapDashRegisterNetworkPanels) window.YapDashRegisterNetworkPanels(window.YapDash);

  async function boot() {
    try {
      await refreshStatus();
      connectConsole();
      pollTimer = setInterval(() => refreshStatus().catch(() => {}), 2000);
      if (window.YapDash.onBoot) window.YapDash.onBoot();
    } catch (e) {
      alert("Login failed: " + e.message);
      logout(true);
    }
  }

  if (token) {
    $("tokenInput").value = token;
    showApp();
  }
})();
