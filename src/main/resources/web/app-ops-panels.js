(() => {
  function register(YapDash) {
    const { $, api, netPost } = YapDash;

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
    ["yap-ranks-auto-apply", "Auto-apply YaPPerms rank pack once", "bool"],
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
      const badge = p.compatWarning
        ? `<span class="badge warn">${p.compatStatus}${p.nativeAlternative ? " → " + p.nativeAlternative : ""}</span>`
        : p.compatStatus === "native"
          ? `<span class="badge on">native</span>`
          : p.compatStatus === "works"
            ? `<span class="badge">works</span>`
            : "";
      li.innerHTML = `<div><strong>${p.fileName}</strong> ${badge}<div class="meta">${p.sizeLabel}${p.compatNote ? " · " + p.compatNote : ""}</div></div>`;
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

  async function refreshRanks() {
    try {
      const r = await api("/api/ranks");
      $("rkYapPerms").textContent = r.yapPermsInstalled ? "yes" : "missing";
      $("rkApplied").textContent = r.applied ? "yes" : "no";
      $("rkCmds").textContent = String(r.commandCount || 0);
      $("rkAuto").textContent = r.autoApply ? "on" : "off";
      const groups = (r.groups || ["default", "vip", "mod", "admin"]).join(" → ");
      $("ranksOut").textContent = "Track: " + (r.track || "yap") + " · " + groups
        + "\n\n" + (r.commands || []).join("\n")
        + "\n\n— Assign players —\n/yapperm user Steve parent set vip\n/yapperm user Alex parent set mod\n/promote Steve";
    } catch (e) {
      $("ranksOut").textContent = e.message;
    }
  }
  async function ranksAction(action, force) {
    try {
      const body = { action };
      if (force) body.force = "true";
      const r = await api("/api/ranks", { method: "POST", body: JSON.stringify(body) });
      $("ranksOut").textContent = (r.result || "") + "\n\n(refreshing…)";
      await refreshRanks();
      if (r.result) $("ranksOut").textContent = r.result + "\n\n" + $("ranksOut").textContent;
    } catch (e) { alert(e.message); }
  }
  $("ranksRefresh").onclick = () => refreshRanks();
  $("ranksApply").onclick = () => ranksAction("apply");
  $("ranksForce").onclick = () => {
    if (!confirm("Force re-apply the YaPPerms rank pack?")) return;
    ranksAction("apply", true);
  };
  $("ranksReset").onclick = () => ranksAction("reset-marker");

  async function refreshEssentials() {
    try {
      const r = await api("/api/essentials");
      $("essInstalled").textContent = r.installed ? "installed" : "missing";
      $("essServerId").textContent = r.serverId || "—";
      const spawn = r.spawn || {};
      $("essSpawnScope").textContent = spawn.scope || "—";
      $("essSpawnWorld").textContent = spawn.world || "—";
      $("essSpawnCoords").textContent = spawn.world
        ? `Spawn: ${spawn.world} @ ${spawn.x}, ${spawn.y}, ${spawn.z} (persist-db=${spawn.persistDb})`
        : "No spawn config yet — use /setspawn in-game.";
      $("essMotd").value = (r.motd || []).join("\n");
      $("essRules").value = (r.rules || []).join("\n");
      renderEssFeatures(r.features || {});
      $("essOut").textContent = r.error ? String(r.error) : "";
    } catch (e) {
      $("essOut").textContent = e.message;
    }
  }

  function renderEssFeatures(features) {
    const grid = $("essFeatures");
    grid.innerHTML = "";
    Object.keys(features).sort().forEach((key) => {
      const lab = document.createElement("label");
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!features[key];
      cb.onchange = () => essSetFeature(key, cb.checked);
      lab.appendChild(cb);
      lab.appendChild(document.createTextNode(key));
      grid.appendChild(lab);
    });
  }

  async function essAction(body) {
    try {
      const r = await api("/api/essentials", { method: "POST", body: JSON.stringify(body) });
      $("essOut").textContent = (r.result || r.note || JSON.stringify(r, null, 2));
      await refreshEssentials();
    } catch (e) { alert(e.message); }
  }

  async function essSetFeature(feature, enabled) {
    try {
      await api("/api/essentials", {
        method: "POST",
        body: JSON.stringify({ action: "set-feature", feature, enabled: enabled ? "true" : "false" }),
      });
    } catch (e) {
      alert(e.message);
      await refreshEssentials();
    }
  }

  $("essRefresh").onclick = () => refreshEssentials();
  $("essReload").onclick = () => essAction({ action: "reload" });
  $("essBroadcastBtn").onclick = () => {
    const message = $("essBroadcast").value.trim();
    if (!message) return;
    essAction({ action: "broadcast", message });
    $("essBroadcast").value = "";
  };
  $("essSaveMotd").onclick = () => essAction({ action: "save-motd", text: $("essMotd").value });
  $("essSaveRules").onclick = () => essAction({ action: "save-rules", text: $("essRules").value });

  async function refreshLink() {
    try {
      const r = await api("/api/link");
      $("linkHome").textContent = r.linkHomeExists ? "ok" : "missing";
      $("linkEmbed").textContent = r.linkEmbed ? "yes" : "no";
      $("linkPluginsOn").textContent = r.pluginsEnabled ? "yes" : "no";
      $("linkSuite").textContent = r.suiteComplete ? "yes" : "partial";
      const sel = r.selector || {};
      $("linkHub").value = sel.hubServer || "lobby";
      $("linkSessionLock").value = sel.sessionLockEnabled ? "true" : "false";
      $("linkPluginsFlag").value = r.pluginsEnabled ? "true" : "false";
      $("linkChatRelay").value = r.chatRelayEnabled ? "true" : "false";

      const servers = r.servers || [];
      const tryList = (r.tryServers || []).join(", ") || "—";
      $("linkServersCard").innerHTML = servers.length
        ? `<strong>Backends</strong> (try: ${tryList})<br/>`
          + servers.map((s) => `<code>${s.name}</code> → ${s.address}`).join("<br/>")
          + `<div class="muted" style="margin-top:8px">bind ${r.bind || "—"} · chat relay ${r.chatRelayEnabled ? "on" : "off"}</div>`
        : `<span class="muted">No link.properties yet — copy link-data/link.properties.example</span>`;

      const ul = $("linkPluginList");
      ul.innerHTML = "";
      (r.plugins || []).forEach((p) => {
        const li = document.createElement("li");
        li.innerHTML = `<div><strong>${p.name}</strong>${p.suite ? " · suite" : ""}`
          + `<div class="meta">${p.id} v${p.version} · ${p.jar}</div></div>`;
        ul.appendChild(li);
      });
      if (!(r.plugins || []).length) {
        const li = document.createElement("li");
        li.innerHTML = `<div class="muted">No jars in link-data/plugins/ — run installIntoLinkPlugins tasks</div>`;
        ul.appendChild(li);
      }

      const mod = r.modSync || {};
      $("linkOut").textContent = [
        r.installHint || "",
        "",
        `Mod sync DB: ${mod.jdbcConfigured ? mod.jdbcUrl : "not configured"}`,
        `Selector hub: ${sel.hubServer || "lobby"} · session lock ${sel.sessionLockEnabled ? "on" : "off"}`,
        r.hint || "",
      ].join("\n");
    } catch (e) {
      $("linkOut").textContent = e.message;
    }
  }

  async function linkPost(body) {
    try {
      const r = await api("/api/link", { method: "POST", body: JSON.stringify(body) });
      $("linkOut").textContent = (r.note || "") + "\n" + JSON.stringify(r, null, 2);
      await refreshLink();
    } catch (e) { alert(e.message); }
  }

  $("linkRefresh").onclick = () => refreshLink();
  $("linkSaveSelector").onclick = () => linkPost({
    action: "save-selector",
    hubServer: $("linkHub").value.trim(),
    sessionLock: $("linkSessionLock").value,
  });
  $("linkSaveFlags").onclick = () => linkPost({
    action: "save-flags",
    pluginsEnabled: $("linkPluginsFlag").value,
    chatRelayEnabled: $("linkChatRelay").value,
  });

  const tabLoads = {
    plugins: loadPlugins,
    modules: loadModules,
    packs: loadPacks,
    settings: loadSettings,
    vehicles: renderVehicles,
    pregen: refreshPregen,
    ranks: refreshRanks,
    essentials: refreshEssentials,
    link: refreshLink,
    protect: refreshProtect,
    world: refreshWorld,
    chat: refreshChat,
    mod: refreshMod,
    perms: refreshPerms,
    data: refreshData,
    discord: refreshDiscord,
    tab: refreshTabPanel,
    map: refreshMap,
    guard: refreshGuard,
    regions: refreshRegions,
    npcs: refreshNpcs,
  };
  document.querySelectorAll(".tabs button").forEach((btn) => {
    const tab = btn.dataset.tab;
    const load = tabLoads[tab];
    if (!load) return;
    btn.addEventListener("click", () => load());
  });
  YapDash.onBoot = () => renderVehicles();

  }
  window.YapDashRegisterOpsPanels = register;
  if (window.YapDash) register(window.YapDash);
})();
