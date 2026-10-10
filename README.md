<!-- dev-docs-ai-translation: {"document":"README.md","language":"en","syncedHash":"1492ad2e38bb97d0778f9fe677eb254434bf121aee8e86685a26589cb7d6b27d"} -->

## Stranded II : Modern

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />


[English](README.md) · [한국어](README-ko.md)

An unofficial modernization of Stranded II, rebuilt with Blitz3D 1.118. It preserves the original survival and exploration experience while supporting higher frame rates and convenient switching between the game and other windows.

The project is under development, with a stable release in preparation.

## Changes

- Adjusted the original `20ms` (50Hz) updates to use elapsed time, keeping game speed consistent at 60/120/144 FPS. The default target is **144 FPS**.
- Added borderless window mode and fixed corrupted graphics after Alt-Tab.
- Added resolution, 60/120/144 FPS, and window mode choices in the settings. Display changes take effect after restarting the game, with an automatic resolution option that fits the monitor.
- Fixed unresponsive quick save/load. Save files are replaced safely with backups of the previous version, and autosaves rotate through five slots. **Continue** on the main menu loads the most recent save.
- **Localization support:** one executable can switch between Korean and English. Change the language under Settings → Graphics → Language, then restart the game. This feature is experimental. Add community translations and fonts through language packs and font file paths.

## Planned

- Translations for Japanese and other additional languages.
- A stable release after checking language switching, compatibility with existing saves, and different display environments.

## Running the game

Use `Play.cmd` in a folder with a prepared build. If you downloaded only the source, follow the [development guide](docs/development.md) to prepare the original game resources and build the executable.

Windows 10 or later is required. See [playing and settings](docs/playing.md) for display settings and rendering limits on high-resolution monitors.

## Documentation

- [Playing and settings](docs/playing.md) — launching the game, frame rate, display and language settings, and saves.
- [Development guide](docs/development.md) — environment setup, directory roles, builds, and verification.
- [Localization guide](docs/localization.md) — editing translations and adding text and language packs.
- [Font guide](docs/fonts.md) — font file selection, missing-glyph fallback, previews, and distribution.

English is the default documentation language. You can edit the corresponding `-ko.md` file directly; an AI agent synchronizes the changed document across the languages in [.dev-docs-ai-translation.json](.dev-docs-ai-translation.json). Korean edits take priority when multiple versions change. See [the documentation workflow](docs/development.md#editing-project-documentation).

## Contributing

There are no separate contribution rules before the stable release. A contribution guide will be added later.

## Copyright

- The original Stranded II game and source code belong to Peter Schauß / [Unreal Software](https://www.unrealsoftware.de/).
- This project is based on the [original Stranded II source](https://github.com/ttyborg/Stranded2).
- See the [copyright and third-party license notices](licenses/THIRD-PARTY-NOTICES.txt) for the original license and the individual licenses of the engine, fonts, and other components.
