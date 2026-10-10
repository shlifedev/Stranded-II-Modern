<!-- dev-docs-ai-translation: {"document":"README.md","language":"en","syncedHash":"63dc37adb72faf3ac25cb4371f0e48b4bf2d63a3b56387d8d401fdc94730472a"} -->

# Stranded II : Modern

[English](README.md) · [한국어](README-ko.md)

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />

An unofficial update of Stranded II for modern Windows systems. Built with Blitz3D 1.118 and currently under development.

## Getting started

- **Play:** extract the entire release ZIP and run `Play.cmd` inside its game folder. Windows 10 or later is required.
- **Build from source:** follow environment setup and the first build in the [development guide](docs/development.md). The source checkout does not include the EXE or original game resources.
- **Translate:** edit `locales/<code>.tsv` following the [localization guide](docs/localization.md).

## Available now

- Timing adjustments that preserve game speed at 60/120/144 FPS. The default target is 144 FPS.
- Borderless mode, resolution selection, and fixes for corrupted graphics after Alt-Tab. Display settings apply after a restart.
- Quick save/load fixes, five autosave slots, previous-save backups, and Continue on the main menu.
- English/Korean selection and custom language packs and fonts. Language switching is experimental and applies after a restart.

Additional languages and a stable release are planned. Language switching, compatibility with older saves, and different display environments need further verification.

## Classic and Extended

| Edition | Purpose | Source checkout launcher |
| --- | --- | --- |
| Classic | Preserve original gameplay and content | `Play.cmd` |
| Extended | Future separate QOL, UI, content, and balance changes | `Play-Extended.cmd` |

Only the separation foundation is implemented; **both editions currently have the same gameplay and content.** They share engine code but have separate settings, saves, versions, and release ZIPs. See the [development guide](docs/development.md#classic-and-extended) to prepare each edition. Both editions' release ZIPs launch with `Play.cmd` inside their game folder.

## Find the right guide

| Task | Guide |
| --- | --- |
| Launch, change display/language settings, or back up saves | [Playing and settings](docs/playing.md) |
| Edit translations or add a language | [Localization guide](docs/localization.md) |
| Change fonts or distribute language packs | [Font guide](docs/fonts.md) |
| Build, develop features, verify, or publish | [Development guide](docs/development.md) |

You can edit either the English or Korean documents directly; see [editing documentation](docs/development.md#editing-project-documentation). There are no separate contribution rules before the stable release yet.

The notes below describe the project's background and future ideas. Use the feature list above for what is currently implemented.

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

## Copyright

- The original Stranded II game and source code belong to Peter Schauß / [Unreal Software](https://www.unrealsoftware.de/).
- This project is based on the [original Stranded II source](https://github.com/ttyborg/Stranded2).
- See the [copyright and third-party license notices](licenses/THIRD-PARTY-NOTICES.txt) for the game, engine, fonts, and other components.
