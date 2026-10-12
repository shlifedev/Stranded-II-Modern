<!-- dev-docs-ai-translation: {"document":"docs/studio.md","language":"en","syncedHash":"4a768fa1f0ae329d8cc817a4a35611f7d708cc11d85adbf5583739cb9d42d8fd"} -->

# Stranded II Studio

[English](studio.md) · [한국어](studio-ko.md)

A standalone development app for Extended content. The Electron/React interface and Python CLI share one project format and command implementation. The 3D scene and play mode run the shared Blitz engine and Extended modules through `StrandedIIStudio.bb`, compiled to WebAssembly/WebGPU. The base game's launch and release routes remain unchanged.

## Preparation and launch

Requirements: Python 3.12+ with the [Python dependencies](../requirements.txt), Node.js 22.12+, Git, CMake and Ninja. macOS needs Xcode Command Line Tools and FFmpeg/ffprobe on PATH; Windows needs the MSVC C++ build tools and Windows SDK. The 3D viewport uses Electron's WebGPU and JSPI support. This is a development version launched from the repository; installer packages are not provided.

Run from the repository root with a Python virtual environment activated. See the [development guide](development.md) for the official game resources and pinned font.

```sh
python -m pip install -r requirements.txt cmake ninja
python scripts/studio.py engine setup --game deps/game --font "deps/fonts/NotoSansKR[wght].ttf"
python scripts/studio.py engine build
python scripts/studio.py engine check
npm --prefix studio ci
npm --prefix studio start
```

If web/Studio preparation settings already exist, the path arguments to `engine setup` may be omitted. Initial preparation downloads the pinned Blitz web toolchain and SDK. Outputs stay under `build/studio/`. The app prefers the repository's `.venv` Python; set `S2_STUDIO_PYTHON` to use another interpreter.

Create a project in an empty folder or open an existing project on the start screen. Use **`build/studio/runtime`** as the base resources for 3D preview. The CLI can edit, validate and export projects using other prepared bases, but preview requires the same base path as its bundled resources.

## Workflow

| Area | Usage |
| --- | --- |
| World | `+` creates a 32×32 island; `↥` imports a prepared UTF-8 `.s2` map. Other sizes are available through the CLI. |
| Scene | Right-drag to look, WASD/QE to move, Shift to accelerate, wheel to zoom, F to frame the selection. |
| Placement | Drag content into the scene or click terrain with the placement tool. New definitions become placeable after **Apply resources**. Shift selects multiple entities. |
| Move | Drag the thick X/Z axes for directional movement or the center square to move along terrain. Y is available when the selection contains no objects. The last selected entity anchors snapping; Esc or lost pointer capture cancels the drag. |
| Inspector | Edit coordinates, rotation, health and quantity, then **Apply**. Objects derive their height from terrain, as in the game. |
| Terrain | Choose raise, lower, flatten or smooth, then click terrain. Adjust radius and strength; strength applies to raise/lower. |
| Content | Search items, objects, units, map information, buildings and recipes; clone definitions, edit properties/materials, and import models, images or audio. |
| Scripts | Edit definition, entity-event or global-map scripts. Game-command completion is available; **Apply script** saves changes. |
| Resources | Saved definition, model, translation and font changes display **Resources pending**. **Apply resources** applies them together and restores camera, selection and tool. Until then, the scene uses the last applied appearance. |
| Test | Apply the latest saved state and pending resources, then play in the actual Blitz game. Click the viewport to capture the mouse, Esc to release, and **Stop** to reload the authoring map. Play-session changes do not enter the project. |
| Diagnostics | Check definition IDs, references, asset paths, recipe structure and player starts. Static validation does not guarantee script execution results. |

Placement, properties, deletion, terrain, undo, redo and CLI edits update the scene through deltas without reloading the map. Hierarchy scroll, camera, selection and tool are retained. Dragging displays a transient position; release saves once, producing one undo entry. Multiple selections share a movement delta; the center handle preserves each entity’s height offset above terrain. External changes wait during a drag. A revision conflict cancels the transient move and reconciles the latest saved state.

Applied commands are saved immediately. Unapplied property and script drafts are stored separately by the app and survive selection changes and restarts. Applying an old draft is rejected if another operation or CLI edit changed the revision. Copy anything needed, use **Discard draft** to reload current values, then edit again.

Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z redoes, Ctrl/Cmd+D duplicates entities and Delete removes them. The script editor prioritizes its own text undo. Entity duplication copies placement properties and event scripts, but not attached states or stored items. Deletion rejects entities with attached states, stored items or scripts. Applying an empty script detaches it.

## Agents and CLI

The CLI works without a GUI and returns JSON on stdout. Errors and failed validation exit with code 1. `serve` implements JSON Lines with echoed request IDs; the GUI uses the same protocol.

```sh
python scripts/studio.py schema
python scripts/studio.py init build/studio-example --base build/studio/runtime --name "My island"
python scripts/studio.py apply build/studio-example --file studio/examples/first-island.json --expect-revision 0
python scripts/studio.py inspect build/studio-example --map island
python scripts/studio.py catalog build/studio-example --kind item --query 나무
python scripts/studio.py validate build/studio-example
python scripts/studio.py undo build/studio-example --expect-revision 1
python scripts/studio.py redo build/studio-example --expect-revision 2
python scripts/studio.py export build/studio-example --out build/studio-example-export
```

`apply` accepts one command or an array; `--file -` reads stdin. An array is one transaction and saves nothing on failure. `--dry-run` returns results and diagnostics without changes. When working alongside the GUI, pass the revision from `inspect`/`revision` as `--expect-revision`. `schema` describes supported commands and fields.

`entities.move` accepts `selection: [{"kind":"unit","id":100}, ...]`, a world-space `delta: {"x":64,"y":0,"z":0}`, and optional `terrain: true`. Vertical movement is rejected when the group includes objects.

`preview PROJECT --map NAME` returns full files and a `token`. Passing `--since TOKEN` then returns a binary `patch` between that token’s scene and the current saved state, the latest native map, and pending-resource status. Advance the baseline token only after the engine acknowledges application. Deltas exclude definitions, assets and language files; scenes requiring new definitions report `resourcesRequired`. Expired/damaged tokens and engine application failures never trigger an automatic reload; use **Apply resources** to reconnect explicitly.

Definition IDs and map instance IDs are separate. Cloning a definition allocates a free ID and rejects exhausted limits. Recipe identifiers use `path#index`. Building names come from their resulting object/unit; recipe names use `genname` or their resulting items. Use `content.create` for new definitions/recipes, string arrays for repeated properties, and `null` to remove a property. Terrain coordinates are samples; world coordinates are `(sample-size/2)*64`.

## Projects and exports

`project.json` stores definition changes and map-edit commands. `sources/` stores original maps and assets named by their hashes. Both are project sources. `.studio/` holds the last 100 undo entries, locks, font caches and recent preview-token caches. Base resources are read-only. The `base` in `project.json` is usually relative; adjust it to the prepared resources when moving to another computer.

Export requires an empty folder. `overrides/` contains native `.inf`, `.s2` and assets; `locales/` contains project translations; `manifest.json` records revision and file hashes. Merge reviewed content into `resources/editions/extended/overrides/` and translations into `locales/editions/extended/<code>.tsv`, then prepare/build Extended. Merge changes into existing files rather than replacing them wholesale. Ordinary builds, CI and releases are not started automatically.

Register new text with `locale.set` and `[[loc:key]]`, including English fallback translations. When a project introduces new characters, preview reuses the existing bitmap-font generator and requires the font used during preparation. Additional fonts are also exported under `fonts/<code>/`; for testing exported content in a separate runtime, apply them to `core/locales/<code>/`. For integration into the repository, follow the catalog/font refresh procedure in the [localization guide](localization.md).

Edits preserve unrelated map data and attachments. Unlock password-protected maps in the original editor before import. The current scope is content authoring within the existing map format. Prefabs, full docking, rotation/scale gizmos and chunk streaming are not implemented.

## Verification

```sh
python -m unittest discover -s tests -p "test_studio.py"
python scripts/studio.py engine check
npm --prefix studio run check
npm --prefix studio test
```

Tests cover data preservation, transactions, deltas/ACKs, pending resources, movement math, cancellation/conflicts and IPC without launching a GUI. `engine check` verifies actual Blitz build artifacts, Extended catalogs, allowlisted resources and audio conversion. Display, selection, dragging, play mode and Korean rendering still require separate checks in the app.
