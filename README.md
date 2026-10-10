<!-- dev-docs-ai-translation: {"document":"README.md","language":"en","syncedHash":"d19e43a5eb16747907a2feeae6a2f64c274578dea72407cddca88de934facf4d"} -->

## Stranded II : Modern

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />

<details>
  <summary>
    Developer Notes
  </summary>

  Hello everyone!

I'm someone who absolutely loved playing **Stranded II** during my childhood. This game holds a very special place in my heart, and I have so many wonderful memories of it.

Recently, I suddenly felt the urge to play a deserted island survival game again. But even in 2026, I couldn't find anything that captured the same feeling Stranded II gave me.

Unfortunately, when I tried running the game on a modern system, it didn't run very smoothly.

The game was locked at 50 FPS, which made it feel awkward on modern hardware. There were also issues such as textures becoming corrupted after switching away from the game with Alt+Tab and returning to it.

I also discovered that many other players in the community were experiencing similar problems.

Technically, these issues weren't particularly difficult to solve. The real problem was the sheer number of changes required.

For example, fixing the framerate isn't as simple as removing the `20ms` frame limit. The update ticks of every entity also need to be adjusted to work correctly with variable framerates.

Doing all of this manually would require an enormous amount of effort, and modifying so many parts of the code would introduce a significant risk of new bugs.

However, thanks to the recent advancements in AI, I was able to tackle these problems much more easily.

## Plans for the Future

I'm planning to maintain two separate versions of the game:

- **Original Version:** Preserves the original gameplay and content.
- **Expanded Version:** Builds upon the original game with quality-of-life improvements, balance adjustments, and additional content.

The Original Version will not change the core gameplay experience. However, it will include the following improvements:

- **Language Support:** You can now select your preferred language and even add your own translations.
- **Unlocked Framerate:** The framerate cap has been removed, and entity update ticks are now calculated correctly regardless of framerate.
- **Graphics Settings:** Additional graphics options will be available.
- **Quick Save / Quick Load Fixes:** Issues preventing quick saving and loading from working properly have been fixed.
- **Improved Auto Save System:** At least three auto-save slots will be maintained, and a new "Continue" button will be added to the main menu.

## Beyond the Original Game

In the future, I'd also like to expand upon the original game.

This won't be a traditional `mod`. Instead, it will involve directly modifying and extending the game's original core source code.

I haven't decided what to call this version yet, but here are some of the features I have in mind:

- **Quality-of-Life Improvements:** Optional QoL features that can be enabled or disabled in the settings.
- **Rebalancing and New Content:** Gameplay balance adjustments and additional content.
- **Multiplayer Support:** Bringing multiplayer functionality to the game.
- **Improved Random Island Experience:** Making survival on randomly generated islands more engaging and enjoyable, rather than focusing primarily on the scenario mode.

That's all for now!

Please keep in mind that these are just plans for the future, and nothing beyond the current improvements is guaranteed yet.

**Have fun, and enjoy the game!**
</details>

[English](README.md) · [한국어](README-ko.md)

An unofficial modernization of Stranded II, rebuilt with Blitz3D 1.118. It preserves the original survival and exploration experience while supporting higher frame rates and convenient switching between the game and other windows.

The project is under development, with a stable release in preparation.

## Classic and Extended

Classic preserves the current game experience. Extended is a separate edition for future UI, QOL, content, and balance changes. Only the separation foundation is implemented so far; both editions currently have the same gameplay and content.

The editions share engine code but have separate runtime folders, settings, saves, versions, and release ZIPs. In the source checkout, launch Classic with `Play.cmd` and Extended with `Play-Extended.cmd`. Prepare Extended with `setup --edition extended` and `build --edition extended` first. See the [development guide](docs/development.md#classic-and-extended) for boundaries and commands.

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
