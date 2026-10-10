<!-- dev-docs-ai-translation: {"document":"docs/playing.md","language":"en","syncedHash":"99b240c89dfc474341c596a6da49d726eccfe4a87bc158ae48e812df6def4215"} -->

# Playing and settings

[English](playing.md) · [한국어](playing-ko.md)

[Back to README](../README.md) · [Development guide](development.md) · [Localization guide](localization.md)

## Launch the game

Windows 10 or later is required. Playing does not require installing Python or Blitz3D.

| What you downloaded | How to launch |
| --- | --- |
| Release ZIP | Extract the entire ZIP and run `Play.cmd` inside its game folder |
| Source checkout — Classic | Complete the [first build](development.md#first-build), then run `Play.cmd` in the repository root |
| Source checkout — Extended | [Prepare Extended](development.md#classic-and-extended), then run `Play-Extended.cmd` in the repository root |

The base edition keeps its existing **Stranded II** name. `Classic` is a development identifier, not a suffix in the window title or executable name. Extended uses the window title **Stranded II - Extended**.

Below, **game folder** means the folder containing `StrandedII.exe` for the base edition or `StrandedII-Extended.exe` for Extended. In a source checkout, it is `build/runtime/` for Classic or `build/extended/runtime/` for Extended. The editions have separate settings and saves; automatic import is not provided.

## Change display, frame rate, or language

1. Open **Settings → Graphics**.
2. Select the resolution, display mode, FPS, or language. Left-click a selector for the next value; right-click for the previous one.
3. Click **Apply**, close the game, and launch it again. **Cancel** discards your changes.

| Setting | Choices and defaults |
| --- | --- |
| FPS | 60 / 120 / **144 (default)**. Actual FPS depends on your PC and the scene. |
| Display mode | Windowed / **borderless (default)** |
| Resolution | **Fit monitor (default)** or a specified size |
| Language | **English (default)** / Korean / added language packs |

Existing installations keep saved settings. **Fit monitor** uses the primary monitor, with rendering dimensions clamped to at least 800×600 and at most 2048 pixels per side. On 2560×1440 and 3840×2160 monitors, the game renders at 2048×1152 and scales to the borderless window. The resolution selector shows the actual rendering size.

Language switching is experimental. Missing translations or uninstalled languages fall back to English. Korean stored as plain sentences in older saves may remain unchanged after switching. To edit translations or add a language, see the [localization guide](localization.md).

## Graphics quality and sound

Use the game's settings menu. New installations start with view distance, terrain, water, sky, effects, and grass at their highest levels. 2D effects, light effects, wind movement, fog, and hardware multitexturing are enabled. Motion blur is enabled at 35%; music and sound effects are each at 50%.

An existing `mods/Stranded II/sys/settings.cfg` keeps its saved values.

## Saves and backups

Saves are in **`mods/Stranded II/saves/` inside the game folder**. Back up this folder before moving to another PC or cleaning the game folder. Also keep custom maps, screenshots, and `preserved/` if present.

| Feature | Behavior |
| --- | --- |
| Continue | Loads the most recently modified manual, quick, or autosave with a valid header and end marker. Disabled if none exists. |
| Quick save/load | Uses the keys assigned in Settings → Controls and the file `QUICKSAVE.sav`. |
| Autosave | Fills five slots, `AUTOSAVE.sav` and `AUTOSAVE_2.sav`–`AUTOSAVE_5.sav`, then replaces the oldest. The interval is unchanged from the original. Automatic loading uses the newest slot. |
| Previous-save backup | Keeps the file being overwritten as `.sav.bak`. Backups do not appear in the save list. |

To recover a backup, close the game and **copy** a file such as `QUICKSAVE.sav.bak` **to a new name, `recovered.sav`**, then load it. Also use copies and separate save names when testing older saves.

Save errors preserve the existing file and show an error. Header and end-marker checks cannot detect every kind of corruption. Development `build/` folders contain real saves too; back them up before deleting a whole folder.

## Settings file and launch options

To edit settings directly, close the game and open **`core/modern.ini` in the game folder**.

```ini
language=en
fps=144
display=borderless
resolution=desktop
render_width=1280
render_height=720
diagnostics=0
```

| Setting | Values |
| --- | --- |
| `language` | `en`, `ko`, or an installed language code |
| `fps` | `60`, `120`, `144` |
| `display` | `borderless`, `windowed` |
| `resolution` | `desktop` (fit monitor), `custom` (use dimensions below). Older settings without this entry use `custom`. |
| `render_width`, `render_height` | Rendering size for `custom`, at least 800×600 and at most 2048 pixels per side |
| `diagnostics` | `1` records frame and display recovery information in `core/modern.log` |

Launch options override the settings file. Specify them when launching in PowerShell. Use `Play-Extended.cmd` for Extended in a source checkout.

```powershell
.\Play.cmd -fps 120
.\Play.cmd -win -fps 60
.\Play.cmd -borderless
```

`config/modern.ini` and `config/editions/extended/modern.ini` are development defaults. To change an already prepared game, edit its `core/modern.ini` above.

For stuttering or recovery from external speed changes, inspect `FPS actual` recorded with `diagnostics=1`. This measures the game's presentation calls, not the monitor's refresh rate. Game-time advancement is capped at 100ms per frame, so actual speed in demanding scenes may be lower than the requested multiplier.
