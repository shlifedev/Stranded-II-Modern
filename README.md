<!-- dev-docs-ai-translation: {"document":"README.md","language":"en","syncedHash":"b15ad072306547e4e4e97bbcf542278a5c47bab481660245f72f836fcd3225c3"} -->

# Stranded II : Modern

[English](README.md) · [한국어](README-ko.md)

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />

An unofficial update of Stranded II for modern Windows systems. Built with Blitz3D 1.118.

## Getting started

Download the version for your operating system, or play directly in your browser.

- **[Windows 10 & 11](https://github.com/shlifedev/Stranded-II-Modern/releases/latest):** Download the Windows ZIP from GitHub Releases.
- **[Web Browser](https://shlifedev.github.io/Stranded-II-Modern/):** Play the experimental WebGPU version without installing it. Requires desktop Chrome/Edge with WebGPU and JSPI.
- **[macOS (Apple Silicon)](https://github.com/shlifedev/Stranded-II-Modern/releases/latest):** Download the macOS ZIP from GitHub Releases.

## Technical improvements

The main technical changes are as follows.

- **Graphics improvements:** Replaced the original Direct3D 7 renderer with a renderer based on WebGPU. (DirectX 12/Metal)
  Added optional per-pixel lighting, sunlight shadows, water reflections, and atmospheric effects.
- **Refresh rate improvements:** Simulation updates are calculated from actual elapsed time so that the game maintains its original speed at high FPS. (60·120·144 Hz)
- **Gameplay improvements:** Added individually configurable QOL features, including a recipe browser, quick storage transfers, automatic closing of empty corpse inventories, automatic sorting, and held construction input. See the [QOL settings guide](docs/qol.md) for usage details.
- **Other bug fixes:** Fixed corrupted graphics after Alt-Tab and quick save/load errors.
- **Localization:** Players can now easily translate the game themselves. (Support for adding languages.)

For other technical changes, see the following documents.

[WebGPU renderer](docs/wgpu-migration.md) · [Web architecture](docs/web-architecture.md) · [macOS architecture](docs/macos-architecture.md) · [Studio](docs/studio.md) · [Development guide](docs/development.md) · [Localization guide](docs/localization.md)

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
