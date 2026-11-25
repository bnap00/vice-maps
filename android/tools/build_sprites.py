#!/usr/bin/env python3
"""Build the Vice map icon sprite: MapGenie-style category badges.

Every POI icon in OpenFreeMap's sprite (tools/vendor/ofm*) is replaced by a rounded-square
badge — thick ink outline, muted category colour, bold ink pictogram — drawn from the
public-domain Maki icons (tools/vendor/maki, CC0). Road shields, one-way arrows and dots are
kept from the OFM sheet unchanged.

Rendering uses headless Chromium via Playwright (dev-time only):
    PLAYWRIGHT_NODE_PATH=/path/to/node_modules CHROME=/path/to/chrome python3 tools/build_sprites.py
Output: app/src/main/assets/sprites/vice{,@2x}.{png,json}
"""
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
VENDOR = ROOT / "tools" / "vendor"
OUT = ROOT / "app" / "src" / "main" / "assets" / "sprites"

INK = "#0f0c1a"

# Category fills: muted pastels, like MapGenie's marker families.
GROUPS = {
    "money": ("#8fd19a", ["bank", "commercial", "industry", "warehouse", "suitcase"]),
    "food": ("#f2c1ae", ["restaurant", "cafe", "fast_food", "bar", "beer", "ice_cream", "bakery",
                          "sushi", "alcohol_shop", "butcher", "drinking_water"]),
    "shop": ("#f6bcd3", ["shop", "grocery", "clothing_store", "gift", "florist", "furniture",
                          "laundry", "hairdresser", "telephone"]),
    "transport": ("#f2d06b", ["car", "fuel", "parking", "parking_garage", "bus", "railway",
                               "railway_light", "railway_metro", "airport", "airfield", "ferry",
                               "harbor", "bicycle", "bicycle_rental", "aerialway", "heliport",
                               "entrance", "rocket"]),
    "health": ("#f4a7a7", ["hospital", "pharmacy", "doctors", "dentist", "veterinary", "police",
                            "fire_station", "danger", "roadblock", "prison", "heart"]),
    "fun": ("#c4b5ec", ["museum", "art_gallery", "theatre", "cinema", "music", "attraction",
                         "monument", "castle", "zoo", "aquarium", "amusement_park", "stadium",
                         "lighthouse", "star", "american_football", "baseball", "basketball",
                         "cricket", "soccer", "tennis", "golf", "skiing", "swimming", "pitch"]),
    "nature": ("#a9dcbc", ["park", "garden", "campsite", "picnic_site", "dog_park", "playground",
                            "wetland", "water", "mountain", "volcano", "dam", "shelter",
                            "ranger_station", "cemetery"]),
    "civic": ("#b3c3e3", ["school", "college", "library", "town_hall", "post", "embassy",
                           "place_of_worship", "religious_christian", "religious_jewish",
                           "religious_muslim", "information", "toilets", "wheelchair",
                           "waste_basket", "building", "lodging", "marker"]),
}
DEFAULT_FILL = "#d5d3dc"

# OFM name -> Maki file name where they differ.
MAKI_ALIASES = {
    "railway": "rail", "railway_light": "rail-light", "railway_metro": "rail-metro",
    "bicycle_rental": "bicycle-share", "doctors": "doctor", "toilets": "toilet",
    "sushi": "restaurant-sushi", "butcher": "restaurant",
}

# Sprite entries that stay as OFM drew them.
KEEP_RE = re.compile(r"^(road_\d|us-|arrow|oneway|circle|square|triangle|dot_|default_|pedestrian_polygon|wetland_bg)")


def group_fill(name: str) -> str:
    for fill, members in GROUPS.values():
        if name in members:
            return fill
    return DEFAULT_FILL


def maki_svg(name: str) -> str | None:
    stem = MAKI_ALIASES.get(name, name.replace("_", "-"))
    for candidate in (stem, stem + "-15"):
        p = VENDOR / "maki" / f"{candidate}.svg"
        if p.exists():
            return p.read_text()
    return None


def inner_paths(svg: str) -> str:
    body = re.sub(r"^.*?<svg[^>]*>", "", svg, flags=re.S)
    body = re.sub(r"</svg>\s*$", "", body.strip(), flags=re.S)
    return re.sub(r'\sfill="[^"]*"', "", body)


BADGE = 24  # css px per badge (1x): 22px square + shadow room
CELL = 26


def badge_svg(name: str) -> str:
    svg = maki_svg(name) or maki_svg("marker")
    fill = group_fill(name)
    # 15-unit Maki glyph scaled into the badge's centre.
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{BADGE}" height="{BADGE}" viewBox="0 0 24 24">
  <rect x="2" y="3" width="20" height="20" rx="5" fill="{INK}" opacity="0.35"/>
  <rect x="1.5" y="1.5" width="20" height="20" rx="5" fill="{fill}" stroke="{INK}" stroke-width="2"/>
  <rect x="3.2" y="3.2" width="16.6" height="3" rx="1.5" fill="#ffffff" opacity="0.35"/>
  <g transform="translate(4.6 4.6) scale(0.92)" fill="{INK}">{inner_paths(svg)}</g>
</svg>"""


def render(names: list[str], scale: int, chrome: str) -> Image.Image:
    cols = 16
    rows = (len(names) + cols - 1) // cols
    cells = "".join(
        f'<div style="position:absolute;left:{(i % cols) * CELL}px;top:{(i // cols) * CELL}px">{badge_svg(n)}</div>'
        for i, n in enumerate(names)
    )
    html = f'<html><body style="margin:0;background:transparent">{cells}</body></html>'
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "s.html"
        page.write_text(html)
        png = Path(tmp) / "s.png"
        script = f"""
const {{ chromium }} = require('playwright');
(async () => {{
  const b = await chromium.launch({{ executablePath: {json.dumps(chrome)} }});
  const p = await b.newPage({{ viewport: {{ width: {cols * CELL}, height: {rows * CELL} }}, deviceScaleFactor: {scale} }});
  await p.goto('file://{page}');
  await p.screenshot({{ path: {json.dumps(str(png))}, omitBackground: true }});
  await b.close();
}})();"""
        env = dict(os.environ, NODE_PATH=os.environ.get("PLAYWRIGHT_NODE_PATH", ""))
        subprocess.run(["node", "-e", script], check=True, env=env)
        return Image.open(png).convert("RGBA").copy()


def build(scale: int, chrome: str):
    suffix = "@2x" if scale == 2 else ""
    ofm_json = json.loads((VENDOR / "ofm" / f"ofm{suffix}.json").read_text())
    ofm_png = Image.open(VENDOR / "ofm" / f"ofm{suffix}.png").convert("RGBA")

    poi_names = sorted(n for n in ofm_json if not KEEP_RE.match(n))
    # One badge per base name; "_11" variants share it.
    bases = sorted({n[:-3] if n.endswith("_11") else n for n in poi_names})
    sheet = render(bases, scale, chrome)

    out = Image.new("RGBA", (max(ofm_png.width, sheet.width), ofm_png.height + sheet.height), (0, 0, 0, 0))
    out.paste(ofm_png, (0, 0))
    out.paste(sheet, (0, ofm_png.height))

    entries = {n: v for n, v in ofm_json.items() if KEEP_RE.match(n)}
    cols = 16
    for i, base in enumerate(bases):
        entry = {
            "x": (i % cols) * CELL * scale,
            "y": ofm_png.height + (i // cols) * CELL * scale,
            "width": BADGE * scale,
            "height": BADGE * scale,
            "pixelRatio": scale,
        }
        for name in (base, base + "_11"):
            if name in ofm_json:
                entries[name] = entry

    OUT.mkdir(parents=True, exist_ok=True)
    out.save(OUT / f"vice{suffix}.png", optimize=True)
    (OUT / f"vice{suffix}.json").write_text(json.dumps(entries, separators=(",", ":"), sort_keys=True))
    missing = [b for b in bases if maki_svg(b) is None]
    print(f"vice{suffix}: {len(bases)} badges, {len(entries)} entries"
          + (f"; marker fallback for: {', '.join(missing)}" if missing else ""))


if __name__ == "__main__":
    chrome = os.environ.get("CHROME") or "chromium"
    for s in (1, 2):
        build(s, chrome)
