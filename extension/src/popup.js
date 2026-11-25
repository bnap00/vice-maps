const DEFAULTS = { enabled: true, mapStyle: "day", glass: true };
const $ = (s) => document.querySelector(s);

function render(s) {
  $("#enabled").checked = s.enabled;
  $("#glass").checked = s.glass;
  document.body.classList.toggle("off", !s.enabled);
  for (const b of document.querySelectorAll(".seg button")) {
    b.setAttribute("aria-checked", String(b.dataset.style === s.mapStyle));
  }
}

chrome.storage.sync.get(DEFAULTS, (s) => {
  let state = { ...DEFAULTS, ...s };
  render(state);
  const save = (patch) => {
    state = { ...state, ...patch };
    render(state);
    chrome.storage.sync.set(patch);
  };
  $("#enabled").addEventListener("change", (e) => save({ enabled: e.target.checked }));
  $("#glass").addEventListener("change", (e) => save({ glass: e.target.checked }));
  for (const b of document.querySelectorAll(".seg button")) {
    b.addEventListener("click", () => save({ mapStyle: b.dataset.style }));
  }
});
