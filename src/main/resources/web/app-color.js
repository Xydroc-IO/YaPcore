(() => {
  const COLORS = [
    { code: "0", hex: "#000000", name: "Black" },
    { code: "1", hex: "#0000AA", name: "Dark blue" },
    { code: "2", hex: "#00AA00", name: "Dark green" },
    { code: "3", hex: "#00AAAA", name: "Dark aqua" },
    { code: "4", hex: "#AA0000", name: "Dark red" },
    { code: "5", hex: "#AA00AA", name: "Purple" },
    { code: "6", hex: "#FFAA00", name: "Gold" },
    { code: "7", hex: "#AAAAAA", name: "Gray" },
    { code: "8", hex: "#555555", name: "Dark gray" },
    { code: "9", hex: "#5555FF", name: "Blue" },
    { code: "a", hex: "#55FF55", name: "Green" },
    { code: "b", hex: "#55FFFF", name: "Aqua" },
    { code: "c", hex: "#FF5555", name: "Red" },
    { code: "d", hex: "#FF55FF", name: "Pink" },
    { code: "e", hex: "#FFFF55", name: "Yellow" },
    { code: "f", hex: "#FFFFFF", name: "White" },
  ];
  const HEX_BY_CODE = Object.fromEntries(COLORS.map((c) => [c.code, c.hex]));
  const FORMATS = [
    { code: "l", label: "B", title: "Bold" },
    { code: "o", label: "I", title: "Italic" },
    { code: "n", label: "U", title: "Underline" },
    { code: "m", label: "S", title: "Strike" },
    { code: "k", label: "✦", title: "Magic text" },
    { code: "r", label: "Reset", title: "Reset formatting" },
  ];

  function esc(text) {
    return String(text || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
  }

  function parseHexToken(raw, i) {
    if (raw[i] !== "&") return null;
    const next = raw[i + 1];
    if (next === "#" && /^[0-9a-fA-F]{6}/.test(raw.slice(i + 2, i + 8))) {
      return { len: 8, hex: "#" + raw.slice(i + 2, i + 8).toLowerCase() };
    }
    if ((next === "x" || next === "X") && raw.length >= i + 14) {
      let hex = "#";
      for (let n = 0; n < 6; n++) {
        if (raw[i + 2 + n * 2] !== "&") return null;
        hex += raw[i + 3 + n * 2];
      }
      return { len: 14, hex: hex.toLowerCase() };
    }
    return null;
  }

  function toHtml(raw) {
    if (!raw) return "";
    let html = "";
    let color = "#AAAAAA";
    let bold = false;
    let italic = false;
    let underline = false;
    let strike = false;
    const push = (text) => {
      if (!text) return;
      const style = ["color:" + color];
      if (bold) style.push("font-weight:700");
      if (italic) style.push("font-style:italic");
      if (underline) style.push("text-decoration:underline");
      if (strike) style.push("text-decoration:line-through");
      html += `<span style="${style.join(";")}">${esc(text)}</span>`;
    };
    let buf = "";
    for (let i = 0; i < raw.length; i++) {
      if (raw[i] === "&" && i + 1 < raw.length) {
        const hexTok = parseHexToken(raw, i);
        if (hexTok) {
          push(buf);
          buf = "";
          color = hexTok.hex;
          i += hexTok.len - 1;
          continue;
        }
        const code = raw[i + 1].toLowerCase();
        push(buf);
        buf = "";
        i++;
        if (code === "r") {
          color = "#AAAAAA";
          bold = italic = underline = strike = false;
        } else if (code === "l") bold = true;
        else if (code === "o") italic = true;
        else if (code === "n") underline = true;
        else if (code === "m") strike = true;
        else if (HEX_BY_CODE[code]) color = HEX_BY_CODE[code];
        continue;
      }
      buf += raw[i];
    }
    push(buf);
    return html;
  }

  function lastColor(raw) {
    if (!raw) return { code: "", hex: "", name: "" };
    let code = "";
    let hex = "";
    for (let i = 0; i < raw.length; i++) {
      if (raw[i] !== "&" || i + 1 >= raw.length) continue;
      const hexTok = parseHexToken(raw, i);
      if (hexTok) {
        code = "";
        hex = hexTok.hex;
        i += hexTok.len - 1;
        continue;
      }
      const ch = raw[i + 1].toLowerCase();
      i++;
      if (HEX_BY_CODE[ch]) {
        code = ch;
        hex = HEX_BY_CODE[ch];
      }
    }
    const named = COLORS.find((c) => c.code === code);
    return { code, hex, name: named ? named.name : (hex ? "Custom" : "") };
  }

  function nearestVanilla(hex) {
    const h = (hex || "").replace("#", "").toLowerCase();
    return COLORS.find((c) => c.hex.slice(1).toLowerCase() === h) || null;
  }

  function hexToMc(hex) {
    const clean = String(hex || "").replace("#", "").toLowerCase();
    if (!/^[0-9a-f]{6}$/.test(clean)) return "";
    const named = nearestVanilla("#" + clean);
    return named ? "&" + named.code : "&#" + clean;
  }

  function fireInput(el) {
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function insertAtCursor(el, token) {
    el.focus();
    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    el.value = el.value.slice(0, start) + token + el.value.slice(end);
    const pos = start + token.length;
    if (el.setSelectionRange) el.setSelectionRange(pos, pos);
    fireInput(el);
  }

  function applyToken(el, token, mode) {
    if (mode === "replace") {
      const formats = (el.value.match(/&[lomnkr]/gi) || []).join("");
      el.value = token + formats;
      fireInput(el);
      return;
    }
    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    const selected = start !== end;
    const hasColor = /&(?:#[0-9a-fA-F]{6}|x(?:&[0-9a-fA-F]){6}|[0-9a-fk-or])/i.test(el.value);
    if (!selected && !hasColor && token.startsWith("&") && token !== "&r") {
      el.value = token + el.value;
      if (el.setSelectionRange) el.setSelectionRange(el.value.length, el.value.length);
      fireInput(el);
      return;
    }
    insertAtCursor(el, token);
  }

  function renderToolbar(input, mode, showFormats) {
    const bar = document.createElement("div");
    bar.className = "mc-color-bar";
    const swatches = document.createElement("div");
    swatches.className = "mc-swatches";
    COLORS.forEach((c) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "mc-swatch";
      btn.title = c.name;
      btn.dataset.code = c.code;
      btn.style.background = c.hex;
      btn.setAttribute("aria-label", c.name);
      btn.onclick = () => applyToken(input, "&" + c.code, mode);
      swatches.appendChild(btn);
    });
    const tools = document.createElement("div");
    tools.className = "mc-color-tools";
    const hexLabel = document.createElement("label");
    hexLabel.className = "mc-hex-label";
    hexLabel.title = "Custom color";
    hexLabel.innerHTML = `<span>Custom</span><input type="color" class="mc-hex" value="#55ff55"/>`;
    const hexInput = hexLabel.querySelector("input");
    hexInput.oninput = () => applyToken(input, hexToMc(hexInput.value), mode);
    tools.appendChild(hexLabel);
    if (showFormats) {
      FORMATS.forEach((f) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "mc-fmt" + (f.code === "r" ? " is-reset" : "");
        btn.title = f.title;
        btn.textContent = f.label;
        if (f.code === "l") btn.style.fontWeight = "700";
        if (f.code === "o") btn.style.fontStyle = "italic";
        if (f.code === "n") btn.style.textDecoration = "underline";
        if (f.code === "m") btn.style.textDecoration = "line-through";
        btn.onclick = () => {
          if (mode === "replace" && f.code !== "r") {
            const base = lastColor(input.value);
            const colorTok = base.code ? "&" + base.code : (base.hex ? "&#" + base.hex.slice(1) : "&f");
            input.value = colorTok + "&" + f.code;
            fireInput(input);
            return;
          }
          applyToken(input, "&" + f.code, mode === "replace" && f.code === "r" ? "replace" : "insert");
        };
        tools.appendChild(btn);
      });
    }
    const selected = document.createElement("span");
    selected.className = "mc-selected muted-small";
    tools.appendChild(selected);
    bar.appendChild(swatches);
    bar.appendChild(tools);

    const sync = () => {
      const cur = lastColor(input.value);
      bar.querySelectorAll(".mc-swatch").forEach((b) => {
        b.classList.toggle("on", !!cur.code && b.dataset.code === cur.code);
      });
      if (cur.hex) hexInput.value = cur.hex;
      selected.textContent = cur.name ? cur.name + (cur.code ? "  &" + cur.code : "") : "";
    };
    input.addEventListener("input", sync);
    input.addEventListener("change", sync);
    sync();
    return bar;
  }

  function ensurePreview(host, input, sample) {
    let preview = host.querySelector(":scope > .mc-live-preview");
    if (!preview) {
      preview = document.createElement("p");
      preview.className = "mc-live-preview";
      host.appendChild(preview);
    }
    const paint = () => {
      const html = toHtml(input.value || "");
      preview.innerHTML = html
        ? `<span class="muted-small">Preview</span> <span class="mc-preview">${html}</span>`
        : "";
    };
    input.addEventListener("input", paint);
    paint();
    if (sample) {
      /* sample used by rank editor via updatePrefixPreview */
    }
    return preview;
  }

  function mount(input, opts) {
    if (!input || input.dataset.mcMounted === "1") return input.closest(".mc-color-wrap");
    const mode = (opts && opts.mode) || input.dataset.mcColor || "insert";
    const showFormats = opts && opts.formats != null
      ? !!opts.formats
      : input.dataset.mcFormats !== "0";
    const showPreview = opts && opts.preview != null
      ? !!opts.preview
      : input.dataset.mcPreview === "1";
    input.dataset.mcMounted = "1";
    const wrap = document.createElement("div");
    wrap.className = "mc-color-wrap" + (mode === "replace" ? " is-replace" : "");
    input.parentNode.insertBefore(wrap, input);
    const bar = renderToolbar(input, mode, showFormats);
    if (mode === "replace") {
      wrap.appendChild(bar);
      wrap.appendChild(input);
      input.classList.add("mc-code-input");
    } else {
      wrap.appendChild(input);
      wrap.appendChild(bar);
    }
    if (showPreview) ensurePreview(wrap, input);
    return wrap;
  }

  function attachAll(root) {
    (root || document).querySelectorAll("[data-mc-color]").forEach((el) => mount(el));
  }

  window.YapMcColor = { COLORS, toHtml, lastColor, hexToMc, mount, attachAll, esc };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => attachAll(document));
  } else {
    attachAll(document);
  }
})();
