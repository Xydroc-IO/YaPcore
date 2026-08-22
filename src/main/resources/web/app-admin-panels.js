(() => {
  function register(YapDash) {
    const { $, api } = YapDash;

    async function refreshAdmin() {
      try {
        const r = await api("/api/admin");
        const rt = r.runtime || {};
        const dash = r.dashboard || {};
        const acc = r.access || {};
        const ngx = r.nginx || {};
        const proxy = r.proxy || {};
        const smoke = r.smoke || {};

        $("admAuthority").textContent = rt.gameAuthority || "—";
        $("admLink").textContent = rt.linkProcessRunning ? "running" : "stopped";
        const smokeTs = smoke.lastNetworkSmoke && smoke.lastNetworkSmoke !== "never"
          ? smoke.lastNetworkSmoke : smoke.lastBedrockPlaySmoke;
        $("admSmoke").textContent = smokeTs && smokeTs !== "never"
          ? smokeTs.replace("T", " ").slice(0, 19) : "never";
        $("admToken").textContent = dash.tokenMasked || "—";

        $("admDashPort").value = dash.port ?? 8080;
        $("admDashBind").value = dash.bind || "0.0.0.0";
        $("admDashLocal").value = dash.localhostOnly ? "true" : "false";
        $("admDashEnabled").value = dash.enabled !== false ? "true" : "false";

        $("admExpose").value = acc.internetExposed ? "true" : "false";
        $("admDomain").value = acc.serverDomain || "";
        $("admPublicHost").value = acc.publicHost || "";
        $("admPublicPort").value = acc.publicPort ?? 0;
        $("admPublicBePort").value = acc.publicBedrockPort ?? 0;
        $("admPublicPackPort").value = acc.publicPackPort ?? 0;
        $("admSrv").value = acc.srvEnabled ? "true" : "false";
        $("admSrvExample").textContent = [
          acc.srvExample || "",
          acc.javaJoin ? "Java: " + acc.javaJoin : "",
          acc.bedrockJoin ? "Bedrock: " + acc.bedrockJoin : "",
        ].filter(Boolean).join("\n");

        $("admAllowLocal").value = ngx.allowLocalhost !== false ? "true" : "false";
        $("admNginxGame").value = ngx.nginxPublicPort ?? 25565;
        $("admNginxPack").value = ngx.nginxPackPort ?? 8081;
        $("admNginxDomain").value = ngx.nginxDomain || "";

        $("admVelocity").value = proxy.velocityEnabled ? "true" : "false";
        $("admVelocityOnline").value = proxy.velocityOnlineMode !== false ? "true" : "false";
        $("admVelocityLocal").value = proxy.velocityBindLocalhost !== false ? "true" : "false";
        $("admVelocitySecret").value = proxy.velocitySecretFile || "";
        $("admLinkEmbed").value = proxy.linkEmbed ? "true" : "false";
        $("admLinkHome").value = proxy.linkEmbedHome || "link-data";

        $("adminOut").textContent = (r.hint || "") + "\n\nRoot: " + (rt.rootDir || "—") + " · PID " + (rt.pid ?? "—");
      } catch (e) {
        $("adminOut").textContent = e.message;
      }
    }

    async function adminPost(body) {
      const r = await api("/api/admin", { method: "POST", body: JSON.stringify(body) });
      if (r.token) {
        localStorage.setItem("yap_token", r.token);
        document.cookie = "yap_token=" + encodeURIComponent(r.token) + "; path=/; SameSite=Strict";
        $("tokenInput").value = r.token;
        alert("New token generated — saved to browser. Copy from config if needed.");
      }
      const note = r.note || r.result || "";
      const out = r.output ? r.output + "\nexit=" + r.exit : "";
      $("adminOut").textContent = [note, out, JSON.stringify(r, null, 2)].filter(Boolean).join("\n\n");
      await refreshAdmin();
      return r;
    }

    $("adminRefresh").onclick = () => refreshAdmin();
    $("admSaveDashboard").onclick = () => adminPost({
      action: "save-dashboard",
      port: $("admDashPort").value,
      bind: $("admDashBind").value.trim(),
      localhostOnly: $("admDashLocal").value,
      enabled: $("admDashEnabled").value,
    });
    $("admRotateToken").onclick = () => {
      if (!confirm("Generate a new dashboard token? You must log in again everywhere.")) return;
      adminPost({ action: "rotate-token" });
    };
    $("admSaveAccess").onclick = () => adminPost({
      action: "save-access",
      internetExposed: $("admExpose").value,
      serverDomain: $("admDomain").value.trim(),
      publicHost: $("admPublicHost").value.trim(),
      publicPort: $("admPublicPort").value,
      publicBedrockPort: $("admPublicBePort").value,
      publicPackPort: $("admPublicPackPort").value,
      srvEnabled: $("admSrv").value,
    });
    $("admSaveNginx").onclick = () => adminPost({
      action: "save-nginx",
      allowLocalhost: $("admAllowLocal").value,
      nginxPublicPort: $("admNginxGame").value,
      nginxPackPort: $("admNginxPack").value,
      nginxDomain: $("admNginxDomain").value.trim(),
    });
    $("admNginxDryRun").onclick = () => adminPost({ action: "nginx-dry-run" });
    $("admSaveProxy").onclick = () => adminPost({
      action: "save-proxy",
      velocityEnabled: $("admVelocity").value,
      velocityOnlineMode: $("admVelocityOnline").value,
      velocityBindLocalhost: $("admVelocityLocal").value,
      velocitySecretFile: $("admVelocitySecret").value.trim(),
      linkEmbed: $("admLinkEmbed").value,
      linkEmbedHome: $("admLinkHome").value.trim(),
    });
    $("adminCrashdump").onclick = () => adminPost({ action: "crashdump" });
    $("adminRunSmoke").onclick = () => {
      if (!confirm("Run smoke-network-full.sh? This can take several minutes.")) return;
      adminPost({ action: "run-smoke" });
    };

    YapDash.tabLoads.admin = refreshAdmin;
    YapDash.refreshAdmin = refreshAdmin;
  }
  window.YapDashRegisterAdminPanels = register;
  if (window.YapDash) register(window.YapDash);
})();
