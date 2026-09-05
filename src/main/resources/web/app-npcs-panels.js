window.YapDashRegisterNpcPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let selectedId = "";
  let questIds = [];
  let useRawAction = false;

  function setOut(text) {
    const el = $("npcOut");
    if (el) el.textContent = text || "";
  }

  function fmtCoord(v) {
    return typeof v === "number" ? v.toFixed(1) : (v ?? "—");
  }

  function parseAction(raw) {
    const out = { shop: "", warp: "", command: "", player: "", spawn: false };
    if (!raw) return out;
    String(raw).split(";").forEach((part) => {
      const t = part.trim();
      if (!t) return;
      const lower = t.toLowerCase();
      if (lower === "spawn" || lower.startsWith("spawn:")) {
        out.spawn = true;
        return;
      }
      const colon = t.indexOf(":");
      if (colon <= 0) return;
      const type = t.slice(0, colon).trim().toLowerCase();
      const value = t.slice(colon + 1).trim();
      if (type === "shop" || type === "trader") out.shop = value;
      else if (type === "warp") out.warp = value;
      else if (type === "command" || type === "console" || type === "cmd") out.command = value;
      else if (type === "player" || type === "playercmd" || type === "sudo") out.player = value;
    });
    return out;
  }

  function fillQuestSelect(sel, ids) {
    if (!sel) return;
    const cur = sel.value;
    sel.innerHTML = "<option value=\"\">— none —</option>";
    (ids || []).forEach((q) => {
      const o = document.createElement("option");
      o.value = q;
      o.textContent = q;
      sel.appendChild(o);
    });
    if (cur) sel.value = cur;
  }

  function renderTable(npcs) {
    const tbody = $("npcBody");
    const empty = $("npcEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    if (!npcs || !npcs.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    npcs.forEach((n) => {
      const tr = document.createElement("tr");
      tr.dataset.id = n.id;
      if (n.id === selectedId) tr.classList.add("selected");
      tr.innerHTML = `<td><strong>${n.id}</strong></td>`
        + `<td>${n.displayName || n.id}</td>`
        + `<td>${n.world || "—"}</td>`
        + `<td>${fmtCoord(n.x)}, ${fmtCoord(n.y)}, ${fmtCoord(n.z)}</td>`
        + `<td>${n.questId || "—"}</td>`
        + `<td class="muted">${n.action || "—"}</td>`
        + `<td><button type="button" class="danger ghost npc-del" data-id="${n.id}">Remove</button></td>`;
      tr.onclick = (ev) => {
        if (ev.target.closest(".npc-del")) return;
        selectNpc(n);
      };
      tr.querySelector(".npc-del").onclick = async (ev) => {
        ev.stopPropagation();
        if (!confirm("Remove NPC " + n.id + "?")) return;
        try {
          const r = await netPost("/api/npcs", { action: "remove", id: n.id });
          setOut(r.result || "Removed.");
          if (selectedId === n.id) selectedId = "";
          await refreshNpcs();
        } catch (e) { setOut(e.message); }
      };
      tbody.appendChild(tr);
    });
  }

  function selectNpc(n) {
    selectedId = n.id;
    useRawAction = false;
    $("npcEditId").textContent = n.id;
    $("npcEditName").value = n.displayName || n.id;
    $("npcEditWorld").value = n.world || "world";
    $("npcEditX").value = n.x ?? 0;
    $("npcEditY").value = n.y ?? 64;
    $("npcEditZ").value = n.z ?? 0;
    $("npcEditYaw").value = n.yaw ?? 0;
    $("npcEditQuest").value = n.questId || "";
    $("npcEditDialogue").value = n.dialogue || "";
    const parsed = parseAction(n.action || "");
    if ($("npcEditSpawn")) $("npcEditSpawn").checked = parsed.spawn;
    if ($("npcEditWarp")) $("npcEditWarp").value = parsed.warp;
    if ($("npcEditCommand")) $("npcEditCommand").value = parsed.command;
    if ($("npcEditPlayerCmd")) $("npcEditPlayerCmd").value = parsed.player;
    if ($("npcEditAction")) $("npcEditAction").value = n.action || "";
    if ($("npcShopHint")) {
      $("npcShopHint").textContent = parsed.shop
        ? ("Shop catalog #" + parsed.shop + " — add offers in-game: /npc shop addbuy " + n.id + " <price>")
        : "No shop linked.";
    }
    document.querySelectorAll("#npcBody tr").forEach((tr) => {
      tr.classList.toggle("selected", tr.dataset.id === selectedId);
    });
  }

  async function refreshNpcs() {
    try {
      const r = await api("/api/npcs");
      $("npcInstalled").textContent = r.installed ? "yes" : "no";
      $("npcQuests").textContent = String(r.questPackCount ?? 0);
      $("npcServer").textContent = r.serverId || "—";
      $("npcCount").textContent = String(r.npcCount ?? (r.npcs || []).length);
      questIds = r.questIds || [];
      fillQuestSelect($("npcNewQuest"), questIds);
      fillQuestSelect($("npcEditQuest"), questIds);
      renderTable(r.npcs || []);
      if (selectedId) {
        const n = (r.npcs || []).find((x) => x.id === selectedId);
        if (n) selectNpc(n);
      }
      setOut(r.installed ? "" : "Install yap-npcs and start the game server to manage NPCs.");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("npcRefresh")?.addEventListener("click", () => refreshNpcs());
  $("npcReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/npcs", { action: "reload" });
      setOut(r.result || "Reloaded.");
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });
  $("npcRespawn")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/npcs", { action: "respawn" });
      setOut(r.result || "Respawn queued.");
    } catch (e) { setOut(e.message); }
  });

  $("npcCreateBtn")?.addEventListener("click", async () => {
    const id = $("npcNewId")?.value.trim();
    if (!id) { alert("NPC id required."); return; }
    try {
      const r = await netPost("/api/npcs", {
        action: "create",
        id,
        name: $("npcNewName")?.value.trim() || id,
        world: $("npcNewWorld")?.value.trim() || "world",
        x: $("npcNewX")?.value || "0",
        y: $("npcNewY")?.value || "64",
        z: $("npcNewZ")?.value || "0",
        yaw: $("npcNewYaw")?.value || "0",
      });
      if (r.npcs) renderTable(r.npcs);
      const quest = $("npcNewQuest")?.value;
      if (quest) {
        await netPost("/api/npcs", { action: "setquest", id, questId: quest });
      }
      setOut(r.result || "NPC created.");
      $("npcNewId").value = "";
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });

  $("npcEditAction")?.addEventListener("input", () => { useRawAction = true; });

  $("npcShopEnable")?.addEventListener("click", async () => {
    if (!selectedId) { alert("Select an NPC from the table."); return; }
    try {
      const r = await netPost("/api/npcs", { action: "shopenable", id: selectedId });
      setOut(r.result || "Shop enabled.");
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });

  $("npcShopClear")?.addEventListener("click", async () => {
    if (!selectedId) { alert("Select an NPC from the table."); return; }
    if (!confirm("Clear shop catalog on " + selectedId + "?")) return;
    try {
      const r = await netPost("/api/npcs", { action: "shopclear", id: selectedId });
      setOut(r.result || "Shop cleared.");
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });

  $("npcSaveEdit")?.addEventListener("click", async () => {
    if (!selectedId) { alert("Select an NPC from the table."); return; }
    try {
      await netPost("/api/npcs", {
        action: "create",
        id: selectedId,
        name: $("npcEditName")?.value.trim() || selectedId,
        world: $("npcEditWorld")?.value.trim() || "world",
        x: $("npcEditX")?.value || "0",
        y: $("npcEditY")?.value || "64",
        z: $("npcEditZ")?.value || "0",
        yaw: $("npcEditYaw")?.value || "0",
      });
      await netPost("/api/npcs", {
        action: "setquest",
        id: selectedId,
        questId: $("npcEditQuest")?.value || "",
      });
      const dialogue = $("npcEditDialogue")?.value.trim();
      if (dialogue) {
        await netPost("/api/npcs", { action: "setdialogue", id: selectedId, dialogue });
      }
      if (useRawAction) {
        await netPost("/api/npcs", {
          action: "setaction",
          id: selectedId,
          npcAction: $("npcEditAction")?.value.trim() || "",
        });
      } else {
        const warp = ($("npcEditWarp")?.value || "").trim();
        if (warp.toLowerCase() === "spawn") {
          alert("Use the spawn checkbox for server spawn — warp name \"spawn\" is reserved.");
          return;
        }
        await netPost("/api/npcs", {
          action: "setspawn",
          id: selectedId,
          spawn: $("npcEditSpawn")?.checked ? "on" : "off",
        });
        await netPost("/api/npcs", { action: "setwarp", id: selectedId, warp });
        await netPost("/api/npcs", {
          action: "setcommand",
          id: selectedId,
          command: ($("npcEditCommand")?.value || "").trim(),
        });
        await netPost("/api/npcs", {
          action: "setplayer",
          id: selectedId,
          playerCommand: ($("npcEditPlayerCmd")?.value || "").trim(),
        });
      }
      setOut("NPC updated.");
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { npcs: refreshNpcs });
};
