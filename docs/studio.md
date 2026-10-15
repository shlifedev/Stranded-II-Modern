<!-- dev-docs-ai-translation: {"document":"docs/studio.md","language":"en","syncedHash":"83c5c8f0289b2aede2f82f485f2fdea14cbf1b169e397d80a738a5028abcc948"} -->

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

Choose a project folder on the welcome screen to create or open a project. The default location is `Stranded II Studio/Projects/` in your Documents folder. A folder containing `.git`, a README or other files is supported; existing paths used by Studio are never overwritten. New projects use v3, storing each map's current state as JSON; existing projects are not converted automatically. Studio remembers ten recent projects and their last active maps. **Open folder** locates the project for external editors and Git tools.

Use **`build/studio/runtime`** as the base resources for 3D preview. After cloning or moving a project, select its folder and this computer's resources on the welcome screen, then choose **Reconnect resources and open existing project**. The connection is stored in Git-ignored `.studio/local.json` without changing `project.json`. v2 and v3 pin the shared resources by content hash, so the connected resources must match that version. A mismatch blocks editing, testing and export. Other prepared resources support editing, validation and export, but preview requires the base path bundled into the Studio build.

## Workflow

| Area | Usage |
| --- | --- |
| World | `+` creates a 32×32 island; `↥` imports a prepared UTF-8 `.s2`. Search by name, instance ID or definition ID, filter by kind, and double-click a result to find it in the scene. Use the CLI for other sizes. |
| Scene | Right-drag to look, WASD/QE to move, Shift to accelerate, wheel to zoom, F to frame the selection. |
| Placement | Drag content into the scene, or select it and click terrain with the **Placement** tool. A translucent green preview follows the cursor in placement mode; Esc cancels. New definitions become placeable after **Apply resources**. Shift selects multiple entities. |
| Move | Drag the thick X/Z axes for directional movement or the center square to move along terrain. Y is available when the selection contains no objects. The last selected entity anchors snapping; Esc or lost pointer capture cancels the drag. |
| Inspector | Edit coordinates, rotation, health and quantity, then **Apply**. Objects derive their height from terrain, as in the game. |
| Terrain | Choose raise, lower, flatten or smooth, then click terrain. Adjust radius and strength; strength applies to raise/lower. |
| Content | Browse items, objects, units, map information, buildings and recipes with actual icons. Use **Find** for model/icon paths, definition references, ingredients and results, or enter values directly. Clone definitions and import models, images or audio. |
| Scripts | Edit definition, entity-event or global-map scripts, or bind `.s2s` files. Multiple file tabs, text search, event lists, command completion and hover help are available. |
| Resources | Saved definition, model, translation and font changes display **Resources pending**. **Apply resources** applies them together and restores camera, selection and tool. Until then, the scene uses the last applied appearance. |
| Test | **Save and test** saves all project drafts in one transaction, validates them and applies changed resources. **Test saved version** excludes drafts. Click the scene for pointer capture, Esc releases it, and **Stop** restores the editor map and camera. Gameplay changes are never written to the project. |
| Diagnostics | Check content references, script delimiters/strings and file references. Click a location to reach the map, entity, definition, property or script line. Runtime events/errors and global/selected-entity variables are also displayed. |

Use **References and script bindings** in the inspector or file editor to navigate to placed entities, recipe/building references, bound files and their targets. Only statically identifiable references are listed; use text search for dynamically computed script references. Advanced editing preserves unknown properties and existing values.

Content icons fit a 64px tile and expand to at most 160px in the inspector, preserving their aspect ratio. Most originals are 40×40; enlargement does not add detail. Buildings show their completed result, and recipes show the first output item with `+N` for additional outputs. Map information uses the game's icon frames. Missing or unreadable images display a fallback symbol and a reason.

Icons work without a map or the 3D engine. Imported project images take precedence, and icons refresh when saved definitions or assets change. Placement, movement, terrain edits and their undo/redo retain existing thumbnails. Unapplied drafts are excluded; icon updates are independent of the scene's **Apply resources** action.

The placement preview displays the currently applied item, object or unit model in green at 40% opacity. Buildings use their completed result model; map information uses a position marker. Preview and click placement share the same snapping, terrain height and alignment rules. The temporary model is never saved in the map and hides outside terrain or while looking with the right mouse button.

Both test actions freeze the saved files and project content hash at startup. If files change again during preparation, startup is rejected; retry the test. Unchanged resources reuse the current engine; changed definitions, script files, models, translations or fonts automatically reconnect it with the new resources. `on:preload`, `on:start` and `on:load` retain their existing order. Code saved through an external editor during play takes effect on the next test.

Use **Cancel start** during the transition. **Stop** reloads the saved map and preserves the editor camera, selection, tool and drafts. There is no separate player engine or background preparation.

Placement, properties, deletion, terrain, undo, redo and CLI edits update the scene through deltas without reloading the map. Hierarchy scroll, camera, selection and tool are retained. Dragging displays a transient position; release saves once, producing one undo entry. Multiple selections share a movement delta; the center handle preserves each entity’s height offset above terrain. External changes wait during a drag. A revision conflict cancels the transient move and reconciles the latest saved state.

In v2 and v3, placement, movement, deletion and terrain edits update the map in memory and send scene deltas. Rapid inputs are processed in order without rereading shared resources or validating the whole project on every edit. A recovery record is written under `.studio/` before acknowledgment; authoring-file writes are grouped at 350ms intervals. Undo retains individual edit boundaries. The top bar shows **Saving… / Saved / Save failed**. **Save all** or Ctrl/Cmd+S in the scene publishes files immediately. Switching projects, normal shutdown, testing and export also finish pending saves. Save failures or external-file conflicts preserve recovery records and display an error.

Unapplied property and script drafts are stored separately by the app and survive selection changes and restarts. **Drafts N** in the top bar includes closed files and other targets. **Save all** saves them in one transaction, which can be undone together.

Properties are compared with their values when editing began, preserving changes to other fields. Changes to the same field, removed targets, changed script bindings or external file conflicts stop the entire save/test and show a comparison. Copy anything needed, use **Discard draft** to reload current values, then edit again. Older property/inline drafts without a comparison baseline are also preserved and must be edited again. Syntax errors can be saved, but block testing in the Diagnostics tab.

Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z redoes, Ctrl/Cmd+D duplicates entities and Delete removes them. The script editor prioritizes its own text undo. Entity duplication copies placement properties and event scripts, but not attached states or stored items. Deletion rejects entities with attached states, stored items or scripts. Applying an empty script detaches it.

## Script files and templates

Existing projects open unchanged. Select a target and use **Extract to file** to save its current script in `scripts/<name>.s2s` and bind it. **Bind to selection** reuses a file; **Selection → Unbind** copies the current text into a private script and leaves the shared file in place. In v2 and v3, private scripts also live in `.s2s` files under `scripts/owned/`; definitions and entities only reference their paths. Each script has one source shared by Studio, external editors and the CLI. v1 retains its existing inline storage for unbound scripts.

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

CLI `script.write` requires `path`, `text` and `expectedHash`. Read the hash from `scripts --path`; use `null` for a new file. The `target` for `script.bind`/`script.extract`/`script.unbind` uses `scope: map|entity|definition` and the corresponding `map`, `kind` and `id`. Existing `map.script`, `entity.script` and `content.set` operations on bound targets also require `expectedHash`. The `scriptsVersion` in `revision` detects external file edits. JSON `preview` rechecks `expectRevision`, `expectScriptsVersion` and `expectProjectHash` during preparation. File writes, bindings and templates participate in undo/redo, which rejects conflicts with external edits.

## Agents and CLI

The CLI works without a GUI and returns JSON on stdout. Errors and failed validation exit with code 1. `serve` implements JSON Lines with echoed request IDs; the GUI uses the same protocol. It keeps v2/v3 projects in memory and groups file saves for map edits. Single-command CLI edits finish writing files before exiting. `flush PROJECT` publishes remaining recovery records; the JSON request `{ "command": "flush" }` finishes saves for all open projects.

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

The `projectHash` in `inspect`/`revision` distinguishes Git checkouts with the same revision. In v2 and v3 it covers all authoring files and shared resource contents, excluding machine-specific paths. Replacing an asset at the same path is also detected. Pass `--expect-project-hash HASH` to `apply`, `undo`, `redo` or `preview` to check it. The GUI also detects external changes using this hash without overwriting drafts. `draft.apply` takes `target`, `section`, the original `base` and edited `changes`. Inline scripts use `text`; files use `scope: file`, `path`, the original file `hash` and new `text`. Conflict responses return the target and comparison values in `error.details`.

```sh
python scripts/studio.py connect PROJECT --base build/studio/runtime
python scripts/studio.py references PROJECT --target '{"scope":"definition","kind":"item","id":1}'
python scripts/studio.py assets PROJECT
```

`entities.move` accepts `selection: [{"kind":"unit","id":100}, ...]`, a world-space `delta: {"x":64,"y":0,"z":0}`, and optional `terrain: true`. Vertical movement is rejected when the group includes objects.

`preview PROJECT --map NAME` returns full files and a `token`. Passing `--since TOKEN` then returns a binary `patch` between that token’s scene and the current edit state, the latest native map, and pending-resource status. Scene deltas in `serve` do not wait for pending file saves. Full preview, testing, explicit validation and export save first and run full checks. Advance the baseline token only after the engine acknowledges application. Deltas exclude definitions, assets and language files; scenes requiring new definitions report `resourcesRequired`. Expired/damaged tokens and engine application failures never trigger an automatic reload; use **Apply resources** to reconnect explicitly.

Definition IDs and map instance IDs are separate, and existing numeric IDs are preserved. Cloning a definition allocates a free ID and rejects exhausted limits. New v2/v3 recipes use stable identifiers independent of order; imported recipes retain their existing `path#index` identifiers. Building names come from their resulting object/unit; recipe names use `genname` or their resulting items. Use `content.create` for new definitions/recipes, string arrays for repeated properties, and `null` to remove a property. Terrain coordinates are samples; world coordinates are `(sample-size/2)*64`.

## Projects and exports

v3 stores each map's current state as JSON. Edit history is used only for local recovery and undo. Shared resources remain external; the project contains added or modified content.

| Path | Contents |
| --- | --- |
| `project.json` | A small manifest containing only format, edition, name and shared resource content hash. Ordinary content edits leave it unchanged. |
| `definitions/<kind>/<ID>.inf`, `*.meta.json` | Per-definition native properties plus source file, order and script reference. Repeated properties, comments and unknown properties are preserved. Recipe filenames hash their stable identifiers. |
| `maps/<name>/map.json` | Map settings and an `entities` array containing current entity properties, order and script references. |
| `maps/<name>/terrain.bin`, `native/*.bin` | Terrain and unsplit native data, states and attachments. Automatic binary merging is not supported. |
| `scripts/**/*.s2s` | The single source for private and shared scripts. |
| `locales/<language>.tsv`, `resources/` | Project translations and actual added/replaced resources such as images, models and audio. |
| `.studio/` | Local connection, pending-save recovery records, last 100 undo entries, migration backups, locks and caches. Excluded from Git. |

Track authoring files, `.gitignore` and `.gitattributes` in Git. Editing an entity's coordinates changes its map's `map.json`; editing definition properties changes that definition's INF. Editing only the text of an existing script changes only its `.s2s` file. Creation preserves existing Git configuration and adds `.studio/`/`export/` exclusions, LF text rules and binary attributes. Use **Save all** before Git operations to publish authoring files. Use external Git tools for commits, branches and remote synchronization.

Interrupted file transactions recover on the next open without overwriting new external edits. External edits/checkouts invalidate local undo history in v2 and v3; `revision` is a local compatibility counter. Missing scripts produce diagnostics and block editing until restored. On another computer, reconnect only the local shared resources.

Existing v1 and v2 projects remain readable and writable. v2 retains per-entity `entities/<kind>-<ID>.json` files and gains memory editing and grouped saves without conversion. To use per-map JSON, open **Convert map storage** in the top bar, save or discard drafts, then run **Preflight**. Review changed files and native definition/map equivalence, then choose **Back up and convert** to switch to v3. Original `sources/` files and a restoration backup are retained. **Format and backups** lets you select, check and restore a migration backup to its original format. Restoration is refused if authoring files, originals or the resource connection changed after conversion.

```sh
python scripts/studio.py migrate PROJECT
python scripts/studio.py migrate PROJECT --apply --expect-project-hash HASH
python scripts/studio.py migrations PROJECT
python scripts/studio.py restore-migration PROJECT --backup ID
python scripts/studio.py restore-migration PROJECT --backup ID --apply
```

`migrate` and `restore-migration` perform checks only by default. Take `HASH` from the preflight result's `projectHash` and `ID` from the backup list. Use `init --format-version 1` or `init --format-version 2` to create a compatibility project. For v1, track the existing `project.json`, `sources/` and `scripts/` in Git; without a local connection, the existing `project.json.base` is used.

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

Storage checks cover per-map and per-entity changes, byte preservation of native maps, definition/recipe identity, pinned shared resources, interrupted migration recovery, backup restoration and preservation of external edits. Existing editing, export, thumbnail and script contracts also run against v1, v2 and v3. Session checks cover successive edits, individual undo, retry after save failure, restart recovery, external conflicts and avoiding full resource checks during placement. UI checks cover placement request ordering and saving before shutdown. In the app, check creation, editing and testing in the new format, plus conversion and restoration of legacy formats.

Workspace checks cover existing Git-folder preservation, local reconnection after cloning, same-revision checkout detection, field-aware draft merging, atomic rejection of conflicts, undo, references and diagnostic targets. UI unit checks cover existing draft keys, changes during saving, rapid script input and external value updates. In the app, check project reopening, draft restoration, search, reference navigation, save-and-test and restoration after stopping.

Icon checks cover color keys, frames and output-image selection, asset precedence, refresh after resource edits and cache retention during map edits, path restrictions, read-only behavior, request caching/cancellation and IPC responses. Placement checks cover display scaling, latest-pointer delivery, model/snap capture, preview cancellation and rejection of unapplied definitions. Actual translucent model display still requires a separate check in the app.

Tests cover data preservation, transactions, deltas/ACKs, pending resources, movement math, cancellation/conflicts, IPC, engine reuse, the latest saved map, stale requests, canceled starts, and map/camera restoration without launching a GUI. `engine check` verifies actual Blitz build artifacts, Extended catalogs, allowlisted resources and audio conversion. After changing the shared map loader, build/check both editions and run `python scripts/verify_game.py` and `python scripts/verify_game.py --edition extended` to check existing save/load behavior. View switching, selection, dragging, mouse capture, Alt-Tab, scaling, sound and Korean rendering still require separate checks in the app. The diagnostic log's time from start request to first game frame is not an FPS measurement.
