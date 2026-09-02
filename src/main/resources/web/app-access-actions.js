window.YapDashBindAccessActions = function (YapDash, ctx) {
  const { $, netPost } = YapDash;
  const {
    state, setOut, refreshAccess, loadDraft, setDraft,
    renderPermEditor, renderOps, catalogNodeSet,
  } = ctx;

  function addNodeToDraft(node, allow) {
    const n = (node || "").trim();
    if (!n || /[\s"']/.test(n)) return false;
    setDraft(n, allow);
    return true;
  }

  $("accAddCustom")?.addEventListener("click", () => {
    const node = $("accCustomNode")?.value.trim();
    if (!node) { alert("Enter a permission node."); return; }
    if (!addNodeToDraft(node, $("accCustomVal")?.value !== "false")) {
      alert("Permission nodes cannot contain spaces or quotes.");
      return;
    }
    $("accCustomNode").value = "";
  });

  $("accAddBulk")?.addEventListener("click", () => {
    const raw = $("accBulkNodes")?.value || "";
    const allow = $("accCustomVal")?.value !== "false";
    let n = 0;
    raw.split(/[,\n]/).forEach((part) => {
      if (addNodeToDraft(part, allow)) n++;
    });
    if (!n) { alert("Enter at least one permission node."); return; }
    $("accBulkNodes").value = "";
    setOut("Added " + n + " custom node(s) to the draft. Save permissions to apply.");
  });

  $("accApplyPackBtn")?.addEventListener("click", async () => {
    const g = state.selectedGroup;
    const template = $("accApplyTemplate")?.value || "player";
    if (!g) { alert("Select a rank first."); return; }
    if (!confirm("Replace explicit permissions on '" + g + "' with the " + template + " pack?")) return;
    try {
      const r = await netPost("/api/access", { action: "apply-template", group: g, template });
      if (r.groupNodes) state.groupNodes = r.groupNodes;
      loadDraft(g);
      setOut("Applied " + template + " pack to " + g + " (" + (r.allow || 0) + " allows).");
    } catch (e) { setOut(e.message, true); }
  });

  $("accCloneBtn")?.addEventListener("click", async () => {
    const g = state.selectedGroup;
    const from = $("accCloneFrom")?.value;
    if (!g || !from) { alert("Select a source and target rank."); return; }
    if (g === from) { alert("Pick a different rank to copy from."); return; }
    if (!confirm("Copy permissions from '" + from + "' onto '" + g + "'?")) return;
    try {
      const r = await netPost("/api/access", { action: "clone-group", group: g, cloneFrom: from });
      if (r.groupNodes) state.groupNodes = r.groupNodes;
      loadDraft(g);
      setOut("Copied perms from " + from + " to " + g + ".");
    } catch (e) { setOut(e.message, true); }
  });

  $("accSaveNodes")?.addEventListener("click", async () => {
    const g = state.selectedGroup;
    if (!g) { alert("Select a rank first."); return; }
    const prev = state.groupNodes[g] || {};
    const keys = new Set([...Object.keys(prev), ...Object.keys(state.draft), ...catalogNodeSet()]);
    const allow = [];
    const deny = [];
    const unset = [];
    keys.forEach((node) => {
      const val = state.draft[node];
      if (val === true) allow.push(node);
      else if (val === false) deny.push(node);
      else if (Object.prototype.hasOwnProperty.call(prev, node)) unset.push(node);
    });
    try {
      const r = await netPost("/api/access", {
        action: "save-group-nodes",
        group: g,
        allow: allow.join(","),
        deny: deny.join(","),
        unset: unset.join(","),
      });
      if (r.groupNodes) state.groupNodes = r.groupNodes;
      else {
        state.groupNodes[g] = { ...state.draft };
      }
      state.dirty = false;
      renderPermEditor();
      setOut("Saved " + (r.allow || allow.length) + " allow / "
        + (r.deny || deny.length) + " deny / "
        + (r.unset || unset.length) + " inherit on " + g + ".");
    } catch (e) { setOut(e.message, true); }
  });

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
    } catch (e) { setOut(e.message, true); }
  });

  $("accSaveOps")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "save-ops", ops: state.ops.join(",") });
      state.ops = r.ops || state.ops;
      renderOps();
      setOut("Operators saved.");
    } catch (e) { setOut(e.message, true); }
  });

  $("accAutoOpBox")?.addEventListener("change", async (ev) => {
    try {
      await netPost("/api/access", { action: "save-auto-op", autoOp: ev.target.checked ? "true" : "false" });
      $("accAutoOp").textContent = ev.target.checked ? "on" : "off";
      setOut("Auto-op " + (ev.target.checked ? "enabled" : "disabled") + ".");
    } catch (e) { setOut(e.message, true); refreshAccess(); }
  });

  $("accSaveDefault")?.addEventListener("click", async () => {
    const g = $("accDefaultGroup")?.value || "default";
    try {
      await netPost("/api/access", { action: "set-default-group", group: g });
      $("accDefault").textContent = g;
      setOut("Default rank set to " + g + ".");
    } catch (e) { setOut(e.message, true); }
  });

  $("accCreateGroup")?.addEventListener("click", async () => {
    const name = $("accNewGroupName")?.value.trim().toLowerCase();
    if (!name) { alert("Enter a rank id (e.g. helper)."); return; }
    try {
      const pack = $("accNewTemplate")?.value || "blank";
      const r = await netPost("/api/access", {
        action: "create-group",
        name,
        weight: $("accNewGroupWeight")?.value || "0",
        prefix: $("accNewGroupPrefix")?.value ?? "",
        suffix: $("accNewGroupSuffix")?.value ?? "",
        nameColor: $("accNewGroupNameColor")?.value ?? "",
        chatColor: $("accNewGroupChatColor")?.value ?? "",
        parents: $("accNewGroupParents")?.value ?? "",
        addToTrack: $("accNewGroupTrack")?.checked ? "true" : "false",
        template: pack === "clone" ? "" : pack,
        cloneFrom: pack === "clone" ? ($("accNewClone")?.value || "") : "",
      });
      state.selectedGroup = name;
      setOut("Created rank " + name + ". " + (r.applypack || ""));
      $("accNewGroupName").value = "";
      refreshAccess();
    } catch (e) { setOut(e.message, true); }
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
        nameColor: $("accEditGroupNameColor")?.value ?? "",
        chatColor: $("accEditGroupChatColor")?.value ?? "",
        parents: $("accEditGroupParents")?.value ?? "",
      });
      setOut("Saved rank " + name + ". " + (r.reload || ""));
      refreshAccess();
    } catch (e) { setOut(e.message, true); }
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
    } catch (e) { setOut(e.message, true); }
  });

  $("accLookup")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "user-info", player: p });
      setOut(r.result || "No info returned.");
    } catch (e) { setOut(e.message, true); }
  });

  $("accSetGroup")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    const g = $("accPlayerGroup")?.value;
    if (!p) { alert("Enter a player name."); return; }
    try {
      const r = await netPost("/api/access", { action: "set-group", player: p, group: g });
      setOut(r.result || "Rank set to " + g + ".");
    } catch (e) { setOut(e.message, true); }
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
    } catch (e) { setOut(e.message, true); }
  });

  $("accClearMeta")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) { alert("Enter a player name."); return; }
    try {
      const r = await netPost("/api/access", { action: "user-meta-clear", player: p });
      $("accMetaPrefix").value = "";
      $("accMetaSuffix").value = "";
      setOut(r.result || "Cleared name override for " + p + ".");
    } catch (e) { setOut(e.message, true); }
  });

  $("accPromote")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "promote", player: p });
      setOut(r.result || "Promoted.");
    } catch (e) { setOut(e.message, true); }
  });

  $("accDemote")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    if (!p) return;
    try {
      const r = await netPost("/api/access", { action: "demote", player: p });
      setOut(r.result || "Demoted.");
    } catch (e) { setOut(e.message, true); }
  });

  $("accUserPerm")?.addEventListener("click", async () => {
    const p = $("accPlayer")?.value.trim();
    const node = $("accPermNode")?.value.trim();
    const val = $("accPermVal")?.value || "true";
    if (!p || !node) { alert("Player and permission node required."); return; }
    try {
      const r = await netPost("/api/access", { action: "user-perm", player: p, node, value: val });
      setOut(r.result || "Permission set on player.");
    } catch (e) { setOut(e.message, true); }
  });

  $("accGroupPerm")?.addEventListener("click", async () => {
    const g = state.selectedGroup || $("accPlayerGroup")?.value;
    const node = $("accPermNode")?.value.trim();
    const val = $("accPermVal")?.value || "true";
    if (!g || !node) { alert("Select a group and enter a permission node."); return; }
    try {
      const r = await netPost("/api/access", { action: "group-perm", group: g, node, value: val });
      setOut(r.result || "Permission set on group " + g + ".");
    } catch (e) { setOut(e.message, true); }
  });

  $("accRefresh")?.addEventListener("click", async () => {
    try { await netPost("/api/access", { action: "dump" }); } catch { /* Folia may be down */ }
    refreshAccess();
  });
  $("accReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "reload" });
      setOut(r.result || "YaPPerms reloaded.");
      refreshAccess();
    } catch (e) { setOut(e.message, true); }
  });
  $("accApplypack")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/access", { action: "applypack" });
      setOut(r.result || "Starter rank pack applied.");
      refreshAccess();
    } catch (e) { setOut(e.message, true); }
  });

};
