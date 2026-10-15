<!-- dev-docs-ai-translation: {"document":"docs/known-issue.md","language":"en","syncedHash":"1f3d5fa27e20442c34f5e7f82583cde983ce472b1bb5bd32f9a5c406e2c025a0"} -->

# Known issues

[English](known-issue.md) · [한국어](known-issue-ko.md)

[Development guide](development.md) · [Stability and performance audit](stability-audit.md)

The following issues remain unfixed as of 2026-10-15. They are in the game code shared by Stranded II and Extended. Verification used isolated game-function fixtures in the macOS ARM64 / Metal build of Stranded II; manual mouse interaction, Windows, and Extended execution were not verified.

## Construction preview: overlapping placement and cancellation

**Status:** Crash reproduced when both inputs occur in the same frame. Placement alone and cancellation alone passed.

**Player steps:** Equip a hammer, open the construction menu with the secondary action (right-click by default), and select a building. Hold the left mouse button over the world to rotate the preview, then release it while pressing the right mouse button to cancel. Both inputs must reach the same frame, so manual reproduction depends on timing.

**Cause:** In [game_build.bb](../src/engine/includes/game_build.bb), `game_buildsetup_update()` handles left-button release and right-button press independently. Both call `game_buildsetup_end()`, which frees `in_buildsetup_dummy` without clearing it. The second call frees the same native handle again.

**Verification:** With a valid preview, `in_mrelease(1)=1` and `in_mhit(2)=1` caused two frees of the same handle and exit code 1 with `Invalid or released native handle`. Each input alone exited normally.

**Fix direction, not applied:** Finish placement/cancellation only once per update; guard preview cleanup and clear its handle after freeing it. Retest placement alone, cancellation alone, and overlapping inputs.

## Logboat and sailraft: missing completion event and invalid array access

**Status:** Out-of-bounds array access and missing completion scripts confirmed. A crash was **not** reproduced in the tested macOS release build.

**Player steps:** Build a logboat or sailraft using the normal construction process and perform the final hammer action. This reaches the faulty path, but the boat may appear normally and the missing internal event may have no visible symptom. Confirm the issue by tracing the `build_finish` task rather than expecting an immediate crash.

**Cause:** Vehicle construction represents unit types as negative numbers: logboat `-31`, sailraft `-30`. In `game_build()` in [game_build.bb](../src/engine/includes/game_build.bb), the event-key lookup uses `Abs(typ)`, but the script lookup uses `Dunit_script$(typ)`. It therefore reads before the array instead of retrieving the unit's completion script, which should dispatch `dugoutbuild` or `sailraftbuild`.

**Verification:** The intended script lengths were 87 for the logboat and 421 for the sailraft. Both actual completion tasks received an empty script of length 0; both processes exited normally. The invalid memory access presents a crash risk, but these results do not establish a reproducible release-build crash.

**Fix direction, not applied:** Use `Dunit_script$(Abs(typ))` for vehicle completion. Verify both boats' completion events and check that ordinary building completion remains unchanged.
