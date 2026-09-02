window.YapDashRegisterNetworkPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  async function refreshPerms() {
    if (!$("prmInstalled")) return;
    try {
      const r = await api("/api/perms");
      $("prmInstalled").textContent = r.installed ? "yes" : "no";
      $("prmDefault").textContent = r.defaultGroup || "—";
      $("prmTrack").textContent = r.defaultTrack || "—";
      $("prmGroups").textContent = (r.groups || []).join(", ") || "default, vip, mod, admin";
      if (r.groups && r.groups.length) {
        const sel = $("prmGroup");
        const cur = sel.value;
        sel.innerHTML = "";
        r.groups.forEach((g) => {
          const o = document.createElement("option");
          o.value = g;
          o.textContent = g;
          sel.appendChild(o);
        });
        if (cur) sel.value = cur;
      }
    } catch (e) { $("prmOut").textContent = e.message; }
  }
  if ($("prmRefresh")) {
    $("prmRefresh").onclick = () => refreshPerms();
    $("prmReload").onclick = async () => { $("prmOut").textContent = (await netPost("/api/perms", { action: "reload" })).result || "ok"; refreshPerms(); };
    $("prmApplypack").onclick = async () => { $("prmOut").textContent = (await netPost("/api/perms", { action: "applypack" })).result || "ok"; };
    $("prmLookup").onclick = async () => {
      const p = $("prmPlayer").value.trim();
      if (!p) return;
      $("prmOut").textContent = (await netPost("/api/perms", { action: "user-info", player: p })).result || "";
    };
    $("prmSetGroup").onclick = async () => {
      const p = $("prmPlayer").value.trim();
      if (!p) { alert("Enter player name."); return; }
      $("prmOut").textContent = (await netPost("/api/perms", { action: "set-group", player: p, group: $("prmGroup").value })).result || "ok";
    };
    $("prmPromote").onclick = async () => {
      const p = $("prmPlayer").value.trim();
      if (!p) return;
      $("prmOut").textContent = (await netPost("/api/perms", { action: "promote", player: p })).result || "ok";
    };
    $("prmDemote").onclick = async () => {
      const p = $("prmPlayer").value.trim();
      if (!p) return;
      $("prmOut").textContent = (await netPost("/api/perms", { action: "demote", player: p })).result || "ok";
    };
  }

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

  async function refreshTebex() {
    if (!$("tbxInstalled")) return;
    try {
      const r = await api("/api/tebex");
      $("tbxInstalled").textContent = r.installed ? "yes" : "no";
      $("tbxSecret").textContent = r.secretConfigured ? (r.secretMasked || "set") : "not set";
      $("tbxBuy").textContent = r.buyCommandEnabled
        ? ("/" + (r.buyCommandName || "buy"))
        : "off";
      $("tbxProxy").textContent = r.proxyMode ? "on" : "off";
      if ($("tbxHint")) $("tbxHint").textContent = r.setupHint || "";
      if ($("tbxBuyEnabled")) $("tbxBuyEnabled").value = r.buyCommandEnabled ? "true" : "false";
      if ($("tbxBuyName")) $("tbxBuyName").value = r.buyCommandName || "buy";
      if ($("tbxProxyMode")) $("tbxProxyMode").value = r.proxyMode ? "true" : "false";
      if ($("tbxVerbose")) $("tbxVerbose").value = r.verbose ? "true" : "false";
      if ($("tbxOpenCreator") && r.creatorUrl) $("tbxOpenCreator").href = r.creatorUrl;
      if ($("tbxOpenDocs") && r.docsUrl) $("tbxOpenDocs").href = r.docsUrl;
      const box = $("tbxRecipes");
      if (box) {
        box.innerHTML = "";
        (r.packageRecipes || []).forEach((recipe) => {
          const wrap = document.createElement("div");
          wrap.className = "card";
          const title = document.createElement("h4");
          title.textContent = recipe.name || "Package";
          const pre = document.createElement("pre");
          pre.className = "report";
          pre.textContent = recipe.commands || "";
          const copy = document.createElement("button");
          copy.type = "button";
          copy.textContent = "Copy";
          copy.onclick = async () => {
            try {
              await navigator.clipboard.writeText(recipe.commands || "");
              copy.textContent = "Copied";
              setTimeout(() => { copy.textContent = "Copy"; }, 1200);
            } catch {
              alert(recipe.commands || "");
            }
          };
          wrap.appendChild(title);
          wrap.appendChild(pre);
          wrap.appendChild(copy);
          box.appendChild(wrap);
        });
      }
      if (!r.installed && r.fetchHint) {
        $("tbxOut").textContent = "Plugin missing — run: " + r.fetchHint;
      }
    } catch (e) {
      if ($("tbxOut")) $("tbxOut").textContent = e.message;
    }
  }
  if ($("tbxRefresh")) {
    $("tbxRefresh").onclick = () => refreshTebex();
    $("tbxReload").onclick = async () => {
      $("tbxOut").textContent = (await netPost("/api/tebex", { action: "reload" })).result || "ok";
      refreshTebex();
    };
    $("tbxSaveSecret").onclick = async () => {
      const secret = ($("tbxSecretInput").value || "").trim();
      if (!secret) { alert("Paste your Tebex game-server secret key."); return; }
      try {
        const r = await netPost("/api/tebex", { action: "set-secret", secret });
        $("tbxOut").textContent = r.result || "Secret saved.";
        $("tbxSecretInput").value = "";
        refreshTebex();
      } catch (e) { $("tbxOut").textContent = e.message; }
    };
    $("tbxSaveSettings").onclick = async () => {
      try {
        const r = await netPost("/api/tebex", {
          action: "save-settings",
          buyCommandEnabled: $("tbxBuyEnabled").value,
          buyCommandName: $("tbxBuyName").value.trim() || "buy",
          proxyMode: $("tbxProxyMode").value,
          verbose: $("tbxVerbose").value,
        });
        $("tbxOut").textContent = r.result || "Settings saved.";
        refreshTebex();
      } catch (e) { $("tbxOut").textContent = e.message; }
    };
    $("tbxInfo").onclick = async () => {
      $("tbxOut").textContent = (await netPost("/api/tebex", { action: "info" })).result || "";
    };
    $("tbxForceCheck").onclick = async () => {
      $("tbxOut").textContent = (await netPost("/api/tebex", { action: "forcecheck" })).result || "ok";
    };
  }

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

  Object.assign(YapDash.tabLoads, {
    perms: refreshPerms,
    data: refreshData,
    discord: refreshDiscord,
    tebex: refreshTebex,
    tab: refreshTabPanel,
  });
};
