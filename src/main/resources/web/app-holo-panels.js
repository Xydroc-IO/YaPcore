window.YapDashRegisterHoloPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let selectedId = "";

  function setOut(text) {
    const el = $("holoOut");
    if (el) el.textContent = text || "";
  }

  function fmtCoord(v) {
    return typeof v === "number" ? v.toFixed(1) : (v ?? "—");
  }

  function linePreview(raw) {
    if (!raw) return "—";
    const first = String(raw).split("|")[0];
    return first.length > 40 ? first.slice(0, 40) + "…" : first;
  }

  function linesToTextarea(raw) {
    return String(raw || "").split(";;").map((page) => page.split("|").join("\n")).join("\n\n");
  }

  function textareaToLines(text) {
    return String(text || "").replace(/\r/g, "").split(/\n\n+/).map((page) => page.split("\n").join("|")).join(";;");
  }

  function renderTable(rows) {
    const tbody = $("holoBody");
    const empty = $("holoEmpty");
    if (!tbody) return;
    tbody.innerHTML = "";
    if (!rows || !rows.length) {
      empty?.classList.remove("hidden");
      return;
    }
    empty?.classList.add("hidden");
    rows.forEach((n) => {
      const tr = document.createElement("tr");
      tr.dataset.id = n.id;
      if (n.id === selectedId) tr.classList.add("selected");
      tr.innerHTML = `<td><strong>${n.id}</strong></td>`
        + `<td>${n.world || "—"}</td>`
        + `<td>${fmtCoord(n.x)}, ${fmtCoord(n.y)}, ${fmtCoord(n.z)}</td>`
        + `<td class="muted">${linePreview(n.lines)}</td>`
        + `<td>${n.view ?? "—"}</td>`
        + `<td><button type="button" class="danger ghost holo-del" data-id="${n.id}">Remove</button></td>`;
      tr.onclick = (ev) => {
        if (ev.target.closest(".holo-del")) return;
        selectHolo(n);
      };
      tr.querySelector(".holo-del").onclick = async (ev) => {
        ev.stopPropagation();
        if (!confirm("Remove hologram " + n.id + "?")) return;
        try {
          const r = await netPost("/api/holo", { action: "delete", id: n.id });
          setOut(r.result || "Removed.");
          if (selectedId === n.id) selectedId = "";
          await refreshHolo();
        } catch (e) { setOut(e.message); }
      };
      tbody.appendChild(tr);
    });
  }

  function selectHolo(n) {
    selectedId = n.id;
    $("holoEditId").textContent = n.id;
    $("holoEditWorld").value = n.world || "world";
    $("holoEditX").value = n.x ?? 0;
    $("holoEditY").value = n.y ?? 64;
    $("holoEditZ").value = n.z ?? 0;
    $("holoEditView").value = n.view ?? 48;
    $("holoEditLines").value = linesToTextarea(n.lines);
    if ($("holoEditAttach")) $("holoEditAttach").value = n.attach || "";
    if ($("holoEditClicks")) $("holoEditClicks").value = n.clicks || "";
    if ($("holoEditPerm")) $("holoEditPerm").value = n.perm || "";
    document.querySelectorAll("#holoBody tr").forEach((tr) => {
      tr.classList.toggle("selected", tr.dataset.id === selectedId);
    });
  }

  async function refreshHolo() {
    try {
      const r = await api("/api/holo");
      $("holoInstalled").textContent = r.installed ? "yes" : "no";
      $("holoCount").textContent = String(r.holoCount ?? (r.holograms || []).length);
      $("holoEntity").textContent = r.entity || "—";
      $("holoEnabled").textContent = r.enabled === false ? "off" : "on";
      renderTable(r.holograms || []);
      if (selectedId) {
        const n = (r.holograms || []).find((x) => x.id === selectedId);
        if (n) selectHolo(n);
      }
      setOut(r.installed ? "" : "Install yap-holo.jar (and yap-lib.jar) then start the game server.");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("holoRefresh")?.addEventListener("click", () => refreshHolo());
  $("holoReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/holo", { action: "reload" });
      setOut(r.result || "Reloaded.");
      refreshHolo();
    } catch (e) { setOut(e.message); }
  });

  $("holoCreateBtn")?.addEventListener("click", async () => {
    const id = $("holoNewId")?.value.trim();
    if (!id) { alert("Hologram id required."); return; }
    try {
      const r = await netPost("/api/holo", {
        action: "create",
        id,
        world: $("holoNewWorld")?.value.trim() || "world",
        x: $("holoNewX")?.value || "0",
        y: $("holoNewY")?.value || "64",
        z: $("holoNewZ")?.value || "0",
        lines: textareaToLines($("holoNewLines")?.value || ("&f" + id)),
      });
      setOut(r.result || "Hologram created.");
      $("holoNewId").value = "";
      refreshHolo();
    } catch (e) { setOut(e.message); }
  });

  $("holoSaveEdit")?.addEventListener("click", async () => {
    if (!selectedId) { alert("Select a hologram from the table."); return; }
    try {
      await netPost("/api/holo", {
        action: "move",
        id: selectedId,
        world: $("holoEditWorld")?.value.trim() || "world",
        x: $("holoEditX")?.value || "0",
        y: $("holoEditY")?.value || "64",
        z: $("holoEditZ")?.value || "0",
      });
      await netPost("/api/holo", {
        action: "setlines",
        id: selectedId,
        lines: textareaToLines($("holoEditLines")?.value || "&f"),
      });
      await netPost("/api/holo", {
        action: "attach",
        id: selectedId,
        attach: $("holoEditAttach")?.value.trim() || "none",
      });
      await netPost("/api/holo", {
        action: "click",
        id: selectedId,
        clicks: $("holoEditClicks")?.value.trim() || "clear",
      });
      await netPost("/api/holo", {
        action: "see",
        id: selectedId,
        perm: $("holoEditPerm")?.value.trim() || "none",
      });
      const view = $("holoEditView")?.value.trim();
      if (view) {
        await netPost("/api/holo", { action: "view", id: selectedId, view });
      }
      setOut("Hologram updated.");
      refreshHolo();
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { holo: refreshHolo });
};
