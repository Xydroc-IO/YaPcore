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
      if ($("datMaxHomes")) $("datMaxHomes").textContent = String(r.maxHomes ?? "—");
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
      if ($("dscEventsHook")) $("dscEventsHook").textContent = r.eventsConfigured ? "yes" : "no";
      $("dscRelay").textContent = r.mcToDiscord ? "on" : "off";
      $("dscInbound").textContent = r.discordToMc ? "on" : "off";
      $("dscMcRelay").value = r.mcToDiscord ? "true" : "false";
      $("dscDiscordMc").value = r.discordToMc ? "true" : "false";
      if ($("dscInboundEnabled")) $("dscInboundEnabled").value = r.inboundEnabled ? "true" : "false";
      if ($("dscInboundPort")) $("dscInboundPort").value = r.inboundPort ?? 8765;
      if ($("dscInboundSecret") && !r.inboundSecretConfigured) $("dscInboundSecret").value = "";
      if ($("dscEvJoin")) $("dscEvJoin").checked = !!r.eventJoin;
      if ($("dscEvLeave")) $("dscEvLeave").checked = !!r.eventLeave;
      if ($("dscEvDeath")) $("dscEvDeath").checked = !!r.eventDeath;
      if ($("dscEvAdv")) $("dscEvAdv").checked = !!r.eventAdvancement;
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
  $("dscSaveEventsHook")?.addEventListener("click", async () => {
    await netPost("/api/discord", { action: "save-webhook", key: "events", url: $("dscEventsUrl").value.trim() });
    refreshDiscord();
  });
  $("dscTestMod").onclick = async () => {
    $("dscOut").textContent = (await netPost("/api/discord", { action: "test-webhook", key: "moderation" })).result || "sent";
  };
  $("dscTestChat").onclick = async () => {
    $("dscOut").textContent = (await netPost("/api/discord", { action: "test-webhook", key: "chat" })).result || "sent";
  };
  $("dscTestEvents")?.addEventListener("click", async () => {
    $("dscOut").textContent = (await netPost("/api/discord", { action: "test-webhook", key: "events" })).result || "sent";
  });
  $("dscSaveEvents")?.addEventListener("click", async () => {
    await netPost("/api/discord", {
      action: "save-events",
      join: $("dscEvJoin")?.checked ? "true" : "false",
      leave: $("dscEvLeave")?.checked ? "true" : "false",
      death: $("dscEvDeath")?.checked ? "true" : "false",
      advancement: $("dscEvAdv")?.checked ? "true" : "false",
    });
    refreshDiscord();
  });
  $("dscSaveRelay").onclick = async () => {
    await netPost("/api/discord", {
      action: "save-relay",
      mcToDiscord: $("dscMcRelay").value,
      discordToMc: $("dscDiscordMc").value,
    });
    refreshDiscord();
  };
  $("dscSaveInbound")?.addEventListener("click", async () => {
    const body = {
      action: "save-inbound",
      enabled: $("dscInboundEnabled")?.value || "false",
      port: String($("dscInboundPort")?.value || "8765"),
    };
    const secret = ($("dscInboundSecret")?.value || "").trim();
    if (secret) body.secret = secret;
    await netPost("/api/discord", body);
    if ($("dscInboundSecret")) $("dscInboundSecret").value = "";
    refreshDiscord();
  });

  async function refreshTebex() {
    if (!$("tbxInstalled")) return;
    try {
      const r = await api("/api/tebex");
      $("tbxInstalled").textContent = r.installed ? "yes" : "no";
      $("tbxSecret").textContent = r.secretConfigured ? (r.secretMasked || "set") : "not set";
      $("tbxBuy").textContent = r.buyCommandEnabled
        ? ("/" + (r.buyCommandName || "buy"))
        : "off";
      if ($("tbxStore")) {
        const store = r.storeName || "";
        const cur = r.currency ? (" · " + r.currency) : "";
        $("tbxStore").textContent = store ? (store + cur) : (r.connected ? "connected" : "—");
      }
      if ($("tbxHub")) $("tbxHub").textContent = r.hubPlacementDetail || (r.hubOnlyOk ? "ok" : "check");
      if ($("tbxPending")) $("tbxPending").textContent = String(r.pendingCount != null ? r.pendingCount : "—");
      if ($("tbxStuck")) $("tbxStuck").textContent = String(r.stuckCount != null ? r.stuckCount : "—");
      if ($("tbxLastCheck")) {
        const at = r.lastCheckAt || r.lastInfoAt || "";
        $("tbxLastCheck").textContent = at ? String(at).replace("T", " ").slice(0, 19) : "never";
      }
      if ($("tbxHint")) $("tbxHint").textContent = r.setupHint || "";
      if ($("tbxBuyEnabled")) $("tbxBuyEnabled").value = r.buyCommandEnabled ? "true" : "false";
      if ($("tbxBuyName")) $("tbxBuyName").value = r.buyCommandName || "buy";
      if ($("tbxProxyMode")) $("tbxProxyMode").value = r.proxyMode ? "true" : "false";
      if ($("tbxVerbose")) $("tbxVerbose").value = r.verbose ? "true" : "false";
      if ($("tbxCheckUpdates")) $("tbxCheckUpdates").value = r.checkForUpdates ? "true" : "false";
      if ($("tbxAutoReport")) $("tbxAutoReport").value = r.autoReportEnabled ? "true" : "false";
      if ($("tbxGuiTitle")) $("tbxGuiTitle").value = r.guiHomeTitle || "Server Shop";
      if ($("tbxGuiRows")) $("tbxGuiRows").value = String(r.guiHomeRows != null ? r.guiHomeRows : 3);
      if ($("tbxWebhookInstalled")) {
        $("tbxWebhookInstalled").textContent = r.webhookInstalled ? "yes" : "no";
      }
      if ($("tbxWebhookEnabledStat")) {
        $("tbxWebhookEnabledStat").textContent = r.webhookEnabled ? "on" : "off";
      }
      if ($("tbxWebhookSecretStat")) {
        $("tbxWebhookSecretStat").textContent = r.webhookSecretConfigured ? "set" : "not set";
      }
      if ($("tbxWebhookListen")) {
        $("tbxWebhookListen").textContent = r.webhookListenHint || "—";
      }
      if ($("tbxWebhookPkgs")) {
        $("tbxWebhookPkgs").textContent = String(r.webhookPackageCount != null ? r.webhookPackageCount : "—");
      }
      if ($("tbxWebhookLast")) {
        const ls = r.webhookLastStatus || {};
        const at = ls.at ? String(ls.at).replace("T", " ").slice(0, 19) : "";
        const detail = ls.detail || ls.type || "";
        $("tbxWebhookLast").textContent = at ? (at + (detail ? " · " + detail : "")) : "never";
      }
      if ($("tbxWebhookUrlHint") && r.webhookUrlHint) {
        $("tbxWebhookUrlHint").textContent = r.webhookUrlHint;
      }
      if ($("tbxWebhookEnabled")) $("tbxWebhookEnabled").value = r.webhookEnabled ? "true" : "false";
      if ($("tbxWebhookPort")) $("tbxWebhookPort").value = String(r.webhookPort != null ? r.webhookPort : 8766);
      if ($("tbxWebhookEnforceIps")) {
        $("tbxWebhookEnforceIps").value = r.webhookEnforceIps === false ? "false" : "true";
      }
      if ($("tbxWebhookSecret") && !r.webhookSecretConfigured) $("tbxWebhookSecret").value = "";
      if ($("tbxOpenCreator") && r.creatorUrl) $("tbxOpenCreator").href = r.creatorUrl;
      if ($("tbxOpenDocs") && r.docsUrl) $("tbxOpenDocs").href = r.docsUrl;
      if ($("tbxOpenYapDocs") && r.yapDocs) $("tbxOpenYapDocs").href = r.yapDocs;
      if ($("tbxRecipesYaml") && r.recipesYaml != null) $("tbxRecipesYaml").value = r.recipesYaml;
      if ($("tbxGrantHint")) $("tbxGrantHint").textContent = r.dbHint || $("tbxGrantHint").textContent;
      if ($("tbxStatusBox")) {
        const bits = [];
        if (r.storeName) bits.push("Store: " + r.storeName);
        if (r.serverName) bits.push("Server: " + r.serverName);
        if (r.webstoreUrl) bits.push("URL: " + r.webstoreUrl);
        if (r.hubPlacementDetail) bits.push("Placement: " + r.hubPlacementDetail);
        $("tbxStatusBox").textContent = bits.join(" · ");
      }
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
          const use = document.createElement("button");
          use.type = "button";
          use.textContent = "Edit";
          use.onclick = () => {
            if ($("tbxRecipeName")) $("tbxRecipeName").value = recipe.name || "";
            if ($("tbxRecipeCommands")) $("tbxRecipeCommands").value = recipe.commands || "";
          };
          wrap.appendChild(title);
          wrap.appendChild(pre);
          wrap.appendChild(copy);
          wrap.appendChild(use);
          box.appendChild(wrap);
        });
      }
      renderTebexGrants(r);
      if (!r.installed && r.fetchHint) {
        $("tbxOut").textContent = "Plugin missing — run: " + r.fetchHint;
      }
    } catch (e) {
      if ($("tbxOut")) $("tbxOut").textContent = e.message;
    }
  }

  function renderTebexGrants(r) {
    const box = $("tbxGrants");
    if (!box) return;
    box.innerHTML = "";
    const rows = []
      .concat(r.stuckGrants || [])
      .concat(r.pendingGrants || []);
    if (!rows.length) {
      box.innerHTML = "<p class=\"muted\">No pending or stuck kit grants.</p>";
      return;
    }
    rows.forEach((g) => {
      const wrap = document.createElement("div");
      wrap.className = "card";
      const who = g.username || g.uuid || "?";
      wrap.innerHTML = "<strong>" + who + "</strong> · kit <code>" + (g.kit || "") + "</code>"
        + " · <span class=\"muted\">" + (g.status || "") + "</span>"
        + (g.createdAt ? " · " + String(g.createdAt).replace("T", " ").slice(0, 19) : "");
      const cancel = document.createElement("button");
      cancel.type = "button";
      cancel.textContent = "Cancel grant";
      cancel.onclick = async () => {
        try {
          const res = await netPost("/api/tebex", { action: "cancel-grant", id: String(g.id) });
          $("tbxOut").textContent = res.ok ? ("Cancelled grant #" + g.id) : (res.error || "not found");
          refreshTebex();
        } catch (e) { $("tbxOut").textContent = e.message; }
      };
      wrap.appendChild(cancel);
      box.appendChild(wrap);
    });
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
          checkForUpdates: $("tbxCheckUpdates") ? $("tbxCheckUpdates").value : "true",
          autoReportEnabled: $("tbxAutoReport") ? $("tbxAutoReport").value : "true",
          guiHomeTitle: $("tbxGuiTitle") ? $("tbxGuiTitle").value.trim() : "",
          guiHomeRows: $("tbxGuiRows") ? $("tbxGuiRows").value : "3",
        });
        $("tbxOut").textContent = r.result || "Settings saved.";
        refreshTebex();
      } catch (e) { $("tbxOut").textContent = e.message; }
    };
    if ($("tbxSaveWebhook")) {
      $("tbxSaveWebhook").onclick = async () => {
        try {
          const body = {
            action: "save-webhook",
            webhookEnabled: $("tbxWebhookEnabled")?.value || "false",
            webhookPort: String($("tbxWebhookPort")?.value || "8766"),
            webhookEnforceIps: $("tbxWebhookEnforceIps")?.value || "true",
          };
          const secret = ($("tbxWebhookSecret")?.value || "").trim();
          if (secret) body.webhookSecret = secret;
          const r = await netPost("/api/tebex", body);
          if ($("tbxWebhookSecret")) $("tbxWebhookSecret").value = "";
          $("tbxOut").textContent = r.result || "Webhook settings saved.";
          refreshTebex();
        } catch (e) { $("tbxOut").textContent = e.message; }
      };
    }
    $("tbxInfo").onclick = async () => {
      const r = await netPost("/api/tebex", { action: "info" });
      $("tbxOut").textContent = r.result || "";
      refreshTebex();
    };
    $("tbxForceCheck").onclick = async () => {
      const r = await netPost("/api/tebex", { action: "forcecheck" });
      $("tbxOut").textContent = r.result || "ok";
      refreshTebex();
    };
    if ($("tbxUpsertRecipe")) {
      $("tbxUpsertRecipe").onclick = async () => {
        try {
          await netPost("/api/tebex", {
            action: "upsert-recipe",
            name: ($("tbxRecipeName").value || "").trim(),
            commands: $("tbxRecipeCommands").value || "",
          });
          $("tbxOut").textContent = "Recipe saved.";
          refreshTebex();
        } catch (e) { $("tbxOut").textContent = e.message; }
      };
    }
    if ($("tbxDeleteRecipe")) {
      $("tbxDeleteRecipe").onclick = async () => {
        try {
          await netPost("/api/tebex", {
            action: "delete-recipe",
            name: ($("tbxRecipeName").value || "").trim(),
          });
          $("tbxOut").textContent = "Recipe deleted.";
          refreshTebex();
        } catch (e) { $("tbxOut").textContent = e.message; }
      };
    }
    if ($("tbxSaveRecipesYaml")) {
      $("tbxSaveRecipesYaml").onclick = async () => {
        try {
          await netPost("/api/tebex", {
            action: "save-recipes",
            yaml: $("tbxRecipesYaml").value || "",
          });
          $("tbxOut").textContent = "Recipes YAML saved.";
          refreshTebex();
        } catch (e) { $("tbxOut").textContent = e.message; }
      };
    }
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
