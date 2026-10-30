<!-- dev-docs-ai-translation: {"document":"docs/proposals/feature-ideas.md","language":"en","syncedHash":"f2cdc50a322df02796d11d945cfdb52a5423f2c8ae151dbd943464d3f78d9c0a"} -->

# Feature ideas and priorities

[English](feature-ideas.md) · [한국어](feature-ideas-ko.md)

[Back to README](../../README.md) · [Development guide](../development.md) · [QOL options](../qol.md) · [Extended development ideas](../extended/plan.md)

This is a proposal, not a list of implemented or committed features. Each idea cites the current sources it builds on (`main` at `3a9dfc7`). Nothing here was built or run; the checks each idea needs are listed with it.

## How to read this

| Field | Meaning |
| --- | --- |
| Effort | **S** about a week or less · **M** 1–3 weeks · **L** a month or more, for one developer including verification |
| Priority | **P1** recommended for next quarter · **P2** next candidates · **P3** backlog |
| Edition | Presentation, input, data-safety and tooling ideas are shared. Anything that changes the experience is an option whose Stranded II default keeps the original behaviour. Rule and content changes belong to Extended only. |

## Not proposed again

These already exist. The ideas below build on them instead of repeating them:

- Five QOL options (recipe book, quick storage transfers, closing emptied corpses, sorting, held construction) and the F2 FPS display — [QOL options](../qol.md).
- Developer menu with cheats, settings preview, logs, CPU/GPU profiler and performance test scene; native and web benchmarks — [development guide](../development.md).
- Stranded II Studio (3D editing, scripts, templates, validation, export) — [Studio](../studio.md).
- Web (WebGPU) and Apple Silicon macOS builds; language packs with bundled EN/KO/DE and user-added languages and fonts.
- Graphics enhancements (water, lighting, shadows, atmosphere, FXAA/MSAA), uncapped FPS, borderless and windowed modes.
- Quick save/load fixes, five rotating autosave slots, Continue, `.bak` save backups.
- F1 screenshots without the HUD.

Ideas already in the developer backlog appear under [Existing backlog](#existing-backlog) with implementation notes only.

## 1. Player data safety

### 1.1 Web save export/import and persistent storage

- **What:** Add **Export saves** and **Import saves** buttons to the web page. Export packs `/persist/classic/saves` and the persisted settings into one uncompressed ZIP; import restores them and reloads the page. Request `navigator.storage.persist()` once after storage is ready.
- **Why:** Web saves exist only in one browser profile's IndexedDB at one address. Clearing site data deletes them, and they cannot move to another browser, the local development address, Windows or macOS ([web architecture](../web-architecture.md)). `src/web/shell.js` has no export path and never requests persistent storage.
- **Where:** `src/web/shell.js` (`prepareStorage`, the serialized `sync()` queue, the Web Locks guard) and `src/web/index.html`. Import checks each `.sav` with the same header and end-marker preflight as `modern_save_valid` (`src/engine/includes/modern_saves.bb`), writes to staging names, keeps the replaced file as `.bak`, and reports success only after `FS.syncfs` completes.
- **Edition:** base game, the only web edition.
- **Effort / risk:** S–M, low risk. Cap file sizes, reject unknown paths, and confirm before replacing newer saves. Start with web-to-web transfers; importing desktop saves needs a separate binary-compatibility check.
- **Verify:** extend the storage transactions in `check --platform web`; in Chrome and Edge, export → clear site data → import → load.
- **Priority:** P1.

### 1.2 Safe updates for the Windows ZIP

- **What:** (a) Ship defaults under separate names, such as `core/modern.default.ini` and `sys/controls.default.cfg`, and create the player's files from them only when missing. Extracting a new release over an old folder then keeps the player's choices. (b) Add **Import from another folder** to copy saves, key bindings, `sys/settings.cfg` and known `core/modern.ini` keys from an earlier installation. (c) Add **Open saves folder**.
- **Why:** Windows saves and settings live inside the extracted folder. Packaging leaves out saves, `settings.cfg` and the runtime's `modern.ini`, but adds the repository's default `core/modern.ini` and the official `sys/controls.cfg` and `sys/scriptcontrols.cfg` (`player_data` and `release_members` in `src/devtools/ci.py`; `config/game-files.json`). Extracting over an old folder therefore resets FPS, display, language, QOL, graphics enhancements and key bindings, while extracting into a new folder starts without saves. The macOS app already keeps its data in Application Support.
- **Where:** `src/devtools/ci.py`, `modern_config` (`modern_runtime.bb`) and `load_settings` (`load_settings.bb`) for the default layer; `platform.windows` services for a folder picker and opening Explorer; `modern_file_copy` and `modern_commit_file` (`modern_files.bb`) for staged copies; the settings menu in `menu.bb`.
- **Edition:** shared; Windows first.
- **Effort / risk:** (a) S, (b) and (c) M. A missing `controls.cfg` currently leaves every key binding at zero, so the default layer must always produce a valid file. `mod_setup` (`mods.bb`) copies `sys/controls.cfg` into new mods and needs the same fallback. Never overwrite existing saves without a backup.
- **Verify:** package two versions, extract the second over the first and into a new folder, then check settings, bindings and saves in the game; run `check` for both editions.
- **Priority:** P1.

### 1.3 Optional timed autosave

- **What:** A setting that autosaves every 10, 20 or 30 minutes of play through the existing autosave queue and five rotating slots.
- **Why:** The engine autosaves only when a script calls `autosave`: `parser_commands.bb` queues mode 3, and `modern_process_saves` rotates `AUTOSAVE*.sav`. Maps whose scripts never call it depend on manual saves, so a crash loses everything since the last save. Which official maps call `autosave` still needs to be confirmed against the prepared resources.
- **Where:** a game-time timer in the game loop (`modern_runtime.bb`). Queue with `modern_queue_save(3)` only when `game_m_loadsave=1`, no menu or sequence is active and the player is alive. The QOL arrays in `qol.bb` are fixed at five entries, so adding the option means extending the arrays, the Settings → QOL layout and `verify_qol_logic.py`.
- **Edition:** shared; OFF by default in Stranded II, following the QOL default policy in Extended.
- **Effort / risk:** S–M. Saving large maps can cause a hitch; never save during scripted sequences.
- **Verify:** `verify_qol_logic.py` and `verify_game.py` for both editions; in the game, check that rotation and Continue choose the newest slot.
- **Priority:** P1.

## 2. Diagnostics

### 2.1 Local error reports and crash capture

- **What:** When the game stops on a Blitz runtime error or a native crash, write `core/reports/<time>.txt` with the version and build information, edition, platform, GPU adapter, current settings and recent log lines. On the next launch, the main menu offers to open the folder or copy a summary for a GitHub issue. Nothing is uploaded automatically.
- **Why:** On Windows, `stub.windows.cpp` runs the game without an attached debugger. `_bbDebugError` (`blitz/debug.cpp`) then only reaches `bbRuntimeLog`, which writes to stdout/stderr and the in-memory developer-menu log, and the process exits with code 1. This path writes no file and shows no dialog, and the stub installs no exception handler. The developer-menu log keeps 2,048 entries (`debugui.cpp`) but disappears with the process; `diagnostics=1` covers timing and movement only.
- **Where:** `src/blitz3d/src/modules/bb/stub/stub.windows.cpp` (Blitz errors, `SetUnhandledExceptionFilter`, optional `MiniDumpWriteDump`), `stub.posix.cpp` (macOS; it already has a `BB_BACKTRACE` dump), `stub.web.cpp` and `src/web/shell.js` (show and copy the report on the page). The report reads the log ring buffer in `debugui/debugui.cpp`; adapter details need a `wgpuAdapterGetInfo` query in `graphics.webgpu.cpp`, which no code calls yet. The next-launch notice goes in `menu.bb`.
- **Edition:** shared, on every platform.
- **Effort / risk:** M. Handlers must still work after memory corruption: preallocate buffers and avoid allocation in the crash path. Remove user names from paths. Never touch saves.
- **Verify:** a test-only `RuntimeError` and a forced access violation in a separate runtime copy on Windows and macOS; check the report contents and the next-launch notice.
- **Priority:** P1.

## 3. Capture and sharing

### 3.1 Better screenshots, then a photo mode

- **What:** Stage 1 saves PNG files with timestamped names under `screenshots/`, downloads the file in the web build and adds an optional capture that includes the HUD. Stage 2 adds a photo mode that pauses the game, hides the HUD and allows a limited free camera with FOV and time-of-day controls.
- **Why:** F1 (`game.bb`) calls `screenshot()` (`functions.bb`) right after `RenderWorld`, so captures are already HUD-free. However, `saveCanvas` (`graphics/graphics.cpp`) writes uncompressed 24-bit BMP files, about 7 MB at 2048×1152, into the mod folder. In the web build that folder is in the in-memory filesystem outside `/persist`, so the file cannot be retrieved.
- **Where:** `graphics.cpp` (a PNG encoder, for example `stb_image_write` next to the existing `stb_image.h`), `functions.bb`, and `src/web/shell.js` (Blob download). `player_data` in `ci.py` already excludes `screen_*`; add the new folder. Photo mode can reuse the developer menu's pause and position tools.
- **Edition:** shared. Photo mode only pauses and must not change game state.
- **Effort / risk:** stage 1 S, stage 2 M. The readback stalls one frame, which is acceptable for a capture.
- **Verify:** captures with and without the HUD on Windows, macOS and the web; packaging still excludes the folder.
- **Priority:** P1 for stage 1, P2 for stage 2.

## 4. Display and accessibility

### 4.1 UI scale on large displays

- **What:** Draw the 2D UI and HUD on a virtual canvas scaled independently of the 3D render resolution.
- **Why:** Windows are drawn at fixed coordinates for the original 800×600 layout (`DrawImage gfx_win,215,0` in `interface.bb` and `menu.bb`). With a 1920×1080 render, menus occupy the top-left corner and text is small. Today the only workaround is a lower render resolution, which also blurs the 3D scene.
- **Where:** the 2D canvas and presentation in `graphics.webgpu`, the existing mouse mapping for scaled borderless presentation, and `modern_settings.bb` for the option. Centering the 800×600 windows is a smaller layout step that belongs to UX work.
- **Edition:** shared; default 100%.
- **Effort / risk:** L, high risk. Back-buffer `CopyRect` (water and script effects), screenshots, 2D drawn between 3D passes and bitmap-font sharpness at non-integer scales all need care.
- **Verify:** `verify_engine.py --render` pixel checks, then menus, inventory, diary, the QOL search EDIT position and IME in the game at several scales and DPI settings.
- **Priority:** P2.

### 4.2 Field-of-view setting

- **What:** An FOV slider whose default matches the current view.
- **Why:** `src/engine` never calls `CameraZoom`, so every display uses Blitz's default zoom of 1.0. Players prone to motion sickness and ultrawide users often need a different FOV.
- **Where:** the main camera `cam` (`load_setup.bb`), the water reflection camera `env_watercam` (`e_environment.bb`), and a `modern.ini` key handled like the `render_*` options (`remaster.bb`).
- **Edition:** shared; the default is unchanged.
- **Effort / risk:** S. Wider views draw more objects because culling uses `EntityInView` (`cull.bb`), the held weapon may need adjusted framing, and reflections must use the same zoom.
- **Verify:** `verify_engine.py --render`; in the game, check weapons, water reflections and performance at the extremes.
- **Priority:** P2.

### 4.3 Readability and colour options

- **What:** An alternative palette for the red and green message styles, and a "reduce screen effects" option that weakens the blur from states and scripts (dizziness, intoxication, death).
- **Why:** Failure and success messages rely on the red (`Cbmpf_red`) and green (`Cbmpf_green`) font styles. `fonts.py` samples their ink from the original `font_norm_bad.bmp` and `font_norm_good.bmp` when generating the seven style pages for each language. Continuous motion blur was removed, but states and scripts still drive blur through `mb_override` (`handle_states.bb`, `motionblur.bb`).
- **Captions:** a full subtitle system is not needed. Player voice lines (`speech()` in `speech.bb`) are short reactions, and most are already paired with an on-screen message (for example `nospace` and `buildingsite`). A few `negative` reactions in `handle_items.bb` are sound-only, and `speech` calls from map scripts (`parser_commands.bb`) can be; a short caption for those is enough.
- **Where:** `src/devtools/fonts.py` (extra style pages), `load_bmpf.bb` (style selection), the blur cap in `mb_update` (`motionblur.bb`), `speech.bb` for captions, and the settings menu.
- **Edition:** shared; the default stays original.
- **Effort / risk:** M. Extra pages enlarge language packs; check every language preview.
- **Verify:** `localize` and `check` for both editions; in the game, compare messages and effects with the option on and off.
- **Priority:** P3.

## 5. Input

### 5.1 Gamepad support

- **What:** Stage 1 opens SDL game controllers with hotplug support and maps sticks and buttons to existing actions (movement keys, mouse look, attack/use, hotbar, menu/Esc) with dead zones and frame-time scaling. Stage 2 adds a virtual cursor for inventory, crafting and menus, plus button prompts. A community Steam Input layout for Steam Deck players who add the EXE as a non-Steam game can ship first as documentation.
- **Why:** The shared `runtime.sdl` used by Windows, macOS and the web opens raw `SDL_Joystick` devices once in `afterCreate`, with no game-controller mapping or hotplug handling. The game itself uses joysticks only to skip the logo (`GetJoy` in `unrealsoftware.bb`).
- **Where:** `src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp`, `input/commands.decls` (new controller queries), `input.bb` and `game_input.bb` (action mapping against `in_keys`).
- **Edition:** shared; inactive until a controller is used.
- **Effort / risk:** L, or M for stage 1 alone. The UI is mouse-centric (drag and drop, the recipe search EDIT, the map editor); keep the editor mouse-only.
- **Verify:** extend `verify_engine.py --input` with simulated controller events; check Xbox and PlayStation controllers in the game on Windows and macOS, and the browser Gamepad API on the web.
- **Priority:** P2.

## 6. Mods and Studio

### 6.1 In-game mod selector

- **What:** List folders under `mods/` that contain `sys/game.inf`, let the player choose one in Settings, store the choice as `mod=` in `core/modern.ini`, and apply it after restarting, like the language setting.
- **Why:** Mods can only be selected with the `-mod <name>` command-line argument (`load_commandline.bb`). `mods.bb` already scaffolds new mod folders and records them in `core/mods.inf`, and saves and `sys/*.cfg` are already separate for each mod.
- **Where:** `load_commandline.bb` (read the stored choice before `ChangeDir`), `mods.bb`, and the settings menu. macOS needs a defined user mods location next to its Application Support profile; the web build is out of scope.
- **Edition:** shared.
- **Effort / risk:** M. Language packs map base-game text, so mod-specific text falls back to the mod's own strings. A broken mod must still allow returning to the base game, falling back as `parse_commandline` does today.
- **Verify:** select, restart and return with a valid mod, a missing mod and a broken mod; check that saves and settings stay separate.
- **Priority:** P2.

### 6.2 Studio export as an installable mod package

- **What:** A Studio export target that writes only changed files and a manifest (target edition, base hashes, translations), plus a game or launcher step that applies it to a copy of the base mod folder.
- **Why:** Studio exports (`overrides/`, `locales/`, `manifest.json`) are currently merged into `resources/editions/extended/` and built from the repository ([Studio](../studio.md)), so players cannot try content without a development setup. Shipping only changed files avoids redistributing the original game assets.
- **Where:** the export in `src/devtools/studio_core` and mod setup in `mods.bb`. It depends on 6.1.
- **Edition:** content is authored against Extended; record the target edition and warn on a mismatch.
- **Effort / risk:** M. Conflicting packages and base-version mismatches need explicit errors.
- **Verify:** export the tonic example, install it into a copy of a release folder, then play and remove it.
- **Priority:** P3.

### 6.3 Random island seeds

- **What:** Show the seed after generating a random island, and allow entering a seed to recreate it.
- **Why:** `load_setup.bb` seeds the generator once with `SeedRnd(MilliSecs())`. The random island menu (`menu.bb`, `randommap.bb`) offers size, terrain and profile but no seed. Seeds let players share islands and make bug reports reproducible; the benchmark already relies on seeded generation (`dev_benchmark.bb`).
- **Where:** `randommapcreate()` and `load_randommap()` in `randommap.bb`, and the random island menu. Seed only the generation, then reseed from the clock so gameplay randomness stays unpredictable.
- **Edition:** shared; no rule changes.
- **Effort / risk:** S–M. Generation must consume random numbers in a fixed order, and floating-point differences between x64, ARM64 and wasm may produce different islands from the same seed.
- **Verify:** hash maps generated from repeated seeds on each platform.
- **Priority:** P2.

## 7. Settings and updates

### 7.1 Graphics presets and a first-run suggestion

- **What:** Low/Medium/High/Ultra presets that set the existing `sys/settings.cfg` fields and `render_*` keys, plus a suggestion on the first launch (no `settings.cfg`) from a short timed sample of the menu scene.
- **Why:** Fresh installs start at the highest quality (`load_settings.bb`: view range 4, water 6, grass 4, and so on). This suits fast desktops, but integrated GPUs and browsers can start slowly with no single place to lower everything.
- **Where:** the graphics settings in `menu.bb` and `remaster.bb`; sampling can reuse the performance-test code in `dev_benchmark.bb`. The WebGPU adapter request in `graphics.webgpu.cpp` exposes no adapter information to Blitz yet.
- **Edition:** shared. Presets apply only when the player selects one or accepts the suggestion; saved settings are never changed silently.
- **Effort / risk:** M. Short samples measure throughput, not displayed FPS, so keep the suggestion conservative.
- **Verify:** first launch without `settings.cfg`, and existing settings preserved on later launches; compare presets with `benchmark_native.py` and `profile_web.py`.
- **Priority:** P2.

### 7.2 Opt-in update notice

- **What:** After the player opts in, check GitHub Releases at most once a day and show "Version X is available" with a button that opens the release page. Nothing is downloaded or replaced automatically.
- **Why:** The main menu shows the build label (`modern_version.bb`, `core/version.txt`), but players hear about fixes only if they visit GitHub. The web build is always current and does not need this.
- **Where:** asynchronous HTTPS in platform services (WinHTTP on Windows, `NSURLSession` on macOS), since the Blitz `sockets` module has no TLS. The channel (stable or `dev`) comes from the build metadata in `build-info.json`.
- **Edition:** shared, desktop builds only.
- **Effort / risk:** M. Network failures must stay silent; send no identifiers.
- **Verify:** offline, rate-limited, newer, same and older versions, and both channels.
- **Priority:** P2; pairs with 1.2.

## 8. Onboarding and goals

### 8.1 Optional beginner hints

- **What:** Short contextual hints the first time hunger, thirst or fatigue becomes low, at the first nightfall, and when crafting or building first becomes possible.
- **Why:** The adventure maps carry their own scripted guidance, but the engine has no hint system for random islands and custom maps.
- **Where:** the existing message display (`if_msg` in `interface.bb`) and state checks in the game loop; seen flags in a per-edition profile file rather than in saves; text in `resources/locales/*.tsv` (EN/KO/DE).
- **Edition:** shared; OFF by default in Stranded II. Suppress hints on maps that already provide a tutorial.
- **Effort / risk:** M, mostly writing and tuning text.
- **Verify:** each trigger fires once per profile in all three languages, and never during the adventure tutorial.
- **Priority:** P3.

### 8.2 Local records and achievements (Extended)

- **What:** Per-edition records (days survived, buildings completed, animals tamed) and achievements on a menu page.
- **Why:** They give long-term goals on random islands, matching the README's "improved random islands" direction, without Steam.
- **Where:** hooks in `game_changeday.bb`, `game_build.bb` and `handle_units.bb`; storage in a separate file under `core/`.
- **Edition:** Extended first.
- **Effort / risk:** M. Mark runs that used developer-menu cheats.
- **Verify:** unlock conditions after save/load, and Stranded II unaffected.
- **Priority:** P3.

## Not recommended now

| Idea | Reason |
| --- | --- |
| Steam Workshop, Steam achievements, Steam Cloud | The project is not distributed on Steam, and distribution rights for the original game would need clarification. The vendored `steamworks` module builds only when a Steamworks SDK target exists and is not in the `s2windows` runtime module list. |
| Hosted cloud saves | Requires accounts and servers. Export/import (1.1) and folder import (1.2) cover moving saves. |
| Automatic crash upload or telemetry | Privacy and hosting cost. Keep reports local and let players share them (2.1). |
| Self-replacing auto-updater | Replacing a running EXE and its signature adds risk, and macOS already ships notarized DMGs. Use a notice only (7.2). |

## Existing backlog

These are already planned in [`docs/check-list`](../check-list) and [Extended development ideas](../extended/plan.md). Only implementation notes follow:

- **Multiplayer (user-hosted server, IP join):** `mp_get_client.bb` and `mp_get_server.bb` are empty, `udp*.bb` holds the original UDP library, and the web build cannot open raw TCP/UDP connections. It needs a state-synchronization design (XL); start with a design spike after the data-safety work.
- **Minimap and full map (M):** the editor already builds a top-down colour image (`editor_gencolormap` and `editor_textobuffer` in `editor_functions.bb`) that could seed the map texture. As the memo says, ship both as QOL toggles.
- **Extended:** food spoilage, terrain editing during play, housing, and a large world.

## Recommended top 5 for next quarter

| Rank | Idea | Player impact | Effort | Why now |
| --- | --- | --- | --- | --- |
| 1 | 1.1 Web save export/import and persistent storage | Web players can back up and move saves | S–M | The public web build has no recovery path; the change stays in the web shell |
| 2 | 2.1 Local error reports and crash capture | Silent exits become actionable bug reports | M | Every later feature benefits; no gameplay risk |
| 3 | 1.2 Safe updates for the Windows ZIP | Saves, settings and bindings survive new releases | S (a) / M (b, c) | Each release currently resets choices or strands saves |
| 4 | 1.3 Optional timed autosave | Limits progress lost to crashes | S–M | Reuses the existing queue and five slots |
| 5 | 3.1 Better screenshots, stage 1 | Shareable PNG files on every platform | S | F1 on the web currently produces no retrievable file |

Quick wins after these are 4.2 FOV (S) and 6.3 seeds (S–M). The larger bets, 4.1 UI scale and 5.1 gamepad support, are L each; plan at most one per quarter.
