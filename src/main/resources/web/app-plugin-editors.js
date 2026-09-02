window.YapDashRegisterPluginEditors = function (YapDash) {
  const { $, api, netPost } = YapDash;
  let plugins = [];
  let selected = "";
  let fields = [];

  function setOut(text) {
    const el = $("edOut");
    if (el) el.textContent = text || "";
  }

  function renderList() {
    const wrap = $("edPluginList");
    if (!wrap) return;
    const q = ($("edSearch")?.value || "").trim().toLowerCase();
    wrap.innerHTML = "";
    plugins.filter((p) => {
      if (!q) return true;
      return (p.title + " " + p.id + " " + p.dataDir).toLowerCase().includes(q);
    }).forEach((p) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "plugin-ed-item" + (p.id === selected ? " selected" : "");
      const status = p.configPresent ? (p.fields + " keys") : "no config yet";
      btn.innerHTML = `<strong>${p.title}</strong><span class="muted-small">${p.installed ? "installed" : "not installed"} · ${status}</span>`;
      btn.onclick = () => loadPlugin(p.id);
      wrap.appendChild(btn);
    });
  }

  function renderFields() {
    const wrap = $("edFields");
    if (!wrap) return;
    wrap.innerHTML = "";
    (fields || []).forEach((f) => {
      const lab = document.createElement("label");
      lab.textContent = f.key;
      if (f.readonly || f.type === "complex") {
        const span = document.createElement("div");
        span.className = "muted-small";
        span.textContent = String(f.value ?? "");
        lab.appendChild(span);
      } else if (f.type === "bool") {
        const sel = document.createElement("select");
        sel.dataset.key = f.key;
        sel.innerHTML = `<option value="true">true</option><option value="false">false</option>`;
        sel.value = String(!!f.value);
        lab.appendChild(sel);
      } else {
        const inp = document.createElement("input");
        inp.dataset.key = f.key;
        inp.value = f.value == null ? "" : String(f.value);
        if (f.secret) inp.type = "password";
        if (f.type === "number") inp.type = "number";
        lab.appendChild(inp);
      }
      wrap.appendChild(lab);
    });
  }

  async function loadPlugin(id) {
    selected = id;
    renderList();
    try {
      const r = await api("/api/plugin-config?plugin=" + encodeURIComponent(id));
      $("edTitle").textContent = r.title || id;
      $("edHint").textContent = (r.configPresent ? "Editing " : "No file yet — save will create ")
        + "plugins/" + r.dataDir + "/" + r.file
        + (r.reload ? " · reload: " + r.reload : "");
      fields = r.fields || [];
      renderFields();
      setOut("");
    } catch (e) {
      setOut(e.message);
    }
  }

  async function refreshEditors() {
    try {
      const r = await api("/api/plugin-config");
      plugins = r.plugins || [];
      renderList();
      if (selected) loadPlugin(selected);
      setOut("");
    } catch (e) {
      setOut(e.message);
    }
  }

  $("edSearch")?.addEventListener("input", renderList);

  $("edSave")?.addEventListener("click", async () => {
    if (!selected) { alert("Select a plugin first."); return; }
    const body = { action: "save", plugin: selected };
    $("edFields")?.querySelectorAll("input,select").forEach((el) => {
      if (el.dataset.key) body[el.dataset.key] = el.value;
    });
    try {
      const r = await netPost("/api/plugin-config", body);
      setOut("Saved " + selected + (r.reload ? ". " + r.reload : "."));
      await loadPlugin(selected);
      refreshEditors();
    } catch (e) { setOut(e.message); }
  });

  $("edReload")?.addEventListener("click", async () => {
    if (!selected) return;
    try {
      const r = await netPost("/api/plugin-config", { action: "reload", plugin: selected });
      setOut(r.result || "Reloaded.");
    } catch (e) { setOut(e.message); }
  });

  Object.assign(YapDash.tabLoads, { editors: refreshEditors });
};
