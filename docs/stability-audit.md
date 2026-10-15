<!-- dev-docs-ai-translation: {"document":"docs/stability-audit.md","language":"en","syncedHash":"442b9b9bf6af6b082d40d4acc18463f523ff14c22d92aae466b3593548aa33cb"} -->

# Stability and performance audit — 2026-10-15

[English](stability-audit.md) · [한국어](stability-audit-ko.md)

[Known issues](known-issue.md) · [Development guide](development.md)

This report records the audit of [`a65becb`](https://github.com/shlifedev/Stranded-II-Modern/commit/a65becb140e92dfd8c872e1bcda088a4132b4406). It covered shared Stranded II/Extended code, the compiler/runtime, web, Studio, build/verification tools, and resources through risk-focused source review and available automated checks. It does not claim verification of every line or gameplay combination. Four product defects and six verification-tool/status defects were confirmed; the audit did not change product code.

**Subsequent changes:** At documentation time, `01e0778` includes the Studio changes from `a09a0d6`. The T01/T02 test-environment fixes are present in source, and new projects use the added v3 storage structure and session handling. Test counts and performance measurements below belong to the audited commit, not the current full suite or v3 performance. Game builds and gameplay checks were not rerun for this documentation update. D03 and P01 require verification against each current storage format.

## Product defects

P1 prioritizes data loss, process termination, or invalid memory reads; P2 is the next priority. Source links aid navigation; reproduction results refer to the baseline commit above.

### D01 · P1 · An incorrect map password deletes the current scene

- **Reproduction:** With terrain and an object in the in-game editor, call `load_map` on a protected map with the wrong password.
- **Observed:** The actual macOS game engine returned `0`; existing objects fell from `1→0` and the terrain handle became `0`.
- **Cause:** `load_map` in [e_load_map.bb](../src/engine/includes/e_load_map.bb) calls `e_clear()` before checking the header and password. The password-error return also omits `CloseFile`. The missing close was confirmed in source; OS handle growth was not measured.
- **Fix and acceptance:** Validate input before mutation, replace the scene only after a successful load, and close files on every exit path. Wrong passwords, truncated files, and invalid headers must preserve the current scene while valid map/save round trips continue to work.

### D02 · P1 · A short string read includes uninitialized memory

- **Reproduction:** Call `ReadString` on a file declaring a length of `64` but containing only `1` payload byte.
- **Observed:** The actual macOS console runtime returned a string of length `64`. The remaining memory contents were not collected.
- **Cause:** `bbReadString` in [stream.cpp](../src/blitz3d/src/modules/bb/stream/stream.cpp) checks only whether the read returned a nonzero value, then constructs `std::string(buff,len)` without requiring the full requested length. Its length-based allocation is also unbounded.
- **Impact:** Corrupt files can produce nondeterministic strings and excessive allocations. The path that includes uninitialized memory was confirmed; exploitation or external disclosure was not demonstrated.
- **Fix and acceptance:** Define exact-length/EOF handling and validate remaining size and allowed lengths in format parsers. Partial payloads, negative lengths, and excessive lengths must fail predictably while valid strings and the 32-bit binary ABI remain compatible.

### D03 · P1 · Inspecting a map with a NaN coordinate terminates Studio's server

- **Reproduction:** Set a new map's player-start `x` to NaN, then send `map.import → inspect → schema` to the JSON server.
- **Observed:** Import and validation succeeded and persisted revision `1`. JSON serialization of `inspect` terminated the server with `ValueError`; the subsequent `schema` request received no response.
- **Cause:** [maps.py](../scripts/studio_core/maps.py) and [project.py](../scripts/studio_core/project.py) do not validate imported coordinate finiteness, and serialization with `allow_nan=False` in [studio.py](../scripts/studio.py) sits outside request exception handling. Numeric checks on editing operations do not protect import paths.
- **Fix and acceptance:** Reject NaN/±Infinity in imported coordinates, rotation, scale, and terrain values before committing; convert serialization errors into request-level errors. The project must remain unchanged and the next valid request must succeed. Recheck v1/v2/v3 behavior with the subsequent Studio changes.

### D04 · P2 · A corrupt draft file blocks Studio startup

- **Reproduction:** Put a truncated `authoring-drafts.json` in isolated `userData` and execute the real startup code from [main.cjs](../studio/electron/main.cjs), replacing only Electron APIs with VM doubles.
- **Observed:** A `JSON.parse` error led to `app.quit` before window creation. This did not verify a full Electron startup under the error condition.
- **Fix and acceptance:** Preserve and quarantine the corrupt file, allow startup without drafts, and provide recovery guidance. Handle truncated JSON, invalid top-level types, and read failures while preserving normal drafts across sessions.

## Verification-tool defects

| ID · priority | Confirmed at the audited commit | Improvement and subsequent status |
| --- | --- | --- |
| T01 · P2 | The [bridge tests](../studio/tests/bridge.test.cjs) omitted `debugui.js`, causing 25 failures with `s2InstallDebugUi is not defined`. The production HTML load order was correct. | All 25 passed in a diagnostic copy with debugui, DOM doubles, and multiple event listeners. The corresponding fix is present in `a09a0d6`. These are not 25 product defects. |
| T02 · P2 | The [launcher tests](../tests/test_studio_launch.py) produced two false failures by comparing macOS `/var` expectations with canonical `/private/var` paths. | All four launcher tests passed in a copy using `resolve()` on the fixture root. The corresponding fix is present in `a09a0d6`. |
| T03 · P2 | [The legacy benchmark](../scripts/benchmark_native.py) injects [native_stress.bb](../tests/native_stress.bb), whose `bench_camera` conflicts with the built-in benchmark. Actual Blitz compilation failed with `duplicate identifier`. | Separate fixture names or consolidate on the built-in benchmark entry point. Verify compilation and a valid trial in both editions. |
| T04 · P2 | [verify_web.py](../scripts/verify_web.py) uses a Windows ABI fixture, failing to resolve `modern_file_move` on macOS. Its subsequent Node lookup is also fixed to `*/node.exe`. | Select the ABI reference and Node path for the host and report the actual comparison platform. The diagnostic macOS/WASM comparison passed; this does not make the original command successful. |
| T05 · P2 | The same verifier fails to escape `@` in `--embed-file` paths, interpreting the ancestor before `@` as the source instead of the fixture. Prolonged packaging was observed. | Apply `@→@@` escaping as [web_build.py](../scripts/web_build.py) already does. The adjusted copy completed the comparison. Test temporary paths containing `@`/spaces and confirm unrelated files cannot enter local artifacts. |
| T06 · P3 | A verification command in [web_build.py](../scripts/web_build.py) exits with `1` but leaves the report's `web_check` as `running`. | Record the failed stage, error, and completion time while propagating the exception. Verify report status agrees with the exit code on success and failure. |

For T05, only the audit's packaging process was terminated; nothing was deployed or transmitted externally. T06's P3 priority denotes a diagnostic-status improvement.

## Performance

### P01 · Studio edit-history replay cost — measured

Using a v1 project at the baseline commit, `strength=0` brush operations were accumulated on a 32×32 map. The final map remained 91,163 bytes. Median `snapshot` times across three calls were:

| Retained operations | 0 | 50 | 200 | 500 | 1,000 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Query time (ms) | 13.64 | 16.73 | 25.76 | 44.75 | 75.76 |

`map_bytes` in [project.py](../scripts/studio_core/project.py) reparses and replays every entry in `edits`. Cost increased approximately 5.56× for unchanged geometry, but the sequential measurements include OS caching effects and do not measure total UI latency. Revision caching, omission of no-op edits, and checkpoints are candidates; undo/redo and original binary preservation must be verified together. **These numbers cannot be applied directly to the subsequent v3 current-state storage structure.**

### P02 · Metal rendering baseline — measured, bottleneck candidates

Actual macOS ARM64/Metal rendering ran at 2048×1152 with no FPS cap, a three-second warmup, and eight seconds of measurement, using a fresh process per trial. Settings: water/lighting/atmosphere ON, shadows OFF, FXAA, water=6, grass=4, view=4, AI/animation OFF. The built-in benchmark was automatically driven in a disposable engine copy with real time; these are not interactive-gameplay FPS measurements.

Three trials per condition plus one retry produced 13 attempts; three invalid focus-loss trials were excluded. Each column reports the median of per-trial values; p95 is not a percentile pooled across all frames.

| Added objects | Valid trials | FPS [minimum–maximum] | Mean frame (ms) | p95 (ms) | Peak RSS (MiB) |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 3 | 179.53 [119.96–224.98] | 5.56 | 9.09 | 285.3 |
| 256 | 3 | 156.84 [149.56–208.31] | 6.37 | 11.88 | 294.2 |
| 768 | 2 | 149.06 [136.51–161.60] | 6.75 | 13.17 | 301.8 |
| 1,536 | 2 | 129.66 [121.35–137.98] | 7.74 | 14.09 | 316.2 |

From 0→1,536 objects, CPU self times changed from 0.45→2.34ms for main rendering, 0.35→1.29ms for reflection, and 0.54→1.00ms for physics. The flip interval also measured 2.89–4.35ms, so overall FPS does not isolate a cause. Further profile main/reflection scene preparation and draw collection without reducing reflection cadence, grass, distance, or graphics quality. GPU pass times exclude some uploads and gaps and may overlap CPU time. No p99/raw frames were collected; peak RSS is not evidence of a long-running leak.

### Unmeasured candidates

- **P03:** [QOL inventory](../src/engine/includes/qol_inventory.bb) rebuilds rows and uses insertion sorting. Caching until inventory changes merits measurement with large inventories; a bottleneck has not been established. Preserve edition defaults and saved selections.
- **P04:** Studio's main JavaScript bundle measured approximately 2.98MB, or 779KB gzip. Lazy-loading heavy screens is a candidate; no perceived improvement is claimed without cold-start measurements.

## Verification scope and limits

All results below belong to the audited commit. Passing diagnostic copies do not override failures of the original tests.

| Check | Result and limit |
| --- | --- |
| Full Python suite | 161 passed, 2 failed out of 163 (T02). |
| Studio `npm test` | TypeScript/Vite build passed. 9 passed, 25 failed out of 34 (T01). |
| macOS `check --suite full` | Passed: 613-byte ABI match against original Blitz3D 1.118, type lifetime, actual Metal rendering/readback, bundle/profile preservation, and 126 game-fixture checks. Game-function fixtures use synthetic time; save/backup/reload checks use isolated test data. |
| Web build/check | Build passed. Original check failed at T04; preceding web unit checks and verification of 23 converted audio files passed. Audio listening was not tested. |
| Web diagnostic copy | With host, Node, and `@` path adjustments, macOS/WASM matched across 4,645 bytes; terrain colour checks covered 16,384 pixels with two transfers. Real Emscripten VFS and simulated DOM/storage transactions passed. This does not replace Windows parity or actual IndexedDB failure recovery. |
| Browser/Studio | Isolated Electron 44.7.0/Chromium 152 rendered a Studio WebGPU scene and completed Play→Stop. The web game rendered its English menu, island, and water after simulating focus/visibility. This was not actual input/focus verification. |
| Resources | Both editions had 2,834 entries per EN/KO/DE catalog, with no missing entries requiring English fallback. All 13 maps in each original/web/Studio resource set passed unchanged binary round trips. |

Windows D3D12 execution and Windows build/check for both editions, native Extended gameplay, the full campaign, long-running memory stability, actual IME/Alt-Tab/pointer capture, Safari/Firefox/mobile, and GPU device-loss recovery remain unverified. The existing incomplete per-face cube-texture storage/sampling was not counted as a new defect.

Suggested order: fix D01/D02 and reproduce D03 in current formats; restore verification tools and add D04 recovery; remeasure P01 per storage format; verify both Windows editions; then measure sustained interactive performance. Every fix needs regression checks that preserve valid files, saved data, and graphics quality.
