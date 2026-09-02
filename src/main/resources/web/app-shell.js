(() => {
  const NAV = [
    { group: "Overview", items: [
      { tab: "status", icon: "◉", label: "Dashboard" },
      { tab: "admin", icon: "⚙", label: "Network setup" },
      { tab: "connect", icon: "🔗", label: "Connect" },
    ]},
    { group: "Server", items: [
      { tab: "console", icon: "▸", label: "Console" },
      { tab: "settings", icon: "☰", label: "Server setup" },
      { tab: "link", icon: "⇄", label: "YaP Link" },
    ]},
    { group: "People", items: [
      { tab: "players", icon: "👤", label: "Players" },
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
    ]},
    { group: "Gameplay", items: [
      { tab: "essentials", icon: "🏠", label: "Essentials" },
      { tab: "vehicles", icon: "🚗", label: "Vehicles" },
      { tab: "pregen", icon: "⬡", label: "Pregen" },
      { tab: "data", icon: "💾", label: "Player data" },
      { tab: "kits", icon: "🎒", label: "Kits" },
      { tab: "tebex", icon: "🛒", label: "Tebex store" },
      { tab: "chat", icon: "💬", label: "Chat" },
      { tab: "tab", icon: "📋", label: "Tab list" },
      { tab: "mmo", icon: "⚔", label: "MMO" },
      { tab: "map", icon: "🗺", label: "Map" },
      { tab: "guard", icon: "🛡", label: "Guard" },
      { tab: "protect", icon: "🔒", label: "Protect" },
      { tab: "discord", icon: "📢", label: "Discord" },
    ]},
  ];

  const TITLES = {};
  NAV.forEach((g) => g.items.forEach((i) => { TITLES[i.tab] = i.label; }));

  function buildSidebar() {
    const nav = document.getElementById("sidebarNav");
    if (!nav) return;
    nav.innerHTML = "";
    NAV.forEach((group) => {
      const wrap = document.createElement("div");
      wrap.className = "nav-group";
      wrap.innerHTML = `<div class="nav-label">${group.group}</div>`;
      group.items.forEach((item) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "nav-item" + (item.tab === "status" ? " active" : "");
        btn.dataset.tab = item.tab;
        btn.innerHTML = `<span class="nav-icon">${item.icon}</span><span>${item.label}</span>`;
        btn.onclick = () => switchTab(item.tab);
        wrap.appendChild(btn);
      });
      nav.appendChild(wrap);
    });
  }

  function switchTab(tab) {
    document.querySelectorAll(".nav-item").forEach((b) => {
      b.classList.toggle("active", b.dataset.tab === tab);
    });
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    const panel = document.getElementById("tab-" + tab);
    if (panel) panel.classList.add("active");
    const title = document.getElementById("topbarTitle");
    if (title) title.textContent = TITLES[tab] || tab;
    const loads = window.YapDashTabLoads || window.YapDash?.tabLoads;
    const load = loads && loads[tab];
    if (load) Promise.resolve(load()).catch((e) => console.error("tab load " + tab, e));
    document.getElementById("sidebar")?.classList.remove("open");
  }

  function filterNav(query) {
    const q = (query || "").trim().toLowerCase();
    document.querySelectorAll(".nav-group").forEach((group) => {
      let visible = 0;
      group.querySelectorAll(".nav-item").forEach((item) => {
        const label = (item.textContent || "").toLowerCase();
        const hide = !!q && !label.includes(q);
        item.classList.toggle("nav-hidden", hide);
        if (!hide) visible++;
      });
      group.classList.toggle("nav-hidden", !!q && visible === 0);
    });
  }

  window.YapShell = { switchTab, buildSidebar, filterNav };
  document.addEventListener("DOMContentLoaded", () => {
    buildSidebar();
    const search = document.getElementById("navSearch");
    if (search) {
      search.addEventListener("input", () => filterNav(search.value));
      search.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        const first = document.querySelector(".nav-item:not(.nav-hidden)");
        if (first) first.click();
      });
    }
  });
})();
