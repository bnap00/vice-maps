/*
 * Vice Maps — content script.
 *
 * Google Maps themes its UI through ~400 CSS custom properties on :root whose
 * names are hashed (e.g. --t25f96c26aad1c4f5) and change between deploys. So
 * instead of hard-coding names, we read every colour token at runtime and remap
 * it by *value* (lightness + hue) onto the Vice palette, writing the result as
 * inline !important overrides on <html>. Turning the theme off just removes them.
 */
(() => {
  const root = document.documentElement;
  const DEFAULTS = { enabled: true, mapStyle: "day", glass: true };
  let settings = { ...DEFAULTS };

  // Apply the class immediately (before paint) so the CSS kicks in; storage may revise it.
  root.classList.add("vice-on", "vice-map-day", "vice-glass");

  const P = {
    surface: "#78487A",
    surfaceHi: "#855381",
    container: "#93608F",
    containerHi: "#A46D9D",
    outline: "#A9789F",
    outlineStrong: "#C07EAA",
    textMuted: "#E9CFE0",
    text: "#FFF3F5",
    cream: "#FFF9CB",
    pink: "#FFC2D0",
    pinkHover: "#EF8FB6",
    peach: "#FFD4A8",
    ink: "#0F0C1A",
  };

  // ---------- colour helpers ----------
  function parseColor(v) {
    v = v.trim().toLowerCase();
    let m = v.match(/^#([0-9a-f]{3,8})$/);
    if (m) {
      let h = m[1];
      if (h.length === 3 || h.length === 4) h = [...h].map((c) => c + c).join("");
      const n = parseInt(h.slice(0, 6), 16);
      const a = h.length === 8 ? parseInt(h.slice(6), 16) / 255 : 1;
      return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255, a };
    }
    m = v.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+%?))?\s*\)$/);
    if (m) {
      let a = m[4] === undefined ? 1 : parseFloat(m[4]);
      if (m[4] && m[4].endsWith("%")) a /= 100;
      return { r: +m[1], g: +m[2], b: +m[3], a };
    }
    return null;
  }

  function toHsl({ r, g, b }) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    const l = (max + min) / 2;
    let h = 0, s = 0;
    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
      else if (max === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60;
    }
    return { h, s, l };
  }

  function hslHex(h, s, l) {
    const k = (n) => (n + h / 30) % 12;
    const a = s * Math.min(l, 1 - l);
    const f = (n) => Math.round(255 * (l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))));
    return "#" + [f(0), f(8), f(4)].map((x) => x.toString(16).padStart(2, "0")).join("");
  }

  function withAlpha(hex, a) {
    if (a >= 1) return hex;
    const c = parseColor(hex);
    return `rgba(${c.r}, ${c.g}, ${c.b}, ${+a.toFixed(3)})`;
  }

  // Map one Google colour onto the Vice palette. `dark` = Maps is in its dark scheme.
  function remap(c, dark) {
    let { h, s, l } = toHsl(c);
    if (dark) l = 1 - l; // treat dark-scheme tokens as their light-scheme equivalents
    const brand = s > 0.25 && h >= 160 && h <= 230; // Google's teal/blue primary family
    const neutral = s <= 0.25 || l > 0.96 || l < 0.06;
    let out;

    if (neutral) {
      if (l >= 0.955) out = P.surface;
      else if (l >= 0.91) out = P.surfaceHi;
      else if (l >= 0.84) out = P.outline;
      else if (l >= 0.62) out = P.outlineStrong;
      else if (l >= 0.33) out = P.textMuted;
      else out = P.text;
    } else if (brand) {
      if (l < 0.2) out = P.cream;          // on-container text
      else if (l < 0.5) out = P.pink;      // primary (buttons, links, icons)
      else if (l < 0.72) out = P.pinkHover;
      else if (l < 0.88) out = P.containerHi;
      else out = P.container;              // tonal containers / chips
    } else {
      // Semantic colours (red/green/amber/purple): keep the hue, but swap
      // pale containers for plum so they don't glow on the dark panel.
      if (l > 0.86) out = P.container;
      else if (h >= 25 && h < 70) out = P.peach; // amber/orange → peach
      else if (l < 0.6) out = hslHex(h, Math.min(1, s + 0.1), 0.78); // red/green text: lift for contrast on plum
      else return null;
    }
    return withAlpha(out, c.a);
  }

  // ---------- token override ----------
  const originals = new Map(); // name -> original value string
  let applied = false;

  function isDarkScheme() {
    // Google may follow prefers-color-scheme; detect from the main panel/surface.
    const probe = document.querySelector('[role="main"]') || document.body;
    let el = probe;
    while (el && el !== document.documentElement) {
      const c = parseColor(getComputedStyle(el).backgroundColor);
      if (c && c.a > 0.5) return toHsl(c).l < 0.4;
      el = el.parentElement;
    }
    return false;
  }

  function scanTokens() {
    if (!settings.enabled) return;
    const cs = getComputedStyle(root);
    const fresh = [];
    for (let i = 0; i < cs.length; i++) {
      const name = cs[i];
      if (!name.startsWith("--") || name.startsWith("--vice")) continue;
      if (originals.has(name)) continue;
      const v = cs.getPropertyValue(name);
      if (!v || !parseColor(v)) continue;
      originals.set(name, v.trim());
      fresh.push(name);
    }
    if (fresh.length || !applied) applyTokens(fresh.length && applied ? fresh : [...originals.keys()]);
  }

  let darkScheme = null; // decided once, before our own overrides change the panel colour
  function applyTokens(names) {
    if (darkScheme === null) darkScheme = isDarkScheme();
    const dark = darkScheme;
    for (const name of names) {
      // Leave state-layer (hover/press overlays) alone: they're semi-transparent tints.
      if (name.includes("state-layer")) continue;
      const c = parseColor(originals.get(name));
      if (!c || c.a === 0) continue;
      const out = remap(c, dark);
      if (out) root.style.setProperty(name, out, "important");
    }
    applied = true;
  }

  function clearTokens() {
    for (const name of originals.keys()) root.style.removeProperty(name);
    originals.clear();
    applied = false;
    darkScheme = null;
  }

  // ---------- map colour grade (SVG filters referenced from vice.css) ----------
  // Matrices were fitted so Google's palette lands on the target look:
  //   day:   water → deep teal, land → slate grey, parks → palm green
  //   night: water → indigo, land → dusk purple, roads → neon pink
  const GRADES = {
    day: "1.712 -1.091 0.016 0 0.015  0.458 0.495 -0.289 0 -0.001  0.786 -1.474 1.330 0 0.037  0 0 0 1 0",
    night: "1.876 -1.988 0.762 0 -0.026  0.867 -0.087 -0.313 0 -0.015  1.022 -1.357 0.919 0 0.017  0 0 0 1 0",
  };
  function injectFilters() {
    if (document.getElementById("vice-filters") || !document.body) return;
    const ns = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(ns, "svg");
    svg.id = "vice-filters";
    svg.setAttribute("aria-hidden", "true");
    svg.style.cssText = "position:absolute;width:0;height:0;overflow:hidden";
    for (const [name, values] of Object.entries(GRADES)) {
      const f = document.createElementNS(ns, "filter");
      f.id = `vice-grade-${name}`;
      f.setAttribute("color-interpolation-filters", "sRGB");
      const m = document.createElementNS(ns, "feColorMatrix");
      m.setAttribute("type", "matrix");
      m.setAttribute("values", values);
      f.appendChild(m);
      svg.appendChild(f);
    }
    document.body.appendChild(svg);
  }

  // Satellite imagery and Street View photos shouldn't be colour-graded.
  let lastHref = "";
  function checkUrl() {
    if (location.href === lastHref) return;
    lastHref = location.href;
    const raw = /,3a,/.test(location.href) || /!1e3(?!\d)/.test(location.href) || /!5m1!1e3/.test(location.href);
    root.classList.toggle("vice-raw", raw);
  }
  setInterval(checkUrl, 600);

  // ---------- DOM tagging ----------
  // Google's class names are obfuscated, so we find landmarks by role / jsaction /
  // geometry and tag them with our own stable classes for vice.css to target.
  const painted = (el) => {
    const c = parseColor(getComputedStyle(el).backgroundColor);
    return c && c.a > 0.3;
  };
  const radius = (el) => parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0;
  const tag = (el, cls) => el && !el.classList.contains(cls) && el.classList.add(cls);
  function ancestor(el, test, max = 12) {
    for (let i = 0; el && el !== document.body && i < max; i++, el = el.parentElement) if (test(el)) return el;
    return null;
  }

  function tagDom() {
    if (!settings.enabled || !document.body) return;
    injectFilters();
    const vh = innerHeight;

    // Map canvas = the largest canvas on the page; its role=application wrapper hosts the vignette.
    let best = null, area = 0;
    for (const c of document.querySelectorAll("canvas")) {
      const r = c.getBoundingClientRect();
      if (r.width * r.height > area) { area = r.width * r.height; best = c; }
    }
    if (best && area > 200000) {
      document.querySelectorAll(".vice-map-canvas").forEach((c) => c !== best && c.classList.remove("vice-map-canvas"));
      tag(best, "vice-map-canvas");
      const host = best.closest('[role="application"]');
      if (host) {
        tag(host, "vice-map-host");
        if (getComputedStyle(host).position === "static") host.style.position = "relative";
      }
    }

    // Side panel: nearest rounded, painted ancestor of the main region.
    for (const main of document.querySelectorAll('[role="main"]')) {
      const panel = ancestor(main, (el) => radius(el) >= 8 && painted(el));
      if (!panel) continue;
      tag(panel, "vice-panel");
      // Full-width opaque layers inside the panel would hide its gradient.
      const pw = panel.getBoundingClientRect().width;
      for (const el of panel.querySelectorAll("div")) {
        if (el.classList.contains("vice-flat") || el.classList.contains("vice-search")) continue;
        const r = el.getBoundingClientRect();
        if (r.width < pw * 0.9 || r.height < 40) continue;
        const c = parseColor(getComputedStyle(el).backgroundColor);
        if (c && c.a > 0.9 && toHsl(c).s < 0.45 && Math.abs(toHsl(c).h - 298) < 25) tag(el, "vice-flat");
      }
    }

    // Search box: the pill around the omnibox input.
    const input = document.querySelector('#searchboxinput, input[name="q"], form input[role="combobox"]');
    if (input) tag(ancestor(input, (el) => radius(el) >= 16 && painted(el), 6), "vice-search");

    // Category chips floating over the map.
    for (const label of document.querySelectorAll(".fontTitleSmall")) {
      if (label.closest(".vice-panel")) continue;
      const chip = ancestor(label, (el) => radius(el) >= 12 && painted(el), 4);
      if (chip && chip.getBoundingClientRect().height <= 44) tag(chip, "vice-chip");
    }

    // Map controls.
    for (const sel of ['[jsaction*="zoom.onZoomIn"]', '[jsaction*="mylocation"]', '[jsaction*="runway.pegman"]']) {
      const b = document.querySelector(sel);
      if (b) tag(ancestor(b, (el) => painted(el) && el.getBoundingClientRect().width < 80, 5), "vice-ctrl");
    }

    // Left navigation rail backdrop.
    const railBtn = document.querySelector('[jsaction*="navigationrail"]');
    if (railBtn) {
      const bg = ancestor(railBtn, (el) => painted(el) && el.getBoundingClientRect().height > vh * 0.6, 10);
      tag(bg, "vice-rail-bg");
    }
  }

  // ---------- settings ----------
  function applySettings() {
    root.classList.toggle("vice-on", settings.enabled);
    root.classList.toggle("vice-glass", settings.enabled && settings.glass);
    for (const s of ["day", "night", "none"]) {
      root.classList.toggle(`vice-map-${s}`, settings.enabled && settings.mapStyle === s);
    }
    if (settings.enabled) scanTokens();
    else clearTokens();
  }

  try {
    chrome.storage.sync.get(DEFAULTS, (s) => {
      settings = { ...DEFAULTS, ...s };
      applySettings();
    });
    chrome.storage.onChanged.addListener((changes) => {
      for (const k in changes) settings[k] = changes[k].newValue;
      applySettings();
    });
  } catch (_) {
    /* storage unavailable: keep defaults */
  }

  // Google loads stylesheets lazily and swaps panels constantly: rescan tokens when
  // new CSS arrives, and re-tag the DOM (throttled) whenever the tree changes.
  let tokenTimer = 0, tagTimer = 0, lastTag = 0;
  const scheduleTokens = () => {
    clearTimeout(tokenTimer);
    tokenTimer = setTimeout(scanTokens, 120);
  };
  const scheduleTag = () => {
    if (tagTimer) return;
    const wait = Math.max(0, 400 - (Date.now() - lastTag));
    tagTimer = setTimeout(() => {
      tagTimer = 0;
      lastTag = Date.now();
      tagDom();
    }, wait);
  };
  new MutationObserver((muts) => {
    let css = false;
    for (const m of muts)
      for (const n of m.addedNodes)
        if (n.nodeName === "STYLE" || n.nodeName === "LINK") css = true;
    if (css) scheduleTokens();
    scheduleTag();
  }).observe(root, { childList: true, subtree: true });

  document.addEventListener("DOMContentLoaded", () => {
    scheduleTokens();
    scheduleTag();
  });
  window.addEventListener("load", () => {
    scheduleTokens();
    setTimeout(() => {
      scanTokens();
      tagDom();
    }, 1500);
  });
  window.addEventListener("resize", scheduleTag);
})();
