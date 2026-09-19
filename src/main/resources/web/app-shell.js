(() => {
  /** Three operator modes — every panel stays registered; only the sidebar list changes. */
  const MODES = [
    {
      id: "operate",
      label: "Operate",
      hint: "Day-to-day control",
      groups: [
        { group: "Now", items: [
          { tab: "status", icon: "◉", label: "Dashboard" },
          { tab: "fleet", icon: "▦", label: "Fleet" },
          { tab: "players", icon: "👤", label: "Players" },
          { tab: "console", icon: "▸", label: "Console" },
          { tab: "connect", icon: "🔗", label: "Connect" },
        ]},
      ],
    },
    {
      id: "configure",
      label: "Configure",
      hint: "Network & setup",
      groups: [
        { group: "Network", items: [
          { tab: "setup", icon: "✓", label: "Setup" },
          { tab: "link", icon: "⇄", label: "YaP Link" },
          { tab: "admin", icon: "⚙", label: "Network setup" },
          { tab: "settings", icon: "☰", label: "Server setup" },
        ]},
        { group: "People", items: [
          { tab: "access", icon: "🔐", label: "Access & ranks" },
          { tab: "ranks", icon: "★", label: "Rank pack" },
        ]},
        { group: "Content", items: [
          { tab: "plugins", icon: "🧩", label: "Plugins" },
          { tab: "editors", icon: "✎", label: "Plugin settings" },
          { tab: "modules", icon: "📦", label: "Modules" },
          { tab: "packs", icon: "🎨", label: "Packs" },
          { tab: "world", icon: "🌍", label: "World" },
          { tab: "regions", icon: "▣", label: "Regions" },
          { tab: "npcs", icon: "💬", label: "NPCs" },
          { tab: "holo", icon: "✦", label: "Holograms" },
          { tab: "shops", icon: "🏪", label: "Shops" },
        ]},
      ],
    },
    {
      id: "gameplay",
      label: "Gameplay",
      hint: "Plugins & systems",
      groups: [
        { group: "Core", items: [
          { tab: "essentials", icon: "🏠", label: "Essentials" },
          { tab: "chat", icon: "💬", label: "Chat" },
          { tab: "tab", icon: "📋", label: "Tab list" },
          { tab: "data", icon: "💾", label: "Player data" },
          { tab: "kits", icon: "🎒", label: "Kits" },
          { tab: "commands", icon: "/", label: "Custom commands" },
        ]},
        { group: "World & safety", items: [
          { tab: "protect", icon: "🔒", label: "Protect" },
          { tab: "guard", icon: "🛡", label: "Guard" },
          { tab: "map", icon: "🗺", label: "Map" },
          { tab: "pregen", icon: "⬡", label: "Pregen" },
          { tab: "discord", icon: "📢", label: "Discord" },
          { tab: "tebex", icon: "🛒", label: "Tebex store" },
        ]},
        { group: "Opt-in", items: [
          { tab: "skills", icon: "⚔", label: "Skills" },
          { tab: "factions", icon: "⚑", label: "Factions" },
          { tab: "disasters", icon: "🌩", label: "Disasters" },
          { tab: "stacker", icon: "☰", label: "Stacker" },
        ]},
      ],
    },
  ];

  const TITLES = {};
  const TAB_MODE = {};
  MODES.forEach((mode) => {
    mode.groups.forEach((g) => {
      g.items.forEach((i) => {
        TITLES[i.tab] = i.label;
        TAB_MODE[i.tab] = mode.id;
      });
    });
  });

  let activeMode = localStorage.getItem("yap_dash_mode") || "operate";
  if (!MODES.some((m) => m.id === activeMode)) activeMode = "operate";

  function currentMode() {
    return MODES.find((m) => m.id === activeMode) || MODES[0];
  }

  function setMode(id, opts = {}) {
    if (!MODES.some((m) => m.id === id)) return;
    activeMode = id;
    localStorage.setItem("yap_dash_mode", id);
    document.querySelectorAll(".mode-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.mode === id);
    });
    const hint = document.getElementById("modeHint");
    if (hint) hint.textContent = currentMode().hint || "";
    buildNavGroups();
    const search = document.getElementById("navSearch");
    if (search) filterNav(search.value);
    if (opts.ensureTab) {
      const tab = opts.ensureTab;
      if (TAB_MODE[tab] === id) {
        document.querySelectorAll(".nav-item").forEach((b) => {
          b.classList.toggle("active", b.dataset.tab === tab);
        });
      }
    }
  }

  function buildModeSwitcher() {
    const host = document.getElementById("modeSwitcher");
    if (!host) return;
    host.innerHTML = "";
    MODES.forEach((mode) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "mode-btn" + (mode.id === activeMode ? " active" : "");
      btn.dataset.mode = mode.id;
      btn.textContent = mode.label;
      btn.title = mode.hint || mode.label;
      btn.onclick = () => {
        const activePanel = document.querySelector(".panel.active");
        const activeTab = activePanel?.id?.replace(/^tab-/, "") || "";
        setMode(mode.id);
        if (TAB_MODE[activeTab] !== mode.id) {
          const first = mode.groups[0]?.items?.[0]?.tab;
          if (first) switchTab(first);
        }
      };
      host.appendChild(btn);
    });
    const hint = document.getElementById("modeHint");
    if (hint) hint.textContent = currentMode().hint || "";
  }

  function buildNavGroups() {
    const nav = document.getElementById("sidebarNav");
    if (!nav) return;
    const prevActive = document.querySelector(".nav-item.active")?.dataset?.tab || "status";
    nav.innerHTML = "";
    currentMode().groups.forEach((group) => {
      const wrap = document.createElement("div");
      wrap.className = "nav-group";
      wrap.innerHTML = `<div class="nav-label">${group.group}</div>`;
      group.items.forEach((item) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "nav-item" + (item.tab === prevActive ? " active" : "");
        btn.dataset.tab = item.tab;
        btn.innerHTML = `<span class="nav-icon">${item.icon}</span><span>${item.label}</span>`;
        btn.onclick = () => switchTab(item.tab);
        wrap.appendChild(btn);
      });
      nav.appendChild(wrap);
    });
  }

  function buildSidebar() {
    buildModeSwitcher();
    buildNavGroups();
  }

  function switchTab(tab) {
    const name = String(tab || "").trim();
    if (!name) return false;
    const modeForTab = TAB_MODE[name];
    if (modeForTab && modeForTab !== activeMode) {
      setMode(modeForTab, { ensureTab: name });
    }
    document.querySelectorAll(".nav-item").forEach((b) => {
      b.classList.toggle("active", b.dataset.tab === name);
    });
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    const panel = document.getElementById("tab-" + name);
    if (!panel) {
      console.warn("YaP shell: missing panel tab-" + name);
      return false;
    }
    panel.classList.add("active");
    panel.scrollTop = 0;
    const title = document.getElementById("topbarTitle");
    if (title) title.textContent = TITLES[name] || name;
    const crumb = document.getElementById("topbarMode");
    if (crumb) {
      const mode = MODES.find((m) => m.id === (modeForTab || activeMode));
      crumb.textContent = mode ? mode.label : "";
    }
    const loads = window.YapDashTabLoads || window.YapDash?.tabLoads;
    const load = loads && loads[name];
    if (load) Promise.resolve(load()).catch((e) => console.error("tab load " + name, e));
    document.getElementById("sidebar")?.classList.remove("open");
    return true;
  }

  function openLink() {
    const ok = switchTab("link");
    if (!ok) {
      const nav = document.querySelector('.nav-item[data-tab="link"]');
      if (nav) nav.click();
    }
    return ok;
  }

  /** Search across all modes; temporarily expands matching items into the nav. */
  function filterNav(query) {
    const q = (query || "").trim().toLowerCase();
    const nav = document.getElementById("sidebarNav");
    if (!nav) return;

    if (!q) {
      buildNavGroups();
      document.querySelectorAll(".nav-item").forEach((b) => {
        const active = document.querySelector(".panel.active");
        const tab = active?.id?.replace(/^tab-/, "");
        b.classList.toggle("active", b.dataset.tab === tab);
      });
      return;
    }

    nav.innerHTML = "";
    const wrap = document.createElement("div");
    wrap.className = "nav-group";
    wrap.innerHTML = `<div class="nav-label">Search results</div>`;
    let hits = 0;
    MODES.forEach((mode) => {
      mode.groups.forEach((g) => {
        g.items.forEach((item) => {
          if (!item.label.toLowerCase().includes(q) && !item.tab.includes(q)) return;
          hits++;
          const btn = document.createElement("button");
          btn.type = "button";
          btn.className = "nav-item";
          btn.dataset.tab = item.tab;
          btn.innerHTML = `<span class="nav-icon">${item.icon}</span>`
            + `<span>${item.label}</span>`
            + `<span class="nav-mode-tag">${mode.label}</span>`;
          btn.onclick = () => switchTab(item.tab);
          wrap.appendChild(btn);
        });
      });
    });
    if (hits === 0) {
      wrap.innerHTML += `<p class="nav-empty">No pages match “${q}”</p>`;
    }
    nav.appendChild(wrap);
  }

  function bootShell() {
    buildSidebar();
    const search = document.getElementById("navSearch");
    if (search && !search.dataset.bound) {
      search.dataset.bound = "1";
      search.placeholder = "Search all pages…";
      search.addEventListener("input", () => filterNav(search.value));
      search.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        const first = document.querySelector(".nav-item:not(.nav-hidden)");
        if (first) first.click();
      });
    }
    document.querySelectorAll("[data-goto-tab]").forEach((el) => {
      if (el.dataset.boundGoto) return;
      el.dataset.boundGoto = "1";
      el.addEventListener("click", () => switchTab(el.dataset.gotoTab));
    });
  }

  window.YapShell = { switchTab, openLink, buildSidebar, filterNav, setMode, MODES, TITLES };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bootShell);
  } else {
    bootShell();
  }
})();
