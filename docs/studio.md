<!-- dev-docs-ai-translation: {"document":"docs/studio.md","language":"en","syncedHash":"51af98c116f276aadc77ae62acddf8a00b313531f9e57cf2a76127defdf12841"} -->

# Stranded II Studio

[English](studio.md) · [한국어](studio-ko.md)

A standalone development app for Extended content. The Electron/React interface and Python CLI share one project format and command implementation. The 3D scene and play mode run the shared Blitz engine and Extended modules through `StrandedIIStudio.bb`, compiled to WebAssembly/WebGPU. The base game's launch and release routes remain unchanged.

## Preparation and launch

Requirements: Python 3.12+, Node.js 22.12+ and Git. macOS needs Xcode Command Line Tools and FFmpeg/ffprobe on PATH; Windows needs the MSVC C++ build tools and Windows SDK. The 3D viewport uses Electron's WebGPU and JSPI support. This is a development version launched from the repository; installer packages are not provided.

On Windows, double-click [Studio.cmd](../Studio.cmd) in the repository root. On macOS/Linux, run [Studio.sh](../Studio.sh) with `sh ./Studio.sh`. Both launchers also work from another directory when called by their path. The [shared launcher](../scripts/studio_launch.py) creates `.venv`, installs missing or changed Python/Node dependencies, prepares resources, builds and verifies the engine, then builds and starts the app. Outside Windows, missing CMake/Ninja tools are also installed in the virtual environment. Preparation failures prevent the app from opening.

Resources and the engine are refreshed on every launch; no separate engine commands are needed. Game/font paths are found in existing Studio, web and Windows preparation settings, in that order, falling back to `deps/game` and `deps/fonts/NotoSansKR[wght].ttf` when no saved path is valid. Pass `--game PATH --font PATH` to the launcher for external inputs. See the [development guide](development.md) for official resources and the pinned font. Projects and personal saves are not reset.

Use `Studio.cmd --prepare-only` or `sh ./Studio.sh --prepare-only` to prepare everything without opening the app. To run individual steps manually, activate a Python virtual environment from the repository root and use:

```sh
python -m pip install -r requirements.txt cmake ninja
python scripts/studio.py engine setup --game deps/game --font "deps/fonts/NotoSansKR[wght].ttf"
python scripts/studio.py engine build
python scripts/studio.py engine check
npm --prefix studio ci
npm --prefix studio start
```

If web/Studio preparation settings already exist, the path arguments to `engine setup` may be omitted. The engine is edited directly in the shared `src/blitz3d/` sources. Preparation downloads pinned external SDKs/libraries; compiler/runtime outputs stay in `build/engine/`, Studio game outputs in `build/studio/`, and app outputs in `studio/dist/`. The launchers and app prefer the repository's `.venv` Python; set `S2_STUDIO_PYTHON` to use another interpreter.

Create a project in an empty folder or open an existing project on the start screen. Use **`build/studio/runtime`** as the base resources for 3D preview. The CLI can edit, validate and export projects using other prepared bases, but preview requires the same base path as its bundled resources.

## Workflow

| Area | Usage |
| --- | --- |
| World | `+` creates a 32×32 island; `↥` imports a prepared UTF-8 `.s2` map. Other sizes are available through the CLI. |
| Scene | Right-drag to look, WASD/QE to move, Shift to accelerate, wheel to zoom, F to frame the selection. |
| Placement | Drag content into the scene, or select it and click terrain with the **Placement** tool. A translucent green preview follows the cursor in placement mode; Esc cancels. New definitions become placeable after **Apply resources**. Shift selects multiple entities. |
| Move | Drag the thick X/Z axes for directional movement or the center square to move along terrain. Y is available when the selection contains no objects. The last selected entity anchors snapping; Esc or lost pointer capture cancels the drag. |
| Inspector | Edit coordinates, rotation, health and quantity, then **Apply**. Objects derive their height from terrain, as in the game. |
| Terrain | Choose raise, lower, flatten or smooth, then click terrain. Adjust radius and strength; strength applies to raise/lower. |
| Content | Browse items, objects, units, map information, buildings and recipes by their actual icons. Select one to enlarge it in the inspector. Clone definitions, edit properties/materials, and import models, images or audio. |
| Scripts | Edit definition, entity-event or global-map scripts, or bind `.s2s` files. Multiple file tabs, text search, event lists, command completion and hover help are available. |
| Resources | Saved definition, model, translation and font changes display **Resources pending**. **Apply resources** applies them together and restores camera, selection and tool. Until then, the scene uses the last applied appearance. |
| Test | Validate the latest saved files and automatically apply changed resources. Click the viewport to capture the mouse, Esc to release, and **Stop** to restore the editor map and camera. Play-session changes do not enter the project. |
| Diagnostics | Check content references, script brackets, strings and file references. View runtime events/errors and read-only global/selected-entity variables. |

Content icons fit a 64px tile and expand to at most 160px in the inspector, preserving their aspect ratio. Most originals are 40×40; enlargement does not add detail. Buildings show their completed result, and recipes show the first output item with `+N` for additional outputs. Map information uses the game's icon frames. Missing or unreadable images display a fallback symbol and a reason.

Icons work without a map or the 3D engine. Imported project images take precedence, and icons refresh when saved definitions or assets change. Placement, movement, terrain edits and their undo/redo retain existing thumbnails. Unapplied drafts are excluded; icon updates are independent of the scene's **Apply resources** action.

The placement preview displays the currently applied item, object or unit model in green at 40% opacity. Buildings use their completed result model; map information uses a position marker. Preview and click placement share the same snapping, terrain height and alignment rules. The temporary model is never saved in the map and hides outside terrain or while looking with the right mouse button.

**Test** freezes the saved files and project revision at startup. If files change again during preparation, startup is rejected; retry the test. Unchanged resources reuse the current engine; changed definitions, script files, models, translations or fonts automatically reconnect it with the new resources. `on:preload`, `on:start` and `on:load` retain their existing order. Unsaved drafts are excluded, and code saved during play takes effect on the next test.

Use **Cancel start** during the transition. **Stop** reloads the saved map and preserves the editor camera, selection, tool and drafts. There is no separate player engine or background preparation.

Placement, properties, deletion, terrain, undo, redo and CLI edits update the scene through deltas without reloading the map. Hierarchy scroll, camera, selection and tool are retained. Dragging displays a transient position; release saves once, producing one undo entry. Multiple selections share a movement delta; the center handle preserves each entity’s height offset above terrain. External changes wait during a drag. A revision conflict cancels the transient move and reconciles the latest saved state.

Applied commands are saved immediately. Unapplied property and script drafts are stored separately by the app and survive selection changes and restarts. Applying an old draft is rejected if another operation or CLI edit changed the revision. Copy anything needed, use **Discard draft** to reload current values, then edit again.

Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z redoes, Ctrl/Cmd+D duplicates entities and Delete removes them. The script editor prioritizes its own text undo. Entity duplication copies placement properties and event scripts, but not attached states or stored items. Deletion rejects entities with attached states, stored items or scripts. Applying an empty script detaches it.

## Script files and templates

Existing projects open unchanged. Select a target and use **Extract to file** to save its current script in `scripts/<name>.s2s` and bind it. **Bind to selection** reuses a file; **Selection → Unbind** keeps the current text inside the target and leaves the file in place. The file is the single source shared by Studio, external editors and the CLI.

Save UTF-8 without BOM and use ASCII letters, numbers, `_`, `-` and `.` in paths. Limits are 1 MiB per file and 1,000 files/16 MiB per project. External changes are detected while editing; conflicting drafts are never overwritten. Use **Save to another file** or **Discard draft** to resolve them. **Save file** or Ctrl/Cmd+S in the editor saves; `●` marks an unsaved draft. Click search matches, events, references or diagnostics to open their files.

Export embeds bound text in native definitions/maps and includes the files under `sys/scripts/studio/`. For example, reference `scripts/story/reward.s2s` with `extendscript 0,0,"sys/scripts/studio/story/reward.s2s";`. Static checks do not guarantee execution semantics. Runtime errors link to the original line only when saved source and parser contents match unambiguously; dynamic or duplicated code shows entity/event/parser-row context. The log retains 300 events; variables show up to 256 entries and the first 1,024 characters of each value. Select the watched entity before starting a test.

Use **Templates** in Content to create a healing item, recipe, building, NPC or tonic-delivery scenario. **Preview changes**, then **Create** allocates IDs, references, files and English/Korean translations in one transaction. The scenario follows nearby ingredient collection → tonic crafting → NPC delivery → reward → completion. Apply the [CLI example](../studio/examples/tonic-scenario.json) to a new project.

```sh
python scripts/studio.py scripts PROJECT
python scripts/studio.py scripts PROJECT --path scripts/tonic_delivery/npc.s2s
python scripts/studio.py scripts PROJECT --query reward
python scripts/studio.py templates PROJECT
python scripts/studio.py apply PROJECT --file studio/examples/tonic-scenario.json --dry-run
python scripts/studio.py apply PROJECT --file studio/examples/tonic-scenario.json --expect-revision 0
```

CLI `script.write` requires `path`, `text` and `expectedHash`. Read the hash from `scripts --path`; use `null` for a new file. The `target` for `script.bind`/`script.extract`/`script.unbind` uses `scope: map|entity|definition` and the corresponding `map`, `kind` and `id`. Existing `map.script`, `entity.script` and `content.set` operations on bound targets also require `expectedHash`. The `scriptsVersion` in `revision` detects external file edits. JSON `preview` rechecks `expectRevision` and `expectScriptsVersion` during preparation. File writes, bindings and templates participate in undo/redo, which rejects conflicts with external edits.

## Agents and CLI

The CLI works without a GUI and returns JSON on stdout. Errors and failed validation exit with code 1. `serve` implements JSON Lines with echoed request IDs; the GUI uses the same protocol.

`thumbnail PROJECT --kind KIND --id ID` returns a PNG `dataUrl`, original `width`/`height`, resource path, `revision` and `version` without modifying the project. `version` is the content-resource cache key exposed as `thumbnailVersion` by `inspect`; map edits leave it unchanged. Unavailable images return `status: "unavailable"` and `reason`; unknown definition IDs are command errors. For recipes, use the identifier string returned by `catalog` unchanged.

JSON requests pass the content identifier as `contentId`. The `id` field identifies the protocol request.

```sh
python scripts/studio.py schema
python scripts/studio.py init build/studio-example --base build/studio/runtime --name "My island"
python scripts/studio.py apply build/studio-example --file studio/examples/first-island.json --expect-revision 0
python scripts/studio.py inspect build/studio-example --map island
python scripts/studio.py catalog build/studio-example --kind item --query 나무
python scripts/studio.py thumbnail build/studio-example --kind item --id 1
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

`project.json` stores definition changes, map-edit commands, file bindings and undo baseline snapshots. `sources/` stores original maps and assets named by their hashes. The `.s2s` files in `scripts/` are the authoritative script sources; preserve all three paths together. `.studio/` holds the last 100 undo entries, locks, font caches and recent preview-token caches. Interrupted file transactions recover on the next open without overwriting new external edits. Base resources are read-only. The `base` in `project.json` is usually relative; adjust it to the prepared resources when moving to another computer.

Export requires an empty folder. `overrides/` contains native `.inf`, `.s2` and assets; `locales/` contains project translations; `manifest.json` records revision and file hashes. Merge reviewed content into `resources/editions/extended/overrides/` and translations into `locales/editions/extended/<code>.tsv`, then prepare/build Extended. Merge changes into existing files rather than replacing them wholesale. Ordinary builds, CI and releases are not started automatically.

Register new text with `locale.set` and `[[loc:key]]`, including English fallback translations. When a project introduces new characters, preview reuses the existing bitmap-font generator and requires the font used during preparation. Additional fonts are also exported under `fonts/<code>/`; for testing exported content in a separate runtime, apply them to `core/locales/<code>/`. For integration into the repository, follow the catalog/font refresh procedure in the [localization guide](localization.md).

Edits preserve unrelated map data and attachments. Unlock password-protected maps in the original editor before import. The current scope is content authoring within the existing map format. Prefabs, full docking, rotation/scale gizmos and chunk streaming are not implemented.

## Verification

```sh
python -m unittest discover -s tests -p "test_studio*.py"
python scripts/studio.py engine check
npm --prefix studio run check
npm --prefix studio test
python scripts/verify_studio_scripts.py
```

Script checks cover external edits, stale hashes, transaction recovery, bindings, undo, source positions and templates. On Windows, `verify_studio_scripts.py` requires prepared Studio and Extended runtimes. It exports the example into a separate copy and uses the real engine to check ingredient collection, crafting, NPC delivery, rewards, healing items, error traces and variable JSON. It uses neither user saves nor GUI input.

Icon checks cover color keys, frames and output-image selection, asset precedence, refresh after resource edits and cache retention during map edits, path restrictions, read-only behavior, request caching/cancellation and IPC responses. Placement checks cover display scaling, latest-pointer delivery, model/snap capture, preview cancellation and rejection of unapplied definitions. Actual translucent model display still requires a separate check in the app.

Tests cover data preservation, transactions, deltas/ACKs, pending resources, movement math, cancellation/conflicts, IPC, engine reuse, the latest saved map, stale requests, canceled starts, and map/camera restoration without launching a GUI. `engine check` verifies actual Blitz build artifacts, Extended catalogs, allowlisted resources and audio conversion. After changing the shared map loader, build/check both editions and run `python scripts/verify_game.py` and `python scripts/verify_game.py --edition extended` to check existing save/load behavior. View switching, selection, dragging, mouse capture, Alt-Tab, scaling, sound and Korean rendering still require separate checks in the app. The diagnostic log's time from start request to first game frame is not an FPS measurement.
