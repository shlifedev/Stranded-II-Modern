<!-- dev-docs-ai-translation: {"document":"docs/playing.md","language":"en","syncedHash":"f0477a924ec8af4a4680b410306fdcd7c330478ed913b81b06084e30a7973b1d"} -->

# Playing and settings

[English](playing.md) · [한국어](playing-ko.md)

[Back to README](../README.md) · [Development guide](development.md) · [Localization guide](localization.md)

## Launching the game

On Windows 10 or later, run `Play.cmd` from the repository root. The executable is `build/runtime/StrandedII.exe`. Python is not required to play the game.

The source repository does not contain the executable or original game resources. For a first setup, complete the environment setup and build steps in the [development guide](development.md).

## Frame rate and display mode

Under **Settings → Graphics**, select a resolution, windowed/borderless mode, and 60/120/144 FPS. Left-click a selector for the next value or right-click for the previous value. Choose **Apply**, then close and restart the game to apply the changes. **Cancel** discards the current selection.

New installations default to **144 FPS, borderless mode, and Fit monitor**. Existing settings are preserved, including older 1280×720 settings. FPS is a target; actual frame rates depend on your PC and display environment.

**Fit monitor** calculates the rendering size from the Windows primary monitor. To avoid initialization failures at large resolutions in the current Direct3D path, each dimension is capped at **2048 pixels**. For example, 2560×1440 and 3840×2160 monitors render at 2048×1152, which borderless mode scales to the monitor size. Numbers in the selector show the actual rendering size. Very wide or tall displays also use the existing UI minimum of 800×600.

You can override these choices from PowerShell:

```powershell
.\Play.cmd -fps 144
.\Play.cmd -fps 120
.\Play.cmd -win -fps 60
.\Play.cmd -borderless
```

To keep using a setting, close the game and edit `build/runtime/core/modern.ini`:

```ini
language=ko
fps=144
display=borderless
resolution=desktop
render_width=1280
render_height=720
diagnostics=0
```

| Field | Settings |
| --- | --- |
| `fps` | `60`, `120`, `144` |
| `display` | `borderless`: borderless window; `windowed`: normal window |
| `resolution` | `desktop`: fit the primary monitor; `custom`: use the resolution below. Older settings without this field use `custom` |
| `render_width`, `render_height` | Internal rendering size for `custom`; clamped to at least 800×600, with each dimension capped at 2048 pixels |
| `language` | `ko`: Korean; `en`: English, or an installed language pack's code |
| `diagnostics` | `1` records frame rate and display recovery events in `core/modern.log` |

The `-fps`, `-win`, and `-borderless` launch options override this file. The repository's `config/modern.ini` supplies defaults when first preparing a runtime folder. Change an already prepared game's settings in `build/runtime/core/modern.ini`.

## Changing game speed during testing

Game time and frame waiting use separate clocks. Game time uses a high-resolution clock that reflects speed changes; the frame limiter uses a separate real-time clock and Windows wait functions. This prevents movement and frame-wait stuttering caused by Cheat Engine leaving low-resolution clock changes active after returning to normal speed. The 100ms simulation cap per frame remains, so actual speed in demanding scenes may be lower than the requested multiplier.

If smoothness changes after returning to normal speed, run with `diagnostics=1` and inspect the `FPS actual` records in `core/modern.log`. They measure how often the game calls frame presentation, not the monitor's physical refresh rate.

## Changing language

Under **Settings → Graphics → Language**, use the same choice control as resolution: left-click for the next language and right-click for the previous one. Apply, close, and restart the game. Cancel does not save the selection. Korean is the default game language.

Korean and English translations are bundled; additional packs appear in the language choices when prepared. Missing translations display English, and a configured but unavailable language falls back to English at startup. See the [localization guide](localization.md) for new languages and the [font guide](fonts.md) for per-language font selection. Language switching remains experimental.

## Save files

Saves are stored in `build/runtime/mods/Stranded II/saves/`. Back up this folder before moving to another PC or resetting the runtime folder. Preserve your custom maps and screenshots as well.

- **Continue:** loads the most recently modified `.sav` with a valid header and end marker from the main menu. Manual, quick, and automatic saves are included. The button is disabled when none exist.
- **Quick save/load:** uses the keys assigned under Settings → Controls and one `QUICKSAVE.sav` file. A message appears when no quick save exists.
- **Five autosave slots:** at the existing autosave points, fills `AUTOSAVE.sav` and `AUTOSAVE_2.sav` through `AUTOSAVE_5.sav`, then replaces the oldest slot. The autosave interval itself is unchanged. Automatic loading selects the newest of these five saves.
- **Previous-save backup:** first writes to a `.tmp` file in the same folder, then replaces the save. The previous contents of the overwritten file are kept as `.sav.bak`. Backups are separate from the five autosave slots, and `.tmp` and `.bak` files do not appear in the save list.

To recover a backup, close the game and copy, for example, `QUICKSAVE.sav.bak` to a new name such as `recovered.sav` in the same folder, then load it. If saving fails, the existing file is preserved and an error appears. Header and end-marker checks reject incomplete files; they do not detect every kind of corruption.

Korean text already stored in saves from the earlier Korean patch may remain in Korean after switching to English. Test existing saves using copies and save them under separate names.

Workspaces containing material preserved during cleanup of earlier installations may also contain `build/runtime/preserved/`. Back up that folder too: it holds earlier saves, custom maps, settings, and other user data. Git ignoring `build/` **does not mean that real user data can be deleted.**
