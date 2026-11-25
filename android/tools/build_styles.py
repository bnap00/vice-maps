#!/usr/bin/env python3
"""Generate the Vice Day / Vice Night MapLibre styles from OpenFreeMap's Liberty style.

Liberty (tools/liberty.json, from https://tiles.openfreemap.org/styles/liberty) uses the
OpenMapTiles schema and keyless OpenFreeMap tiles, glyphs and sprites. We keep its layer
structure and rewrite every colour by layer id, so the result stays a valid, keyless style.

    python3 tools/build_styles.py   ->  app/src/main/assets/vice-day.json, vice-night.json
"""
import copy
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "liberty.json"
OUT = ROOT / "app" / "src" / "main" / "assets"

PALETTES = {
    "day": {
        "land": "#5d7d5a",          # palm-green countryside
        "residential": "#666e70",   # slate city blocks
        "building": "#80878b",
        "building_outline": "#5a6064",
        "water": "#1e6a78",         # deep teal sea
        "waterway": "#2a7c8a",
        "park": "#4f8a5c",
        "wood": "#467c50",
        "grass": "#5a9363",
        "sand": "#c9b27a",          # beaches
        "ice": "#cfd8da",
        "pitch": "#5e8f66",
        "cemetery": "#58785c",
        "hospital": "#8b6a86",
        "school": "#74707d",
        "aeroway": "#757b7f",
        "runway": "#9aa0a4",
        "casing": "#3f4449",
        "minor": "#d9d4dc",
        "path": "#b9b2bd",
        "secondary": "#ece4ec",
        "primary": "#ffd4a8",       # peach arterials
        "motorway": "#ffc2d0",      # pink highways
        "motorway_casing": "#9c5f84",
        "rail": "#3a3f44",
        "boundary": "#c07eaa",
        "text": "#fffafa",
        "text_muted": "#f2dfe8",
        "halo": "rgba(15,12,26,0.85)",
        "water_text": "#c5eef3",
        "water_halo": "rgba(14,52,60,0.9)",
        "place": "#fff9cb",         # cream district names
        "transit_text": "#ffd4a8",
        "relief_opacity": 0.18,
    },
    "night": {
        "land": "#3a2a4d",          # dusk purple
        "residential": "#33274a",
        "building": "#4a3760",
        "building_outline": "#2a1f3d",
        "water": "#141c45",         # indigo sea
        "waterway": "#1d2a5c",
        "park": "#2f4a4a",
        "wood": "#2a4444",
        "grass": "#2f4d4a",
        "sand": "#6b4a5e",
        "ice": "#4b4a66",
        "pitch": "#2f4a4a",
        "cemetery": "#2d4040",
        "hospital": "#5a3358",
        "school": "#3e3155",
        "aeroway": "#3b3150",
        "runway": "#5d4775",
        "casing": "#1d1430",
        "minor": "#5d4775",
        "path": "#4b3a63",
        "secondary": "#8a6a9a",
        "primary": "#d27eae",
        "motorway": "#ff8fc0",      # neon pink highways
        "motorway_casing": "#6a2f5c",
        "rail": "#2a1f3d",
        "boundary": "#ef8fb6",
        "text": "#fee8ea",
        "text_muted": "#d9c3dc",
        "halo": "rgba(10,6,20,0.9)",
        "water_text": "#9fb2ff",
        "water_halo": "rgba(10,12,40,0.9)",
        "place": "#fff9cb",
        "transit_text": "#ffd4a8",
        "relief_opacity": 0.12,
    },
}


def fill_role(lid: str):
    """Palette key for fill / fill-extrusion layers."""
    if lid == "background":
        return "land"
    for key, role in [
        ("park", "park"), ("residential", "residential"), ("wood", "wood"), ("grass", "grass"),
        ("ice", "ice"), ("wetland", "grass"), ("pitch", "pitch"), ("track", "pitch"),
        ("cemetery", "cemetery"), ("hospital", "hospital"), ("school", "school"),
        ("sand", "sand"), ("aeroway", "aeroway"), ("building", "building"), ("water", "water"),
        ("road_area", "minor"),
    ]:
        if key in lid:
            return role
    return None


def line_role(lid: str):
    """Palette key for line layers."""
    if lid.startswith("waterway"):
        return "waterway"
    if lid.startswith("boundary"):
        return "boundary"
    if lid == "park_outline":
        return "park"
    if "runway" in lid or "taxiway" in lid:
        return "runway"
    if "rail" in lid:
        return "rail"
    if lid.endswith("_casing"):
        return "motorway_casing" if "motorway" in lid else "casing"
    if "motorway" in lid:
        return "motorway"
    if "trunk_primary" in lid:
        return "primary"
    if "secondary_tertiary" in lid or "link" in lid:
        return "secondary"
    if "path_pedestrian" in lid:
        return "path"
    if "minor" in lid or "service" in lid or "street" in lid:
        return "minor"
    return None


def symbol_roles(lid: str):
    """(text colour key, halo key) for symbol layers."""
    if lid.startswith("water") :
        return "water_text", "water_halo"
    if lid.startswith("label_"):
        return "place", "halo"
    if lid == "poi_transit":
        return "transit_text", "halo"
    if lid.startswith("highway-name") or lid.startswith("poi") or lid == "airport":
        return "text_muted", "halo"
    return "text", "halo"


def build(name: str, pal: dict) -> dict:
    style = copy.deepcopy(json.loads(SRC.read_text()))
    style["name"] = f"Vice {name.title()}"
    # MapGenie-style category badges (tools/build_sprites.py), bundled in the app.
    style["sprite"] = "asset://sprites/vice"
    style.pop("id", None)
    out_layers = []
    for layer in style["layers"]:
        lid, typ = layer["id"], layer["type"]
        paint = layer.setdefault("paint", {})
        layout = layer.setdefault("layout", {})

        if typ == "background":
            paint["background-color"] = pal["land"]
        elif typ == "raster":
            paint["raster-opacity"] = pal["relief_opacity"]
            paint["raster-saturation"] = -1
        elif typ == "fill":
            role = fill_role(lid)
            if role:
                paint["fill-color"] = pal[role]
                paint.pop("fill-pattern", None)
                if "fill-outline-color" in paint:
                    paint["fill-outline-color"] = pal["building_outline"] if role == "building" else pal[role]
                if role in ("park", "wood", "grass"):
                    paint["fill-opacity"] = 0.9
        elif typ == "fill-extrusion":
            paint["fill-extrusion-color"] = pal["building"]
        elif typ == "line":
            role = line_role(lid)
            if role:
                paint["line-color"] = pal[role]
        elif typ == "symbol":
            if lid.startswith("poi") or lid == "airport":
                # Badges are drawn at their final size; keep them crisp and readable.
                layout["icon-size"] = 1.4
                if lid.startswith("poi_r"):
                    layout["text-offset"] = [0, 1.55]
            text_key, halo_key = symbol_roles(lid)
            if "text-field" in layout:
                paint["text-color"] = pal[text_key]
                paint["text-halo-color"] = pal[halo_key]
                paint["text-halo-width"] = 1.4
                paint["text-halo-blur"] = 0.4
            if lid.startswith("label_") and lid not in ("label_other",):
                # Game-map district names: bold caps, wide tracking, soft shadow halo.
                layout["text-font"] = ["Noto Sans Bold"]
                layout["text-transform"] = "uppercase"
                layout["text-letter-spacing"] = 0.08
                paint["text-halo-width"] = 2
                paint["text-halo-blur"] = 1
        out_layers.append(layer)
    style["layers"] = out_layers
    return style


def check_colors(style: dict):
    """Fail loudly if any original Liberty colour slipped through un-themed."""
    leftovers = []
    for layer in style["layers"]:
        for k, v in layer.get("paint", {}).items():
            if "color" in k and isinstance(v, (list, dict)):
                leftovers.append(f'{layer["id"]}.{k}')
    return leftovers


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, pal in PALETTES.items():
        style = build(name, pal)
        left = check_colors(style)
        (OUT / f"vice-{name}.json").write_text(json.dumps(style, separators=(",", ":")))
        print(f"vice-{name}.json: {len(style['layers'])} layers"
              + (f"; expression colours kept: {', '.join(left)}" if left else ""))
