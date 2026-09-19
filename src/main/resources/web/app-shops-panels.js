window.YapDashRegisterShopsPanels = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let state = {
    shops: [],
    presets: [],
    selected: "",
    offers: [],
    catalogId: null,
    displayName: "",
    chestShops: [],
    instances: [],
  };

  const MATERIALS = [
    "BREAD", "COOKED_BEEF", "COOKED_CHICKEN", "COOKED_PORKCHOP", "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE",
    "GOLDEN_CARROT", "APPLE", "COOKIE", "PUMPKIN_PIE", "CAKE", "MILK_BUCKET",
    "WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD",
    "BOW", "CROSSBOW", "ARROW", "SHIELD", "TRIDENT",
    "WOODEN_PICKAXE", "IRON_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE",
    "WOODEN_AXE", "IRON_AXE", "DIAMOND_AXE", "FISHING_ROD",
    "LEATHER_HELMET", "IRON_HELMET", "DIAMOND_HELMET", "NETHERITE_HELMET",
    "LEATHER_CHESTPLATE", "IRON_CHESTPLATE", "DIAMOND_CHESTPLATE", "NETHERITE_CHESTPLATE",
    "LEATHER_LEGGINGS", "IRON_LEGGINGS", "DIAMOND_LEGGINGS", "NETHERITE_LEGGINGS",
    "LEATHER_BOOTS", "IRON_BOOTS", "DIAMOND_BOOTS", "NETHERITE_BOOTS", "ELYTRA",
    "COBBLESTONE", "STONE", "OAK_LOG", "OAK_PLANKS", "GLASS", "TORCH", "OBSIDIAN",
    "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "EMERALD", "COAL", "REDSTONE", "LAPIS_LAZULI",
    "REDSTONE_TORCH", "REPEATER", "COMPARATOR", "PISTON", "HOPPER", "OBSERVER",
    "CRAFTING_TABLE", "FURNACE", "CHEST", "ENDER_CHEST", "ANVIL", "ENCHANTING_TABLE",
    "BOOK", "ENCHANTED_BOOK", "EXPERIENCE_BOTTLE",
  ];

  function setOut(text) {
    const el = $("shopOut");
    if (el) el.textContent = text || "";
  }

  function setChestOut(text) {
    const el = $("chestOut");
    if (el) el.textContent = text || "";
  }

  function switchShopPane(pane) {
    document.querySelectorAll("#shopSubnav [data-shop-pane]").forEach((b) => {
      b.classList.toggle("active", b.dataset.shopPane === pane);
    });
    const npc = $("shopPaneNpc");
    const chest = $("shopPaneChest");
    if (npc) npc.hidden = pane !== "npc";
    if (chest) chest.hidden = pane !== "chest";
  }

  function fillInstances(ids) {
    const sel = $("chestInstance");
    if (!sel) return;
    const cur = sel.value;
    sel.innerHTML = "";
    const list = (ids && ids.length) ? ids : ["lobby"];
    list.forEach((id) => {
      const o = document.createElement("option");
      o.value = id;
      o.textContent = id;
      sel.appendChild(o);
    });
    if (cur && list.includes(cur)) sel.value = cur;
  }

  function chestKey(s) {
    return [s.serverId || "", s.world || "", s.x, s.y, s.z].join(":");
  }

  function fillChestForm(s) {
    if (!s) return;
    if (s.serverId) {
      const sel = $("chestInstance");
      if (sel) sel.value = s.serverId;
    }
    $("chestWorld") && ($("chestWorld").value = s.world || "world");
    $("chestX") && ($("chestX").value = s.x ?? "");
    $("chestY") && ($("chestY").value = s.y ?? "");
    $("chestZ") && ($("chestZ").value = s.z ?? "");
    $("chestMaterial") && ($("chestMaterial").value = s.material || "");
    $("chestAmount") && ($("chestAmount").value = s.amount ?? 1);
    $("chestPrice") && ($("chestPrice").value = s.price ?? "");
    $("chestOwner") && ($("chestOwner").value = s.ownerName || "");
  }

  function chestPayload(extra) {
    const body = Object.assign({
      instance: $("chestInstance")?.value || "",
      world: ($("chestWorld")?.value || "world").trim(),
      x: ($("chestX")?.value || "").trim(),
      y: ($("chestY")?.value || "").trim(),
      z: ($("chestZ")?.value || "").trim(),
      material: ($("chestMaterial")?.value || "").trim().toUpperCase(),
      amount: $("chestAmount")?.value || "1",
      price: ($("chestPrice")?.value || "").trim(),
      owner: ($("chestOwner")?.value || "").trim(),
    }, extra || {});
    return body;
  }

  function renderChestTable() {
    const tbody = $("chestBody");
    if (!tbody) return;
    tbody.innerHTML = "";
    (state.chestShops || []).forEach((s) => {
      const tr = document.createElement("tr");
      tr.style.cursor = "pointer";
      tr.innerHTML =
        `<td>${s.serverId || ""}</td>`
        + `<td>${s.world || ""}</td>`
        + `<td>${s.x}, ${s.y}, ${s.z}</td>`
        + `<td>${s.material || ""}</td>`
        + `<td>${s.amount ?? ""}</td>`
        + `<td>${s.price ?? ""}</td>`
        + `<td>${s.ownerName || ""}</td>`;
      tr.addEventListener("click", () => fillChestForm(s));
      tbody.appendChild(tr);
    });
    const empty = $("chestEmpty");
    if (empty) empty.classList.toggle("hidden", (state.chestShops || []).length > 0);
    $("chestCount") && ($("chestCount").textContent = String((state.chestShops || []).length));
  }

  async function refreshChest(fromGet) {
    if (fromGet) {
      renderChestTable();
      return;
    }
    try {
      const r = await netPost("/api/shops", { action: "chest-list", instance: $("chestInstance")?.value || "" });
      state.chestShops = r.chestShops || [];
      renderChestTable();
    } catch (e) {
      setChestOut(e.message);
    }
  }

  function fillMaterials() {
    const list = $("shopMaterials");
    if (!list || list.childElementCount) return;
    MATERIALS.forEach((m) => {
      const o = document.createElement("option");
      o.value = m;
      list.appendChild(o);
    });
  }

  function fillPresets(presets) {
    const sel = $("shopPreset");
    if (!sel) return;
    const cur = sel.value;
    sel.innerHTML = "";
    (presets || []).forEach((p) => {
      const o = document.createElement("option");
      o.value = p;
      o.textContent = p;
      sel.appendChild(o);
    });
    if (cur) sel.value = cur;
  }

  function itemKey(o) {
    return (o.material || "?") + "\0" + (o.meta || "");
  }

  /** Merge BUY + SELL offers into one row per material (+ meta). */
  function mergeItems(offers) {
    const map = new Map();
    (offers || []).forEach((o) => {
      const key = itemKey(o);
      let row = map.get(key);
      if (!row) {
        row = {
          key,
          material: o.material || "?",
          meta: o.meta || "",
          amount: o.amount ?? 1,
          stock: o.stock ?? -1,
          buyId: null,
          sellId: null,
          buyPrice: "",
          sellPrice: "",
        };
        map.set(key, row);
      }
      if (o.mode === "BUY") {
        row.buyId = o.id;
        row.buyPrice = Number(o.price).toFixed(2);
        row.amount = o.amount ?? row.amount;
        row.stock = o.stock ?? row.stock;
      } else if (o.mode === "SELL") {
        row.sellId = o.id;
        row.sellPrice = Number(o.price).toFixed(2);
        if (!row.buyId) {
          row.amount = o.amount ?? row.amount;
          row.stock = o.stock ?? row.stock;
        }
      }
    });
    return [...map.values()].sort((a, b) =>
      String(a.material).localeCompare(String(b.material)));
  }

  function renderShopCards() {
    const wrap = $("shopCards");
    if (!wrap) return;
    wrap.innerHTML = "";
    (state.shops || []).forEach((s) => {
      const id = s.npcId || s.id;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "group-card" + (id === state.selected ? " selected" : "");
      btn.innerHTML = `<strong>${s.displayName || id}</strong>`
        + `<span class="muted">${id}</span>`
        + `<span class="muted-small">catalog #${s.catalogId ?? "—"}</span>`;
      btn.onclick = () => selectShop(id);
      wrap.appendChild(btn);
    });
    const empty = $("shopEmpty");
    if (empty) {
      empty.classList.toggle("hidden", (state.shops || []).length > 0);
    }
  }

  function renderOffers() {
    const tbody = $("shopOfferBody");
    if (!tbody) return;
    tbody.innerHTML = "";
    const items = mergeItems(state.offers);
    $("shopOfferCount") && ($("shopOfferCount").textContent = String(items.length));
    $("shopEditLabel") && ($("shopEditLabel").textContent = state.selected
      ? `${state.displayName || state.selected} · #${state.catalogId ?? "—"}`
      : "—");
    items.forEach((it) => {
      const tr = document.createElement("tr");
      const meta = it.meta ? " ✦" : "";
      const enchanted = !!it.meta;
      tr.dataset.key = it.key;
      tr.dataset.material = it.material;
      tr.dataset.meta = it.meta || "";
      tr.dataset.buyId = it.buyId != null ? String(it.buyId) : "";
      tr.dataset.sellId = it.sellId != null ? String(it.sellId) : "";
      tr.innerHTML = `<td><strong>${it.material}</strong>${meta}</td>`
        + `<td><input type="number" min="1" step="1" class="shop-amt" value="${it.amount ?? 1}" style="width:4.5rem"/></td>`
        + `<td><input type="number" min="0" step="0.01" class="shop-buy" value="${it.buyPrice}" placeholder="—" style="width:6rem" title="Player pays (blank = no buy)"/></td>`
        + `<td><input type="number" min="0" step="0.01" class="shop-sell" value="${it.sellPrice}" placeholder="—" style="width:6rem" ${enchanted ? "disabled title=\"Enchanted — buy only\"" : "title=\"Player receives (blank = no sell)\""}/></td>`
        + `<td><input type="number" step="1" class="shop-stock" value="${it.stock ?? -1}" title="-1 = unlimited" style="width:4.5rem"/></td>`
        + `<td class="toolbar" style="margin:0;gap:0.25rem">`
        + `<button type="button" class="ghost shop-save">Save</button>`
        + `<button type="button" class="danger ghost shop-del">Del</button>`
        + `</td>`;
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll(".shop-save").forEach((btn) => {
      btn.onclick = async () => {
        const row = btn.closest("tr");
        await saveItemRow(row);
      };
    });
    tbody.querySelectorAll(".shop-del").forEach((btn) => {
      btn.onclick = async () => {
        const row = btn.closest("tr");
        const mat = row?.dataset.material || "?";
        if (!confirm("Remove " + mat + " from this shop?")) return;
        try {
          if (row.dataset.meta) {
            await deleteOfferIds(row.dataset.buyId, row.dataset.sellId);
          } else {
            const r = await netPost("/api/shops", {
              action: "setitem",
              id: state.selected,
              material: mat,
              amount: "1",
              buyPrice: "-",
              sellPrice: "-",
              stock: "-1",
            });
            setOut(r.result || "Removed " + mat);
          }
          await loadOffers(state.selected);
          await refreshShops(false);
        } catch (e) { setOut(e.message); }
      };
    });
  }

  async function deleteOfferIds(buyId, sellId) {
    const msgs = [];
    for (const oid of [buyId, sellId]) {
      if (!oid) continue;
      const r = await netPost("/api/shops", {
        action: "deloffer",
        id: state.selected,
        offerId: oid,
      });
      msgs.push(r.result || ("del #" + oid));
    }
    setOut(msgs.join(" · ") || "Deleted.");
  }

  async function saveItemRow(row) {
    if (!row || !state.selected) return;
    const material = row.dataset.material;
    const amount = row.querySelector(".shop-amt")?.value || "1";
    const buyRaw = (row.querySelector(".shop-buy")?.value || "").trim();
    const sellRaw = (row.querySelector(".shop-sell")?.value || "").trim();
    const stock = row.querySelector(".shop-stock")?.value || "-1";
    const meta = row.dataset.meta || "";
    if (!buyRaw && !sellRaw) {
      setOut("Set at least a buy or sell price (or Del to remove).");
      return;
    }
    try {
      if (meta) {
        // Enchanted / special: update existing offer ids only
        const msgs = [];
        if (row.dataset.buyId) {
          if (!buyRaw) {
            const r = await netPost("/api/shops", {
              action: "deloffer", id: state.selected, offerId: row.dataset.buyId,
            });
            msgs.push(r.result || "buy off");
          } else {
            const r = await netPost("/api/shops", {
              action: "setoffer",
              offerId: row.dataset.buyId,
              price: buyRaw,
              amount,
              stock,
            });
            msgs.push(r.result || "buy ok");
          }
        }
        setOut(msgs.join(" · ") || "Saved.");
      } else {
        const r = await netPost("/api/shops", {
          action: "setitem",
          id: state.selected,
          material,
          amount,
          buyPrice: buyRaw || "-",
          sellPrice: sellRaw || "-",
          stock,
        });
        setOut(r.result || "Updated " + material);
      }
      await loadOffers(state.selected);
    } catch (e) { setOut(e.message); }
  }

  async function selectShop(id) {
    state.selected = id;
    renderShopCards();
    await loadOffers(id);
  }

  async function loadOffers(id) {
    if (!id) {
      state.offers = [];
      state.catalogId = null;
      renderOffers();
      return;
    }
    try {
      const r = await netPost("/api/shops", { action: "list", id });
      const shop = r.shop || {};
      state.offers = shop.offers || [];
      state.catalogId = shop.catalogId ?? null;
      state.displayName = shop.displayName || id;
      if (r.shops) state.shops = r.shops;
      renderOffers();
      const n = mergeItems(state.offers).length;
      setOut(`Loaded ${n} item${n === 1 ? "" : "s"} for ${id}`);
    } catch (e) {
      setOut(e.message);
    }
  }

  async function refreshShops(reloadSelected) {
    fillMaterials();
    try {
      const data = await api("/api/shops");
      state.shops = data.shops || [];
      state.presets = data.presets || [];
      state.chestShops = data.chestShops || [];
      state.instances = data.instances || [];
      fillPresets(state.presets);
      fillInstances(state.instances);
      renderChestTable();
      $("shopCount") && ($("shopCount").textContent = String(data.shopCount ?? state.shops.length));
      $("chestCount") && ($("chestCount").textContent = String(data.chestCount ?? state.chestShops.length));
      $("shopNpcCount") && ($("shopNpcCount").textContent = String(data.npcCount ?? "—"));
      $("shopPresetCount") && ($("shopPresetCount").textContent = String(state.presets.length));
      renderShopCards();
      if (reloadSelected !== false) {
        if (state.selected && state.shops.some((s) => (s.npcId || s.id) === state.selected)) {
          await loadOffers(state.selected);
        } else if (state.shops.length) {
          await selectShop(state.shops[0].npcId || state.shops[0].id);
        } else {
          state.selected = "";
          state.offers = [];
          renderOffers();
        }
      }
      setOut("");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("shopRefresh")?.addEventListener("click", () => refreshShops(true));

  document.querySelectorAll("#shopSubnav [data-shop-pane]").forEach((btn) => {
    btn.addEventListener("click", () => switchShopPane(btn.dataset.shopPane));
  });

  $("chestRefresh")?.addEventListener("click", () => refreshChest(false));
  $("chestCreate")?.addEventListener("click", async () => {
    const body = chestPayload({ action: "chest-create" });
    if (!body.world || !body.x || !body.y || !body.z || !body.material || !body.price) {
      setChestOut("World, XYZ, material, and price are required. Place the chest first.");
      return;
    }
    try {
      const r = await netPost("/api/shops", body);
      state.chestShops = r.chestShops || state.chestShops;
      renderChestTable();
      setChestOut(r.result || "Chest shop saved.");
    } catch (e) { setChestOut(e.message); }
  });
  $("chestRemove")?.addEventListener("click", async () => {
    const body = chestPayload({ action: "chest-remove" });
    if (!body.world || !body.x || !body.y || !body.z) {
      setChestOut("Select a row or fill XYZ.");
      return;
    }
    if (!confirm("Remove chest shop at " + body.world + " " + body.x + "," + body.y + "," + body.z + "?")) return;
    try {
      const r = await netPost("/api/shops", body);
      state.chestShops = r.chestShops || state.chestShops;
      renderChestTable();
      setChestOut(r.result || "Removed.");
    } catch (e) { setChestOut(e.message); }
  });
  $("chestInfo")?.addEventListener("click", async () => {
    const body = chestPayload({ action: "chest-info" });
    if (!body.world || !body.x || !body.y || !body.z) {
      setChestOut("Select a row or fill XYZ.");
      return;
    }
    try {
      const r = await netPost("/api/shops", body);
      const c = r.chest || {};
      const stock = c.stock != null ? c.stock : "?";
      setChestOut(r.result || ("Stock " + stock + " · " + (c.material || "") + " @ $" + (c.price || "")));
      if (c.material) fillChestForm(c);
    } catch (e) { setChestOut(e.message); }
  });

  $("shopApplyPreset")?.addEventListener("click", async () => {
    if (!state.selected) {
      setOut("Select a shop NPC first (enable a shop on the NPCs tab).");
      return;
    }
    const preset = $("shopPreset")?.value;
    if (!preset) return;
    if (!confirm(`Apply preset "${preset}" to ${state.selected}? This replaces existing offers.`)) return;
    try {
      const r = await netPost("/api/shops", {
        action: "apply",
        id: state.selected,
        preset,
        replace: "true",
      });
      setOut(r.result || "Preset applied.");
      await loadOffers(state.selected);
      await refreshShops(false);
    } catch (e) { setOut(e.message); }
  });

  $("shopClearOffers")?.addEventListener("click", async () => {
    if (!state.selected) return;
    if (!confirm("Clear all offers on " + state.selected + "? Catalog stays linked.")) return;
    try {
      const r = await netPost("/api/shops", { action: "clearoffers", id: state.selected });
      setOut(r.result || "Cleared.");
      await loadOffers(state.selected);
    } catch (e) { setOut(e.message); }
  });

  $("shopClearLink")?.addEventListener("click", async () => {
    if (!state.selected) return;
    if (!confirm("Unlink and delete the shop catalog on " + state.selected + "?")) return;
    try {
      const r = await netPost("/api/shops", { action: "clear", id: state.selected });
      setOut(r.result || "Shop cleared.");
      state.selected = "";
      await refreshShops(true);
    } catch (e) { setOut(e.message); }
  });

  $("shopAddOffer")?.addEventListener("click", async () => {
    if (!state.selected) {
      setOut("Select a shop first.");
      return;
    }
    const material = ($("shopAddMaterial")?.value || "").trim().toUpperCase();
    const amount = $("shopAddAmount")?.value || "1";
    const buy = ($("shopAddBuy")?.value || "").trim();
    const sell = ($("shopAddSell")?.value || "").trim();
    const stock = $("shopAddStock")?.value || "-1";
    if (!material) {
      setOut("Material is required.");
      return;
    }
    if (!buy && !sell) {
      setOut("Enter a buy price, sell price, or both.");
      return;
    }
    try {
      const r = await netPost("/api/shops", {
        action: "setitem",
        id: state.selected,
        material,
        amount,
        buyPrice: buy || "-",
        sellPrice: sell || "-",
        stock,
      });
      setOut(r.result || "Saved " + material);
      await loadOffers(state.selected);
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { shops: () => refreshShops(true) });
};
