(() => {
  const KEY = "yap_fleet_context";

  function toast(msg, kind) {
    const el = document.getElementById("yapToast");
    if (!el) {
      if (kind === "err") alert(msg);
      return;
    }
    el.textContent = msg;
    el.className = "yap-toast " + (kind || "ok");
    el.classList.remove("hidden");
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.add("hidden"), 3200);
  }

  function getContext() {
    try {
      const raw = sessionStorage.getItem(KEY);
      if (!raw) return { type: "network", instanceId: null };
      const parsed = JSON.parse(raw);
      if (parsed && parsed.type === "instance" && parsed.instanceId) {
        return { type: "instance", instanceId: String(parsed.instanceId) };
      }
    } catch (_) {}
    return { type: "network", instanceId: null };
  }

  function setContext(next) {
    const ctx = next && next.type === "instance" && next.instanceId
      ? { type: "instance", instanceId: String(next.instanceId) }
      : { type: "network", instanceId: null };
    sessionStorage.setItem(KEY, JSON.stringify(ctx));
    window.dispatchEvent(new CustomEvent("yap-fleet-context", { detail: ctx }));
    renderChips(window.YapFleetContext._lastSnapshot || null);
    return ctx;
  }

  function stateDot(state, running) {
    const s = String(state || "").toUpperCase();
    if (running === true || s === "RUNNING") return "on";
    if (s === "STARTING" || s === "STOPPING") return "warn";
    return "off";
  }

  function renderChips(snapshot) {
    const bar = document.getElementById("fleetContextBar");
    const chips = document.getElementById("fleetContextChips");
    if (!bar || !chips) return;
    const fleetOn = !!(snapshot && snapshot.fleetEnabled);
    bar.classList.toggle("hidden", !fleetOn);
    if (!fleetOn) return;

    const ctx = getContext();
    const instances = (snapshot && snapshot.instances) || [];
    chips.innerHTML = "";
    const net = document.createElement("button");
    net.type = "button";
    net.className = "fleet-chip" + (ctx.type === "network" ? " active" : "");
    net.textContent = "YaP Link";
    net.title = "Network edge proxy — all game servers";
    net.onclick = () => {
      setContext({ type: "network" });
      if (window.YapShell?.openLink) window.YapShell.openLink();
      else window.YapShell?.switchTab?.("link");
    };
    chips.appendChild(net);

    instances.forEach((i) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "fleet-chip" + (ctx.type === "instance" && ctx.instanceId === i.id ? " active" : "");
      btn.innerHTML = `<span class="chip-dot ${stateDot(i.state, i.running)}"></span>${esc(i.displayName || i.id)}`;
      btn.onclick = () => setContext({ type: "instance", instanceId: i.id });
      chips.appendChild(btn);
    });
  }

  function esc(v) {
    return String(v == null ? "" : v)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  async function refreshFromApi() {
    try {
      const api = window.YapDash && window.YapDash.api;
      if (!api) return;
      const snap = await api("/api/fleet");
      window.YapFleetContext._lastSnapshot = snap;
      renderChips(snap);
      return snap;
    } catch (_) {
      return null;
    }
  }

  document.getElementById("fleetContextAdd")?.addEventListener("click", () => {
    window.YapShell?.switchTab?.("fleet");
    const adv = document.getElementById("fleetAdvanced");
    if (adv) adv.open = true;
    document.getElementById("fleetNewId")?.focus();
  });

  window.YapFleetContext = {
    get: getContext,
    set: setContext,
    toast,
    refresh: refreshFromApi,
    renderChips,
    notify() {
      window.dispatchEvent(new CustomEvent("yap-fleet-context", { detail: getContext() }));
    },
    _lastSnapshot: null,
  };

  document.addEventListener("DOMContentLoaded", () => {
    refreshFromApi();
    setInterval(() => {
      if (document.getElementById("app")?.classList.contains("hidden")) return;
      refreshFromApi();
    }, 4000);
  });
})();
