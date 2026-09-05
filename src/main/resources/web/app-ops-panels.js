(() => {
  function register(YapDash) {
    const { $, api, netPost } = YapDash;

  let settingsCfg = {};
  let settingsAdvanced = false;

  function paintSettings() {
    if (window.YapFriendlyForms) {
      window.YapFriendlyForms.renderSettings($("settingsForm"), settingsCfg, settingsAdvanced);
    }
  }

  async function loadSettings() {
    settingsCfg = await api("/api/config");
    paintSettings();
    const out = $("settingsOut");
    if (out) { out.hidden = true; out.textContent = ""; }
  }

  $("settingsShowAdvanced")?.addEventListener("click", () => {
    settingsAdvanced = !settingsAdvanced;
    $("settingsShowAdvanced").textContent = settingsAdvanced ? "Hide advanced" : "Show advanced";
    paintSettings();
  });

  $("saveSettings").onclick = async () => {
    const body = window.YapFriendlyForms
      ? window.YapFriendlyForms.collect($("settingsForm"))
      : {};
    const out = $("settingsOut");
    try {
      await api("/api/config", { method: "POST", body: JSON.stringify(body) });
      Object.assign(settingsCfg, body);
      if (out) {
        out.hidden = false;
        out.className = "easy-save-msg ok";
        out.textContent = "Saved. Restart the server if you changed ports or RAM.";
      }
      await YapDash.refreshStatus();
    } catch (e) {
      if (out) {
        out.hidden = false;
        out.className = "easy-save-msg err";
        out.textContent = e.message;
      } else {
        alert(e.message);
      }
    }
  };

  function pluginTierBadge(tier) {
    const t = (tier || "THIRD_PARTY").toUpperCase();
    if (t === "CORE") return `<span class="badge warn">CORE</span>`;
    if (t === "GAMEPLAY") return `<span class="badge">GAMEPLAY</span>`;
    if (t === "NETWORK") return `<span class="badge on">NETWORK</span>`;
    return `<span class="badge">3rd-party</span>`;
  }

  async function pluginAction(body) {
    const r = await api("/api/plugins", { method: "POST", body: JSON.stringify(body) });
    if (r && r.needsRestart) {
      alert((r.fileName || "Plugin") + ": Folia restart required for hard jar changes to take effect.");
    } else if (r && r.reload) {
      // soft reload attempted; keep quiet unless softEnabled flipped with no reload
    }
    return r;
  }

  async function loadPlugins() {
    const d = await api("/api/plugins");
    const ul = $("pluginList");
    ul.innerHTML = "";
    (d.plugins || []).forEach((p) => {
      const li = document.createElement("li");
      const softLabel = p.softSupported === false || p.softSupported === "false"
        ? "soft n/a"
        : (p.softEnabled === true || p.softEnabled === "true" ? "soft on" : p.softEnabled === false || p.softEnabled === "false" ? "soft off" : "soft ?");
      const hardOn = p.hardEnabled === true || p.hardEnabled === "true";
      const compat = p.compatWarning
        ? `<span class="badge warn">${p.compatStatus}${p.nativeAlternative ? " → " + p.nativeAlternative : ""}</span>`
        : p.compatStatus === "native"
          ? `<span class="badge on">native</span>`
          : p.compatStatus === "works"
            ? `<span class="badge">works</span>`
            : "";
      const title = p.title && p.title !== p.activeName ? p.title + " · " : "";
      li.innerHTML = `<div><strong>${p.activeName || p.fileName}</strong> ${pluginTierBadge(p.tier)} ${compat}
        <div class="meta">${title}${p.sizeLabel || ""} · ${softLabel} · hard ${hardOn ? "on" : "off"}${p.compatNote ? " · " + p.compatNote : ""}${!hardOn ? " · renamed .disabled" : ""}</div></div>`;
      const actions = document.createElement("div");
      actions.className = "plugin-actions";

      if (p.softSupported !== false && p.softSupported !== "false") {
        const softBtn = document.createElement("button");
        const softOn = p.softEnabled === true || p.softEnabled === "true";
        softBtn.textContent = softOn ? "Soft off" : "Soft on";
        softBtn.title = "Writes enabled in plugin config (reload when available)";
        softBtn.onclick = async () => {
          try {
            const force = p.protected === true || p.protected === "true"
              ? confirm("CORE plugin — confirm soft " + (softOn ? "disable" : "enable") + "?")
              : false;
            if ((p.protected === true || p.protected === "true") && softOn && !force) return;
            await pluginAction({
              action: softOn ? "disable" : "enable",
              fileName: p.fileName,
              mode: "soft",
              force: force ? "true" : "false",
            });
            loadPlugins();
          } catch (e) { alert(e.message); }
        };
        actions.appendChild(softBtn);
      }

      const hardBtn = document.createElement("button");
      hardBtn.textContent = hardOn ? "Hard off" : "Hard on";
      hardBtn.title = "Rename jar ↔ .jar.disabled (Folia restart required)";
      hardBtn.onclick = async () => {
        try {
          if (hardOn && !confirm("Hard-disable " + (p.activeName || p.fileName) + "? Jar will not load after Folia restart.")) return;
          const force = (p.protected === true || p.protected === "true") && hardOn
            ? confirm("CORE hard-disable requires force. Continue?")
            : false;
          if ((p.protected === true || p.protected === "true") && hardOn && !force) return;
          await pluginAction({
            action: hardOn ? "disable" : "enable",
            fileName: p.fileName,
            mode: "hard",
            force: force ? "true" : "false",
          });
          loadPlugins();
        } catch (e) { alert(e.message); }
      };
      actions.appendChild(hardBtn);

      const rm = document.createElement("button");
      rm.className = "danger";
      rm.textContent = "Uninstall";
      rm.onclick = async () => {
        try {
          if (!confirm("Delete " + p.fileName + " from plugins/? This cannot be undone.")) return;
          let force = false;
          if (p.protected === true || p.protected === "true") {
            force = confirm("CORE uninstall requires force. Type OK in the next dialog…") &&
              prompt("Type FORCE to uninstall CORE plugin " + p.fileName) === "FORCE";
            if (!force) return;
          }
          await api("/api/plugins", {
            method: "DELETE",
            body: JSON.stringify({ fileName: p.fileName, force: force ? "true" : "false" }),
          });
          loadPlugins();
        } catch (e) { alert(e.message); }
      };
      actions.appendChild(rm);
      li.appendChild(actions);
      ul.appendChild(li);
    });
  }
  $("refreshPlugins").onclick = () => loadPlugins().catch((e) => alert(e.message));
  $("addPlugin").onclick = async () => {
    try {
      await pluginAction({ action: "install", path: $("pluginPath").value.trim() });
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

  function linkServerRow(name, address, bedrock) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><input class="link-srv-name" value="${escHtml(name || "")}" placeholder="hub"/></td>
      <td><input class="link-srv-addr" value="${escHtml(address || "")}" placeholder="127.0.0.1:25566"/></td>
      <td><input class="link-srv-bedrock" value="${escHtml(bedrock || "")}" placeholder="optional"/></td>
      <td class="row-actions"><button type="button" class="link-srv-remove danger">Remove</button></td>`;
    tr.querySelector(".link-srv-remove").onclick = () => tr.remove();
    return tr;
  }

  function linkForcedRow(host, server) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><input class="link-fh-host" value="${escHtml(host || "")}" placeholder="hub.example.com"/></td>
      <td><input class="link-fh-server" value="${escHtml(server || "")}" placeholder="hub"/></td>
      <td class="row-actions"><button type="button" class="link-fh-remove danger">Remove</button></td>`;
    tr.querySelector(".link-fh-remove").onclick = () => tr.remove();
    return tr;
  }

  function escHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function renderLinkServers(servers) {
    const body = $("linkServersBody");
    body.innerHTML = "";
    const list = servers && servers.length ? servers : [{ name: "hub", address: "127.0.0.1:25566", bedrock: "" }];
    list.forEach((s) => body.appendChild(linkServerRow(s.name, s.address, s.bedrock || "")));
  }

  function renderLinkForced(forced) {
    const body = $("linkForcedBody");
    body.innerHTML = "";
    (forced || []).forEach((f) => body.appendChild(linkForcedRow(f.host, f.server)));
  }

  function collectLinkServers() {
    return [...document.querySelectorAll("#linkServersBody tr")].map((tr) => ({
      name: tr.querySelector(".link-srv-name").value.trim(),
      address: tr.querySelector(".link-srv-addr").value.trim(),
      bedrock: tr.querySelector(".link-srv-bedrock").value.trim(),
    })).filter((s) => s.name || s.address);
  }

  function collectLinkForced() {
    return [...document.querySelectorAll("#linkForcedBody tr")].map((tr) => ({
      host: tr.querySelector(".link-fh-host").value.trim(),
      server: tr.querySelector(".link-fh-server").value.trim(),
    })).filter((f) => f.host);
  }

  function collectLinkTry() {
    const raw = $("linkTryOrder").value.trim();
    if (!raw) return [];
    return raw.split(/[,\s]+/).map((s) => s.trim()).filter(Boolean);
  }

  async function refreshLink() {
    try {
      const r = await api("/api/link");
      const running = r.linkRunning ? "running" : "stopped";
      $("linkRunning").textContent = r.linkEmbed ? "embedded" : running;
      $("linkHome").textContent = r.linkHomeExists ? "ok" : "missing";
      $("linkEmbed").textContent = r.linkEmbed ? "yes" : "no";
      $("linkPluginsOn").textContent = r.pluginsEnabled ? "yes" : "no";
      $("linkSuite").textContent = r.suiteComplete ? "yes" : "partial";
      $("linkStart").disabled = r.linkEmbed || r.linkRunning;
      $("linkStop").disabled = r.linkEmbed || !r.linkRunning;

      $("linkBind").value = r.bind || "0.0.0.0:25565";
      $("linkMotd").value = r.motd || "YaP Link";
      $("linkMaxPlayers").value = r.maxPlayers || "500";
      $("linkOnlineMode").value = r.onlineMode ? "true" : "false";
      $("linkPublicHost").value = r.publicHost || "127.0.0.1";
      $("linkPublicPort").value = r.publicPort || "0";
      $("linkPingPassthrough").value = r.pingPassthrough !== false ? "true" : "false";
      $("linkAggregateCount").value = r.aggregatePlayerCount !== false ? "true" : "false";
      $("linkGlobalTab").value = r.globalTabList ? "true" : "false";
      $("linkPluginsFlag").value = r.pluginsEnabled ? "true" : "false";
      $("linkChatRelay").value = r.chatRelayEnabled ? "true" : "false";
      $("linkChatChannel").value = r.chatRelayChannel || "network";
      $("linkChatFormat").value = r.chatRelayFormat || "[{server}] {name}: {message}";
      $("linkJoinAnnounce").value = r.chatJoinAnnounce ? "true" : "false";
      $("linkBedrockEnabled").value = r.bedrockEnabled ? "true" : "false";
      $("linkBedrockBind").value = r.bedrockBind || "0.0.0.0:19132";
      $("linkBedrockBackend").value = r.bedrockBackend || "127.0.0.1:25566";

      const sel = r.selector || {};
      $("linkHub").value = sel.hubServer || "lobby";
      $("linkSessionLock").value = sel.sessionLockEnabled ? "true" : "false";

      renderLinkServers(r.servers);
      $("linkTryOrder").value = (r.tryServers || []).join(", ");
      renderLinkForced(r.forcedHosts);

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
        li.innerHTML = `<div class="muted">No jars in link-data/plugins/</div>`;
        ul.appendChild(li);
      }

      const mod = r.modSync || {};
      $("linkOut").textContent = [
        r.hint || "",
        r.linkConsoleHint || "",
        "",
        `Jar: ${r.linkJarPresent ? "ok" : "missing"}`,
        `Mod sync DB: ${mod.jdbcConfigured ? mod.jdbcUrl : "not configured"}`,
        `Selector hub: ${sel.hubServer || "lobby"} · session lock ${sel.sessionLockEnabled ? "on" : "off"}`,
      ].join("\n");
    } catch (e) {
      $("linkOut").textContent = e.message;
    }
  }

  let linkEs = null;

  function connectLinkConsole() {
    if (linkEs) linkEs.close();
    const token = localStorage.getItem("yap_token") || "";
    const url = "/api/link/console/stream?token=" + encodeURIComponent(token);
    linkEs = new EventSource(url);
    const out = $("linkConsoleOut");
    linkEs.onmessage = (ev) => {
      if (!ev.data) return;
      out.textContent += (out.textContent ? "\n" : "") + ev.data;
      out.scrollTop = out.scrollHeight;
    };
    api("/api/link/console").then((d) => {
      if (d.text) out.textContent = d.text;
      out.scrollTop = out.scrollHeight;
    }).catch(() => {});
  }

  async function linkPost(body) {
    try {
      const r = await api("/api/link", { method: "POST", body: JSON.stringify(body) });
      if (r.output) {
        $("linkConsoleOut").textContent += "\n" + r.output;
      }
      $("linkOut").textContent = (r.note || r.result || "") + "\n" + JSON.stringify(r, null, 2);
      await refreshLink();
    } catch (e) { alert(e.message); }
  }

  $("linkRefresh").onclick = () => { refreshLink(); connectLinkConsole(); };
  $("linkStart").onclick = () => linkPost({ action: "start" });
  $("linkStop").onclick = () => {
    if (!confirm("Stop YaP Link?")) return;
    linkPost({ action: "stop" });
  };
  $("linkEnableForwarding").onclick = () => linkPost({ action: "enable-backend-forwarding" });
  $("linkAddServer").onclick = () => $("linkServersBody").appendChild(linkServerRow("", "", ""));
  $("linkAddForced").onclick = () => $("linkForcedBody").appendChild(linkForcedRow("", ""));
  $("linkSaveProxy").onclick = () => linkPost({
    action: "save-proxy",
    bind: $("linkBind").value.trim(),
    motd: $("linkMotd").value.trim(),
    maxPlayers: $("linkMaxPlayers").value.trim(),
    onlineMode: $("linkOnlineMode").value,
    publicHost: $("linkPublicHost").value.trim(),
    publicPort: $("linkPublicPort").value.trim(),
    pingPassthrough: $("linkPingPassthrough").value,
    aggregatePlayerCount: $("linkAggregateCount").value,
    globalTabList: $("linkGlobalTab").value,
    pluginsEnabled: $("linkPluginsFlag").value,
    chatRelayEnabled: $("linkChatRelay").value,
    chatRelayChannel: $("linkChatChannel").value.trim(),
    chatRelayFormat: $("linkChatFormat").value.trim(),
    chatJoinAnnounce: $("linkJoinAnnounce").value,
    bedrockEnabled: $("linkBedrockEnabled").value,
    bedrockBind: $("linkBedrockBind").value.trim(),
    bedrockBackend: $("linkBedrockBackend").value.trim(),
  });
  $("linkSaveServers").onclick = () => linkPost({
    action: "save-servers",
    servers: collectLinkServers(),
    try: collectLinkTry(),
    forcedHosts: collectLinkForced(),
  });
  $("linkSaveSelector").onclick = () => linkPost({
    action: "save-selector",
    hubServer: $("linkHub").value.trim(),
    sessionLock: $("linkSessionLock").value,
  });
  $("linkCmdForm").onsubmit = async (e) => {
    e.preventDefault();
    const cmd = $("linkCmdInput").value.trim();
    if (!cmd) return;
    $("linkCmdInput").value = "";
    try {
      await linkPost({ action: "command", command: cmd });
    } catch (err) {
      alert(err.message);
    }
  };

  Object.assign(YapDash.tabLoads, {
    plugins: loadPlugins,
    modules: loadModules,
    packs: loadPacks,
    settings: loadSettings,
    pregen: refreshPregen,
    ranks: refreshRanks,
    essentials: refreshEssentials,
    link: () => { refreshLink(); connectLinkConsole(); },
    players: () => { if (YapDash.refreshPlayers) YapDash.refreshPlayers(); },
  });

  }
  window.YapDashRegisterOpsPanels = register;
  if (window.YapDash) register(window.YapDash);
})();
