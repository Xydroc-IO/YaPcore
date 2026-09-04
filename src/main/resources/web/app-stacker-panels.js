(() => {
  const $ = (id) => document.getElementById(id);
  const { api, netPost, setOut } = YapDash;

  async function refreshStacker() {
    try {
      const r = await api("/api/stacker");
      $("stkInstalled").textContent = r.installed ? "yes" : "no";
      $("stkEnabled").textContent = r.enabled ? "on" : "off";
      $("stkKillMode").textContent = r.killMode || "—";
      $("stkMobMax").textContent = String(r.mobMaxStack ?? "—");
      if ($("stkEnabledBox")) $("stkEnabledBox").checked = !!r.enabled;
      if ($("stkMobsBox")) $("stkMobsBox").checked = !!r.mobsEnabled;
      if ($("stkItemsBox")) $("stkItemsBox").checked = !!r.itemsEnabled;
      if ($("stkSpawnersBox")) $("stkSpawnersBox").checked = !!r.spawnersEnabled;
      if ($("stkKillSelect") && r.killMode) $("stkKillSelect").value = r.killMode;
      if ($("stkMobMaxInput")) $("stkMobMaxInput").value = r.mobMaxStack ?? 100;
      setOut("stkStatus", r.status || "");
      setOut("stkStatsOut", r.stats || "");
      setOut("stkOut", r.hint || "");
    } catch (e) {
      setOut("stkOut", e.message);
    }
  }

  $("stkRefresh")?.addEventListener("click", () => refreshStacker());
  $("stkReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/stacker", { action: "reload" });
      setOut("stkOut", r.result || "reloaded");
      refreshStacker();
    } catch (e) { setOut("stkOut", e.message); }
  });
  $("stkStats")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/stacker", { action: "stats" });
      setOut("stkStatsOut", r.result || "");
    } catch (e) { setOut("stkOut", e.message); }
  });
  $("stkSave")?.addEventListener("click", async () => {
    try {
      await netPost("/api/stacker", {
        action: "save-settings",
        enabled: $("stkEnabledBox")?.checked ? "true" : "false",
        mobsEnabled: $("stkMobsBox")?.checked ? "true" : "false",
        itemsEnabled: $("stkItemsBox")?.checked ? "true" : "false",
        spawnersEnabled: $("stkSpawnersBox")?.checked ? "true" : "false",
        killMode: $("stkKillSelect")?.value || "DECREMENT",
        mobMaxStack: $("stkMobMaxInput")?.value || "100",
      });
      setOut("stkOut", "Stacker settings saved.");
      refreshStacker();
    } catch (e) { setOut("stkOut", e.message); }
  });

  Object.assign(YapDash.tabLoads, { stacker: refreshStacker });
})();
