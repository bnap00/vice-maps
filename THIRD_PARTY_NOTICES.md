# Third-party notices

Vice Maps' own code is MIT licensed (see `LICENSE`). It bundles or builds on the following.

## Bundled in this repository

| What | Where | License |
|---|---|---|
| Big Shoulders Display, Jost (fonts) | `extension/fonts/`, `android/app/src/main/res/font/` | SIL Open Font License 1.1, see `LICENSES/OFL-1.1.txt` |
| Maki icons (Mapbox) | `android/tools/vendor/maki/`, redrawn into `android/app/src/main/assets/sprites/` | CC0 1.0, see `android/tools/vendor/maki/LICENSE.txt` |
| Liberty map style from OpenFreeMap (fork of OSM Liberty, itself derived from OSM Bright) | `android/tools/liberty.json`, recoloured into `android/app/src/main/assets/vice-*.json` | Style code: BSD 3-Clause (OSM Liberty) and MIT (OpenFreeMap styles, Copyright (c) 2023 Zsolt Ero). Design: CC BY (OSM Bright / OpenMapTiles) |
| OpenFreeMap sprite (road shields, arrows) | `android/tools/vendor/ofm/`, merged into the app sprite | Same as the Liberty style above; POI icons in it are Maki (CC0 1.0) |

## Used at runtime (Android app)

| Service / library | License or terms |
|---|---|
| Map data © OpenStreetMap contributors | ODbL 1.0. Attribution is shown in the map's ⓘ button |
| Vector tiles and glyphs from OpenFreeMap, schema © OpenMapTiles | Free public instance; attribution required (shown by MapLibre) |
| Routing: Valhalla, public server run by FOSSGIS e.V. | Fair use only. Self-host Valhalla for anything beyond personal use |
| Search: Photon, public server run by Komoot | Fair use only; heavy use is throttled or banned. Self-host Photon for more |
| Ferrostar (Stadia Maps) | BSD 3-Clause |
| MapLibre Native / maplibre-compose | BSD 2-Clause / BSD 3-Clause |
| AndroidX, Jetpack Compose, Car App Library, Play Services Location | Apache 2.0 / Android SDK terms |
| OkHttp, kotlinx.serialization, kotlinx.coroutines | Apache 2.0 |

## Trademarks

Vice Maps is a fan-made theme. It is not affiliated with or endorsed by Google, Rockstar Games,
Take-Two Interactive or MapGenie. Google Maps is a trademark of Google LLC. The extension only
changes how Google Maps looks in your own browser; it does not copy any of Google's map data.
