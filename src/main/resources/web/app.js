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
      if (btn.dataset.tab === "plugins") loadPlugins();
      if (btn.dataset.tab === "modules") loadModules();
      if (btn.dataset.tab === "packs") loadPacks();
      if (btn.dataset.tab === "settings") loadSettings();
      if (btn.dataset.tab === "connect") loadConnect();
      if (btn.dataset.tab === "vehicles") renderVehicles();
      if (btn.dataset.tab === "pregen") refreshPregen();
    };
  });

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

  const SETTINGS_FIELDS = [
    ["server-name", "Server name"],
    ["motd", "MOTD"],
    ["bind-host", "Bind host"],
    ["port", "Java port"],
    ["bedrock-port", "Bedrock port"],
    ["max-players", "Max players"],
    ["ram-mb", "RAM max (MB)"],
    ["ram-min-mb", "RAM min (MB)"],
    ["view-distance", "View distance"],
    ["public-host", "Public host"],
    ["server-domain", "Domain"],
    ["public-port", "Public JE port"],
    ["resource-pack-file", "Active pack file"],
    ["java-enabled", "Java enabled", "bool"],
    ["bedrock-enabled", "Bedrock enabled", "bool"],
    ["shared-listen-port", "Shared listen port", "bool"],
    ["crossplay-enabled", "Crossplay", "bool"],
    ["allow-localhost", "Allow localhost", "bool"],
    ["online-mode", "Online mode", "bool"],
    ["resource-pack-enabled", "Pack HTTP", "bool"],
    ["internet-exposed", "Internet exposed", "bool"],
  ];

  async function loadSettings() {
    const cfg = await api("/api/config");
    const form = $("settingsForm");
    form.innerHTML = "";
    SETTINGS_FIELDS.forEach(([key, label, type]) => {
      const lab = document.createElement("label");
      lab.textContent = label;
      if (type === "bool") {
        const sel = document.createElement("select");
        sel.name = key;
        sel.innerHTML = `<option value="true">true</option><option value="false">false</option>`;
        sel.value = String(!!cfg[key]);
        lab.appendChild(sel);
      } else {
        const inp = document.createElement("input");
        inp.name = key;
        inp.value = cfg[key] == null ? "" : cfg[key];
        lab.appendChild(inp);
      }
      form.appendChild(lab);
    });
  }

  $("saveSettings").onclick = async () => {
    const body = {};
    $("settingsForm").querySelectorAll("input,select").forEach((el) => {
      body[el.name] = el.value;
    });
    try {
      await api("/api/config", { method: "POST", body: JSON.stringify(body) });
      alert("Settings saved");
      await refreshStatus();
    } catch (e) { alert(e.message); }
  };

  async function loadPlugins() {
    const d = await api("/api/plugins");
    const ul = $("pluginList");
    ul.innerHTML = "";
    (d.plugins || []).forEach((p) => {
      const li = document.createElement("li");
      li.innerHTML = `<div><strong>${p.fileName}</strong><div class="meta">${p.sizeLabel}</div></div>`;
      const rm = document.createElement("button");
      rm.className = "danger";
      rm.textContent = "Remove";
      rm.onclick = async () => {
        if (!confirm("Remove " + p.fileName + "?")) return;
        await api("/api/plugins", { method: "DELETE", body: JSON.stringify({ fileName: p.fileName }) });
        loadPlugins();
      };
      li.appendChild(rm);
      ul.appendChild(li);
    });
  }
  $("refreshPlugins").onclick = () => loadPlugins().catch((e) => alert(e.message));
  $("addPlugin").onclick = async () => {
    try {
      await api("/api/plugins", { method: "POST", body: JSON.stringify({ path: $("pluginPath").value.trim() }) });
      $("pluginPath").value = "";
      loadPlugins();
    } catch (e) { alert(e.message); }
  };

  async function loadModules() {
    const d = await api("/api/modules");
    const ul = $("moduleList");
    ul.innerHTML = "";
    (d.modules || []).forEach((m) => {
      const li = document.createElement("li");
      li.innerHTML = `<div><strong>${m.fileName}</strong><div class="meta">${m.sizeLabel}</div></div>`;
      const rm = document.createElement("button");
      rm.className = "danger";
      rm.textContent = "Remove";
      rm.onclick = async () => {
        await api("/api/modules", { method: "DELETE", body: JSON.stringify({ fileName: m.fileName }) });
        loadModules();
      };
      li.appendChild(rm);
      ul.appendChild(li);
    });
  }
  $("refreshModules").onclick = () => loadModules().catch((e) => alert(e.message));
  $("addModule").onclick = async () => {
    try {
      await api("/api/modules", { method: "POST", body: JSON.stringify({ path: $("modulePath").value.trim() }) });
      loadModules();
    } catch (e) { alert(e.message); }
  };

  async function loadPacks() {
    const d = await api("/api/packs");
    const ul = $("packList");
    ul.innerHTML = "";
    (d.packs || []).forEach((p) => {
      const li = document.createElement("li");
      li.innerHTML = `<div><strong>${p.fileName}</strong>${p.active ? " · active" : ""}<div class="meta">${p.sizeLabel || ""}</div></div>`;
      const actions = document.createElement("div");
      actions.style.display = "flex";
      actions.style.gap = "6px";
      if (!p.active) {
        const act = document.createElement("button");
        act.className = "primary";
        act.textContent = "Set active";
        act.onclick = async () => {
          await api("/api/packs", { method: "POST", body: JSON.stringify({ action: "setActive", fileName: p.fileName }) });
          loadPacks();
        };
        actions.appendChild(act);
      }
      const rm = document.createElement("button");
      rm.className = "danger";
      rm.textContent = "Remove";
      rm.onclick = async () => {
        await api("/api/packs", { method: "POST", body: JSON.stringify({ action: "remove", fileName: p.fileName }) });
        loadPacks();
      };
      actions.appendChild(rm);
      li.appendChild(actions);
      ul.appendChild(li);
    });
  }
  $("refreshPacks").onclick = () => loadPacks().catch((e) => alert(e.message));

  function renderVehicles() {
    const grid = $("vehGrid");
    grid.innerHTML = "";
    TYPES.forEach((t) => {
      const b = document.createElement("button");
      b.textContent = t;
      b.onclick = () => veh("spawn", t);
      grid.appendChild(b);
    });
  }
  document.querySelectorAll("[data-veh]").forEach((b) => {
    b.onclick = () => veh(b.dataset.veh);
  });
  async function veh(action, type) {
    try {
      const r = await api("/api/vehicles", {
        method: "POST",
        body: JSON.stringify({ action, type: type || "buggy" }),
      });
      $("vehOut").textContent = (r.command || "") + "\n" + (r.result || "");
    } catch (e) { alert(e.message); }
  }

  async function refreshPregen() {
    try {
      const r = await api("/api/pregen");
      $("pregenOut").textContent = r.status || "(no jobs)";
    } catch (e) {
      $("pregenOut").textContent = e.message;
    }
  }
  async function pregen(action, extra = {}) {
    try {
      const body = { action, world: $("pregenWorld").value.trim() || "world", target: "all", ...extra };
      const r = await api("/api/pregen", { method: "POST", body: JSON.stringify(body) });
      $("pregenOut").textContent = (r.command || "") + "\n" + (r.result || "");
    } catch (e) { alert(e.message); }
  }
  $("pregenStart").onclick = () => {
    const shape = $("pregenShape").value;
    const extra = { shape, radius: $("pregenRadius").value };
    if (shape === "corners") {
      const p = $("pregenCorners").value.trim().split(/\s+/);
      extra.x1 = p[0] || "0";
      extra.z1 = p[1] || "0";
      extra.x2 = p[2] || "128";
      extra.z2 = p[3] || "128";
    }
    pregen("start", extra);
  };
  $("pregenPause").onclick = () => pregen("pause");
  $("pregenResume").onclick = () => pregen("resume");
  $("pregenCancel").onclick = () => pregen("cancel");
  $("pregenStatus").onclick = () => refreshPregen();

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
})();
