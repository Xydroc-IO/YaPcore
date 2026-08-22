(() => {
  function register(YapDash) {
    const { $, api, netPost } = YapDash;
    let selectedRow = null;
    let onlineList = [];

    function target() {
      return $("plTarget").value.trim();
    }

    function reason() {
      const r = $("plReason").value.trim();
      return r || "Staff action via admin dashboard";
    }

    function duration() {
      return $("plDuration").value.trim() || "1d";
    }

    async function plAction(body, confirmMsg) {
      if (confirmMsg && !confirm(confirmMsg)) return;
      const t = body.player || target();
      if (!t && body.action !== "banlist") {
        alert("Enter a player name, UUID, or IP.");
        return;
      }
      try {
        const r = await api("/api/players", {
          method: "POST",
          body: JSON.stringify({ ...body, player: t, reason: reason(), duration: duration() }),
        });
        $("plOut").textContent = (r.result || "Done.") + "\n\n/" + (r.command || "");
        if (r.online) renderTable(r.online);
      } catch (e) {
        $("plOut").textContent = e.message;
      }
    }

    function selectPlayer(p, tr) {
      $("plTarget").value = p.name || "";
      $("plSelectedMeta").textContent = [
        p.uuid ? "UUID: " + p.uuid : "",
        p.ip ? "IP: " + p.ip : "",
        p.world ? `${p.world} @ ${p.x}, ${p.y}, ${p.z}` : "",
      ].filter(Boolean).join(" · ");
      if (p.x != null) {
        $("plTpX").value = p.x;
        $("plTpY").value = p.y;
        $("plTpZ").value = p.z;
      }
      if (selectedRow) selectedRow.classList.remove("selected");
      selectedRow = tr || null;
      if (tr) tr.classList.add("selected");
    }

    function renderTpDestOptions(list) {
      const sel = $("plTpDest");
      const cur = sel.value;
      sel.innerHTML = '<option value="">—</option>';
      (list || []).forEach((p) => {
        if (!p.name) return;
        const o = document.createElement("option");
        o.value = p.name;
        o.textContent = p.name;
        sel.appendChild(o);
      });
      if (cur) sel.value = cur;
    }

    function renderTable(list) {
      onlineList = list || [];
      const body = $("plBody");
      body.innerHTML = "";
      $("plEmpty").classList.toggle("hidden", onlineList.length > 0);
      onlineList.forEach((p) => {
        const tr = document.createElement("tr");
        const loc = p.world ? `${p.world} ${p.x}, ${p.y}, ${p.z}` : "—";
        tr.innerHTML = `<td><strong>${esc(p.name)}</strong></td>`
          + `<td class="mono-sm">${esc((p.uuid || "").slice(0, 8))}…</td>`
          + `<td>${esc(p.ip || "—")}</td><td class="muted">${esc(loc)}</td>`;
        tr.onclick = () => selectPlayer(p, tr);
        body.appendChild(tr);
      });
      renderTpDestOptions(onlineList);
    }

    function esc(s) {
      return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
    }

    function fillGroups(groups) {
      if (!groups || !groups.length) return;
      ["plGroup", "prmGroup"].forEach((id) => {
        const sel = $(id);
        if (!sel) return;
        const cur = sel.value;
        sel.innerHTML = "";
        groups.forEach((g) => {
          const o = document.createElement("option");
          o.value = g;
          const labels = { default: "default — new players", vip: "vip — donor", mod: "mod — staff", admin: "admin — full" };
          o.textContent = labels[g] || g;
          sel.appendChild(o);
        });
        if (cur) sel.value = cur;
      });
    }

    async function refreshPlayers() {
      try {
        const r = await api("/api/players");
        $("plCount").textContent = r.count + " / " + r.maxPlayers;
        $("plMax").textContent = String(r.maxPlayers);
        $("plModOk").textContent = r.moderation?.installed ? "ready" : "missing jar";
        renderTable(r.online || []);
        fillGroups(r.groups);
        if (r.spawn) {
          $("plSelectedMeta").dataset.spawn = JSON.stringify(r.spawn);
        }
      } catch (e) {
        $("plOut").textContent = e.message;
      }
    }

    $("plRefresh").onclick = () => refreshPlayers();
    $("plKick").onclick = () => plAction({ action: "kick" }, "Kick this player?");
    $("plWarn").onclick = () => plAction({ action: "warn" });
    $("plMute").onclick = () => plAction({ action: "mute" });
    $("plTempMute").onclick = () => plAction({ action: "tempmute" });
    $("plTimeout").onclick = () => plAction({ action: "tempban" }, "Timeout (temp ban) this player?");
    $("plBan").onclick = () => plAction({ action: "ban" }, "Permanently ban this player?");
    $("plIpBan").onclick = () => {
      const t = target();
      const isIp = t.includes(".");
      plAction({ action: "ipban", ip: isIp ? t : undefined }, "IP-ban this player/address?");
    };
    $("plUnban").onclick = () => plAction({ action: "unban" });
    $("plUnmute").onclick = () => plAction({ action: "unmute" });
    $("plUnbanIp").onclick = () => plAction({ action: "unbanip", ip: target() });
    $("plHistory").onclick = () => plAction({ action: "history" });
    $("plCheck").onclick = () => plAction({ action: "check" });
    $("plBanList").onclick = async () => {
      try {
        const r = await api("/api/players", { method: "POST", body: JSON.stringify({ action: "banlist", limit: "30" }) });
        $("plOut").textContent = r.result || "";
      } catch (e) { $("plOut").textContent = e.message; }
    };
    $("plPermInfo").onclick = () => plAction({ action: "user-info" });
    $("plSetRank").onclick = () => plAction({ action: "set-group", group: $("plGroup").value });
    $("plPromote").onclick = () => plAction({ action: "promote" });
    $("plDemote").onclick = () => plAction({ action: "demote" });
    $("plTpCoords").onclick = () => plAction({
      action: "tp",
      x: $("plTpX").value,
      y: $("plTpY").value,
      z: $("plTpZ").value,
    });
    $("plTpTo").onclick = () => {
      const dest = $("plTpDest").value;
      if (!dest) { alert("Pick a destination player."); return; }
      plAction({ action: "tp-to", destination: dest });
    };
    $("plTpSpawn").onclick = () => plAction({ action: "tp-spawn" });

    YapDash.tabLoads.players = refreshPlayers;
    YapDash.refreshPlayers = refreshPlayers;
  }
  window.YapDashRegisterPlayersPanels = register;
  if (window.YapDash) register(window.YapDash);
})();
