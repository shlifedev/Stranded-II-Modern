<!-- dev-docs-ai-translation: {"document":"README.md","language":"en","syncedHash":"b42caa6eac9a657444365220359523c4d4f3972555940fcd5c7c3374efdca8fe"} -->

# Stranded II : Modern

[English](README.md) · [한국어](README-ko.md)

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />

An unofficial update of Stranded II for modern Windows systems. Built with Blitz3D 1.118.

## Getting started

- **Play:** extract the entire release ZIP and double-click `StrandedII.exe` in the game folder. Windows 10 or later is required; no separate installation is needed.
- **Build from source:** follow environment setup and the first build in the [development guide](docs/development.md). The source checkout does not include the EXE or original game resources.
- **Translate:** edit `locales/<code>.tsv` following the [localization guide](docs/localization.md).

## Improvements

- Refresh rate - The original game ran at 50Hz; it now supports 60Hz/120Hz/144Hz+.
- Display - Borderless mode, resolution selection, and fixes for corrupted graphics after Alt-Tab. Display settings apply after a restart.
- Saving - Quick save/load fixes, five autosave slots, previous-save backups, and Continue on the main menu.
- Localization - Players can now easily translate the game themselves. (Support for adding languages.)

## Find the right guide

| Topic | Guide |
| --- | --- |
| Translate, add languages, change fonts, or distribute language packs | [Localization guide](docs/localization.md) |
| Build, develop features, verify, or publish | [Development guide](docs/development.md) |

You can edit either the English or Korean documents directly; see [editing documentation](docs/development.md#editing-project-documentation). There are no separate contribution rules before the stable release yet.

The notes below describe the project's background and future ideas. Use the feature list above for what is currently implemented.

<details>
  <summary>Developer notes</summary>

Hello!

I loved playing **Stranded II** as a child. This game means a lot to me, and I have many happy memories of it.

Recently, I felt like playing a deserted island survival game again. But even in 2026, I couldn't find another game that felt quite like Stranded II.

Unfortunately, the game didn't run smoothly on modern systems. The 50 FPS limit felt awkward, and textures could become corrupted after switching away with Alt-Tab and returning. Other players in the community were experiencing similar problems.

The technical problems weren't especially difficult, but there were many places to change. For example, fixing the frame rate takes more than removing the `20ms` limit. Every entity's update timing also needs to work correctly at variable frame rates.

Doing it manually would take considerable effort, and changing so much code could introduce new bugs. Recent advances in AI have made this work much easier.

### Future plans

I plan to maintain two separate versions.

- **Original version:** Preserve the original gameplay and content.
- **Expanded version:** Add quality-of-life improvements, balance changes, and new content.

I intend to preserve the original version's core experience while including these improvements.

- **Language support:** Select a language and add your own translations.
- **Unlocked frame rate:** Remove the frame limit and calculate entity update times correctly regardless of frame rate.
- **Graphics settings:** Provide additional graphics options.
- **Quick save/load fixes:** Fix quick saving and loading that did not work.
- **Improved autosaving:** Keep at least three autosave slots and add a Continue button to the main menu.

### Beyond the original

In the longer term, I'd like to expand the game by modifying and extending its core source code rather than making a conventional `mod`.

I haven't named this version yet. Here are some ideas for it.

- **Quality-of-life improvements:** QOL features that can be toggled in settings.
- **Rebalancing and new content:** Balance changes and additional content.
- **Multiplayer:** Playing together.
- **Improved random islands:** Make survival on randomly generated islands more enjoyable alongside the scenario mode.

These are future ideas; features beyond the current improvements have not been finalized.

**Enjoy the game!**
</details>


### Bugs and feature requests

Please submit bugs, feature requests, and translation requests on GitHub.

## Copyright

- The original Stranded II game and source code belong to Peter Schauß / [Unreal Software](https://www.unrealsoftware.de/).
- This project is based on the [original Stranded II source](https://github.com/ttyborg/Stranded2).
- See the [copyright and third-party license notices](licenses/THIRD-PARTY-NOTICES.txt) for the game, engine, fonts, and other components.
