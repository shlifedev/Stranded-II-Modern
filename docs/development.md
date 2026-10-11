<!-- dev-docs-ai-translation: {"document":"docs/development.md","language":"en","syncedHash":"3777164578737426ed1cca40544702ec0e6377c8fac08b7239989ea143412ca8"} -->

# Development guide

[English](development.md) · [한국어](development-ko.md)

[Back to README](../README.md) · [Localization guide](localization.md)

For a first build, follow **environment setup → first build**. Once set up, use [running after changes](#running-after-changes) to choose the command for your edit. To contribute translations only, start with the [localization guide](localization.md).

## Setting up the development environment

You need Windows 10 or later, Python 3.10 or later, and Pillow and fontTools from [requirements.txt](../requirements.txt). Use an ASCII path such as `C:\Stranded II` because of Blitz's file-path handling. Spaces are allowed.

Both editions require Visual Studio's MSVC x86/x64 C++ build tools and a Windows SDK. `build` compiles the shared `src/native/qol_input.c` into the 32-bit `QolInput.dll`, even when QOL is disabled. No separate C runtime installation is needed. Both packages include the DLL; native outputs and reports are stored under the selected edition's `build` directory in `native/`.

These dependencies are not included in the repository; prepare them yourself:

| Dependency | Download | Location |
| --- | --- | --- |
| Stranded II Gold 1.0.0.1 English ZIP | [Official download](https://www.strandedonline.de/s2_download.php) | `deps/game/` |
| Blitz3D 1.118 | `Blitz3D-V1.118.zip` from [Blitz Research](https://blitzresearch.itch.io/blitz3d) | `deps/Blitz3D-V1.118/` |
| Noto Sans KR | [Pinned font version](https://raw.githubusercontent.com/google/fonts/4efc2774c63917927efe769ca845def6bd6debae/ofl/notosanskr/NotoSansKR%5Bwght%5D.ttf) | `deps/fonts/NotoSansKR[wght].ttf` |

Arrange the extracted files as follows. Use an unmodified English original for the game:

```text
deps/
  game/
    StrandedII.exe
    core/
    mods/
  Blitz3D-V1.118/
    bin/
      blitzcc.exe
    ...
  fonts/
    NotoSansKR[wght].ttf
```

Dependency versions and SHA-256 hashes are recorded in [config/dependencies.json](../config/dependencies.json), game files in [config/game-files.json](../config/game-files.json), and compiler components in [config/toolchain-files.json](../config/toolchain-files.json). Setup validates them, so use the specified releases. A separate original source ZIP is not required.

## First build

Run these commands in PowerShell from the repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --game deps/game --blitz deps/Blitz3D-V1.118 --font 'deps/fonts/NotoSansKR[wght].ttf'
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check
.\Play.cmd
```

`setup` copies original resources to the runtime folder and prepares UTF-8 text references and fonts. It also copies the compiler to `build/toolchain/` and adds this project's external function declarations. Original dependencies are not modified.

You can specify dependencies elsewhere with `--game`, `--blitz`, and `--font`. The first `setup` records those paths in `build/dependencies.local.json`, so subsequent runs can omit the arguments. That file contains personal paths and is excluded from Git.

## Running after changes

Use the following commands from the repository root. Close the development game before refreshing resources or replacing its executable. **Commands default to Stranded II**; add `--edition extended` for Extended.

| Change | Command |
| --- | --- |
| Edit `src/engine/**/*.bb` | `.\dev.ps1 build -Python .\.venv\Scripts\python.exe` |
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

## Experimental WebGPU build

All Python development commands accept `--platform windows|web`, defaulting to `windows`. Web builds support the base game only. `--platform web --edition extended` returns an error; Extended CI remains disabled.

`src/engine/StrandedIIWeb.bb` includes the existing `StrandedII.bb` and browser platform services. Gameplay stays shared: Windows uses Blitz3D 1.118, while the web uses the [pinned Blitz3D NG WebGPU port and Emscripten](../config/web-toolchain.json). Engine changes are maintained in `patches/blitz3d-web/`. No conversion to another game engine or generation of `.bb` game sources is involved.

In addition to the Windows development environment above, install Git, Visual Studio's CMake/Ninja tools, and the DIA SDK. `setup` downloads pinned tools into `deps/blitz3d-web/`, `deps/emsdk/`, and `deps/ffmpeg-web/`, so initial preparation needs time and disk space. Complete Windows setup first, then run:

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

`check --platform web` checks artifact/source hashes, packaged-resource boundaries, converted audio, Windows/wasm numeric/string/binary parity, line splitting of the actual menu and intro scripts, and save replacement/failure recovery in the web filesystem. A simulated DOM checks autostart, new English settings and preservation of existing Korean settings, and input/fullscreen transitions. Results are written to `build/web/verification.json`. Simulated DOM and storage transactions do not verify a real browser or IndexedDB.

After building the web version, prepare an actual-engine comparison with the command below. It runs the Windows reference in a separate copy without using the current game or saves. Audio comparison uses the web-prepared files in both engines. Open `/checks/` on the web server and click the check button to compare the same scenario in WebGPU. This checks Korean bitmap glyphs and image colour keys, vertical orientation through frame copies and motion blur, audio decoding, the menu camera loop, the original intro's ship/camera/events, transition to the first island, and skipping the intro. Synthetic time does not verify real-time playback speed or FPS.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/verify_web_game.py
```

In the browser, verify the menu, the roughly 53-second intro in `maps/adventure/map01.s2`, and the first playable island, `map02.s2`, in sequence. Terrain, models, Korean UI, audible sound, movement, collision, gathering, crafting, saving/loading, recovery after refresh/browser restart, and focus/pointer-lock return require separate gameplay verification. A successful build does not establish that these checks passed.

The current scope is an experimental single-player web build. Native Mac builds, mobile, web Extended, the editor, and the full campaign are outside the completed support scope. Raw TCP/UDP operations unavailable in browsers return connection failure.

### GitHub Pages deployment

The [WebPlay workflow](../.github/workflows/web.yml) builds, checks, and deploys to Pages on main pushes and manual runs targeting main. PRs only run checks and create artifacts. Windows builds with the existing CI dependency ZIP and pinned web tools; Ubuntu publishes the site. The web workflow creates no Releases or tags. The existing Windows release policy remains in effect.

Before the first publication, set the repository's **Settings → Pages → Build and deployment → Source** to **GitHub Actions** and allow main deployments in the `github-pages` environment. The address is `https://shlifedev.github.io/Stranded-II-Modern/`. Local and Pages browser saves are separate and are not migrated automatically.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check --platform web
.\.venv\Scripts\python.exe -X utf8 scripts/web_pages.py
```

`web_pages.py` stages only the eight runtime files from the current checked build in `build/web/pages/`. It excludes `/checks/`, logs, and personal saves, uses relative paths and a deployment-specific cache key, and records file hashes in `build/web/pages-report.json`. Unexpected output files cause an error instead of being deleted. Deployments are serialized, and build/check failures leave the existing site in place. Post-deployment checks compare HTTP file hashes and the WASM media type; actual gameplay still needs separate verification.

## Where to make changes

**Implement game features in `src/engine/`.** `build/` contains executables and check results, not authoritative sources. The paths and commands below are for Stranded II. See the [next section](#stranded-ii-and-extended) for Extended paths.

```text
edit src/engine/ → build command → build/runtime/StrandedII.exe → Play.cmd
edit locales/ translations, languages or fonts → localize command → refresh build/runtime/core/locales/
edit resources/ → setup command → refresh resources in build/runtime/
```

| Path | Role | Git tracking |
| --- | --- | --- |
| `src/engine/` | Directly edited Blitz3D game sources; entrypoint: `StrandedII.bb` | Included |
| `locales/` | Per-language translations, names, font settings, and legacy text-number mappings | Included |
| `resources/` | Original-text-to-key mappings and edited resources | Included |
| `scripts/`, `dev.ps1` | Preparation, build, launch, and verification tools | Included |
| `config/` | Initial settings, dependency versions/hashes, and Blitz external function declarations | Included |
| `docs/`, `licenses/` | Documentation and copyright notices | Included |
| `deps/` | Original game, compiler, and fonts prepared locally | Excluded |
| `.venv/` | Python virtual environment | Excluded |
| `build/toolchain/` | Project-specific compiler copy | Excluded |
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

Both `modules.bb` files select the shared UI, input, construction, and `includes/qol*.bb` implementations. Edition profiles define `Cedition_qol_default` (0 for Stranded II, 1 for Extended). Saved `qol_*` values take precedence; missing or invalid values use that edition's default. QOL applies immediately after a successful settings save; language and display changes still require restarting. Search uses a Win32 Unicode EDIT control and IME, with declarations in `config/userlibs/qol_*.decls`. Separate only the modules needed when behavior diverges. Python does not generate engine code. Build reports record the selected entrypoint's actual `Include` graph; including another edition's private source fails validation.

The recipe book reads loaded `Tcom` data. Guides for cooking, water use, and similar interactions are maintained separately in the shared override `mods/Stranded II/sys/qol_guides.inf`; each `source` identifies the supporting script and event. Guides do not execute scripts on the player's behalf. When changing an original event, review the guide's conditions, quantities, and probability descriptions too. QOL settings are saved only in that edition's `core/modern.ini`.

The native search bridge translates and dispatches Unicode messages only for that EDIT control on the current game thread. Blitz's default message loop lacks character translation, so simply creating an EDIT is insufficient for typing. The parent's `WS_CLIPCHILDREN` excludes the search field from game presentation; its position is updated only when dimensions change. Do not erase and repaint it every frame with `InvalidateRect`. Closing the search field removes the message hook and restores the added window style.

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
| `build` | Actual Blitz3D compilation and executable/source hashes | `build/build-report.json` |
| `check` | Text references, substitution variables, fonts, actual Blitz loader/parser, settings preservation, timing/frame waits, save replacement/locking failures/backups/five slots/queue/Continue selection | `build/check-*/report.json` and detailed results in that folder |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_game.py` | Actual map/save/backup/quick save/load/autosave state recovery in an isolated game | `build/game-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol_logic.py --language ko` | Actual Blitz QOL logic, crafting, transfers, sorting, and settings; uses graphics entity and script test doubles | `build/qol-logic-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol.py --language ko` | Actual crafting, transfers, script vetoes, and save/load in a copied runtime; requires a graphics device | `build/qol-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_collisions.py --edition classic` | Rock mesh sanitation, slope approach/retreat, jumping/landing, corridors/swimming, control transitions and projectile collisions in an isolated Blitz runtime | `build/collision-smoke-*/report.json` |

For movement and collision changes, run `verify_collisions.py` for both editions. Extended uses `--edition extended` and writes results to `build/extended/collision-smoke-*/`. The check uses real terrain and rock models with 60/120/144 Hz and 100ms time steps and sends no GUI input. Actual rendering FPS and the user's reported location require separate verification. The existing `diagnostics=1` option in `core/modern.ini` logs requested/resolved movement, terrain correction, contact points/normals and rejected moves to `core/modern.log` at most four times per second.

`check` tests timers, parsers, save-file handling, and related behavior without rendering. Actual rendering FPS, Alt-Tab, visual smoothness, and full progression require separate in-game checks.

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
- External speed changes: return to normal speed from Cheat Engine 3×, 5×, and 10×, comparing smoothness and frame records with `diagnostics=1`.

When sharing results, include the build, display settings, actions checked, and remaining checks. Automated checks passing does not establish full-playthrough coverage or display recovery on every PC.

See [QOL options](qol.md) for shared features and shortcuts, and [Extended differences](extend-features.md) for edition defaults and development status.

## Editing project documentation

Edit the English `.md` or Korean `-ko.md` directly. Name new Korean documents like `example-ko.md`. An AI agent updates the other languages during repository tasks; this is not a continuously running translation service.

[.dev-docs-ai-translation.json](../.dev-docs-ai-translation.json) controls document languages, independently of game languages. English-only edits are synchronized from English; when both languages change, Korean takes priority. Do not manually change the hash on the first line. See [AGENTS.md](../AGENTS.md) for the full rules.

As an exception, `README-ko.md` is maintained by the user and agents do not change it without an explicit editing request. Other README languages are synchronized from the Korean original. When creating or updating documentation, follow each document's purpose and style in [auto-gen-please.json](auto-gen-please.json), updating only the documents needed.

## GitHub CI and release ZIPs

The [Windows workflow](../.github/workflows/windows.yml) builds and checks the base game on pushes to `main`/`dev`, pull requests, and manual runs, storing game ZIPs and SHA-256 files as artifacts. A `main` push automatically publishes a stable Release and advances its patch number after checks pass. Pushes to `dev` and PRs only produce `-local` verification ZIPs without publishing Releases or consuming version numbers. Extended CI edition selection, build and release routing are commented out; Extended remains under local development for now.

To publish a development release or manually publish a stable release, follow these steps on GitHub:

1. Open **Actions → Windows build and release → Run workflow**.
2. Select `main` for a stable release or `dev` for a prerelease.
3. Enable `publish`.
4. After the build and checks pass, a Release is published.

For manual runs, `publish=false` only creates ZIPs and runs checks. Other branches and tag pushes do not publish.

CI runs `setup → build → check → package` on Windows Server 2022 with Python 3.13 and publishes Windows packages only. A native macOS port is planned separately. `check` tests non-graphical runtime behavior; it does not replace actual graphics, rendering FPS, Alt-Tab, or full-playthrough verification. Actual GitHub Actions runs must also be checked separately from local tests.

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

This creates `build/ci-upload/StrandedII-ci-deps-v1.zip` and `.zip.sha256`. It validates every existing game/compiler manifest hash and the font hash before bundling only required files and original licenses. Personal saves/settings and the Blitz editor's examples/media are excluded. The dependency ZIP's complete SHA-256, Release tag, and filename are pinned in [config/ci-dependencies.json](../config/ci-dependencies.json).

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

Then manually run `Windows build and release` in Actions to download the game ZIP. To update dependencies, rebuild the bundle and update the configuration with its printed SHA-256 and a new Release tag/filename. A mismatched ZIP is rejected before extraction, even on a cache hit. Ordinary changes to synchronized documents or game sources do not require rebuilding the dependency ZIP. Changes to licenses included in the bundle require rebuilding it and updating its configured hash.

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
