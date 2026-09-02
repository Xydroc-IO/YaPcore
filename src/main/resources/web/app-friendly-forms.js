(() => {
  function humanize(key) {
    const leaf = String(key || "").split(".").pop() || "";
    return leaf.replace(/[-_]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
  }

  function fieldMeta(f) {
    return {
      key: f.key,
      title: f.title || humanize(f.key),
      hint: f.hint || "",
      group: f.group || "Basics",
      type: f.type || "text",
      value: f.value,
      secret: !!f.secret,
      readonly: !!f.readonly,
      advanced: !!f.advanced,
      placeholder: f.placeholder || "",
    };
  }

  function switchHtml(name, on) {
    const checked = on ? "true" : "false";
    return `<div class="easy-switch-wrap">
      <button type="button" class="easy-switch" role="switch" aria-checked="${on}" data-name="${name}"></button>
      <span class="easy-switch-label">${on ? "Yes" : "No"}</span>
      <input type="hidden" name="${name}" data-key="${name}" value="${checked}"/>
    </div>`;
  }

  function controlHtml(f) {
    const name = f.key;
    const val = f.value == null ? "" : String(f.value);
    if (f.readonly || f.type === "complex") {
      return `<div class="easy-readonly">${val || "—"}</div>`;
    }
    if (f.type === "bool") {
      return switchHtml(name, val === "true" || val === "yes" || f.value === true);
    }
    const type = f.secret ? "password" : (f.type === "number" ? "number" : "text");
    const ph = f.placeholder ? ` placeholder="${escapeAttr(f.placeholder)}"` : "";
    return `<input name="${escapeAttr(name)}" data-key="${escapeAttr(name)}" type="${type}" value="${escapeAttr(val)}"${ph}/>`;
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function fieldHtml(f, showKeys) {
    const m = fieldMeta(f);
    return `<label class="easy-field${m.advanced ? " easy-advanced" : ""}" data-key="${escapeAttr(m.key)}">
      <span class="easy-label">${escapeAttr(m.title)}</span>
      ${m.hint ? `<span class="easy-hint">${escapeAttr(m.hint)}</span>` : ""}
      ${controlHtml(m)}
      ${showKeys ? `<span class="easy-key">${escapeAttr(m.key)}</span>` : ""}
    </label>`;
  }

  function bindSwitches(root) {
    root.querySelectorAll(".easy-switch").forEach((btn) => {
      btn.onclick = () => {
        const on = btn.getAttribute("aria-checked") !== "true";
        btn.setAttribute("aria-checked", on ? "true" : "false");
        const wrap = btn.closest(".easy-switch-wrap");
        const hidden = wrap?.querySelector("input[type=hidden]");
        const lab = wrap?.querySelector(".easy-switch-label");
        if (hidden) hidden.value = on ? "true" : "false";
        if (lab) lab.textContent = on ? "Yes" : "No";
      };
    });
  }

  function renderGroups(host, fields, opts) {
    const showAdvanced = !!(opts && opts.showAdvanced);
    const showKeys = !!(opts && opts.showKeys);
    const query = ((opts && opts.query) || "").trim().toLowerCase();
    const blurbs = (opts && opts.groupBlurbs) || {};
    const groups = new Map();
    (fields || []).forEach((raw) => {
      const f = fieldMeta(raw);
      if (!showAdvanced && f.advanced) return;
      const blob = (f.title + " " + f.key + " " + f.hint + " " + f.group).toLowerCase();
      if (query && !blob.includes(query)) return;
      if (!groups.has(f.group)) groups.set(f.group, []);
      groups.get(f.group).push(raw);
    });
    host.innerHTML = "";
    if (groups.size === 0) {
      host.innerHTML = `<p class="muted hint">No settings match. Try another search or show advanced.</p>`;
      return;
    }
    groups.forEach((list, name) => {
      const card = document.createElement("div");
      card.className = "card easy-card";
      const blurb = blurbs[name] ? `<p class="easy-hint">${escapeAttr(blurbs[name])}</p>` : "";
      card.innerHTML = `<h3>${escapeAttr(name)}</h3>${blurb}<div class="easy-grid">${list.map((f) => fieldHtml(f, showKeys)).join("")}</div>`;
      host.appendChild(card);
    });
    bindSwitches(host);
  }

  const SETTINGS = [
    { group: "What players see", blurb: "This is what shows up in the Minecraft server list.", fields: [
      { key: "server-name", title: "Server name", hint: "Shows in the Minecraft server list." },
      { key: "motd", title: "Welcome line", hint: "Short message under the name. Color codes like &a are ok." },
      { key: "resource-pack-enabled", title: "Send our textures", hint: "Yes = players are offered the server pack on join.", type: "bool" },
      { key: "resource-pack-file", title: "Which pack file", hint: "Usually yapcore-default.zip. Leave this unless you added another pack." },
    ]},
    { group: "Who can join", blurb: "How many people, and which Minecraft editions.", fields: [
      { key: "max-players", title: "Player limit", hint: "How many people can be online at once.", type: "number" },
      { key: "online-mode", title: "Official accounts only", hint: "Yes = Microsoft/Mojang login. No = LAN / cracked / YaP Link offline.", type: "bool" },
      { key: "java-enabled", title: "Java Edition", hint: "PC / Mac / Linux Minecraft.", type: "bool" },
      { key: "bedrock-enabled", title: "Bedrock Edition", hint: "Phones, consoles, and Windows Bedrock.", type: "bool" },
      { key: "crossplay-enabled", title: "Java + Bedrock together", hint: "Keep on so both editions share the same world.", type: "bool" },
      { key: "allow-localhost", title: "Allow this computer", hint: "Yes lets you join from the same machine that runs the server.", type: "bool" },
    ]},
    { group: "How the world feels", blurb: "Higher numbers look nicer but need more RAM.", fields: [
      { key: "view-distance", title: "How far they can see", hint: "Chunks. 8–10 is smooth. Higher needs more RAM.", type: "number" },
      { key: "ram-mb", title: "Max memory (MB)", hint: "4096 is a good start for a small public box.", type: "number" },
      { key: "ram-min-mb", title: "Memory to reserve (MB)", hint: "Usually half of max, or 1024.", type: "number" },
    ]},
    { group: "Advanced network", advanced: true, blurb: "Leave these unless a guide told you to change a port or hostname.", fields: [
      { key: "bind-host", title: "Listen address", hint: "0.0.0.0 = all network cards. Leave this if you are not sure." },
      { key: "port", title: "Java port", hint: "Default 25565. Players type this after the IP.", type: "number" },
      { key: "bedrock-port", title: "Bedrock port", hint: "Default 19132 unless you share one port.", type: "number" },
      { key: "shared-listen-port", title: "Same port for Java and Bedrock", hint: "Yes = one public port. No = separate Java + Bedrock ports.", type: "bool" },
      { key: "public-host", title: "Public hostname", hint: "What you give players, e.g. play.example.com" },
      { key: "server-domain", title: "Domain", hint: "Used for links and the pack URL." },
      { key: "public-port", title: "Public Java port", hint: "Port people use from the internet if it differs from the listen port.", type: "number" },
      { key: "internet-exposed", title: "This box is on the internet", hint: "Yes if friends join from outside your house.", type: "bool" },
      { key: "yap-ranks-auto-apply", title: "Install starter ranks once", hint: "Creates default / VIP / staff / admin the first time.", type: "bool" },
    ]},
  ];

  function renderSettings(host, cfg, showAdvanced) {
    const fields = [];
    const groupBlurbs = {};
    SETTINGS.forEach((section) => {
      if (section.advanced && !showAdvanced) return;
      if (section.blurb) groupBlurbs[section.group] = section.blurb;
      section.fields.forEach((f) => {
        fields.push({
          ...f,
          group: section.group,
          advanced: !!section.advanced,
          value: cfg[f.key],
        });
      });
    });
    renderGroups(host, fields, { showAdvanced: true, showKeys: !!showAdvanced, groupBlurbs });
  }

  function collect(root) {
    const body = {};
    root.querySelectorAll("input[name], select[name]").forEach((el) => {
      if (el.name) body[el.name] = el.value;
    });
    return body;
  }

  window.YapFriendlyForms = { renderGroups, renderSettings, collect, bindSwitches, humanize };
})();
