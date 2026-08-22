window.YapDashRegisterAccessPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let state = { ops: [], groups: [], groupNames: [], selectedGroup: "" };

  const MC_COLORS = {
    "0": "#000000", "1": "#0000AA", "2": "#00AA00", "3": "#00AAAA",
    "4": "#AA0000", "5": "#AA00AA", "6": "#FFAA00", "7": "#AAAAAA",
    "8": "#555555", "9": "#5555FF", a: "#55FF55", b: "#55FFFF",
    c: "#FF5555", d: "#FF55FF", e: "#FFFF55", f: "#FFFFFF",
  };

  function setOut(text) {
    const el = $("accOut");
    if (el) el.textContent = text || "";
  }

  function mcColorHtml(raw, sampleName) {
    if (!raw) return sampleName || "Player";
    let html = "";
    let color = "#AAAAAA";
    let bold = false;
    let italic = false;
    const push = (text) => {
      if (!text) return;
      const style = [];
      if (color) style.push("color:" + color);
      if (bold) style.push("font-weight:700");
      if (italic) style.push("font-style:italic");
      html += `<span style="${style.join(";")}">${text.replace(/</g, "&lt;")}</span>`;
    };
    let buf = "";
    for (let i = 0; i < raw.length; i++) {
      const ch = raw[i];
      if (ch === "&" && i + 1 < raw.length) {
        push(buf);
        buf = "";
        const code = raw[i + 1].toLowerCase();
        i++;
        if (code === "r") {
          color = "#AAAAAA";
          bold = false;
          italic = false;
        } else if (code === "l") bold = true;
        else if (code === "o") italic = true;
        else if (MC_COLORS[code]) color = MC_COLORS[code];
        continue;
      }
      buf += ch;
    }
    push(buf);
    return html + (sampleName ? `<span style="color:#fff">${sampleName.replace(/</g, "&lt;")}</span>` : "");
  }

  function updatePrefixPreview() {
    const el = $("accPrefixPreview");
    if (!el) return;
    const prefix = $("accEditGroupPrefix")?.value || "";
    const suffix = $("accEditGroupSuffix")?.value || "";
    el.innerHTML = mcColorHtml(prefix + "Steve" + suffix);
  }

  function fillSelect(sel, names, current) {
    if (!sel) return;
    sel.innerHTML = "";
    (names || []).forEach((g) => {
      const o = document.createElement("option");
      o.value = g;
      o.textContent = g;
      sel.appendChild(o);
    });
    if (current && names.includes(current)) sel.value = current;
    else if (names.length) sel.value = names[0];
  }

  function groupByName(name) {
    return (state.groups || []).find((g) => g.name === name);
  }

  function loadEditGroup(name) {
    const g = groupByName(name);
    if (!g) return;
    if ($("accEditGroupName")) $("accEditGroupName").value = g.name;
    if ($("accEditGroupWeight")) $("accEditGroupWeight").value = g.weight ?? 0;
    if ($("accEditGroupPrefix")) $("accEditGroupPrefix").value = g.prefix || "";
    if ($("accEditGroupSuffix")) $("accEditGroupSuffix").value = g.suffix || "";
    if ($("accEditGroupParents")) $("accEditGroupParents").value = (g.parents || []).join(", ");
    if ($("accGroupDetail")) $("accGroupDetail").textContent = JSON.stringify(g, null, 2);
    updatePrefixPreview();
  }

  function renderOps() {
    const list = $("accOpsList");
    if (!list) return;
    list.innerHTML = "";
    state.ops.forEach((name) => {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.innerHTML = `<span>${name}</span><button type="button" title="Remove OP">×</button>`;
      chip.querySelector("button").onclick = async () => {
        try {
          const r = await netPost("/api/access", { action: "deop", player: name });
          state.ops = r.ops || state.ops.filter((n) => n.toLowerCase() !== name.toLowerCase());
          renderOps();
          $("accOpCount").textContent = String(state.ops.length);
          setOut(r.result || "Removed " + name);
        } catch (e) { setOut(e.message); }
      };
      list.appendChild(chip);
    });
  }

  function renderGroupCards() {
    const wrap = $("accGroupCards");
    if (!wrap) return;
    wrap.innerHTML = "";
    (state.groups || []).forEach((g) => {
      const card = document.createElement("button");
      card.type = "button";
      card.className = "group-card" + (g.name === state.selectedGroup ? " selected" : "");
      const parents = (g.parents || []).join(", ") || "—";
      const tagPreview = (g.prefix || "") + g.name + (g.suffix || "");
      card.innerHTML = `<strong>${g.name}</strong><span class="muted">weight ${g.weight ?? 0}</span>`
        + `<span class="prefix">${tagPreview.replace(/</g, "&lt;")}</span>`
        + `<span class="muted-small">inherits: ${parents}</span>`;
      card.onclick = async () => {
        state.selectedGroup = g.name;
        renderGroupCards();
        loadEditGroup(g.name);
        try {
          const r = await netPost("/api/access", { action: "group-info", group: g.name });
          if (r.result) $("accGroupDetail").textContent = r.result;
        } catch { /* keep JSON fallback */ }
      };
      wrap.appendChild(card);
    });
    if (!state.selectedGroup && state.groups.length) {
      state.selectedGroup = state.groups[0].name;
      loadEditGroup(state.selectedGroup);
      renderGroupCards();
    } else if (state.selectedGroup) {
      loadEditGroup(state.selectedGroup);
    }
  }

  async function refreshAccess() {
    try {
      const r = await api("/api/access");
      state.ops = r.ops || [];
      state.groups = r.groups || [];
      state.groupNames = r.groupNames || (state.groups.map((g) => g.name));
      if (!state.groupNames.length) state.groupNames = ["default", "vip", "mod", "admin"];

      $("accDefault").textContent = r.defaultGroup || "default";
      $("accAutoOp").textContent = r.autoOp ? "on" : "off";
      $("accOpCount").textContent = String(state.ops.length);
      $("accPermsOk").textContent = r.installed ? "ready" : "missing";

      const autoBox = $("accAutoOpBox");
      if (autoBox) autoBox.checked = !!r.autoOp;

      fillSelect($("accDefaultGroup"), state.groupNames, r.defaultGroup);
      fillSelect($("accPlayerGroup"), state.groupNames, r.defaultGroup);

      if (state.selectedGroup && !state.groupNames.includes(state.selectedGroup)) {
        state.selectedGroup = state.groupNames[0] || "";
      }

      renderOps();
      renderGroupCards();
      setOut("");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("accEditGroupPrefix")?.addEventListener("input", updatePrefixPreview);
  $("accEditGroupSuffix")?.addEventListener("input", updatePrefixPreview);

  $("accOpAdd")?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") $("accOpAddBtn")?.click();
  });

  $("accOpAddBtn")?.addEventListener("click", async () => {
    const p = $("accOpAdd")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "op", player: p });
      state.ops = r.ops || state.ops;
      renderOps();
      $("accOpCount").textContent = String(state.ops.length);
      $("accOpAdd").value = "";
      setOut(r.result || "OP granted to " + p);
    } catch (e) { setOut(e.message); }
  });

  $("accSaveOps")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "save-ops", ops: state.ops.join(",") });
      state.ops = r.ops || state.ops;
      renderOps();
      setOut("Operators saved.");
    } catch (e) { setOut(e.message); }
  });

  $("accAutoOpBox")?.addEventListener("change", async (ev) => {
    try {
      await netPost("/api/access", { action: "save-auto-op", autoOp: ev.target.checked ? "true" : "false" });
      $("accAutoOp").textContent = ev.target.checked ? "on" : "off";
      setOut("Auto-op " + (ev.target.checked ? "enabled" : "disabled") + ".");
    } catch (e) { setOut(e.message); refreshAccess(); }
  });

  $("accSaveDefault")?.addEventListener("click", async () => {
    const g = $("accDefaultGroup")?.value || "default";
    try {
      await netPost("/api/access", { action: "set-default-group", group: g });
      $("accDefault").textContent = g;
      setOut("Default rank set to " + g + ".");
    } catch (e) { setOut(e.message); }
  });

  $("accCreateGroup")?.addEventListener("click", async () => {
    const name = $("accNewGroupName")?.value.trim().toLowerCase();
    if (!name) { alert("Enter a rank id (e.g. helper)."); return; }
    try {
      const r = await netPost("/api/access", {
        action: "create-group",
        name,
        weight: $("accNewGroupWeight")?.value || "0",
        prefix: $("accNewGroupPrefix")?.value ?? "",
        suffix: $("accNewGroupSuffix")?.value ?? "",
        parents: $("accNewGroupParents")?.value ?? "",
        addToTrack: $("accNewGroupTrack")?.checked ? "true" : "false",
      });
      state.selectedGroup = name;
      setOut("Created rank " + name + ". " + (r.applypack || ""));
      $("accNewGroupName").value = "";
      refreshAccess();
    } catch (e) { setOut(e.message); }
  });

  $("accSaveGroup")?.addEventListener("click", async () => {
    const name = ($("accEditGroupName")?.value || state.selectedGroup || "").trim().toLowerCase();
    if (!name) { alert("Select a rank first."); return; }
    try {
      const r = await netPost("/api/access", {
        action: "save-group",
        name,
        weight: $("accEditGroupWeight")?.value || "0",
        prefix: $("accEditGroupPrefix")?.value ?? "",
        suffix: $("accEditGroupSuffix")?.value ?? "",
        parents: $("accEditGroupParents")?.value ?? "",
      });
      setOut("Saved rank " + name + ". " + (r.reload || ""));
      refreshAccess();
    } catch (e) { setOut(e.message); }
  });

  $("accDeleteGroup")?.addEventListener("click", async () => {
    const name = ($("accEditGroupName")?.value || state.selectedGroup || "").trim().toLowerCase();
    if (!name) return;
    if (name === "default") { alert("Cannot delete the default rank."); return; }
    if (!confirm("Delete rank '" + name + "'? Players on this rank may need reassignment.")) return;
    try {
      await netPost("/api/access", { action: "delete-group", name });
      state.selectedGroup = "";
      setOut("Deleted rank " + name + ".");
      refreshAccess();
    } catch (e) { setOut(e.message); }
  });

  $("accLookup")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "user-info", player: p });
      setOut(r.result || "No info returned.");
    } catch (e) { setOut(e.message); }
  });

  $("accSetGroup")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    const g = $("accPlayerGroup")?.value;
    if (!p) { alert("Enter a player name."); return; }
    try {
      const r = await netPost("/api/access", { action: "set-group", player: p, group: g });
      setOut(r.result || "Rank set to " + g + ".");
    } catch (e) { setOut(e.message); }
  });

  $("accSetMeta")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) { alert("Enter a player name."); return; }
    try {
      const r = await netPost("/api/access", {
        action: "user-meta-set",
        player: p,
        prefix: $("accMetaPrefix")?.value ?? "",
        suffix: $("accMetaSuffix")?.value ?? "",
      });
      setOut(r.result || "Name tags updated for " + p + ".");
    } catch (e) { setOut(e.message); }
  });

  $("accClearMeta")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) { alert("Enter a player name."); return; }
    try {
      const r = await netPost("/api/access", { action: "user-meta-clear", player: p });
      $("accMetaPrefix").value = "";
      $("accMetaSuffix").value = "";
      setOut(r.result || "Cleared name override for " + p + ".");
    } catch (e) { setOut(e.message); }
  });

  $("accPromote")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "promote", player: p });
      setOut(r.result || "Promoted.");
    } catch (e) { setOut(e.message); }
  });

  $("accDemote")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "demote", player: p });
      setOut(r.result || "Demoted.");
    } catch (e) { setOut(e.message); }
  });

  $("accUserPerm")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    const node = $("accPermNode")?.value.trim();
    const val = $("accPermVal")?.value || "true";
    if (!p || !node) { alert("Player and permission node required."); return; }
    try {
      const r = await netPost("/api/access", { action: "user-perm", player: p, node, value: val });
      setOut(r.result || "Permission set on player.");
    } catch (e) { setOut(e.message); }
  });

  $("accGroupPerm")?.addEventListener("click", async () => {
    const g = state.selectedGroup || $("accPlayerGroup")?.value;
    const node = $("accPermNode")?.value.trim();
    const val = $("accPermVal")?.value || "true";
    if (!g || !node) { alert("Select a group and enter a permission node."); return; }
    try {
      const r = await netPost("/api/access", { action: "group-perm", group: g, node, value: val });
      setOut(r.result || "Permission set on group " + g + ".");
    } catch (e) { setOut(e.message); }
  });

  $("accRefresh")?.addEventListener("click", () => refreshAccess());
  $("accReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "reload" });
      setOut(r.result || "YaPPerms reloaded.");
      refreshAccess();
    } catch (e) { setOut(e.message); }
  });
  $("accApplypack")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "applypack" });
      setOut(r.result || "Starter rank pack applied.");
      refreshAccess();
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { access: refreshAccess });
};
