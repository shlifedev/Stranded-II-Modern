<!-- dev-docs-ai-translation: {"document":"docs/official-site-post.md","language":"en","syncedHash":"53f640022cbbb91c8ee5de8b9c713fceb3fc502540316cb4a94a5b67524987b1"} -->

# Draft post for the official site

[English](official-site-post.md) · [한국어](official-site-post-ko.md)

Hello! I really enjoyed playing Stranded II as a child.
It is a special game to me, with many lasting memories.

Recently, I suddenly felt like playing a deserted island survival game. Even in 2026, I couldn't find another game that resonated with me the same way.
However, when I tried running it on a modern system, it did not run smoothly.

Unfortunately, its frame rate was locked at 50 FPS, which felt awkward on modern devices. Some textures also became corrupted after switching away with Alt-Tab and returning to the game.

I found that many other players in the community had similar problems.

These problems were not especially difficult to solve, but there were many places to change. In particular, fixing the frame rate takes more than removing the `20ms` limit: every entity's update tick also needs to account for it. Doing this manually would take a lot of work, and so many changes could easily introduce bugs.

Recent advances in AI have made it much easier to solve these problems.

The project now maintains Stranded II, preserving the original content and game rules, separately from Extended, intended for future balance and content changes. Both editions share these improvements:

- You can now select a language and add languages yourself.
- The refresh rate limit has been removed. (Entity ticks are calculated correctly too.)
- A shared WebGPU renderer with optional lighting, shadows, water, atmosphere and anti-aliasing settings.
- Fixes for quick save/load not working.
- Five rotating autosave slots and a Continue button on the main menu.
- Individually configurable recipe browsing, quick storage transfers, automatic closing of empty corpses, display sorting and held construction input. All default to OFF in Stranded II and ON in Extended; saved selections are preserved.

Windows builds use a custom LLVM-based Blitz compiler and Dawn/D3D12 runtime. The base game also has experimental WebGPU browser and Apple Silicon macOS versions (Dawn/Metal). The web target requires desktop Chrome/Edge with WebGPU and JSPI; the Mac app targets macOS 15 or later. This does not establish verification of the entire campaign or every environment.

Extended is currently for local development, with CI builds and distribution disabled. It has no exclusive gameplay or content yet. Future work on the game core and edition-specific resources may explore these ideas:

- Rebalancing and additional content.
- Multiplayer support.
- More enjoyable gameplay on random islands beyond the scenario mode.

These ideas remain plans, not implemented features. See the [QOL guide](qol.md), [edition differences](extend-features.md) and [development guide](development.md) for current usage. Enjoy the game!
