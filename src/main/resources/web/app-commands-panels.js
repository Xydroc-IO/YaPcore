(() => {
  const $ = (id) => document.getElementById(id);
  const api = (path) => YapDash.netGet(path);
  const netPost = (path, body) => YapDash.netPost(path, body);

  let state = { commands: [], selected: "", requireUsePerm: true };

  function out(msg) {
    const el = $("cmdOut");
    if (el) el.textContent = typeof msg === "string" ? msg : JSON.stringify(msg, null, 2);
  }

  function findCmd(name) {
    return (state.commands || []).find((c) => c.name === name);
  }

  function renderCards() {
    const host = $("cmdCards");
    if (!host) return;
    host.innerHTML = "";
    (state.commands || []).forEach((c) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "group-card" + (c.name === state.selected ? " active" : "");
      btn.textContent = "/" + c.name + (c.enabled ? "" : " (off)");
      btn.onclick = () => {
        state.selected = c.name;
        loadCmd(c);
        renderCards();
      };
      host.appendChild(btn);
    });
  }

  function loadCmd(c) {
    if (!c) return;
    $("cmdEditLabel").textContent = "/" + c.name;
    $("cmdId").value = c.name || "";
    $("cmdDesc").value = c.description || "";
    $("cmdPerm").value = c.permission || "";
    $("cmdCooldown").value = c.cooldownSeconds ?? 0;
    $("cmdEnabled").checked = c.enabled !== false;
    $("cmdHidePerm").checked = c.hideNoPermission !== false;
    $("cmdAliases").value = (c.aliases || []).join("\n");
    $("cmdMessages").value = (c.messages || []).join("\n");
    $("cmdPlayerCmds").value = (c.playerCommands || []).join("\n");
    $("cmdConsoleCmds").value = (c.consoleCommands || []).join("\n");
    $("cmdBroadcast").value = c.broadcast || "";
  }

  function clearEditor() {
    $("cmdEditLabel").textContent = "—";
    $("cmdId").value = "";
    $("cmdDesc").value = "";
    $("cmdPerm").value = "";
    $("cmdCooldown").value = 0;
    $("cmdEnabled").checked = true;
    $("cmdHidePerm").checked = true;
    $("cmdAliases").value = "";
    $("cmdMessages").value = "";
    $("cmdPlayerCmds").value = "";
    $("cmdConsoleCmds").value = "";
    $("cmdBroadcast").value = "";
  }

  async function refreshCommands() {
    try {
      const r = await api("/api/commands");
      state.commands = r.commands || [];
      state.requireUsePerm = !!r.requireUsePerm;
      $("cmdCount").textContent = String(state.commands.length);
      $("cmdJarOk").textContent = r.jarPresent ? "yap-commands.jar" : "missing";
      $("cmdFileOk").textContent = r.configPresent ? "commands.yml" : "missing";
      $("cmdRequireUse").checked = state.requireUsePerm;
      if (state.selected && !state.commands.some((c) => c.name === state.selected)) {
        state.selected = "";
        clearEditor();
      }
      if (!state.selected && state.commands.length) {
        state.selected = state.commands[0].name;
        loadCmd(state.commands[0]);
      } else if (state.selected) {
        loadCmd(findCmd(state.selected));
      }
      renderCards();
      out(r.hint || "ok");
    } catch (e) {
      out(String(e.message || e));
    }
  }

  $("cmdCreate")?.addEventListener("click", async () => {
    const name = ($("cmdNewId").value || "").trim().toLowerCase();
    if (!name) return out("Enter a command name");
    try {
      const r = await netPost("/api/commands", {
        action: "save-command",
        name,
        enabled: "true",
        description: "",
        messages: "&e/" + name + " works.",
        aliases: "",
        permission: "",
        cooldownSeconds: "0",
        hideNoPermission: "true",
        playerCommands: "",
        consoleCommands: "",
        broadcast: "",
      });
      if (r.error) return out(r.error);
      state.commands = r.commands || state.commands;
      state.selected = name;
      $("cmdNewId").value = "";
      await refreshCommands();
      out(r.reload || "saved");
    } catch (e) {
      out(String(e.message || e));
    }
  });

  $("cmdClone")?.addEventListener("click", async () => {
    const from = state.selected;
    const to = ($("cmdNewId").value || "").trim().toLowerCase();
    if (!from || !to) return out("Select a command and enter a new name");
    try {
      const r = await netPost("/api/commands", { action: "clone-command", from, to });
      if (r.error) return out(r.error);
      state.selected = to;
      $("cmdNewId").value = "";
      await refreshCommands();
      out(r.reload || "cloned");
    } catch (e) {
      out(String(e.message || e));
    }
  });

  $("cmdSave")?.addEventListener("click", async () => {
    const name = ($("cmdId").value || "").trim();
    if (!name) return out("Select or create a command first");
    try {
      const r = await netPost("/api/commands", {
        action: "save-command",
        name,
        enabled: String($("cmdEnabled").checked),
        description: $("cmdDesc").value || "",
        permission: $("cmdPerm").value || "",
        cooldownSeconds: String($("cmdCooldown").value || 0),
        hideNoPermission: String($("cmdHidePerm").checked),
        aliases: $("cmdAliases").value || "",
        messages: $("cmdMessages").value || "",
        playerCommands: $("cmdPlayerCmds").value || "",
        consoleCommands: $("cmdConsoleCmds").value || "",
        broadcast: $("cmdBroadcast").value || "",
      });
      if (r.error) return out(r.error);
      state.selected = name;
      await refreshCommands();
      out(r.reload || "saved");
    } catch (e) {
      out(String(e.message || e));
    }
  });

  $("cmdDelete")?.addEventListener("click", async () => {
    const name = state.selected;
    if (!name) return;
    if (!confirm("Delete /" + name + "?")) return;
    try {
      const r = await netPost("/api/commands", { action: "delete-command", name });
      if (r.error) return out(r.error);
      state.selected = "";
      clearEditor();
      await refreshCommands();
      out(r.reload || "deleted");
    } catch (e) {
      out(String(e.message || e));
    }
  });

  $("cmdRefresh")?.addEventListener("click", () => refreshCommands());
  $("cmdReload")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/commands", { action: "reload" });
      state.commands = r.commands || state.commands;
      renderCards();
      out(r.result || "reloaded");
    } catch (e) {
      out(String(e.message || e));
    }
  });
  $("cmdSaveRequire")?.addEventListener("click", async () => {
    try {
      const r = await netPost("/api/commands", {
        action: "set-require-use",
        requireUsePerm: String($("cmdRequireUse").checked),
      });
      if (r.error) return out(r.error);
      await refreshCommands();
      out(r.reload || "saved");
    } catch (e) {
      out(String(e.message || e));
    }
  });

  Object.assign(YapDash.tabLoads, { commands: refreshCommands });
})();
