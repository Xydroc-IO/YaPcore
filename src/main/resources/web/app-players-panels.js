(() => {
  function register(YapDash) {
    const { $, api, netPost } = YapDash;
    let selectedRow = null;
    let onlineList = [];
    let seenList = [];

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
        if (r.seen) {
          renderSeen(r.seen);
          if ($("plSeenCount")) $("plSeenCount").textContent = String(r.seen.length);
        }
      } catch (e) {
        $("plOut").textContent = e.message;
      }
    }

    function selectPlayer(p, tr) {
      $("plTarget").value = p.name || p.username || "";
      $("plSelectedMeta").textContent = [
        p.uuid ? "UUID: " + p.uuid : "",
        (p.nickname && p.nickname !== (p.name || p.username)) ? "Nick: " + p.nickname : "",
        p.ip ? "IP: " + p.ip : "",
        p.ips && p.ips !== p.ip ? "IPs: " + p.ips : "",
        p.world ? `${p.world} @ ${p.x}, ${p.y}, ${p.z}` : "",
      ].filter(Boolean).join(" · ");
      $("plSelectedMeta").dataset.ip = p.ip || "";
      $("plSelectedMeta").dataset.uuid = p.uuid || "";
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
        const nick = p.displayName && p.displayName !== p.name ? p.displayName : "—";
        tr.innerHTML = `<td><strong>${esc(p.name)}</strong></td>`
          + `<td>${esc(nick)}</td>`
          + `<td class="mono-sm">${esc(p.uuid || "")}</td>`
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
        try {
          await api("/api/players", { method: "POST", body: JSON.stringify({ action: "seen-refresh" }) });
        } catch { /* Folia may be down — use last snapshot */ }
        const r = await api("/api/players");
        $("plCount").textContent = r.count + " / " + r.maxPlayers;
        $("plMax").textContent = String(r.maxPlayers);
        $("plModOk").textContent = r.moderation?.installed ? "ready" : "missing jar";
        renderTable(r.online || []);
        renderSeen(r.seen || []);
        if ($("plSeenCount")) $("plSeenCount").textContent = String(r.seenCount != null ? r.seenCount : (r.seen || []).length);
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
    function formatSeen(ms) {
      const n = Number(ms);
      if (!n) return "—";
      const d = new Date(n);
      if (Number.isNaN(d.getTime())) return "—";
      return d.toLocaleString();
    }

    function renderSeen(list) {
      seenList = list || [];
      const q = ($("plSeenSearch")?.value || "").trim().toLowerCase();
      const body = $("plSeenBody");
      if (!body) return;
      body.innerHTML = "";
      const rows = seenList.filter((p) => {
        if (!q) return true;
        const hay = [p.username, p.nickname, p.uuid, p.ip, p.ips].join(" ").toLowerCase();
        return hay.includes(q);
      });
      $("plSeenEmpty")?.classList.toggle("hidden", rows.length > 0 || seenList.length === 0);
      if ($("plSeenEmpty") && seenList.length === 0) $("plSeenEmpty").classList.remove("hidden");
      rows.forEach((p) => {
        const tr = document.createElement("tr");
        if (p.online) tr.classList.add("selected");
        const ips = p.ips && String(p.ips) !== String(p.ip || "") ? p.ips : "";
        tr.innerHTML = `<td>${p.online ? "●" : ""}</td>`
          + `<td><strong>${esc(p.username || "—")}</strong></td>`
          + `<td>${esc(p.nickname || "—")}</td>`
          + `<td class="mono-sm">${esc(p.uuid || "")}</td>`
          + `<td>${esc(p.ip || "—")}</td>`
          + `<td class="mono-sm">${esc(ips || "—")}</td>`
          + `<td class="muted">${esc(formatSeen(p.firstSeen))}</td>`
          + `<td class="muted">${esc(formatSeen(p.lastSeen))}</td>`;
        tr.onclick = () => selectPlayer({
          name: p.username,
          username: p.username,
          nickname: p.nickname,
          uuid: p.uuid,
          ip: p.ip,
          ips: p.ips,
        }, tr);
        body.appendChild(tr);
      });
    }

    $("plSeenSearch")?.addEventListener("input", () => renderSeen(seenList));

    $("plIpBan").onclick = () => {
      const t = target();
      const storedIp = $("plSelectedMeta")?.dataset.ip || "";
      const ip = (t.includes(".") || t.includes(":")) ? t : storedIp;
      plAction({ action: "ipban", ip: ip || undefined, player: t },
        "IP-ban " + (ip || t) + "? This blocks that address from joining.");
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
