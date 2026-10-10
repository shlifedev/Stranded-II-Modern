<!-- dev-docs-ai-translation: {"document":"docs/official-site-post.md","language":"en","syncedHash":"a2a33e3b99c40504daabb3c48c97f2ce3d2b2122fe9df0c86d609f574e8459f3"} -->

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

I plan to produce separate builds: one preserving the original content, and another adding convenience features and balance changes. The original version will preserve gameplay, with the following changes:

- You can now select a language and add languages yourself.
- The refresh rate limit has been removed. (Entity ticks are calculated correctly too.)
- Additional graphics options.
- Fixes for quick save/load not working.
- At least three Auto Save slots and a Continue button on the main menu.

If I expand the original game in the future, it will involve modifying the game's core source code rather than making a `mod`. I haven't decided what to call it yet, but these are some ideas:

- QoL features (toggleable in settings).
- Rebalancing and additional content.
- Multiplayer support.
- More enjoyable gameplay on random islands beyond the scenario mode.
- Cross-platform support. (This may require modifying the Blitz3D engine. I intend to try if it is feasible.)

That's all for now. These are only plans, though. Enjoy the game!
