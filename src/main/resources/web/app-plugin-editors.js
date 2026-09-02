window.YapDashRegisterPluginEditors = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let plugins = [];
  let selected = "";
  let fields = [];

  function setOut(text, err) {
    const el = $("edOut");
    if (!el) return;
    el.textContent = text || "";
    el.className = "easy-save-msg" + (text ? (err ? " err" : " ok") : "");
  }

  const STARTER = ["yap-perms", "yap-playerdata", "yap-chat", "yap-essentials", "yap-moderation"];

  function renderList() {
    const wrap = $("edPluginList");
    if (!wrap) return;
    const q = ($("edSearch")?.value || "").trim().toLowerCase();
    wrap.innerHTML = "";
    const shown = plugins.filter((p) => {
      if (!q) return true;
      return (p.title + " " + p.id + " " + (p.blurb || "")).toLowerCase().includes(q);
    }).slice().sort((a, b) => {
      const ai = STARTER.indexOf(a.id);
      const bi = STARTER.indexOf(b.id);
      if (ai !== bi) return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      return String(a.title).localeCompare(String(b.title));
    });
    shown.forEach((p) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "plugin-ed-item" + (p.id === selected ? " selected" : "");
      const starter = STARTER.includes(p.id) ? `<span class="easy-pill">Start here</span>` : "";
      btn.innerHTML = `<strong>${p.title}${starter}</strong><span class="muted-small">${p.blurb || "Settings for this plugin."}</span>`;
      btn.onclick = () => loadPlugin(p.id);
      wrap.appendChild(btn);
    });
  }

  const GROUP_BLURBS = {
    "What this plugin does": "Yes means players can use that feature. No hides it.",
    "Basics": "Everyday switches. Read the gray line if you are not sure.",
    "Money": "Starting cash and whether /bal and /pay work.",
    "Extra bag": "The extra backpack (/bag), not the vanilla E inventory.",
    "Login / register": "Only needed on a public offline-mode server.",
    "Word filter": "Stops or stars out words you list.",
    "Database": "Leave these unless the database login failed.",
    "Other servers": "For a multi-server network with YaP Link.",
  };

  function paintFields() {
    if (!window.YapFriendlyForms) return;
    const advanced = !!$("edShowAdvanced")?.checked;
    if (!selected) {
      $("edFields").innerHTML = `<div class="card easy-empty"><p>Pick a plugin on the left. Start with <strong>Ranks</strong>, <strong>Player data</strong>, or <strong>Chat</strong>.</p></div>`;
      return;
    }
    window.YapFriendlyForms.renderGroups($("edFields"), fields, {
      showAdvanced: advanced,
      showKeys: advanced,
      query: $("edFieldSearch")?.value || "",
      groupBlurbs: GROUP_BLURBS,
    });
  }

  async function loadPlugin(id) {
    selected = id;
    renderList();
    try {
      const r = await api("/api/plugin-config?plugin=" + encodeURIComponent(id));
      $("edTitle").textContent = r.title || id;
      $("edHint").textContent = (r.blurb ? r.blurb + " " : "")
        + (r.configPresent ? "Press Save and apply when you are done." : "No file yet — save creates it.");
      fields = r.fields || [];
      paintFields();
      setOut("");
    } catch (e) {
      setOut(e.message, true);
    }
  }

  async function refreshEditors() {
    try {
      const r = await api("/api/plugin-config");
      plugins = r.plugins || [];
      renderList();
      if (selected) loadPlugin(selected);
      else paintFields();
      setOut("");
    } catch (e) {
      setOut(e.message, true);
    }
  }

  $("edSearch")?.addEventListener("input", renderList);
  $("edFieldSearch")?.addEventListener("input", paintFields);
  $("edShowAdvanced")?.addEventListener("change", paintFields);

  $("edSave")?.addEventListener("click", async () => {
    if (!selected) { setOut("Pick a plugin on the left first.", true); return; }
    const body = window.YapFriendlyForms
      ? window.YapFriendlyForms.collect($("edFields"))
      : {};
    body.action = "save";
    body.plugin = selected;
    try {
      const r = await netPost("/api/plugin-config", body);
      setOut("Saved. " + (r.reload || "The plugin will use the new values."));
      await loadPlugin(selected);
      refreshEditors();
    } catch (e) { setOut(e.message, true); }
  });

  $("edReload")?.addEventListener("click", async () => {
    if (!selected) return;
    try {
      const r = await netPost("/api/plugin-config", { action: "reload", plugin: selected });
      setOut(r.result || "Reloaded from the file on disk.");
    } catch (e) { setOut(e.message, true); }
  });

  Object.assign(YapDash.tabLoads, { editors: refreshEditors });
};
