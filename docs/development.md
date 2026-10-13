<!-- dev-docs-ai-translation: {"document":"docs/development.md","language":"en","syncedHash":"d0d0e18f4163100eb7e970bd45909db493de2db32f87545fd8ed6b9f5e3d9d1f"} -->

# Development guide

[English](development.md) · [한국어](development-ko.md)

[Back to README](../README.md) · [Localization guide](localization.md) · [WebGPU renderer migration](wgpu-migration.md)

For a first build, follow **environment setup → first build**. Once set up, use [running after changes](#running-after-changes) to choose the command for your edit. To contribute translations only, start with the [localization guide](localization.md).

## Setting up the development environment

You need Windows 10/11 x64 with a Direct3D 12 GPU, Python 3.12 or later, and [requirements.txt](../requirements.txt). Use an ASCII workspace path such as `C:\Stranded II`; spaces are allowed.

Install Visual Studio’s MSVC x64 C++ build tools, Windows SDK, CMake/Ninja tools and DIA SDK. `build` compiles the editable Blitz3D engine in `src/blitz3d/` with LLVM and links the game as a native x64 executable. Rendering uses Dawn/D3D12 and audio uses OpenAL Soft. Unicode input, IME and Windows services are linked into the runtime; `QolInput.dll` is no longer required. Runtime DLLs and their licenses are copied beside each edition’s EXE.

Native runtimes use the speed-optimized `Release` configuration (`/O2` on MSVC), retaining strict floating-point behavior. WebGPU combines mesh edits until drawing and batches camera/presentation commands while preserving buffer-update and draw order. Copies sharing a mesh and material use instanced draws with individual colours and fade values. Transparent copies are merged only when consecutive, preserving depth order. Mesh visibility reuses the camera's frustum planes, and transparent sorting computes each model's distance once per camera; reflection frequency, grass density and graphics settings are unchanged.

Dynamic mesh updates preserve GPU buffers used by earlier draws and upload new data into another version, reducing intermediate submissions. Versions are reused after submission, with extra buffer capacity capped at 32 MiB overall and 16 versions per mesh. Reaching either limit or using the wireframe index cache retains the existing submission path.

Terrain shading lazily caches normals per height-field grid point and reuses them across frames and reflection views. Height edits invalidate the changed point and its four neighbours, including wrapped edges; clearing a terrain invalidates its cache. The original normal calculation, LOD and morphing remain unchanged. A 64×64 terrain uses 52 KiB for this cache after its first shaded render.

Mesh visibility keeps the original test for the first view. Further views in the same scene preparation reuse world-space bounds, with the original oriented test retained at intersecting planes. Every scene preparation invalidates those bounds, including tweened captures and camera children. `verify_engine.py --render` compares visibility and capture pixels after transform, mesh and custom-bound changes; it also compares terrain normals and lit pixels after height edits.

Water reflections use `RenderWorldCube camera,texture[,tween]` to capture all six directions consecutively. With the default tween of `1` and no camera children, object lists and transforms are prepared once; otherwise, preparation runs for each direction. Each view retains visibility, distance fade and transparency sorting, and preparation is never reused between calls. Set the camera viewport to `(0,0,texture width,texture height)` before calling.

WebGPU cube textures currently lack per-face storage and sampling, so they use the last copied 2D image. Sharing preparation across six views preserves this existing output behavior; it does not complete cube-texture support.

These dependencies are not included in the repository; prepare them yourself:

| Dependency | Download | Location |
| --- | --- | --- |
| Stranded II Gold 1.0.0.1 English ZIP | [Official download](https://www.strandedonline.de/s2_download.php) | `deps/game/` |
| Noto Sans KR | [Pinned font version](https://raw.githubusercontent.com/google/fonts/4efc2774c63917927efe769ca845def6bd6debae/ofl/notosanskr/NotoSansKR%5Bwght%5D.ttf) | `deps/fonts/NotoSansKR[wght].ttf` |

Arrange the extracted files as follows. Use an unmodified English original for the game:

```text
deps/
  game/
    StrandedII.exe
    core/
    mods/
  fonts/
    NotoSansKR[wght].ttf
```

Game/font versions and hashes are in [config/dependencies.json](../config/dependencies.json) and [config/game-files.json](../config/game-files.json). `setup` downloads the SDKs and libraries pinned in [config/blitz3d-toolchain.json](../config/blitz3d-toolchain.json) to `deps/blitz3d/`. Blitz3D 1.118 is an optional reference dependency, not the production compiler. Engine origin and integrated changes are recorded in [src/blitz3d/upstream.json](../src/blitz3d/upstream.json).

## First build

Run these commands in PowerShell from the repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --game deps/game --font 'deps/fonts/NotoSansKR[wght].ttf'
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check
.\Play.cmd
```

`setup` prepares original resources, UTF-8 references, fonts and external engine dependencies. It never downloads over or patches `src/blitz3d/`. Compiler, runtime libraries and generated CMake headers stay under `build/engine/<target>/`; game outputs stay in each edition’s own build directory.

Use `--game` and `--font` for external resource locations. `setup` records them in the ignored `build/dependencies.local.json`, so later runs can omit them. The deprecated `--blitz` argument is accepted but does not select a compiler.

## Running after changes

Use the following commands from the repository root. Close the development game before refreshing resources or replacing its executable. **Commands default to Stranded II**; add `--edition extended` for Extended.

| Change | Command |
| --- | --- |
| Edit `src/engine/**/*.bb` or `src/blitz3d/` | `.\dev.ps1 build -Python .\.venv\Scripts\python.exe` |
| Edit `locales/` translations, languages, or fonts | `.\dev.ps1 localize -Python .\.venv\Scripts\python.exe` |
| Edit `resources/` | `.\dev.ps1 setup -Python .\.venv\Scripts\python.exe` |
| Automated checks | `.\dev.ps1 check -Python .\.venv\Scripts\python.exe` |
| Launch the game | `.\Play.cmd` |

The repository's `Play.cmd` and `Play-Extended.cmd` are development shortcuts to the built runtime folders. They are excluded from release ZIPs; players launch their edition's EXE directly.

For changes to both code and resources, use `setup → build → check → launch`. Translation, new-language, and font changes use `localize → check → launch` without recompiling the EXE. `setup` includes the same language-pack preparation. See the [localization guide](localization.md#choose-a-font) for font settings.

`setup` preserves existing runtime saves, `.cfg` files, `settings.inf`, and `core/modern.ini`. Changing `config/modern.ini` therefore does not automatically change an existing runtime's settings. Change personal settings in the game menu, or close the game and edit `core/modern.ini` in the runtime folder.

`build` compiles a temporary executable, then replaces `build/runtime/StrandedII.exe`. If replacement fails because the game is running, close the game and run `build` again.

You can also launch directly through Python:

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py run -- -win -fps 120
```

## Developer menu

Both editions share a Dear ImGui menu. Assign a shortcut under **Settings → Controls → Developer menu**. Distribution defaults to `None`; a fresh local development build uses `Insert`. Right-click to clear the binding. A saved `None` or custom shortcut is retained in development builds too.

Opening the menu keeps the game running and blocks game input. The Status tab's pause stays active after closing the menu. Tools include single-player cheats, an item icon grid, position/time changes, settings previews, and searchable/copyable logs. **Save** and **Revert to saved settings** are separate; changes are never saved automatically. Display mode, resolution, FPS limit and language require restarting after saving. Logs retain at most 2,048 entries / 2MiB; scrolling up stops following new messages.

Shared game UI lives in `src/engine/includes/devmenu.bb`, native ImGui/SDL/WebGPU integration in `src/blitz3d/src/modules/bb/debugui/`, and browser IME integration in `src/web/debugui.js`. Windows editions, macOS, Web and Studio use the same implementation. On Windows, `python scripts/verify_engine.py --graphics --input` checks input ownership and window/context lifetimes. The `s2_debugui_check` CMake target accepts a prepared `core/locales/<code>/ui.ttf` path and checks Unicode glyphs, sliders and log following without a window. Check actual display, IME composition/paste and each platform's execution separately.

## Native rendering benchmark

Both Windows editions expose Settings → Graphics enhancements. `render_antialias` selects off (`0`), low-cost FXAA (`1`, default), or sharper MSAA 4x (`4`). Saved selections are preserved; missing or invalid values use `1`. FXAA runs once after ordinary `RenderWorld`; MSAA renders the main canvas with four colour/depth samples and resolves before bitmap text and HUD. Both skip reflection/shadow captures; MSAA does not also apply FXAA and costs more GPU time and memory. Water, lighting and atmosphere default to ON (`render_water=1`, `render_lighting=1`, `render_atmosphere=1`); sun shadows default to OFF (`render_shadows=0`). These defaults also apply when keys are missing; existing saved selections take precedence. Applying changes updates the current scene only after settings save successfully. Water uses the existing reflection resolution and cadence; shadows increase rendering cost. The FPS selector offers Unlimited (`fps=0`, or `-fps 0`) and 60/120/144; display/FPS changes still require restart.

When lighting or shadows is enabled, sun light shares the original ambient budget to reduce excessive daytime brightness and transitions smoothly to night values at sunrise/sunset. The `check` suite's `verify_lighting.py` tests times, sky colours and option combinations through the actual Blitz function; `verify_engine.py --render` checks noon ground brightness and shadow contrast using actual D3D12 pixels.

On Windows, prepare the selected edition's runtime, then run the isolated benchmark. It generates and reloads seeded `.s2` stress maps with 0/256/768/1536 trees and rocks, and also tests the first adventure island. It uses the real game loop, native renderer and clock. Camera position and input are fixed; no GUI operation is needed. Existing games, saves and settings are preserved.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/benchmark_native.py --run
.\.venv\Scripts\python.exe -X utf8 scripts/benchmark_native.py --existing build/benchmark-<name> --run --suite resolution --width 1280 --height 720 --label lowres
.\.venv\Scripts\python.exe -X utf8 scripts/benchmark_native.py --existing build/benchmark-<name> --rebuild --run --cases baseline,island,objects_1536 --label optimized
.\.venv\Scripts\python.exe -X utf8 scripts/benchmark_native.py --run --cases baseline,island --width 1920 --height 1080 --render water,lighting,shadows,atmosphere --label remaster
```

The default is 2048×1152, three repeats per case, a 3-second warmup and 8-second measurement. Each trial starts a fresh process. It compares water, grass, view range, terrain, wind, object density and sky views. `--suite smoke` selects five controls; `--cases baseline,water_3,grass_0` selects individual cases. Use a new `--label` to retain earlier results. `--edition extended` uses only that edition's resources and settings; include it when reusing its folder.

Trials are in `build/benchmark-<name>/results.json`; `summary.json` contains each condition's median and range, with completed repeat counts. Source hashes, plans, logs, per-frame CSVs, screenshots and generated maps are stored alongside them. GPU readbacks drain work before and after each measurement. FPS includes the final drain; phase timings and p95/p99 frame intervals are host elapsed times, not GPU timestamps. Reflection time is included in water time. The `sync_control` case adds a costly readback every frame and is only a completion control.

Only the test copy continues rendering without focus; minimize it and the trial is rejected. Background results measure completed rendering throughput, not foreground presentation or display scanout FPS. Compare options under the same conditions and inspect captures; this does not verify input response, camera movement or full gameplay.

`--render` enables only the listed water/lighting/shadow/atmosphere enhancements in the disposable runtime. Anti-aliasing is independent and defaults to FXAA (`--antialias on`); choose `--antialias off` or `--antialias msaa4x` for comparisons. The plan and measured metadata record both choices. Compare matching scene, resolution and graphics settings when evaluating a 144 FPS target.

## Experimental WebGPU build

All Python development commands accept `--platform windows|web|macos`, defaulting to `windows`. Web builds support the base game only. `--platform web --edition extended` returns an error; Extended CI remains disabled.

`src/engine/StrandedIIWeb.bb` includes the shared game and browser services. Windows, web, macOS and Studio build the same editable engine in `src/blitz3d/`; the web target uses [Emscripten](../config/web-toolchain.json). Edit engine sources directly and rebuild. [How the web version works](web-architecture.md) explains WASM, WebGPU, JSPI and browser storage.

Web development also needs Git. Preparation downloads pinned dependencies into `deps/blitz3d/`, `deps/emsdk/` and `deps/ffmpeg-web/`; the first build takes time and disk space. Complete Windows setup first, then run:

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py run --platform web
```

Open `http://127.0.0.1:8765/` in desktop Chrome/Edge. After checking WebGPU and WebAssembly JSPI support and preparing resources and storage, the original game starts automatically. There is no separate start or language selection screen; HTML messages are English. Select the game language in its Settings; saved selections are preserved, with English as the first-run default. The first canvas click enables mouse capture and audio without also acting as game input.

Use the Full screen button or `Alt+Enter` to toggle fullscreen. When the browser permits keyboard lock in fullscreen, a short `Esc` reaches the original game; hold it to release the lock. Loading and the main menu display without capture; during gameplay, releasing the pointer or switching windows pauses without covering the game. Click the game to return; if the browser rejects a new capture request, wait briefly and click again. If the server port is blocked, specify another, for example `run --platform web --port 4173`. Do not pass game arguments to web `run`.

Web output goes into `build/web/site/`, with prepared resources in `build/web/runtime/`. Resources come from verified official inputs and shared language packs; Windows runtime saves and settings are never copied. CI uses the dependency ZIP extracted to `build/ci/deps/game`, checking the resource and font hashes again during web setup. Generated runtime directories cannot be used as resource inputs. After translation or font changes, run `localize --platform web`, then `build --platform web` to repack.

WAV encodings unsupported by the web runtime are converted to PCM in `build/web/audio/` using hash-verified FFmpeg and included only in the web package. File paths, sample rates, and channel counts are preserved; source resources remain unchanged. FFmpeg is a preparation tool and is not distributed with the game.

Saves and settings use a separate base-game store in the browser's IndexedDB. Save replacement and backups wait for persistence to finish; settings defaults are seeded only on first use. Keep the same browser, profile, address, and port to access the same store. Clearing site data deletes these saves. A lock prevents two tabs at the same address from using the store simultaneously.

`check --platform web` checks artifact/source hashes, packaged-resource boundaries, converted audio, Windows/wasm numeric/string/binary parity, line splitting of the actual menu and intro scripts, and save replacement/failure recovery in the web filesystem. A CPU buffer fixture runs the actual terrain colour generator with fixed height bands, checking colours and limiting full-buffer transfers to prevent per-pixel GPU synchronization. A simulated DOM checks autostart, new English settings and preservation of existing Korean settings, and input/fullscreen transitions. Results are written to `build/web/verification.json`. CPU fixtures, simulated DOM and storage transactions do not verify a real GPU, browser or IndexedDB.

After building the web version, prepare an actual-engine comparison with the command below. It runs the Windows reference in a separate copy without using the current game or saves. Audio comparison uses the web-prepared files in both engines. Open `/checks/` on the web server and click the check button to compare the same scenario in WebGPU. This checks Korean bitmap glyphs and image colour keys, vertical orientation through frame copies and motion blur, audio decoding, editor and random-island initialization (including terrain colours, grass colours, player creation, and a 30-second limit per generated world), the menu camera loop, the original intro's ship/camera/events, transition to the first island, and skipping the intro. Synthetic sequence time does not verify real-time playback speed or FPS.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/verify_web_game.py
```

WebGPU reuses a temporary texture for same-buffer `CopyRect` operations and batches source regions unaffected by earlier writes, reducing copies and draws for water and underwater effects. Overlapping copies see earlier results; scaled copies keep the original path. The C++ CPU fixture in `check --platform web` runs the actual copy methods to check pixels, colour keys, clipping, ordering, two snapshots per water update, and texture reuse. Actual GPU performance requires gameplay verification.

For frame-drop comparisons, prepare `scripts/profile_web.py --serve`, then open `http://127.0.0.1:8766/` in Chrome/Edge and start the comparison. It uses a separate engine copy with real WebGPU and real clocks, without mounting IndexedDB or player saves. Select the island or menu scene and resolution. Water qualities 6, 3 and 0 at a 144 FPS cap, and quality 0 at a 60 FPS cap, each run three times for 30 seconds after a 5-second warmup. Every trial uses a fresh WASM instance; losing window focus invalidates the trial. Keep the window foreground for about seven minutes per scene.

Results are saved to `build/web/profile-*/report.json`: FPS and p95/p99 intervals between completed Flip calls; CPU time for water, reflections, main rendering and frame waiting; and texture creation, copy and draw-call counts. Reflection time is part of water time, so do not add them together. These values are not GPU execution time or display scanout FPS, and instrumentation adds overhead. The automatic camera and muted audio do not replace input, audio or full-gameplay verification. Use `--existing build/web/profile-<name>` to serve a prepared profiler again.

In the browser, verify the menu, the roughly 53-second intro in `maps/adventure/map01.s2`, and the first playable island, `map02.s2`, in sequence. Terrain, models, Korean UI, audible sound, movement, collision, gathering, crafting, saving/loading, recovery after refresh/browser restart, and focus/pointer-lock return require separate gameplay verification. A successful build does not establish that these checks passed.

The current scope is an experimental single-player web build. Mobile, web Extended, the editor, and the full campaign are outside the completed support scope. Raw TCP/UDP operations unavailable in browsers return connection failure.

### GitHub Pages deployment

The [WebPlay workflow](../.github/workflows/web.yml) builds, checks, and deploys to Pages on main pushes and manual runs targeting main. Pushes and PRs that change only files with the lowercase `.md` extension are excluded; this filter does not apply to manual runs. PRs only run checks and create artifacts. Windows builds with the existing CI dependency ZIP and pinned web tools; Ubuntu publishes the site. The web workflow creates no Releases or tags. The existing Windows release policy remains in effect.

Before the first publication, set the repository's **Settings → Pages → Build and deployment → Source** to **GitHub Actions** and allow main deployments in the `github-pages` environment. The address is `https://shlifedev.github.io/Stranded-II-Modern/`. Local and Pages browser saves are separate and are not migrated automatically.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/web_pages.py
```

`web_pages.py` stages only the eight runtime files from the current checked build in `build/web/pages/`. It excludes `/checks/`, logs, and personal saves, uses relative paths and a deployment-specific cache key, and records file hashes in `build/web/pages-report.json`. Unexpected output files cause an error instead of being deleted. Deployments are serialized, and build/check failures leave the existing site in place. Post-deployment checks compare HTTP file hashes and the WASM media type; actual gameplay still needs separate verification.

## Experimental native macOS build

The base game has an experimental Apple Silicon build. Game code compiles to a native ARM64 executable, and the WebGPU renderer uses Metal through Dawn. [How the macOS version works](macos-architecture.md) explains compilation, rendering, and operating-system integration. [Target settings](../config/macos-toolchain.json), the shared `src/blitz3d/` engine and `src/native/macos/` define this target. Windows remains the default platform; Extended Mac builds are rejected.

Build on Apple Silicon with Xcode Command Line Tools, Git, Python 3.12+, CMake, Ninja, FFmpeg/ffprobe, and zstd. The bundled Dawn library sets the deployment target to macOS 15.0. CI checks compilation and non-graphical execution on macOS 15; actual graphical gameplay still requires separate verification. Preparation downloads shared pinned dependencies under `deps/blitz3d/` and builds the compiler/runtime under `build/engine/`; it can take substantial time and disk space. Supply verified official game resources and the pinned font, never a runtime containing player data.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python scripts/dev.py setup --platform macos --game deps/game --font deps/fonts/NotoSansKR.ttf
.venv/bin/python scripts/dev.py build --platform macos
.venv/bin/python scripts/dev.py check --platform macos
.venv/bin/python scripts/macos_package.py
.venv/bin/python scripts/dev.py run --platform macos
```

`build` prints the locally signed `build/macos/apps/<build-id>/Stranded II.app` path and records it in `build/macos/build-report.json`. Double-clicking the app prepares its resources and stores settings/saves in `~/Library/Application Support/Stranded II Native Preview/`. Existing settings and saves survive resource refreshes. `run --platform macos` uses the app with the isolated `build/macos/runtime/` profile instead. Neither path imports Windows or browser data. Fresh profiles start windowed at 1280×720. `localize --platform macos` refreshes development language packs; rebuild to update the app. Unsupported source WAV encodings are converted to PCM in the native runtime; original files are preserved.

`check --platform macos --suite core` opens no windows. It compares 613 bytes against the original Blitz3D 1.118 result and checks reused Type storage, app resources/signature/execute permissions, ARM64/minimum OS, and system-only library dependencies. The default `--suite full` additionally checks actual Metal rendering/readback, shared menu/intro/island scenarios, Korean glyphs, audio decoding, save/backup/reload, Cocoa text roundtrips, fresh profile installation, and data preservation during resource refresh. `--suite` is exclusive to Mac checks. The latest result is `build/macos/verification.json`; suite-specific results are `verification-core.json` and `verification-full.json`. Synthetic game time does not verify real-time FPS, audible sound, IME composition, focus/fullscreen transitions, or the campaign.

`macos_package.py` creates a ZIP and `.zip.sha256` only when the latest successful check matches the current sources, tools, executable, and app. Local builds use `build/macos/release/StrandedII-<version>-macos-arm64-preview-<build-id>.zip`; release builds use `StrandedII-<display_version>-macos-arm64.zip`. Supply release metadata with `build --platform macos --version-info build/ci/version.json`; it must match the current commit. The app bundle uses the numeric version, while the game and verification summary retain the full version metadata. The ZIP contains the app, English/Korean launch instructions, and a verification summary without local paths; it excludes personal settings/saves. Extraction rechecks contents, permissions, and signing. Packages checked with `core` mark graphical checks `not_run`. Output paths are recorded in `build/macos/distribution-report.json`.

[Mac CI](../.github/workflows/macos.yml) supports manual previews and reusable builds called by the desktop release workflow. On ARM64 `macos-15`, it validates the pinned dependencies, extracts them under `deps/ci-macos/`, and runs preparation → build → `core` checks → ZIP packaging. Release builds share the Windows version artifact and source commit. ZIP/checksum and report artifacts are retained for 14 days. Manual Mac runs do not publish releases; publication is handled by the desktop release procedure below.

Test the ZIP on another Apple Silicon Mac, especially macOS 15: extraction, launch/relaunch, and data preservation after replacing the app. IME composition, fullscreen/app switching and input recovery, music/effects, and a 30-minute session followed by save/quit/reload require manual checks and must not be reported as automatically passed. The app is locally ad hoc signed and not notarized. Native movies/raw TCP/UDP, Intel, and native Extended are unsupported. Distribution signing and notarization are a separate step.

## Extended authoring app

[Stranded II Studio](studio.md) edits content and maps through a standalone GUI and JSON CLI. It provides a 3D viewport using the actual Blitz/Extended engine, undo, validation and native resource exports. Use `python scripts/studio.py` for its commands. `Studio.cmd` on Windows or `sh ./Studio.sh` on macOS/Linux automatically prepares dependencies and the engine before launching the app. Follow the Studio guide for details. Engine outputs stay under `build/studio/` and app outputs under `studio/dist/`; ordinary game, CI and release edition selection remain unchanged.

Studio uses the same iframe and Blitz engine for editing and play. `studio/engine/studio.js` waits for pending scene ACKs and checks the latest saved map; `studio_load` in `src/engine/studio/runtime.bb` reloads that map while retaining the engine and definitions. Resource changes require explicit **Apply resources** initialization. Stop restores the authoring map in the in-memory filesystem and the editor camera without writing to the project. Changes to this path require Studio builds/checks; changes to the shared engine also require both editions' builds/checks and relevant game verification.

## Where to make changes

**Implement game features in `src/engine/` and compiler/runtime changes in `src/blitz3d/`.** `build/` contains executables and check results, not authoritative sources. The paths and commands below are for Stranded II. See the [next section](#stranded-ii-and-extended) for Extended paths.

```text
edit src/engine/ → build command → build/runtime/StrandedII.exe → Play.cmd
edit locales/ translations, languages or fonts → localize command → refresh build/runtime/core/locales/
edit resources/ → setup command → refresh resources in build/runtime/
```

| Path | Role | Git tracking |
| --- | --- | --- |
| `src/engine/` | Directly edited Blitz3D game sources; entrypoint: `StrandedII.bb` | Included |
| `src/blitz3d/` | Editable shared compiler, runtime, graphics/input/audio modules and original licenses | Included |
| `locales/` | Per-language translations, names, font settings, and legacy text-number mappings | Included |
| `resources/` | Original-text-to-key mappings and edited resources | Included |
| `scripts/`, `dev.ps1` | Preparation, build, launch, and verification tools | Included |
| `config/` | Initial settings, dependency pins and optional legacy reference declarations | Included |
| `docs/`, `licenses/` | Documentation and copyright notices | Included |
| `deps/` | Original game, fonts and pinned third-party SDKs/libraries | Excluded |
| `.venv/` | Python virtual environment | Excluded |
| `build/engine/` | Per-target compiler/runtime builds and generated headers | Excluded |
| `build/check-*/` | Temporary code, executables, and reports generated by checks | Excluded |
| `build/runtime/` | Built game, resources, user settings, and saves | Excluded |

Changes to generated output may be overwritten by the next `setup` or `build`. Keep code, translations, and resource changes in the source directories above. Python tools generate resources and verification code; they do not regenerate the game sources in `src/engine/`.

## Stranded II and Extended

`classic` preserves original gameplay with optional QOL disabled by default; `extended` enables the same QOL by default and remains the separate edition for future features, balance changes, and content. Both expose five individual Settings > QOL options. QOL calls the existing crafting, transfer, and construction rules without changing material costs or tool speed. Change shared code once and verify both editions; write Extended-only changes in that edition's code and resources.

`classic` is an internal identifier. The base edition keeps the window title `Stranded II` and executable `StrandedII.exe`; only Extended uses `Stranded II - Extended` and `StrandedII-Extended.exe`. Builds, launchers, and packaging use these names consistently.

```text
src/engine/
  StrandedII.bb                    Stranded II entrypoint
  StrandedIIExtended.bb            Extended entrypoint
  includes/engine_init.bb          Shared initialization and functions
  includes/engine_loop.bb          Shared runtime loop
  includes/                       Currently shared engine implementations
  editions/classic/profile.bb     Stranded II identity and window title
  editions/classic/modules.bb     Stranded II UI/input/building selection
  editions/extended/profile.bb    Extended identity and window title
  editions/extended/modules.bb    Extended UI/input/building selection
resources/overrides/              Shared resource overrides (create when needed)
resources/editions/classic/overrides/   Stranded II resource overrides
resources/editions/extended/overrides/  Extended resource overrides
build/runtime/                   Stranded II runtime, settings and saves
build/extended/runtime/          Extended runtime, settings and saves
```

Both `modules.bb` files select the shared UI, input, construction, and `includes/qol*.bb` implementations. Edition profiles define `Cedition_qol_default` (0 for Stranded II, 1 for Extended). Saved `qol_*` values take precedence; missing or invalid values use that edition's default. QOL applies immediately after a successful settings save; language and display changes still require restarting. Search uses a Win32 Unicode EDIT control and IME, through the typed `src/blitz3d/src/modules/bb/platform.windows/` services. Separate only the modules needed when behavior diverges. Python does not generate engine code. Build reports record the selected entrypoint's actual `Include` graph; including another edition's private source fails validation.

The recipe book reads loaded `Tcom` data. Guides for cooking, water use, and similar interactions are maintained separately in the shared override `mods/Stranded II/sys/qol_guides.inf`; each `source` identifies the supporting script and event. Guides do not execute scripts on the player's behalf. When changing an original event, review the guide's conditions, quantities, and probability descriptions too. QOL settings are saved only in that edition's `core/modern.ini`.

The native search bridge translates and dispatches messages only for its Unicode EDIT on the game thread. Windows `HidePointer` hides the cursor without enabling relative mouse capture, so the child control can receive clicks. When the EDIT has focus, game key/text handling yields to it. `WS_CLIPCHILDREN` protects the child; resize it only when dimensions change. Closing it removes the hook and restores the added style.

Windows enables relative mouse input only during gameplay camera control. `MoveMouse` then shifts the virtual origin without warping the OS cursor, preserving unread motion received after the last `MouseXSpeed` or `MouseYSpeed` call. Old motion is discarded only on capture or focus transitions. Menus, the console and search release capture. Return and keypad Enter produce the original Blitz character code 13. If borderless mode scales the selected render resolution, presentation uses linear filtering and mouse positions are mapped back to that render resolution.

Menu cursors read the current Windows cursor position in the active game window and convert it to render coordinates. Coordinates still update when external speed tools put `GetTickCount` and Windows message timestamps on different timelines, causing SDL to discard motion events. The query is skipped during relative input, search focus, focus loss or minimization. Game time retains the existing QPC path.

Both editions remove continuous motion blur and its graphics toggle/strength controls. Script and state effects, including scenario starts, dizziness, intoxication and death, retain the original sprite accumulation method. The last two motion-blur values in existing `settings.cfg` files are read and preserved for compatibility but do not affect rendering. See [renderer details](wgpu-migration.md#15-readwrite-hazards-in-copyrect-water-distortion-and-motion-blur).

Resources are prepared in this order: official input, localization preparation, shared overrides, then the selected edition's overrides. Place edition overrides at runtime-relative `mods/Stranded II/...` paths. Write item, recipe, building, stat, map, and script rebalancing in Extended overrides. Overrides cannot replace settings, saves, or executables. Follow the [resource localization binary rules](#localizing-new-text-and-resources) for `.s2` edits. See the [localization guide](localization.md#edition-specific-translations) for edition translations.

All development commands accept `--edition classic|extended` and default to Stranded II. Dependencies and the compiler are shared; runtime folders, reports, and verification artifacts are separate. Stranded II data is neither moved nor automatically copied to Extended.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --edition extended
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build --edition extended
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check --edition extended
.\Play-Extended.cmd
```

Use these commands after registering dependency paths in the [first build](#first-build). To prepare Extended first, add `--edition extended` to the initial `setup` command. Extended defaults are in `config/editions/extended/modern.ini`; `setup` does not replace existing personal settings.

## Localizing new text and resources

This section is for developers adding UI or quests. For existing text, follow the [localization guide](localization.md).

1. Add a descriptive key such as `quest.island1.arrival` and its English text to `locales/en.tsv`. Use `locales/editions/extended/en.tsv` for Extended-only text.
2. Add the same key and translation at the corresponding path for each translated language.
3. Reference it as `[[loc:key]]` in the code or script that displays it.

```blitzbasic
bmpf_txt(20,20,"[[loc:ui.language]]")
```

```text
msg "[[loc:quest.island1.arrival]]";
```

Keys use letters, digits, `_`, `.`, and `-` and must be unique. Preserve existing `legacy.*` and hash keys. TSV ignores blank lines and lines starting with `#`, and supports `\n`, `\r`, `\t`, and `\\` escapes. References may nest up to eight levels; do not create cycles. A key missing from both languages produces `[missing: key]` and diagnostics in the runtime's `core/localization.log`. Resolve `check` errors before distributing.

Use distinct keys for different meanings of the same English word. The stock string loader separates compass east, keyboard keys, and the fishing action from the legacy keys for the E key, inventory keys, and fish; custom mod labels are preserved.

| Edit | Location and rules |
| --- | --- |
| Original display text to key mappings | `resources/source-keys.json`; help text uses `resources/help-terms.json` |
| Legacy translation numbers to keys | `locales/legacy-index.json` — preserve existing numbers |
| Edited resources | Shared `resources/overrides/mods/Stranded II/...` or edition-specific `resources/editions/<edition>/overrides/mods/Stranded II/...` |
| `.s2` maps | Length-prefixed binary. Use the map editor or [scripts/s2map.py](../scripts/s2map.py) to edit strings only, preserving terrain, IDs, other fields, and attachments |

Apply resource edits with `setup`, code edits with `build`, or translation-only edits with `localize`, then run `check`. New resource files must also be connected so the game reads them. Do not replace settings, saves, or executables through resource overrides.

Original input is CP1252; prepared resources and engine sources are UTF-8. Convert legacy delimiters 166/182/191 to 31/30/29 only in strings, preserving numeric IDs. Diary titles are lookup keys: translate their display aliases, not the titles themselves.

The engine stores script variables as `[[loc:key|name=HEX_UTF8_BYTES]]` to preserve their values at execution time. Do not author this form manually. Plain translated sentences in older saves may remain unchanged after switching languages, so check compatibility using copies.

## Verifying changes

For game-code changes, run `build` and `check`, then verify the changed feature in the game. For translations and fonts, run `localize` and `check`; for resources, run `setup` and `check`, then inspect the display. For documentation-only changes, check links and consistency with the described behavior.

| Check | Coverage | Results |
| --- | --- | --- |
| `setup` | Dependency hashes, catalogs, string conversion in the 13 official maps, and preservation of other bytes and attachments | `build/setup-report.json` |
| `localize` | Registered languages, catalogs, TTF/OTF glyphs and fallback sources, preserving the working pack on failure | `build/localization-report.json`, `build/runtime/core/locales/fonts-report.json` |
| `build` | Embedded compiler/runtime build, actual Blitz compilation and executable/source/DLL hashes | `build/build-report.json` |
| `check` | Text references, substitution variables, fonts, actual Blitz loader/parser, settings preservation, timing/frame waits, save replacement/locking failures/backups/five slots/queue/Continue selection | `build/check-*/report.json` and detailed results in that folder |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_game.py` | Actual map/save/backup/quick save/load/autosave state recovery in an isolated game | `build/game-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol_logic.py --language ko` | Actual Blitz QOL logic, crafting, transfers, sorting, and settings; uses graphics entity and script test doubles | `build/qol-logic-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol.py --language ko` | Actual crafting, transfers, script vetoes, and save/load in a copied runtime; requires a graphics device | `build/qol-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_collisions.py --edition classic` | Rock mesh sanitation, slope approach/retreat, jumping/landing, corridors/swimming, control transitions and projectile collisions in an isolated Blitz runtime | `build/collision-smoke-*/report.json` |

For movement and collision changes, run `verify_collisions.py` for both editions. Extended uses `--edition extended` and writes results to `build/extended/collision-smoke-*/`. The check uses real terrain and rock models with 60/120/144 Hz and 100ms time steps and sends no GUI input. Actual rendering FPS and the user's reported location require separate verification. The existing `diagnostics=1` option in `core/modern.ini` logs requested/resolved movement, terrain correction, contact points/normals and rejected moves to `core/modern.log` at most four times per second.

`check` compares the native ABI with the frozen original Blitz3D 1.118 output and tests timers, parsers, save-file handling and related behavior without rendering. It also exercises actual settings saves and reloads to check that every view-distance level applies consistently. `python scripts/verify_engine.py --input` checks console character codes, held keys, committed text and mouse event delivery through the real SDL queue without a window or OS input. With capture state substituted, it also checks single counts arriving between reads and recentering, independent axis consumption, direction reversal and 0.02-degree rotation. A substituted cursor query and simulated timestamp offsets after 1 or 10 seconds at 2×/3×/5×/10× check immediate coordinate recovery despite discarded motion, deduplication, drags, clicks, wheels and query failures. Actual Cheat Engine use, Win32 cursor queries and focus transitions require separate verification.

`python scripts/verify_engine.py --render` draws into textures using a real D3D12 device and the engine shaders without a window, checking matte/specular colours, textures, fog, distance fade, instanced transparency, alpha-test silhouettes and depth order across cameras. It also compares optimized frustum tests against transformed bounding-box corners and the legacy calculation. Six-view capture checks compare pixels with individual legacy renders and cover preparation counts, scene changes between calls, camera children and tween values. Specular lighting follows the original D3D7 material strength and exponent and is added after texturing. `--graphics` additionally checks D3D12 drawing, pixel readback and graphics-context lifetime in an isolated fixture with a window. Actual rendering FPS, raw mouse response, Alt-Tab, visual smoothness, and full progression require separate in-game checks.

Both editions' `check` runs `verify_qol_logic.py`. It tests edition defaults, missing/invalid settings, all 32 option combinations, and the shared engine functions with entity/script test doubles; it does not verify display, real scripts, map saves, or IME. Both QOL verification commands accept `--edition classic|extended` and default to Stranded II; Extended reports are under `build/extended/`. `verify_qol.py --interactive` prepares a separate test game with QOL enabled and no personal saves, without launching it. Run that copy to check Korean composition/commit/cancel, search focus and Alt-Tab, long names and detail scrolling, Shift-click/R/Shift+R, each option on/off, and cancellation of held construction. Use `--language en` for English and `--desktop` for borderless mode. Simulated construction intervals do not measure actual rendering FPS.

For save changes, also use `scripts/verify_game.py` to test actual map loading and saving/loading. It uses a copied runtime and an empty save folder without writing to the user's game or saves. It needs a graphics device and does not test UI interaction. The default is an 800×600 window; `--desktop` tests borderless mode fitted to the monitor, and `--edition extended` selects Extended. Extended reports are under `build/extended/`.

When changing timing code, review `modern_runtime.bb`, `modern_math.bb`, `modern_ui.bb`, and their callers together. Keep game time (QPC) separate from frame waiting, and preserve full-frame elapsed time, the 100ms cap, fractional accumulation, and the shared focus/presentation path. External-speed simulation in `verify_clock_hooks.py` runs only in a separate test process.

Choose relevant checks for the change and verify them in the game:

- Language: Korean → English → an additional language, apply and restart, cycling in both directions, preservation of the previous selection on cancel, and English for untranslated entries.
- Fonts: static TTF/OTF and variable fonts, Korean fallback for Latin-only fonts, Latin letters, numbers, spaces, carets, wrapping, and previews.
- Display: menus, diary, items, crafting, dialogue, long-text scrolling, and map-editor help.
- Saves: load and resave a new save in both languages. For earlier Korean saves, load a copy → save under a separate name → reload.
- Frame rate/display: movement and time progression at 60/120/144 FPS, repeated Alt-Tab, recovery after an extended time in the background, and relevant multiple-monitor/DPI environments.
- Display settings: 800×600 and Fit monitor, cancel/apply/restart, and actual rendering size on monitors larger than 2048 pixels.
- Save features: quick save/load keys, five-slot rotation after at least six autosaves, main-menu Continue, and recovery by copying `.bak` to a separate name.
- External speed changes: use Cheat Engine 2×/3×/5×/10× for 1 and 10 seconds, return to 1× and check immediate menu/inventory cursor movement, camera control, dragging, Alt-Tab and frame records with `diagnostics=1`.

When sharing results, include the build, display settings, actions checked, and remaining checks. Automated checks passing does not establish full-playthrough coverage or display recovery on every PC.

See [QOL options](qol.md) for shared features and shortcuts, and [Extended differences](extend-features.md) for edition defaults and development status.

## Editing project documentation

Edit the English `.md` or Korean `-ko.md` directly. Name new Korean documents like `example-ko.md`. An AI agent updates the other languages during repository tasks; this is not a continuously running translation service.

[.dev-docs-ai-translation.json](../.dev-docs-ai-translation.json) controls document languages, independently of game languages. English-only edits are synchronized from English; when both languages change, Korean takes priority. Do not manually change the hash on the first line. See [AGENTS.md](../AGENTS.md) for the full rules.

As an exception, `README-ko.md` is maintained by the user and agents do not change it without an explicit editing request. Other README languages are synchronized from the Korean original. When creating or updating documentation, follow each document's purpose and style in [auto-gen-please.json](auto-gen-please.json), updating only the documents needed.

## GitHub CI and release ZIPs

The [desktop workflow](../.github/workflows/windows.yml) builds and checks the base game for Windows on pushes to `main`/`dev`, pull requests, and manual runs. A `main` push also builds the same version for Mac, then publishes both ZIPs and their SHA-256 files in one stable Release and advances the patch number after both platforms pass. If either platform fails, publication stops. Pushes to `dev` and PRs only produce Windows `-local` verification ZIPs without publishing Releases or consuming version numbers. Extended CI edition selection, build and release routing are commented out; Extended remains under local development for now.

Desktop and web pushes/PRs use `paths-ignore: ["**.md"]`. If every changed file has the lowercase `.md` extension, at the root or in a subdirectory, automatic CI is skipped along with the Mac build, Release publication, and Pages redeployment on `main`. If any file with another extension is included, the existing execution conditions apply. Manual runs are unaffected by this filter.

To publish a development release or manually publish a stable release, follow these steps on GitHub:

1. Open **Actions → Desktop build and release → Run workflow**.
2. Select `main` for a stable release or `dev` for a prerelease.
3. Enable `publish`.
4. After both Windows and Mac builds and checks pass, a Release is published.

For manual runs, `publish=false` only creates ZIPs and runs checks. Other branches and tag pushes do not publish.

Write public release titles and descriptions in English.

Windows CI runs `setup → build → check → package` on Windows Server 2022 with Python 3.13. Publication uses `scripts/ci.py release --include-macos` to validate both platforms' versions, commits, and checksums. The Mac ZIP is also checked for app file hashes, execute permissions, ARM64 format, and successful native checks. Mac files cannot be appended to an existing published Windows-only release. Windows `check` and Mac `core` are non-graphical checks; they do not verify display, rendering FPS, app switching, or full gameplay. Verify actual GitHub Actions runs separately from local checks.

To avoid repeated download traffic to upstream sites, store game resources, the compiler, and the font once in a dependency Release in the same repository. For these build inputs, CI uses only this Release and the Actions cache. Missing files cause a failure with preparation instructions; CI does not automatically download those inputs from upstream sites.

### Setting versions and automatic increments

The baseline is in [config/version.json](../config/version.json) on `main`, initially `1.1.0`. CI never changes the first two numbers or configuration files, and creates no version commits. The patch is the starting value for that series; existing stable tags advance the next release number. Extended's local baseline is managed separately in [config/editions/extended/version.json](../config/editions/extended/version.json).

| Release situation | Menu label | GitHub tag |
|---|---|---|
| First Stranded II stable release | `1.1.0-main` | `v1.1.0` |
| Subsequent Stranded II development release | `1.1.1-dev.1` | `v1.1.1-dev.1` |
| Next Stranded II stable release | `1.1.1-main` | `v1.1.1` |

When publishing, `dev` reads the baseline file from `main` and freezes its commit SHA. Before a stable release exists, development releases start at the baseline with `-dev.1`; otherwise they target the next patch and increment `dev.N`. Development releases do not consume stable patches. `main` uses the baseline file from the release commit.

Automatic and manual release runs share a queue and are serialized from version allocation through publication. Build/check failures create no tags and consume no numbers. Publication interruptions may reserve a number, but rerunning the same edition, source commit, and channel recovers its tag and draft. Draft retries require the original artifact names and hashes. Published Releases are never overwritten; reruns verify and retain the existing publication, including artifacts from retired targets. Other editions' tags and `ci-deps-v1` do not affect number allocation. Development releases do not replace the latest stable Release marker.

Builds generate `core/version.txt` and `core/version.json`. The game displays the label in the main menu. `build-info.json` includes the edition, version, channel, source commit, baseline-file commit, and executable hash. The build report also records the native source and DLL hashes; packaging checks these against the current shared source and selected runtime. Packaging checks the selected edition's build report and source graph, rejecting another edition's runtime or stale resource overrides. The existing `Cversion="1.0.0.1"` remains unchanged for map/script compatibility. Local builds display `-local`.

### Preparing the dependency Release once

Prepare local dependencies as described above, then run from the repository root. Existing prepared `deps/` inputs do not need to be downloaded again.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/ci.py bundle
```

This creates `build/ci-upload/StrandedII-ci-deps-v1.zip` and `.zip.sha256`. It validates every existing game/compiler manifest hash and the font hash before bundling only required files and original licenses. Bundle license names and hashes are pinned in [config/dependency-license-files.json](../config/dependency-license-files.json), so adding release licenses does not affect validation of the existing dependency ZIP. The final game release includes every file currently in `licenses/`. Personal saves/settings and the Blitz editor's examples/media are excluded. The dependency ZIP's complete SHA-256, Release tag, and filename are pinned in [config/ci-dependencies.json](../config/ci-dependencies.json).

Create the remote repository and push your chosen commit yourself, then upload with the command below. Replace `OWNER/REPO` and `COMMIT_SHA` with the actual repository and pushed commit SHA. GitHub CLI authentication is required. The dependency Release is not marked as the latest game Release.

```powershell
gh release create ci-deps-v1 `
  "build/ci-upload/StrandedII-ci-deps-v1.zip" `
  "build/ci-upload/StrandedII-ci-deps-v1.zip.sha256" `
  --repo OWNER/REPO --target COMMIT_SHA `
  --title "CI dependencies" `
  --notes "Pinned game resources, Blitz3D 1.118 and Noto Sans KR. Original licenses are included in the ZIP." `
  --latest=false
```

Then manually run `Windows build and release` in Actions to download the game ZIP. To update dependencies, rebuild the bundle and update the configuration with its printed SHA-256 and a new Release tag/filename. A mismatched ZIP is rejected before extraction, even on a cache hit. Ordinary changes to synchronized documents or game sources do not require rebuilding the dependency ZIP. If bundled license names or contents change, update `config/dependency-license-files.json`, rebuild the dependency ZIP and update its complete pinned hash.

### Release ZIP contents

The release ZIP's `StrandedII/` folder includes `StrandedII.exe`, original game resources, localized resources, per-language bitmap fonts, initial settings, and licenses. Extract the whole ZIP and double-click `StrandedII.exe`. No launch CMD is included. Players do not need separate Python, Blitz3D, or font installations; display, FPS, and language are changed in the game's settings menu.

Packaging uses an explicit file list, so a used development runtime's personal saves, custom maps, screenshots, `preserved/`, logs, and check reports are excluded. Key bindings come from the original dependencies; `core/modern.ini` comes from the repository's initial settings. Personal `sys/settings.cfg` is also excluded, so new installations use the engine's graphics quality and volume defaults. The development runtime is not modified. Local packaging is also available.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/ci.py package --game deps/game
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p 'test_ci*.py'
```

Output names are `build/release/StrandedII-<display version>-windows.zip` and `.zip.sha256`. An example local build is `StrandedII-1.1.0-local-windows.zip`. Packaging does not compile or run game checks, so first complete `setup → build → check` with the latest sources. CI stores version information in `version-context` and game ZIPs in `StrandedII-windows`.

## Generated output and user data

[.gitignore](../.gitignore) excludes `deps/`, `build/`, `.venv/`, Python caches, and logs. Sources, translations, edited resources, scripts, settings, documents, and licenses are tracked in Git.

`build/runtime/` and `build/extended/runtime/` contain real saves and user settings as well as build output. Before resetting either folder, back up its `mods/Stranded II/saves/`, custom maps, screenshots, and the contents of `preserved/` if present.
