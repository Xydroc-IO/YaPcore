window.YapDashRegisterSetupPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;

  async function refreshSetup() {
    if (!$("setupReady")) return;
    try {
      const r = await api("/api/setup");
      $("setupReady").textContent = r.summary || ((r.readyCount || 0) + " / " + (r.totalCount || 0));
      $("setupOs").textContent = r.os || "—";
      $("setupBash").textContent = r.bashAvailable ? "yes" : "no";
      $("setupPs").textContent = r.powershellAvailable ? "yes" : "no";
      if ($("setupOsHint")) $("setupOsHint").textContent = r.hint || "";
      const box = $("setupSteps");
      if (box) {
        box.innerHTML = "";
        (r.steps || []).forEach((step) => {
          const row = document.createElement("div");
          row.className = "card";
          const mark = step.ready ? "✓" : "○";
          row.innerHTML = "<strong>" + mark + " " + (step.label || step.id) + "</strong>"
            + "<div class=\"muted\">" + (step.detail || "") + "</div>"
            + (step.deeplinkHint ? ("<div class=\"muted\">" + step.deeplinkHint + "</div>") : "");
          if (step.runnable) {
            const b = document.createElement("button");
            b.type = "button";
            b.textContent = step.ready ? "Re-run" : "Run";
            b.onclick = () => runSetup(step.id);
            row.appendChild(b);
          }
          box.appendChild(row);
        });
      }
      const pc = r.platformCommands || {};
      if ($("setupLinuxCmds")) $("setupLinuxCmds").textContent = (pc.linux || []).join("\n");
      if ($("setupWinCmds")) $("setupWinCmds").textContent = (pc.windows || []).join("\n");
    } catch (e) {
      if ($("setupOut")) $("setupOut").textContent = e.message;
    }
  }

  async function runSetup(action, extra) {
    if (!$("setupOut")) return;
    $("setupOut").textContent = "Running " + action + "…";
    try {
      const body = Object.assign({ action }, extra || {});
      if (action === "production-profile" && $("setupWithLink")) {
        body.withLink = $("setupWithLink").checked ? "true" : "false";
      }
      if (action === "link-forwarding") {
        body.enable = "true";
      }
      const r = await netPost("/api/setup", body);
      const bits = [];
      bits.push(r.ok === false ? "FAILED" : "OK");
      if (r.result) bits.push(r.result);
      if (r.error) bits.push(r.error);
      if (r.hint) bits.push(r.hint);
      if (r.output) bits.push(r.output);
      $("setupOut").textContent = bits.join("\n");
      refreshSetup();
    } catch (e) {
      $("setupOut").textContent = e.message;
    }
  }

  if ($("setupRefresh")) {
    $("setupRefresh").onclick = () => refreshSetup();
    $("setupEula").onclick = () => runSetup("accept-eula");
    $("setupSeed").onclick = () => runSetup("seed-defaults");
    $("setupForward").onclick = () => runSetup("link-forwarding", { enable: "true" });
    $("setupTebex").onclick = () => runSetup("fetch-tebex");
    $("setupFetchGrim").onclick = () => runSetup("fetch-grim");
    $("setupEnableGrim").onclick = () => runSetup("enable-grim");
    $("setupProd").onclick = () => runSetup("production-profile");
    $("setupNginx").onclick = () => runSetup("nginx-dry-run");
    $("setupFolia").onclick = () => runSetup("build-folia");
  }
  if ($("setupOpenFleet")) {
    $("setupOpenFleet").onclick = (e) => {
      e.preventDefault();
      window.YapShell?.switchTab("fleet");
    };
  }
  if ($("setupOpenTebex")) {
    $("setupOpenTebex").onclick = (e) => {
      e.preventDefault();
      window.YapShell?.switchTab("tebex");
    };
  }
  if ($("setupOpenAdmin")) {
    $("setupOpenAdmin").onclick = (e) => {
      e.preventDefault();
      window.YapShell?.switchTab("admin");
    };
  }

  Object.assign(YapDash.tabLoads, { setup: refreshSetup });
};
