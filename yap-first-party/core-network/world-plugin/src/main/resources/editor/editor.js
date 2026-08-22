(function () {
  "use strict";

  const PALETTE = {
    Building: ["STONE", "COBBLESTONE", "STONE_BRICKS", "DEEPSLATE", "DEEPSLATE_BRICKS", "BRICKS", "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS"],
    Natural: ["GRASS_BLOCK", "DIRT", "COARSE_DIRT", "SAND", "RED_SAND", "GRAVEL", "CLAY", "MOSS_BLOCK", "MYCELIUM"],
    Glass: ["GLASS", "TINTED_GLASS", "WHITE_STAINED_GLASS", "LIGHT_BLUE_STAINED_GLASS"],
    Decorative: ["GLOWSTONE", "SEA_LANTERN", "LANTERN", "QUARTZ_BLOCK", "PRISMARINE", "COPPER_BLOCK"],
    Utility: ["AIR", "WATER", "LAVA", "OBSIDIAN", "BEDROCK", "BARRIER"]
  };

  const params = new URLSearchParams(location.search);
  const token = params.get("token");

  let state = {};
  let busy = false;
  let lastSync = 0;
  let selectedSchem = null;
  let pasteAnchor = "player";

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => document.querySelectorAll(sel);

  function toast(msg, ok) {
    const el = document.createElement("div");
    el.className = "toast " + (ok ? "ok" : "err");
    el.textContent = msg;
    $("#toast-stack").appendChild(el);
    setTimeout(() => el.remove(), 4500);
  }

  function setBusy(on) {
    busy = on;
    $("#busy-overlay").classList.toggle("hidden", !on);
  }

  function fmtNum(n) { return (n || 0).toLocaleString(); }
  function fmtBytes(n) {
    if (!n) return "0 B";
    if (n < 1024) return n + " B";
    if (n < 1048576) return (n / 1024).toFixed(1) + " KB";
    return (n / 1048576).toFixed(1) + " MB";
  }
  function fmtDate(ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleString();
  }

  async function api(path, opts) {
    const res = await fetch(path, opts);
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText || "Request failed");
    return data;
  }

  async function act(action, extra, silent) {
    if (busy || !token) return;
    setBusy(true);
    try {
      const body = Object.assign({ token, action }, extra || {});
      const data = await api("/api/world-edit/action", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      if (!silent) toast(data.message || "Done", true);
      await refresh(true);
      return data;
    } catch (e) {
      toast(e.message, false);
    } finally {
      setBusy(false);
    }
  }

  async function refresh(silent) {
    if (!token) return;
    try {
      const data = await api("/api/world-edit/state?token=" + encodeURIComponent(token));
      state = data;
      lastSync = Date.now();
      render(data);
      $("#gate").classList.add("hidden");
      $("#app").classList.remove("hidden");
      $("#sync-dot").classList.remove("stale");
    } catch (e) {
      if (!silent) toast(e.message, false);
      $("#gate").classList.remove("hidden");
      $("#app").classList.add("hidden");
      $("#gate-error").textContent = e.message;
      $("#gate-error").classList.remove("hidden");
      $("#sync-dot").classList.add("stale");
    }
  }

  function render(s) {
    $("#player-name").textContent = s.player || "—";
    const badge = $("#online-badge");
    badge.textContent = s.online ? "in-game" : "offline";
    badge.className = "badge " + (s.online ? "on" : "off");
    $("#live-coords").textContent = s.online
      ? s.world + "  " + s.x + ", " + s.y + ", " + s.z
      : "player offline — reconnect in-game";
    renderOverview(s);
    renderSelection(s);
    renderBrush(s);
    renderSchematics(s);
    renderWorlds(s);
    renderHistory(s);
    renderPregen(s);
  }

  function renderOverview(s) {
    const lib = (s.schematicLibrary || []).length;
    const stats = [
      ["Mode", (s.mode || "select").toUpperCase()],
      ["Material", s.material || "—"],
      ["Volume", fmtNum(s.volume) + " / " + fmtNum(s.maxVolume)],
      ["Schematics", String(lib)],
      ["Undo", String(s.undoDepth || 0)],
      ["World", s.world || "—"],
      ["Selection", s.selectionReady ? "Ready" : "Incomplete"],
      ["Pregen", s.pregenAvailable ? "On" : "Off"]
    ];
    $("#overview-stats").innerHTML = stats.map(([k, v]) =>
      '<div class="stat"><span>' + k + '</span><strong>' + v + '</strong></div>'
    ).join("");
    const prev = $("#selection-preview");
    if (s.bounds) {
      const b = s.bounds;
      prev.className = "selection-preview ready";
      prev.innerHTML =
        "<div><strong>" + b.world + "</strong></div>" +
        "Size: " + b.sizeX + " × " + b.sizeY + " × " + b.sizeZ + " (" + fmtNum(s.volume) + " blocks)<br>" +
        "Min: " + b.minX + ", " + b.minY + ", " + b.minZ + "<br>" +
        "Max: " + b.maxX + ", " + b.maxY + ", " + b.maxZ;
    } else {
      prev.className = "selection-preview muted";
      prev.textContent = s.selectionIssue || "No selection — set pos1 and pos2";
    }
  }

  function renderSelection(s) {
    if (s.pos1) {
      $("#p1x").value = s.pos1.x; $("#p1y").value = s.pos1.y;
      $("#p1z").value = s.pos1.z; $("#p1w").value = s.pos1.world;
    }
    if (s.pos2) {
      $("#p2x").value = s.pos2.x; $("#p2y").value = s.pos2.y;
      $("#p2z").value = s.pos2.z; $("#p2w").value = s.pos2.world;
    }
    const st = $("#selection-status");
    if (s.selectionReady) {
      st.textContent = "Selection ready — " + fmtNum(s.volume) + " blocks";
      st.style.color = "var(--ok)";
    } else {
      st.textContent = s.selectionIssue || "Waiting for pos1 and pos2";
      st.style.color = "var(--muted)";
    }
  }

  function renderBrush(s) {
    $("#op-material").textContent = s.material || "STONE";
    $("#mode-segment").querySelectorAll("button").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.mode === (s.mode || "select"));
    });
    const r = s.radius || 3;
    const slider = $("#brush-radius");
    slider.max = s.maxRadius || 16;
    slider.value = r;
    $("#brush-radius-label").textContent = r;
    buildPalette(s.material);
  }

  function buildPalette(selected) {
    const root = $("#palette");
    root.innerHTML = "";
    Object.keys(PALETTE).forEach((cat) => {
      PALETTE[cat].forEach((mat) => {
        const btn = document.createElement("button");
        btn.textContent = mat.replace(/_/g, " ").toLowerCase();
        btn.title = cat + ": " + mat;
        if (mat === selected) btn.classList.add("selected");
        btn.addEventListener("click", () => act("set-material", { material: mat }));
        root.appendChild(btn);
      });
    });
  }

  function filterSchematics(list) {
    const q = ($("#schem-search") && $("#schem-search").value.trim().toLowerCase()) || "";
    if (!q) return list;
    return list.filter((s) => (s.name || "").toLowerCase().includes(q));
  }

  function renderSchematics(s) {
    const tbody = $("#schem-table-body");
    const empty = $("#schem-empty");
    if (!s.schematicsEnabled) {
      tbody.innerHTML = "";
      empty.textContent = "Schematics disabled on this server.";
      empty.classList.remove("hidden");
      return;
    }
    const lib = filterSchematics(s.schematicLibrary || []);
    if (!lib.length) {
      tbody.innerHTML = "";
      empty.classList.remove("hidden");
      return;
    }
    empty.classList.add("hidden");
    tbody.innerHTML = lib.map((item) => {
      const sel = selectedSchem === item.name ? " row-selected" : "";
      const size = item.sizeX + "×" + item.sizeY + "×" + item.sizeZ;
      return '<tr class="schem-row' + sel + '" data-name="' + item.name + '">' +
        '<td><strong>' + item.name + '</strong></td>' +
        '<td class="mono">' + size + '</td>' +
        '<td class="mono">' + fmtNum(item.blocks) + '</td>' +
        '<td><span class="fmt-badge">' + item.format + '</span></td>' +
        '<td><button type="button" class="ghost sm" data-quick-paste="' + item.name + '">Paste</button></td>' +
        '</tr>';
    }).join("");

    tbody.querySelectorAll(".schem-row").forEach((row) => {
      row.addEventListener("click", (e) => {
        if (e.target.closest("[data-quick-paste]")) return;
        selectSchematic(row.dataset.name, lib);
      });
    });
    tbody.querySelectorAll("[data-quick-paste]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        act("paste-schem", { name: btn.dataset.quickPaste, anchor: "player" });
      });
    });

    if (selectedSchem) {
      const found = lib.find((x) => x.name === selectedSchem);
      if (found) showSchemDetail(found);
    }
  }

  function selectSchematic(name, lib) {
    selectedSchem = name;
    const item = (lib || state.schematicLibrary || []).find((x) => x.name === name);
    if (item) showSchemDetail(item);
    renderSchematics(state);
  }

  function showSchemDetail(item) {
    $("#schem-detail-empty").classList.add("hidden");
    $("#schem-detail-body").classList.remove("hidden");
    $("#schem-detail-meta").innerHTML =
      '<dt>Name</dt><dd>' + item.name + '</dd>' +
      '<dt>Dimensions</dt><dd>' + item.sizeX + ' × ' + item.sizeY + ' × ' + item.sizeZ + '</dd>' +
      '<dt>Blocks</dt><dd>' + fmtNum(item.blocks) + '</dd>' +
      '<dt>Format</dt><dd>' + item.format + '</dd>' +
      '<dt>File size</dt><dd>' + fmtBytes(item.bytes) + '</dd>' +
      '<dt>Modified</dt><dd>' + fmtDate(item.modified) + '</dd>';
    $("#schem-rename-to").placeholder = item.name + "-copy";
  }

  function renderWorlds(s) {
    const loaded = new Set(s.loadedWorlds || []);
    const discovered = s.discoveredWorlds || [];
    $("#worlds-loaded").innerHTML = (s.loadedWorlds || []).map((w) =>
      '<div class="world-row loaded">' +
      '<span class="world-name">' + w + '</span>' +
      '<div class="world-actions">' +
      '<button type="button" data-world-tp="' + w + '">Go</button>' +
      (s.allowWorldUnload ? '<button type="button" class="ghost" data-world-unload="' + w + '">Unload</button>' : '') +
      '</div></div>'
    ).join("") || '<p class="hint muted">No worlds loaded.</p>';

    $("#worlds-discovered").innerHTML = discovered.map((w) => {
      const isLoaded = loaded.has(w);
      return '<div class="world-row' + (isLoaded ? " loaded" : "") + '">' +
        '<span class="world-name">' + w + (isLoaded ? ' <span class="tag">loaded</span>' : '') + '</span>' +
        '<div class="world-actions">' +
        (!isLoaded && s.allowWorldLoad ? '<button type="button" data-world-load="' + w + '">Load</button>' : '') +
        (isLoaded ? '<button type="button" data-world-tp="' + w + '">Go</button>' : '') +
        '</div></div>';
    }).join("");

    $$("[data-world-load]").forEach((btn) => {
      btn.onclick = () => act("world-load", { world: btn.dataset.worldLoad });
    });
    $$("[data-world-unload]").forEach((btn) => {
      btn.onclick = () => act("world-unload", { world: btn.dataset.worldUnload });
    });
    $$("[data-world-tp]").forEach((btn) => {
      btn.onclick = () => act("world-tp", { world: btn.dataset.worldTp });
    });
  }

  function renderHistory(s) {
    $("#history-stats").innerHTML =
      '<div class="stat"><span>Undo stack</span><strong>' + (s.undoDepth || 0) + " / " + (s.maxUndoSessions || 10) + '</strong></div>' +
      '<div class="stat"><span>Redo stack</span><strong>' + (s.redoDepth || 0) + '</strong></div>';
  }

  function renderPregen(s) {
    const st = $("#pregen-status");
    st.textContent = s.pregenAvailable
      ? "YaPPregen is loaded — pregenerate chunks from selection or radius."
      : "YaPPregen is not loaded. Install it to use pregen controls.";
    st.className = "card " + (s.pregenAvailable ? "" : "muted");
    $$("#tab-pregen [data-action^='pregen']").forEach((btn) => { btn.disabled = !s.pregenAvailable; });
    $("#btn-pregen-radius").disabled = !s.pregenAvailable;
  }

  async function importFile(file) {
    if (!file) return;
    if (file.size > 8 * 1024 * 1024) {
      toast("File too large (max 8 MB)", false);
      return;
    }
    setBusy(true);
    try {
      const buf = await file.arrayBuffer();
      const bytes = new Uint8Array(buf);
      let binary = "";
      for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
      const data = btoa(binary);
      await act("import-schem", { filename: file.name, data: data }, true);
      toast("Imported " + file.name, true);
    } catch (e) {
      toast(e.message, false);
    } finally {
      setBusy(false);
    }
  }

  /* Navigation */
  $$("#nav .nav-item").forEach((btn) => {
    btn.addEventListener("click", () => {
      $$("#nav .nav-item").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      $$(".tab").forEach((t) => t.classList.remove("active"));
      $("#tab-" + btn.dataset.tab).classList.add("active");
    });
  });

  $$("[data-action]").forEach((el) => {
    el.addEventListener("click", () => {
      const action = el.dataset.action;
      if (action.startsWith("pregen-") && action !== "pregen-start-selection") {
        const world = $("#pregen-world").value.trim();
        act(action, world ? { world } : {});
      } else {
        act(action);
      }
    });
  });

  $("#mode-segment").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-mode]");
    if (btn) act("set-mode", { mode: btn.dataset.mode });
  });

  $("#brush-radius").addEventListener("input", (e) => {
    $("#brush-radius-label").textContent = e.target.value;
  });
  $("#brush-radius").addEventListener("change", (e) => act("set-radius", { radius: e.target.value }));
  $("#radius-minus").addEventListener("click", () => act("adjust-radius", { delta: "-1" }));
  $("#radius-plus").addEventListener("click", () => act("adjust-radius", { delta: "1" }));

  $("#btn-set-pos1-manual").addEventListener("click", () =>
    act("set-pos1", { x: $("#p1x").value, y: $("#p1y").value, z: $("#p1z").value, world: $("#p1w").value }));
  $("#btn-set-pos2-manual").addEventListener("click", () =>
    act("set-pos2", { x: $("#p2x").value, y: $("#p2y").value, z: $("#p2z").value, world: $("#p2w").value }));

  function bindMaterial(inputId, btnId) {
    const run = () => {
      const v = $(inputId).value.trim().toUpperCase();
      if (v) act("set-material", { material: v });
    };
    $(btnId).addEventListener("click", run);
    $(inputId).addEventListener("keydown", (e) => { if (e.key === "Enter") run(); });
  }
  bindMaterial("#op-material-input", "#op-material-apply");
  bindMaterial("#brush-material-input", "#brush-material-apply");

  $("#btn-replace").addEventListener("click", () => {
    const from = $("#replace-from").value.trim();
    const to = $("#replace-to").value.trim();
    if (!from || !to) { toast("Enter from and to blocks", false); return; }
    act("replace", { from, to });
  });

  $("#btn-save-schem").addEventListener("click", () => {
    act("save-schem", { name: $("#schem-save-name").value.trim() });
  });

  $("#btn-pregen-radius").addEventListener("click", () => {
    act("pregen-start-radius", { radius: $("#pregen-radius").value });
  });

  $("#btn-world-load").addEventListener("click", () => {
    const w = $("#world-load-name").value.trim();
    if (w) act("world-load", { world: w });
  });

  $("#schem-search") && $("#schem-search").addEventListener("input", () => renderSchematics(state));

  $("#paste-anchor").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-anchor]");
    if (!btn) return;
    pasteAnchor = btn.dataset.anchor;
    $("#paste-anchor").querySelectorAll("button").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    $("#paste-custom-coords").classList.toggle("hidden", pasteAnchor !== "custom");
  });

  $("#btn-paste-selected").addEventListener("click", () => {
    if (!selectedSchem) { toast("Select a schematic first", false); return; }
    const extra = { name: selectedSchem, anchor: pasteAnchor };
    if (pasteAnchor === "custom") {
      extra.x = $("#paste-x").value;
      extra.y = $("#paste-y").value;
      extra.z = $("#paste-z").value;
    }
    act("paste-schem", extra);
  });

  $("#btn-download-schem").addEventListener("click", () => {
    if (!selectedSchem || !token) return;
    window.open("/api/world-edit/schematic/download?token=" + encodeURIComponent(token) +
      "&name=" + encodeURIComponent(selectedSchem), "_blank");
  });

  $("#btn-delete-schem").addEventListener("click", () => {
    if (!selectedSchem || !confirm("Delete " + selectedSchem + "?")) return;
    act("delete-schem", { name: selectedSchem }).then(() => { selectedSchem = null; });
  });

  $("#btn-duplicate-schem").addEventListener("click", () => {
    if (!selectedSchem) return;
    const to = ($("#schem-rename-to").value.trim() || selectedSchem + "-copy");
    act("duplicate-schem", { from: selectedSchem, to: to });
  });

  $("#btn-rename-schem").addEventListener("click", () => {
    if (!selectedSchem) return;
    const to = $("#schem-rename-to").value.trim();
    if (!to) { toast("Enter new name", false); return; }
    act("rename-schem", { from: selectedSchem, to: to }).then(() => { selectedSchem = to; });
  });

  const drop = $("#schem-drop");
  const fileInput = $("#schem-file-input");
  $("#schem-browse").addEventListener("click", () => fileInput.click());
  fileInput.addEventListener("change", () => importFile(fileInput.files[0]));
  drop.addEventListener("dragover", (e) => { e.preventDefault(); drop.classList.add("drag"); });
  drop.addEventListener("dragleave", () => drop.classList.remove("drag"));
  drop.addEventListener("drop", (e) => {
    e.preventDefault();
    drop.classList.remove("drag");
    if (e.dataTransfer.files[0]) importFile(e.dataTransfer.files[0]);
  });

  if (!token) {
    $("#gate-error").textContent = "Missing session token. Run /yapworld editor in-game.";
    $("#gate-error").classList.remove("hidden");
  } else {
    refresh();
    setInterval(() => {
      if (!busy) refresh(true);
      if (Date.now() - lastSync > 8000) $("#sync-dot").classList.add("stale");
    }, 2000);
  }
})();
