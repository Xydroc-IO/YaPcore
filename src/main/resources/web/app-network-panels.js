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
    tab: refreshTabPanel,
  });
};
