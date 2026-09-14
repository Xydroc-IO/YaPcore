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
      options: Array.isArray(f.options) ? f.options : null,
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
    if (f.type === "enum" && f.options && f.options.length) {
      const optsList = f.options.map((o) => String(o));
      if (val && !optsList.includes(val)) {
        optsList.push(val);
      }
      const opts = optsList.map((s) => {
        const sel = s === val ? " selected" : "";
        const custom = f.options.map(String).includes(s) ? s : s + " (custom)";
        return `<option value="${escapeAttr(s)}"${sel}>${escapeAttr(custom)}</option>`;
      }).join("");
      return `<select name="${escapeAttr(name)}" data-key="${escapeAttr(name)}">${opts}</select>`;
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
    { group: "What players see", blurb: "Same name and welcome line for Java and Bedrock — one shared world.", fields: [
      { key: "server-name", title: "Server name", hint: "Shows in both Java and Bedrock server lists." },
      { key: "motd", title: "Welcome line", hint: "Short message under the name for both editions. Color codes like &a are ok." },
      { key: "resource-pack-file", title: "Which pack file", hint: "Usually yapcore-default.zip. Leave this unless you added another pack." },
    ]},
    { group: "Who can join", blurb: "Java and Bedrock play together on this same world. These limits apply to everyone.", fields: [
      { key: "max-players", title: "Player limit", hint: "Total slots for Java + Bedrock combined.", type: "enum", options: ["10", "20", "50", "100", "200", "500"] },
      { key: "online-mode", title: "Official accounts only", hint: "Yes = Microsoft/Mojang login for both editions. No = LAN / cracked / YaP Link offline.", type: "bool" },
      { key: "java-enabled", title: "Allow Java Edition", hint: "PC / Mac / Linux Minecraft. Keep on for crossplay.", type: "bool" },
      { key: "allow-bedrock-players", title: "Allow Bedrock Edition", hint: "Phones / consoles / Win10 Bedrock. UDP is on YaP Link (not chassis). Keep on with Java for both editions.", type: "bool" },
      { key: "crossplay-enabled", title: "Shared world (crossplay)", hint: "Keep on so Java and Bedrock share the same Folia world.", type: "bool" },
      { key: "bedrock-mode", title: "Bedrock join path", hint: "native = Link owns Bedrock UDP (recommended). Change only while stopped.", type: "enum", options: ["native", "forwarder", "geyser-backup"] },
      { key: "allow-localhost", title: "Allow this computer", hint: "Yes lets you join from the same machine that runs the server.", type: "bool" },
    ]},
    { group: "Bedrock look & feel (Java)", blurb: "Optional: Bedrock skins, emotes, and ported blocks on Java. Needs yap-presence / yap-blocks client mods.", fields: [
      { key: "parity.bedrock-feel", title: "Bedrock-feel for Java", hint: "On = JE clients must have yap-presence. Bedrock phones are unchanged.", type: "bool" },
      { key: "parity.bedrock-band", title: "Parity band", hint: "Which Bedrock extract set to use. Leave on the Folia-matched band.", type: "enum", options: ["band_26_50"] },
      { key: "resource-pack-enabled", title: "Send our textures", hint: "Yes = players are offered the server pack on join (Java zip + Bedrock mcpack).", type: "bool" },
    ]},
    { group: "How the world feels", blurb: "Higher numbers look nicer but need more RAM. Chassis defaults — per-fleet servers set RAM under Fleet → Setup.", fields: [
      { key: "view-distance", title: "How far they can see", hint: "Chunks. 8–10 is smooth. Higher needs more RAM.", type: "enum", options: ["6", "8", "10", "12", "16"] },
      { key: "ram-mb", title: "Chassis max memory (MB)", hint: "Used when a fleet server is set to inherit. Prefer per-server RAM in Fleet Setup.", type: "enum", options: ["2048", "4096", "8192", "16384", "32768"] },
      { key: "ram-min-mb", title: "Chassis memory reserve (MB)", hint: "Usually 512–2048.", type: "enum", options: ["512", "1024", "2048", "4096"] },
    ]},
    { group: "Advanced network", advanced: true, blurb: "Leave these unless a guide told you to change a port or hostname.", fields: [
      { key: "bind-host", title: "Listen address", hint: "0.0.0.0 = all network cards. Leave this if you are not sure." },
      { key: "port", title: "Game world port (Folia)", hint: "Backend world listen port (default 25566). Players join via Link, not this port.", type: "number" },
      { key: "bedrock-port", title: "Bedrock advertise port", hint: "Usually 19132 for phones. Link binds this in native mode.", type: "number" },
      { key: "bedrock-enabled", title: "Chassis Bedrock UDP (forwarder only)", hint: "Always off in native mode — Link owns UDP. Do not force this on for native.", type: "bool" },
      { key: "shared-listen-port", title: "Chassis same-port TCP+UDP", hint: "Forwarder only. Native Link already shares the public edge.", type: "bool" },
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
