window.YapDashRegisterKitsPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let state = { kits: [], selected: "" };

  const MATERIALS = [
    "BREAD", "COOKED_BEEF", "COOKED_CHICKEN", "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE",
    "APPLE", "CARROT", "POTATO", "BAKED_POTATO", "COOKED_PORKCHOP",
    "WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD",
    "WOODEN_PICKAXE", "STONE_PICKAXE", "IRON_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE",
    "WOODEN_AXE", "STONE_AXE", "IRON_AXE", "DIAMOND_AXE", "BOW", "ARROW", "SHIELD",
    "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
    "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
    "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
    "TORCH", "COBBLESTONE", "OAK_LOG", "OAK_PLANKS", "DIRT", "WATER_BUCKET",
    "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "EMERALD", "ENDER_PEARL", "EXPERIENCE_BOTTLE",
  ];

  function setOut(text) {
    const el = $("kitOut");
    if (el) el.textContent = text || "";
  }

  function fillMaterials() {
    const list = $("kitMaterials");
    if (!list || list.childElementCount) return;
    MATERIALS.forEach((m) => {
      const o = document.createElement("option");
      o.value = m;
      list.appendChild(o);
    });
  }

  function kitById(id) {
    return (state.kits || []).find((k) => k.id === id);
  }

  function emptyItem() {
    return { material: "STONE", amount: 1, slot: "inventory", name: "", lore: "", enchantments: "" };
  }

  function renderCards() {
    const wrap = $("kitCards");
    if (!wrap) return;
    wrap.innerHTML = "";
    (state.kits || []).forEach((k) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "group-card" + (k.id === state.selected ? " selected" : "");
      const extra = [];
      if (k.firstJoin) extra.push("first join");
      if (k.cost) extra.push("cost " + k.cost);
      const n = k.itemCount || (k.items || []).length;
      extra.push(n === 1 ? "1 item" : n + " items");
      btn.innerHTML = `<strong>${k.id}</strong><span class="muted">cd ${k.delaySeconds || 0}s</span>`
        + `<span class="muted-small">${extra.join(" · ") || "—"}</span>`;
      btn.onclick = () => {
        state.selected = k.id;
        renderCards();
        loadKit(k);
      };
      wrap.appendChild(btn);
    });
    if (!state.selected && state.kits.length) {
      state.selected = state.kits[0].id;
      loadKit(state.kits[0]);
      renderCards();
    }
  }

  function loadKit(kit) {
    if (!kit) return;
    $("kitEditLabel").textContent = kit.id;
    $("kitId").value = kit.id;
    $("kitDelay").value = kit.delaySeconds ?? 86400;
    $("kitMaxUses").value = kit.maxUses ?? 0;
    $("kitCost").value = kit.cost ?? 0;
    $("kitFirstJoin").checked = !!kit.firstJoin;
    $("kitCommands").value = (kit.commands || []).join("\n");
    const items = kit.items || [];
    const lossy = $("kitLossy");
    if (lossy) lossy.hidden = !items.some((it) => it.lossy);
    renderItems(items);
  }

  function renderItems(items) {
    const wrap = $("kitItems");
    if (!wrap) return;
    wrap.innerHTML = "";
    (items.length ? items : [emptyItem()]).forEach((item) => wrap.appendChild(itemRow(item)));
  }

  function itemRow(item) {
    const row = document.createElement("div");
    row.className = "kit-item-row";
    row.innerHTML = `<input data-f="material" list="kitMaterials" placeholder="BREAD" value="${esc(item.material || "")}"/>`
      + `<input data-f="amount" type="number" min="1" value="${item.amount || 1}"/>`
      + `<select data-f="slot">`
      + slotOpts(item.slot)
      + `</select>`
      + `<input data-f="name" placeholder="&amp;6Name" value="${esc(item.name || "")}"/>`
      + `<input data-f="enchantments" placeholder="sharpness:1,unbreaking:1" value="${esc(item.enchantments || "")}"/>`
      + `<input data-f="lore" placeholder="lore line; second line" value="${esc(item.lore || "")}"/>`
      + `<button type="button" class="danger kit-item-del" title="Remove">×</button>`;
    row.querySelector(".kit-item-del").onclick = () => {
      row.remove();
      if (!$("kitItems").children.length) $("kitItems").appendChild(itemRow(emptyItem()));
    };
    return row;
  }

  function slotOpts(current) {
    const slots = ["inventory", "helmet", "chestplate", "leggings", "boots", "offhand"];
    return slots.map((s) => `<option value="${s}"${s === (current || "inventory") ? " selected" : ""}>${s}</option>`).join("");
  }

  function esc(s) {
    return String(s || "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function collectItems() {
    const lines = [];
    $("kitItems")?.querySelectorAll(".kit-item-row").forEach((row) => {
      const g = (f) => row.querySelector("[data-f=" + f + "]")?.value.trim() || "";
      const material = g("material").toUpperCase().replace(/\s+/g, "_");
      if (!material) return;
      const clean = (v) => v.replace(/\|/g, "/");
      lines.push([material, g("amount") || "1", g("slot") || "inventory",
        clean(g("name")), clean(g("lore")), clean(g("enchantments"))].join("|"));
    });
    return lines.join("\n");
  }

  async function refreshKits() {
    fillMaterials();
    try {
      const r = await api("/api/kits");
      state.kits = r.kits || [];
      $("kitCount").textContent = String(state.kits.length);
      $("kitFileOk").textContent = r.configPresent ? "kits.yml" : "missing";
      if (state.selected && !state.kits.some((k) => k.id === state.selected)) {
        state.selected = "";
      }
      renderCards();
      if (state.selected) {
        const k = kitById(state.selected);
        if (k) loadKit(k);
      }
    } catch (e) {
      setOut(e.message);
    }
  }

  $("kitAddItem")?.addEventListener("click", () => {
    $("kitItems")?.appendChild(itemRow(emptyItem()));
  });

  $("kitCreate")?.addEventListener("click", async () => {
    const id = ($("kitNewId")?.value || "").trim().toLowerCase().replace(/\s+/g, "_");
    if (!id) { alert("Enter a kit id."); return; }
    try {
      const r = await netPost("/api/kits", {
        action: "save-kit",
        id,
        delaySeconds: "86400",
        maxUses: "0",
        cost: "0",
        firstJoin: "false",
        commands: "",
        items: "BREAD|16|inventory|||",
      });
      state.selected = id;
      state.kits = r.kits || state.kits;
      $("kitNewId").value = "";
      setOut("Created kit " + id + ". Add items and save.");
      refreshKits();
    } catch (e) { setOut(e.message); }
  });

  $("kitClone")?.addEventListener("click", async () => {
    const from = state.selected;
    const to = ($("kitNewId")?.value || "").trim().toLowerCase().replace(/\s+/g, "_");
    if (!from) { alert("Select a kit to clone."); return; }
    if (!to) { alert("Enter a new kit id."); return; }
    try {
      const r = await netPost("/api/kits", { action: "clone-kit", from, to });
      state.selected = to;
      state.kits = r.kits || state.kits;
      $("kitNewId").value = "";
      setOut("Cloned " + from + " to " + to + ".");
      refreshKits();
    } catch (e) { setOut(e.message); }
  });

  $("kitSave")?.addEventListener("click", async () => {
    const id = $("kitId")?.value.trim();
    if (!id) { alert("Select or create a kit first."); return; }
    try {
      const r = await netPost("/api/kits", {
        action: "save-kit",
        id,
        delaySeconds: $("kitDelay")?.value || "0",
        maxUses: $("kitMaxUses")?.value || "0",
        cost: $("kitCost")?.value || "0",
        firstJoin: $("kitFirstJoin")?.checked ? "true" : "false",
        commands: $("kitCommands")?.value || "",
        items: collectItems(),
      });
      state.kits = r.kits || state.kits;
      setOut("Saved kit " + id + ".");
      renderCards();
    } catch (e) { setOut(e.message); }
  });

  $("kitDelete")?.addEventListener("click", async () => {
    const id = $("kitId")?.value.trim();
    if (!id) return;
    if (!confirm("Delete kit '" + id + "'?")) return;
    try {
      const r = await netPost("/api/kits", { action: "delete-kit", id });
      state.selected = "";
      state.kits = r.kits || [];
      $("kitId").value = "";
      $("kitEditLabel").textContent = "—";
      renderItems([]);
      setOut("Deleted " + id + ".");
      refreshKits();
    } catch (e) { setOut(e.message); }
  });

  $("kitGive")?.addEventListener("click", async () => {
    const player = $("kitGivePlayer")?.value.trim();
    const kit = $("kitId")?.value.trim();
    if (!player || !kit) { alert("Player and kit required."); return; }
    try {
      const r = await netPost("/api/kits", { action: "give", player, kit });
      setOut(r.result || "Gave " + kit + " to " + player + ".");
    } catch (e) { setOut(e.message); }
  });

  $("kitGrant")?.addEventListener("click", async () => {
    const player = $("kitGivePlayer")?.value.trim();
    const kit = $("kitId")?.value.trim();
    if (!player || !kit) { alert("Player and kit required."); return; }
    try {
      const r = await netPost("/api/kits", { action: "grant", player, kit });
      setOut(r.result || "Granted " + kit + " to " + player + ".");
    } catch (e) { setOut(e.message); }
  });

  $("kitRefresh")?.addEventListener("click", () => refreshKits());
  $("kitReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/kits", { action: "reload" });
      setOut(r.result || "Reloaded.");
      refreshKits();
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { kits: refreshKits });
};
