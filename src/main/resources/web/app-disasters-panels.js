window.YapDashRegisterDisastersPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let snap = null;

  function setOut(text, err) {
    const el = $("disOut");
    if (!el) return;
    el.textContent = text || "";
    el.className = "report muted-small" + (err ? " err" : "");
  }

  function yn(v) {
    return v ? "yes" : "no";
  }

  function paintTypes(disasters) {
    const body = $("disTypeBody");
    if (!body) return;
    body.innerHTML = "";
    (disasters || []).forEach((d) => {
      const tr = document.createElement("tr");
      const extras = [];
      const map = [
        ["temporaryLavaTicks", "lava ticks"],
        ["temporarySnowTicks", "snow ticks"],
        ["temporaryDryTicks", "dry ticks"],
        ["temporaryFireTicks", "fire ticks"],
        ["temporaryWaterTicks", "water ticks"],
        ["waveRadius", "wave r"],
        ["floodHeight", "flood h"],
      ];
      map.forEach(([k, label]) => {
        if (d[k] == null) return;
        extras.push(
          `<label class="muted-small">${label} <input data-extra="${d.id}.${k}" type="number" min="1" value="${d[k]}" style="width:72px"/></label>`
        );
      });
      const period = d.periodTicks != null
        ? `<input data-period="${d.id}" type="number" min="2" value="${d.periodTicks}" style="width:80px"/>`
        : "—";
      tr.innerHTML = `
        <td><strong>${d.id}</strong></td>
        <td><input type="checkbox" data-type-en="${d.id}" ${d.enabled ? "checked" : ""}/></td>
        <td>${period}</td>
        <td>${extras.join(" ") || "—"}</td>`;
      body.appendChild(tr);
    });
  }

  function paintWeights(weights) {
    const wrap = $("disWeights");
    if (!wrap) return;
    wrap.innerHTML = "";
    Object.keys(weights || {}).sort().forEach((k) => {
      const label = document.createElement("label");
      label.innerHTML = `${k} <input data-weight="${k}" type="number" min="0" value="${weights[k] || 0}"/>`;
      wrap.appendChild(label);
    });
  }

  function paintSites(sites) {
    const body = $("disSiteBody");
    if (!body) return;
    body.innerHTML = "";
    (sites || []).forEach((s) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${s.id}${s.dormant ? " <span class=\"muted\">(dormant)</span>" : ""}</td>
        <td>${s.world}</td>
        <td>${Math.round(s.x)}, ${Math.round(s.y)}, ${Math.round(s.z)}</td>
        <td>
          <button type="button" data-erupt="${s.id}" class="secondary">Erupt</button>
          <button type="button" data-rm-site="${s.id}" class="danger">Remove</button>
        </td>`;
      body.appendChild(tr);
    });
    body.querySelectorAll("[data-erupt]").forEach((btn) => {
      btn.onclick = async () => {
        try {
          const r = await netPost("/api/disasters", {
            action: "site-erupt",
            id: btn.getAttribute("data-erupt"),
            seconds: String($("disStartSec")?.value || "120"),
          });
          setOut(r.result || "Erupting…");
          refresh();
        } catch (e) {
          setOut(e.message, true);
        }
      };
    });
    body.querySelectorAll("[data-rm-site]").forEach((btn) => {
      btn.onclick = async () => {
        try {
          await netPost("/api/disasters", {
            action: "site-remove",
            id: btn.getAttribute("data-rm-site"),
          });
          setOut("Site removed.");
          refresh();
        } catch (e) {
          setOut(e.message, true);
        }
      };
    });
  }

  function applySnap(d) {
    snap = d;
    $("disInstalled").textContent = yn(d.installed);
    $("disLive").textContent = (d.liveStatus || "—").replace(/^.*—\s*/, "").slice(0, 48) || "—";
    $("disRandomStat").textContent = yn(d.randomEnabled);
    $("disSiteCount").textContent = String((d.volcanoSites || []).length);
    $("disGriefStat").textContent = yn(d.grief);

    $("disEnabled").checked = !!d.enabled;
    $("disGrief").checked = !!d.grief;
    $("disRealLightning").checked = !!d.realLightning;
    $("disProtectClaims").checked = !!d.protectClaims;
    $("disProtectRegions").checked = !!d.protectRegions;
    $("disWarnings").checked = !!d.warningsEnabled;
    $("disBroadcastStart").checked = !!d.broadcastStart;
    $("disBroadcastEnd").checked = !!d.broadcastEnd;
    $("disSitesAmbient").checked = !!d.volcanoSitesAmbient;
    $("disDefaultDur").value = d.defaultDurationSeconds ?? 120;
    $("disAllowedWorlds").value = d.allowedWorlds || "";
    $("disSiteSnap").value = d.volcanoSiteSnapBlocks ?? 48;

    $("disRandomEnabled").checked = !!d.randomEnabled;
    $("disRandomPlayers").checked = !!d.randomRequirePlayers;
    $("disRandomMin").value = d.randomMinIntervalSeconds ?? 900;
    $("disRandomMax").value = d.randomMaxIntervalSeconds ?? 2400;
    $("disRandomWarn").value = d.randomWarningSeconds ?? 30;
    $("disRandomDur").value = d.randomDurationSeconds ?? 120;

    paintWeights(d.weights || {});
    paintTypes(d.disasters || []);
    paintSites(d.volcanoSites || []);
  }

  function collectBody() {
    const body = {
      action: "save-settings",
      enabled: String($("disEnabled").checked),
      grief: String($("disGrief").checked),
      realLightning: String($("disRealLightning").checked),
      protectClaims: String($("disProtectClaims").checked),
      protectRegions: String($("disProtectRegions").checked),
      warningsEnabled: String($("disWarnings").checked),
      broadcastStart: String($("disBroadcastStart").checked),
      broadcastEnd: String($("disBroadcastEnd").checked),
      volcanoSitesAmbient: String($("disSitesAmbient").checked),
      defaultDurationSeconds: String($("disDefaultDur").value || "120"),
      allowedWorlds: $("disAllowedWorlds").value || "",
      volcanoSiteSnapBlocks: String($("disSiteSnap").value || "48"),
      randomEnabled: String($("disRandomEnabled").checked),
      randomRequirePlayers: String($("disRandomPlayers").checked),
      randomMinIntervalSeconds: String($("disRandomMin").value || "900"),
      randomMaxIntervalSeconds: String($("disRandomMax").value || "2400"),
      randomWarningSeconds: String($("disRandomWarn").value || "30"),
      randomDurationSeconds: String($("disRandomDur").value || "120"),
    };
    document.querySelectorAll("[data-weight]").forEach((input) => {
      body["weight." + input.getAttribute("data-weight")] = String(input.value || "0");
    });
    document.querySelectorAll("[data-type-en]").forEach((input) => {
      const id = input.getAttribute("data-type-en");
      body["disaster." + id + ".enabled"] = String(input.checked);
    });
    document.querySelectorAll("[data-period]").forEach((input) => {
      const id = input.getAttribute("data-period");
      body["disaster." + id + ".periodTicks"] = String(input.value || "20");
    });
    document.querySelectorAll("[data-extra]").forEach((input) => {
      const raw = input.getAttribute("data-extra"); // type.key
      const [id, key] = raw.split(".");
      body["disaster." + id + "." + key] = String(input.value || "0");
    });
    return body;
  }

  async function refresh() {
    try {
      const d = await api("/api/disasters");
      applySnap(d);
      setOut("");
    } catch (e) {
      setOut(e.message, true);
    }
  }

  $("disRefresh")?.addEventListener("click", refresh);
  $("disSave")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", collectBody());
      setOut("Saved. " + (r.reload || ""));
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", { action: "reload" });
      setOut(r.result || "Reloaded");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disStart")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", {
        action: "start",
        type: $("disStartType").value,
        seconds: String($("disStartSec").value || "120"),
        world: $("disStartWorld").value || "",
      });
      setOut(r.result || r.command || "Started");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disStop")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", {
        action: "stop",
        world: $("disStartWorld").value || "",
      });
      setOut(r.result || "Stopped");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disRandomOn")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", { action: "random", mode: "on" });
      setOut(r.result || "Random on");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disRandomOff")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", { action: "random", mode: "off" });
      setOut(r.result || "Random off");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disRandomNow")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/disasters", {
        action: "random",
        mode: "now",
        type: $("disStartType").value || "",
        world: $("disStartWorld").value || "",
      });
      setOut(r.result || "Queued");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });
  $("disSiteAdd")?.addEventListener("click", async () => {
    try {
      await netPost("/api/disasters", {
        action: "site-add",
        id: $("disSiteId").value || "",
        world: $("disSiteWorld").value || "world",
        x: String($("disSiteX").value || "0"),
        y: String($("disSiteY").value || "64"),
        z: String($("disSiteZ").value || "0"),
      });
      setOut("Site saved.");
      await refresh();
    } catch (e) {
      setOut(e.message, true);
    }
  });

  Object.assign(YapDash.tabLoads, { disasters: refresh });
};
