/* Database setup card on Fleet tab — /api/database */
(function () {
  const $ = (id) => document.getElementById(id);
  const api = (...args) => window.YapDash.api(...args);
  const toast = (m, k) => {
    if (window.YapFleetContext?.toast) return window.YapFleetContext.toast(m, k);
    if (typeof window.toast === "function") return window.toast(m, k);
  };

  function engine() {
    return ($("dbEngine")?.value || "mysql").trim();
  }

  function appendLog(text) {
    const el = $("dbLog");
    if (!el) return;
    el.textContent += (typeof text === "string" ? text : JSON.stringify(text, null, 2)) + "\n";
    el.scrollTop = el.scrollHeight;
  }

  function renderStatus(s) {
    const line = $("dbStatusLine");
    if (!line || !s) return;
    const yapdb = s.yapdb || {};
    const maria = s.mariadb || {};
    const pg = s.postgres || {};
    line.textContent =
      "Docker running: " + !!s.dockerRunning +
      " · bash: " + !!s.bashAvailable +
      " · MariaDB: " + (maria.health || "n/a") +
      " · Postgres: " + (pg.health || "n/a") +
      " · JDBC: " + (yapdb.jdbcUrl || "(not configured)");
  }

  async function refresh() {
    try {
      const s = await api("/api/database");
      renderStatus(s);
    } catch (e) {
      appendLog("Status failed: " + (e.message || e));
    }
  }

  async function ensure() {
    try {
      appendLog("Setting up " + engine() + "…");
      const r = await api("/api/database", {
        method: "POST",
        body: JSON.stringify({
          action: "ensure",
          engine: engine(),
          serverId: $("dbServerId")?.value?.trim() || "lobby",
          syncFleet: "true",
        }),
      });
      if (r.ensure?.output) appendLog(r.ensure.output);
      else appendLog(JSON.stringify(r, null, 2));
      toast(r.ok ? "Database ready" : (r.error || "Setup failed"));
      await refresh();
    } catch (e) {
      appendLog("Ensure failed: " + (e.message || e));
      toast(e.message || "Setup failed");
    }
  }

  async function docker(start) {
    try {
      appendLog((start ? "Starting" : "Stopping") + " Docker for " + engine() + "…");
      const r = await api("/api/database", {
        method: "POST",
        body: JSON.stringify({
          action: start ? "start-docker" : "stop-docker",
          engine: engine(),
        }),
      });
      appendLog(r.output || JSON.stringify(r, null, 2));
      toast(r.ok ? "Docker action done" : (r.error || "Docker action failed"));
      await refresh();
    } catch (e) {
      appendLog(String(e.message || e));
      toast(e.message || "Docker failed");
    }
  }

  async function syncFleet() {
    try {
      const r = await api("/api/database", {
        method: "POST",
        body: JSON.stringify({ action: "sync-fleet" }),
      });
      appendLog("Synced " + (r.filesWritten || 0) + " file(s) to fleet instances");
      toast("Fleet catalog synced");
    } catch (e) {
      appendLog(String(e.message || e));
      toast(e.message || "Sync failed");
    }
  }

  function bind() {
    if (!$("dbEnsure")) return;
    $("dbEnsure").onclick = ensure;
    $("dbStartDocker").onclick = () => docker(true);
    $("dbStopDocker").onclick = () => docker(false);
    $("dbRefresh").onclick = refresh;
    $("dbSyncFleet").onclick = syncFleet;
    // Defer until YapDash exists (app-core loads after this file).
    setTimeout(refresh, 0);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }

  window.YapDatabase = { refresh, ensure };
})();
