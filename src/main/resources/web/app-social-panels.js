window.YapDashRegisterSocialPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  function setOut(id, text, err) {
    const el = $(id);
    if (!el) return;
    el.textContent = text || "";
    el.className = "report muted-small" + (err ? " err" : "");
  }

  function setText(id, value) {
    const el = $(id);
    if (el) el.replaceChildren(document.createTextNode(value == null ? "—" : String(value)));
  }

  function checked(id, on) {
    const el = $(id);
    if (el) el.checked = !!on;
  }

  function num(id, value) {
    const el = $(id);
    if (el && value != null) el.value = value;
  }

  function paintPreview(tbodyId, emptyId, rows, cols) {
    const tbody = $(tbodyId);
    const empty = $(emptyId);
    if (!tbody) return;
    tbody.innerHTML = "";
    const list = Array.isArray(rows) ? rows : [];
    if (!list.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    list.forEach((row) => {
      const tr = document.createElement("tr");
      tr.innerHTML = cols.map((c) => `<td>${row[c] ?? "—"}</td>`).join("");
      tbody.appendChild(tr);
    });
  }

  async function refreshFactions() {
    try {
      const d = await api("/api/factions");
      setText("facInstalled", d.installed ? "yap-factions" : "missing");
      setText("facCount", d.factions ?? 0);
      setText("facMembers", d.members ?? 0);
      setText("facClaims", d.claimOverlays ?? 0);
      setText("facAllies", d.alliances ?? 0);
      checked("facEnabled", d.enabled !== false);
      checked("facBank", d.bankEnabled !== false);
      checked("facAlliesBuild", d.alliesCanBuild !== false);
      checked("facEnemyPvp", d.enemyPvpOnly !== false);
      num("facBasePower", d.baseMaxPower ?? 50);
      num("facPowerMember", d.powerPerMember ?? 10);
      num("facClaimBlocks", d.claimBlocksPerPower ?? 100);
      paintPreview("facPreviewBody", "facPreviewEmpty", d.preview, ["name", "tag", "power", "maxPower"]);
      setOut("facOut", d.error || d.live || "");
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  }

  $("facRefresh")?.addEventListener("click", refreshFactions);
  $("facReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", { action: "reload" });
      setOut("facOut", r.result || "Reloaded.");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSave")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "save-settings",
        enabled: $("facEnabled")?.checked ? "true" : "false",
        bankEnabled: $("facBank")?.checked ? "true" : "false",
        alliesCanBuild: $("facAlliesBuild")?.checked ? "true" : "false",
        enemyPvpOnly: $("facEnemyPvp")?.checked ? "true" : "false",
        baseMaxPower: $("facBasePower")?.value || "50",
        powerPerMember: $("facPowerMember")?.value || "10",
        claimBlocksPerPower: $("facClaimBlocks")?.value || "100",
      });
      setOut("facOut", r.reload || "Saved.");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSetPower")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "setpower",
        faction: $("facAdminName")?.value || "",
        power: $("facAdminPower")?.value || "0",
        max: $("facAdminMax")?.value || "",
      });
      setOut("facOut", r.result || r.command || "OK");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facSetJoin")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "setjoin",
        faction: $("facAdminName")?.value || "",
        mode: $("facAdminJoin")?.value || "invite",
      });
      setOut("facOut", r.result || r.command || "OK");
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });
  $("facDisband")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/factions", {
        action: "disband",
        faction: $("facAdminName")?.value || "",
      });
      setOut("facOut", r.result || r.command || "OK");
      await refreshFactions();
    } catch (e) {
      setOut("facOut", e.message, true);
    }
  });

  Object.assign(YapDash.tabLoads, {
    factions: refreshFactions,
  });
};
