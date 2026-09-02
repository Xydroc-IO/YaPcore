window.YapDashRegisterAccessPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let state = {
    ops: [], groups: [], groupNames: [], selectedGroup: "",
    catalog: [], groupNodes: {}, draft: {}, dirty: false,
  };

  function paint(raw) {
    return (window.YapMcColor && YapMcColor.toHtml)
      ? YapMcColor.toHtml(raw)
      : String(raw || "").replace(/</g, "&lt;");
  }

  function setOut(text, isError) {
    const el = $("accOut");
    if (!el) return;
    const msg = text || "";
    el.textContent = msg;
    el.classList.toggle("hidden", !msg);
    el.classList.toggle("is-error", !!isError && !!msg);
    el.toggleAttribute("hidden", !msg);
  }

  function chatLineHtml(prefix, nameColor, suffix, chatColor, name) {
    const who = name || "Steve";
    return paint((prefix || "") + (nameColor || "&f") + who + (suffix || "") + "&7: " + (chatColor || "&f") + "hello");
  }

  function updatePrefixPreview() {
    const el = $("accPrefixPreview");
    if (!el) return;
    el.innerHTML = chatLineHtml(
      $("accEditGroupPrefix")?.value,
      $("accEditGroupNameColor")?.value,
      $("accEditGroupSuffix")?.value,
      $("accEditGroupChatColor")?.value,
    );
  }

  function updateNewPreview() {
    const el = $("accNewPreview");
    if (!el) return;
    const name = $("accNewGroupName")?.value.trim() || "helper";
    el.innerHTML = chatLineHtml(
      $("accNewGroupPrefix")?.value,
      $("accNewGroupNameColor")?.value,
      $("accNewGroupSuffix")?.value,
      $("accNewGroupChatColor")?.value,
      name,
    );
  }

  function switchAccPane(pane) {
    document.querySelectorAll("#accSubnav [data-acc-pane]").forEach((b) => {
      b.classList.toggle("active", b.dataset.accPane === pane);
    });
    ["ranks", "ops", "players"].forEach((id) => {
      const el = $("accPane" + id.charAt(0).toUpperCase() + id.slice(1));
      if (el) el.hidden = id !== pane;
    });
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
    if ($("accEditGroupNameColor")) $("accEditGroupNameColor").value = g.nameColor || "&f";
    if ($("accEditGroupChatColor")) $("accEditGroupChatColor").value = g.chatColor || "&f";
    if ($("accEditGroupParents")) $("accEditGroupParents").value = (g.parents || []).join(", ");
    if ($("accEditTitle")) $("accEditTitle").textContent = g.name;
    if ($("accGroupDetail")) $("accGroupDetail").textContent = "";
    updatePrefixPreview();
    ["accEditGroupPrefix", "accEditGroupSuffix", "accEditGroupNameColor", "accEditGroupChatColor"]
      .forEach((id) => $(id)?.dispatchEvent(new Event("input")));
    loadDraft(name);
  }

  function loadDraft(name) {
    const src = (state.groupNodes && state.groupNodes[name]) || {};
    state.draft = { ...src };
    state.dirty = false;
    renderPermEditor();
  }

  function catalogNodeSet() {
    const set = new Set();
    (state.catalog || []).forEach((cat) => (cat.nodes || []).forEach((n) => set.add(n.node)));
    return set;
  }

  function extraDraftNodes() {
    const catalog = catalogNodeSet();
    return Object.keys(state.draft || {}).filter((n) => !catalog.has(n)).sort();
  }

  function setDraft(node, value) {
    if (value === null) delete state.draft[node];
    else state.draft[node] = value;
    state.dirty = true;
    renderPermEditor();
  }

  function categoryAllowCount(cat) {
    let n = 0;
    (cat.nodes || []).forEach((row) => { if (state.draft[row.node] === true) n++; });
    return n;
  }

  function renderPermEditor() {
    const wrap = $("accPermCats");
    const label = $("accPermGroupLabel");
    const dirty = $("accPermDirty");
    if (label) label.textContent = state.selectedGroup || "—";
    if (dirty) dirty.textContent = state.dirty ? "Unsaved changes" : "";
    if (!wrap) return;
    const q = ($("accPermSearch")?.value || "").trim().toLowerCase();
    const extras = extraDraftNodes();
    const cats = (state.catalog || []).slice();
    if (extras.length) {
      cats.push({
        id: "custom",
        title: "Custom nodes",
        hint: "Nodes set on this rank that are not in the catalog.",
        nodes: extras.map((node) => ({ node, label: node, desc: "Custom / extra node" })),
      });
    }
    wrap.innerHTML = "";
    if (!state.selectedGroup) {
      wrap.innerHTML = `<p class="muted">Select a rank above to edit its commands.</p>`;
      return;
    }
    cats.forEach((cat) => {
      const nodes = (cat.nodes || []).filter((row) => {
        if (!q) return true;
        const hay = `${row.node} ${row.label || ""} ${row.desc || ""} ${cat.title}`.toLowerCase();
        return hay.includes(q);
      });
      if (!nodes.length && q) return;
      const open = !!q || cat.id === "player" || cat.id === "vanilla" || cat.id === "custom";
      const box = document.createElement("div");
      box.className = "perm-cat";
      const allowed = categoryAllowCount(cat);
      box.innerHTML = `<button type="button" class="perm-cat-head" data-toggle="1">`
        + `<strong>${cat.title}</strong>`
        + `<span class="perm-count">${allowed}/${(cat.nodes || []).length} allowed</span>`
        + `<span class="muted-small">${open ? "▾" : "▸"}</span></button>`
        + (cat.hint ? `<p class="perm-cat-hint">${cat.hint}</p>` : "")
        + `<div class="perm-body" ${open ? "" : "hidden"}></div>`
        + `<div class="perm-cat-actions" ${open ? "" : "hidden"}>`
        + `<button type="button" data-bulk="allow">Allow category</button>`
        + `<button type="button" data-bulk="inherit">Inherit category</button></div>`;
      const body = box.querySelector(".perm-body");
      nodes.forEach((row) => {
        const val = state.draft[row.node];
        const mode = val === true ? "allow" : val === false ? "deny" : "inherit";
        const line = document.createElement("div");
        line.className = "perm-row" + (row.danger ? " danger" : "");
        line.innerHTML = `<div>`
          + `<div class="perm-label">${(row.label || row.node).replace(/</g, "&lt;")}</div>`
          + `<div class="perm-desc">${(row.desc || "").replace(/</g, "&lt;")}</div>`
          + `<div class="perm-node">${row.node.replace(/</g, "&lt;")}</div></div>`
          + `<div class="perm-tri">`
          + `<button type="button" data-node="${row.node}" data-mode="inherit" class="${mode === "inherit" ? "on-inherit" : ""}">Inherit</button>`
          + `<button type="button" data-node="${row.node}" data-mode="allow" class="${mode === "allow" ? "on-allow" : ""}">Allow</button>`
          + `<button type="button" data-node="${row.node}" data-mode="deny" class="${mode === "deny" ? "on-deny" : ""}">Deny</button>`
          + `</div>`;
        body.appendChild(line);
      });
      box.querySelector("[data-toggle]").onclick = () => {
        const hide = !body.hasAttribute("hidden");
        body.toggleAttribute("hidden", hide);
        box.querySelector(".perm-cat-actions").toggleAttribute("hidden", hide);
        box.querySelector(".muted-small").textContent = hide ? "▸" : "▾";
      };
      box.querySelectorAll(".perm-tri button").forEach((btn) => {
        btn.onclick = () => {
          const mode = btn.dataset.mode;
          setDraft(btn.dataset.node, mode === "allow" ? true : mode === "deny" ? false : null);
        };
      });
      box.querySelector("[data-bulk=allow]").onclick = () => {
        (cat.nodes || []).forEach((row) => { state.draft[row.node] = true; });
        state.dirty = true;
        renderPermEditor();
      };
      box.querySelector("[data-bulk=inherit]").onclick = () => {
        (cat.nodes || []).forEach((row) => { delete state.draft[row.node]; });
        state.dirty = true;
        renderPermEditor();
      };
      wrap.appendChild(box);
    });
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
        } catch (e) { setOut(e.message, true); }
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
      const look = chatLineHtml(g.prefix, g.nameColor, g.suffix, g.chatColor, g.name);
      card.innerHTML = `<strong>${g.name}</strong><span class="muted">weight ${g.weight ?? 0}</span>`
        + `<div class="look">${look}</div>`
        + `<span class="muted-small">inherits: ${parents}</span>`;
      card.onclick = async () => {
        state.selectedGroup = g.name;
        renderGroupCards();
        loadEditGroup(g.name);
        try {
          await netPost("/api/access", { action: "group-info", group: g.name });
        } catch { /* preview already filled from snapshot */ }
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
      state.catalog = r.catalog || state.catalog || [];
      state.groupNodes = r.groupNodes || {};
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
      fillSelect($("accNewClone"), state.groupNames, r.defaultGroup);
      fillSelect($("accCloneFrom"), state.groupNames, r.defaultGroup);
      if (r.templates && $("accNewTemplate")) {
        const sel = $("accNewTemplate");
        const cur = sel.value;
        if (sel.options.length <= 5) {
          /* keep static options */
        }
        sel.value = cur || "blank";
      }

      if (state.selectedGroup && !state.groupNames.includes(state.selectedGroup)) {
        state.selectedGroup = state.groupNames[0] || "";
      }

      renderOps();
      renderGroupCards();
      setOut("");
    } catch (e) {
      setOut(e.message, true);
    }
  }

  document.querySelectorAll("#accSubnav [data-acc-pane]").forEach((btn) => {
    btn.addEventListener("click", () => switchAccPane(btn.dataset.accPane));
  });

  ["accEditGroupPrefix", "accEditGroupSuffix", "accEditGroupNameColor", "accEditGroupChatColor"]
    .forEach((id) => $(id)?.addEventListener("input", updatePrefixPreview));
  ["accNewGroupPrefix", "accNewGroupSuffix", "accNewGroupNameColor", "accNewGroupChatColor", "accNewGroupName"]
    .forEach((id) => $(id)?.addEventListener("input", updateNewPreview));
  updateNewPreview();
  $("accPermSearch")?.addEventListener("input", () => renderPermEditor());

  if (typeof YapDashBindAccessActions === "function") {
    YapDashBindAccessActions(YapDash, {
      state, setOut, refreshAccess, loadDraft, setDraft,
      renderPermEditor, renderOps, catalogNodeSet,
    });
  }

  Object.assign(YapDash.tabLoads, { access: refreshAccess });
};
