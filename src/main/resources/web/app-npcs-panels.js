window.YapDashRegisterNpcPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let selectedId = "";
  let questIds = [];

  function setOut(text) {
    const el = $("npcOut");
    if (el) el.textContent = text || "";
  }

  function fmtCoord(v) {
    return typeof v === "number" ? v.toFixed(1) : (v ?? "—");
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
    $("npcEditId").textContent = n.id;
    $("npcEditName").value = n.displayName || n.id;
    $("npcEditWorld").value = n.world || "world";
    $("npcEditX").value = n.x ?? 0;
    $("npcEditY").value = n.y ?? 64;
    $("npcEditZ").value = n.z ?? 0;
    $("npcEditYaw").value = n.yaw ?? 0;
    $("npcEditQuest").value = n.questId || "";
    $("npcEditDialogue").value = n.dialogue || "";
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
      setOut("NPC updated.");
      refreshNpcs();
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { npcs: refreshNpcs });
};
