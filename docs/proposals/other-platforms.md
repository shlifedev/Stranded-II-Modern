<!-- dev-docs-ai-translation: {"document":"docs/proposals/other-platforms.md","language":"en","syncedHash":"80f79bbcccd53c20783886ab38be30d71e9a88778d97610a9774bf3191169b5a"} -->

# Feasibility of additional platforms

[English](other-platforms.md) · [한국어](other-platforms-ko.md)

[Development guide](../development.md) · [How the macOS version works](../macos-architecture.md) · [How the web version works](../web-architecture.md) · [WebGPU renderer migration](../wgpu-migration.md)

The current release targets are **Windows x64 (Dawn/D3D12)**, **Apple Silicon macOS 15 or later (Dawn/Metal)**, and **desktop Chrome/Edge with WebGPU and JSPI**. This proposal assesses how far other platforms can be supported, based on code evidence and the current external landscape. It does not change the supported scope. Ubuntu/Linux desktop is covered by a separate proposal (`docs/proposals/ubuntu-desktop.md`); this document mentions it only where it overlaps with Steam Deck.

> **Basis**: commit `3a9dfc7`, 2026-10-30. No builds or device runs were performed. "Confirmed" means the repository code or a pinned external artifact was inspected directly; "unconfirmed" means public sources could not confirm it. Effort estimates are rough figures for one developer familiar with this repository.

## Summary

| Platform | Verdict | Estimated effort | Key evidence and blockers | Priority |
| --- | --- | --- | --- | --- |
| Steam Deck — existing Windows build under Proton | Possible with conditions (unverified) | 0.5–1 week to check on a device, +0.5–1 week for a Vulkan backend option | D3D12 → vkd3d-proton path unverified; no gamepad support, so a Steam Input keyboard/mouse template is needed | 1 |
| Web — desktop Firefox and Safari | Possible with conditions (unverified) | 1–2 weeks | Both required features are listed in support tables, but real rendering and storage are unverified; Firefox has not shipped WebGPU on Linux | 2 |
| ChromeOS — web build | Possible with conditions (unverified) | 0.5 week | WebGPU in Chrome 113+, JSPI in 137+ | With 2 |
| Windows on ARM64 — x64 emulation | Possible with conditions (unverified) | 0.5 week | Windows 11 only; Dawn D3D12 has little track record on ARM64 GPU drivers | 3 |
| Gamepad and virtual-cursor input layer (native Steam Deck UX, prerequisite for mobile and consoles) | Conditional | 4–8 weeks | The runtime does not pass controllers to the game; the UI depends on an absolute cursor and hover | 4 |
| Windows on ARM64 — native | Conditional | 2–4 weeks | No ARM64 prebuilts for Dawn or OpenAL Soft; toolchain and linker fixed to x64 | Only if emulation shows problems |
| Intel Mac / Universal 2 | Possible | 1–2 weeks + CI | Dawn x86_64 artifacts exist; toolchain and validation fixed to arm64; declining Intel Mac demand | 5 |
| (Side finding) Apple Silicon on macOS 12–14 | Conditional | 1–2 weeks | The xcframework from the same Dawn revision targets macOS 12.0 | Review with 5 |
| Native Linux on SteamOS | Conditional | Ubuntu port + 1–2 weeks | No Linux runtime or X11/Wayland surface | After the Ubuntu proposal |
| iPadOS (keyboard and trackpad) | Conditional | 6–10 weeks | iOS runtime, Metal surface, resource installation, App Store rights documentation | 6 |
| iOS (iPhone, touch) | Conditional | iPadOS + 6–10 weeks | Touch UI, small screens, review | 7 |
| Android (+ Android apps on ChromeOS) | Conditional | 10–16 weeks | New runtime, Dawn Vulkan, OpenAL Soft, asset copying, touch, device variation | 7 |
| Mobile browsers | Not possible (current input model) | After a touch layer | Assumes Pointer Lock and a keyboard | On hold |
| ChromeOS — Linux container | Not recommended | — | VM graphics acceleration and Vulkan availability vary by device (unconfirmed) | Excluded |
| Consoles (Switch, Xbox, PlayStation) | Not possible as things stand | Months per platform once non-technical barriers are resolved | NDA SDKs, rights-holder permission, the CC license's technical-measures clause, certification | On hold |

## Recommended order

1. **Run the existing Windows build on Steam Deck through Proton.** This checks a representative handheld PC without code changes, and the result also informs Proton/Wine use on ordinary Linux. If the D3D12 path misbehaves, check whether a single setting that selects the Vulkan backend, already included in the pinned Windows Dawn library, works around it.
2. **Check the web build on desktop Firefox, Safari and ChromeOS.** The build stays the same and startup uses feature detection, so verification plus a wording update could widen reach.
3. **For Windows on ARM64, check x64 emulation on a device first.** A native build needs Dawn and OpenAL Soft built from source; pursue it only if emulation shows rendering errors or a large slowdown.
4. **Build a gamepad and virtual-cursor input layer.** It lets Steam Deck play without a keyboard/mouse template and is the shared prerequisite for Android, iOS and consoles.
5. **Decide on the Mac by demand.** macOS 26 is the last release for Intel Macs, so their value is shrinking. Using the xcframework from the same Dawn revision to lower the Apple Silicon minimum OS may reach more players.
6. **After rights are confirmed and the input layer exists, proceed with iPadOS, then iPhone and Android.** An iPad with keyboard and trackpad changes the original controls the least.
7. **Do not start on consoles until the original rights holder grants permission and a publisher is in place.**

## What changes when adding a platform

A new platform generally touches the following points. All entries are confirmed.

| Layer | Current state | Evidence |
| --- | --- | --- |
| Toolchain host and target | Only Windows or Apple Silicon macOS is accepted; `BB_PLATFORM` and `ARCH` are fixed to `win64/x86_64` or `macos/arm64` | [`blitz3d_toolchain.py:26-31`](../../src/devtools/blitz3d_toolchain.py#L26-L31), [`:213-245`](../../src/devtools/blitz3d_toolchain.py#L213-L245) |
| Development commands | `--platform windows\|web\|macos` | [`dev.py:125`](../../src/devtools/dev.py#L125) |
| Native runtime selection | For the native game, Windows uses `s2windows` and everything else uses `s2macos` | [`runtime/CMakeLists.txt:236-243`](../../src/blitz3d/src/runtime/CMakeLists.txt#L236-L243) |
| Render surface and backend | Three branches: macOS Metal layer, Windows HWND, otherwise an Emscripten canvas. Backend fixed to Metal or D3D12 | [`graphics.webgpu.cpp:227-245`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L227-L245), [`:259-264`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L259-L264) |
| Graphics system libraries | Only macOS and Windows sets are specified | [`graphics.webgpu/CMakeLists.txt:9-13`](../../src/blitz3d/src/modules/bb/graphics.webgpu/CMakeLists.txt#L9-L13) |
| Audio | Apple uses the system OpenAL framework, Windows uses OpenAL Soft, others use a system `openal` | [`audio.openal/CMakeLists.txt:6-12`](../../src/blitz3d/src/modules/bb/audio.openal/CMakeLists.txt#L6-L12) |
| Platform services | Each platform's `services.bb` implements `modern_*` functions such as timing, windows, file replacement and the search field | [`platforms/macos/services.bb`](../../src/engine/platforms/macos/services.bb), [`services.mm`](../../src/native/macos/services.mm) |
| Linker | Windows targets are fixed to `/machine:X64` | [`linker_lld.cpp:328-329`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L328-L329) |

Some pieces are already in place:

- CMake still has the upstream `woa64`, `linux`, `ios`, `android` and `nx` branches ([`CMakeLists.txt:116-166`](../../src/blitz3d/CMakeLists.txt#L116-L166)).
- The pinned LLVM 19.1.5 is built with the `AArch64;ARM;WebAssembly;X86` targets ([blitz3d-ng/build-llvm `llvm.cmake`](https://github.com/blitz3d-ng/build-llvm/blob/master/llvm.cmake)), and the compiler initializes all targets for a non-host triple ([`codegen_llvm.cpp:86-119`](../../src/blitz3d/src/tools/compiler/codegen_llvm/codegen_llvm.cpp#L86-L119)). Cross-compilation is not blocked at the code generation stage.
- The 32-bit number / 64-bit pointer ABI is already compared against original results on AArch64 (macOS) and x86_64 (Windows) ([How the macOS version works §4](../macos-architecture.md)).
- On POSIX, a failed file open retries with a case-insensitive path lookup ([`filesystem.cpp:16-51`](../../src/blitz3d/src/modules/bb/filesystem/filesystem.cpp#L16-L51)). Mixed-case paths in the original resources do not immediately break on Linux or Android.
- Upstream pieces for iOS, Android and Switch homebrew exist (`ios.toolchain.cmake`, `system.ios.m`, `runtime.sdl/android/`, `filesystem.ndk`, `package_apk.cpp`, `package_nro.cpp`, `runtime.nx`, `input.nx`), but they belong to the old GL runtime and are not connected to the current native game path.

### Prebuilt artifacts for the pinned Dawn revision

The artifacts of Dawn release `v20260423.175430` (revision `31e25af2`), pinned in `config/blitz3d-toolchain.json`, were downloaded and inspected with `lipo -info`, `otool -l` (`minos` in `LC_BUILD_VERSION`) and static-library symbol strings.

| Artifact | Architecture | Minimum OS and contents | Currently used |
| --- | --- | --- | --- |
| `windows-latest-Release` | x64 | D3D12, D3D11 and Vulkan backends; no OpenGL | Yes |
| `macos-latest-Release` | arm64 | macOS 15.0 | Yes |
| `macos-15-intel-Release` | x86_64 | macOS 15.0, includes CMake config | No |
| `dawn-apple…xcframework` | macOS arm64+x86_64, iOS arm64, iOS simulator | macOS 12.0, iOS 14.0, no CMake config | No |
| `dawn-android` | arm64-v8a, armeabi-v7a, x86, x86_64 | Static libraries | No |
| `ubuntu-latest-Release` | x64 | — (see the Ubuntu proposal) | No |
| Windows ARM64 | None | — | — |

## Platform reviews

### Windows on ARM64

**Current state**: The release ZIP contains an x64 EXE and x64 DLLs (VC runtime, OpenAL Soft, DXC) ([`blitz3d_toolchain.py:251-262`](../../src/devtools/blitz3d_toolchain.py#L251-L262)). It will likely run under Windows 11 on Arm x64 emulation, but this has not been checked on a device. Windows 10 on Arm, which lacks x64 emulation, is out of scope.

**Emulation risks**

- Chrome still keeps WebGPU behind a flag on Windows ARM64 ([gpuweb implementation status](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status), edited 2026-10-02). This suggests that the Dawn D3D12 path has little track record on ARM64 GPU drivers.
- DXC also runs as emulated x64, so the first shader compilation and pipeline creation may be slower.
- The renderer's CPU work (batching, culling) bears the full emulation cost.

**Native ARM64 blockers**

1. The pinned Dawn release has no Windows ARM64 prebuilt. A source build, hash pinning and a CI cache are needed.
2. The OpenAL Soft 1.25.2 binary ZIP contains only `Win32` and `Win64`. ARM64 needs a source build.
3. The toolchain fixes `vcvars64.bat`, `win64`/`x86_64`, `libs/Win64`, `bin/x64` and the `x64` VC redistributable ([`:153`](../../src/devtools/blitz3d_toolchain.py#L153), [`:213-245`](../../src/devtools/blitz3d_toolchain.py#L213-L245), [`:251-262`](../../src/devtools/blitz3d_toolchain.py#L251-L262)).
4. The linker fixes `/machine:X64` ([`linker_lld.cpp:328-329`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L328-L329)).

**What helps**: CMake's `woa64` already leads to `BB_MSVC` and `BB_WINDOWS` ([`CMakeLists.txt:122-124`](../../src/blitz3d/CMakeLists.txt#L122-L124), [`:168-180`](../../src/blitz3d/CMakeLists.txt#L168-L180)), the pinned DXC ZIP includes `bin/arm64`, and LLVM includes the AArch64 target. GitHub announced `windows-11-arm` runners for public repositories in April 2025 (current general-availability status unconfirmed).

**Effort and recommendation**: 0.5 week to check emulation on a device. Native support takes 2–4 weeks including a source-build pipeline for dependencies, and CI time grows. Check emulation first; if ARM64 driver problems appear, compare against the D3D11 backend in the same Dawn library (renderer compatibility unverified).

### Intel Mac / Universal 2

**Current state**: [`config/macos-toolchain.json`](../../config/macos-toolchain.json) fixes `arm64` and `15.0`, and the host checks ([`blitz3d_toolchain.py:29-31`](../../src/devtools/blitz3d_toolchain.py#L29-L31), [`macos_toolchain.py:28`](../../src/devtools/macos_toolchain.py#L28)), CMake architecture ([`blitz3d_toolchain.py:207`](../../src/devtools/blitz3d_toolchain.py#L207)), app validation ([`macos_validation.py:99-101`](../../src/devtools/macos_validation.py#L99-L101)) and release filenames (`macos-arm64`, [`release.py:112`](../../src/devtools/release.py#L112)) all assume a single arm64 architecture.

**What helps**: Dawn provides x86_64 artifacts (table above). LLVM includes the X86 target, so an Apple Silicon host can emit x86_64 code; CMake adds `-arch` when the host and target architectures differ ([`CMakeLists.txt:85-91`](../../src/blitz3d/CMakeLists.txt#L85-L91)), and the linker passes the target architecture ([`linker_lld.cpp:281-285`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L281-L285)).

**Required work**: x86_64 game and compatibility runtimes, x86_64 game compilation and linking, merging with `lipo`, updating validation, packaging and release names, and x86_64 checks. On Apple Silicon, Rosetta 2 only shows basic execution of the x86_64 slice; real Intel GPU verification is separate. GitHub's `macos-15-intel` is reportedly the last Intel runner image, available into 2027 (sources differ between August and fall).

**Risks and demand**: Apple has announced that macOS 26 Tahoe is the last macOS for Intel Macs, and general Rosetta 2 app support shrinks after macOS 27. The pinned Intel Dawn requires macOS 15, so supported Intel models are limited to those running macOS 15 or 26.

**Side finding**: The xcframework from the same revision targets macOS 12.0. Switching to it could also support Apple Silicon users on macOS 12–14. CMake integration (no config files) and Metal feature differences on older macOS need checking.

**Effort and recommendation**: 1–2 weeks + CI. Low priority; better reviewed together with lowering the minimum OS.

### Steam Deck / SteamOS

**A. Running the Windows build under Proton (no code changes)**

- Under Proton, D3D12 goes through vkd3d-proton to Vulkan (RADV). Whether the Dawn D3D12 backend works on this path is unverified.
- Alternative: the pinned Windows Dawn library includes the Vulkan backend (confirmed). Adding a setting that selects the backend (one place, [`graphics.webgpu.cpp:262-263`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L262-L263)) would use Wine's Vulkan path directly, with no new dependencies.
- Input: gamepads are not supported, so add the game to Steam as a non-Steam game and play with the Steam Input keyboard (WASD) and mouse template and the trackpads.
- Display: 1280×800 exceeds the original UI minimum of 800×600 ([`modern_settings.bb:8-18`](../../src/engine/includes/modern_settings.bb#L8-L18)), and "Fit monitor" selects the desktop resolution. Translated fonts render at 16px and 12px ([`fonts.py:22`](../../src/devtools/fonts.py#L22), [`:74`](../../src/devtools/fonts.py#L74)). Whether text meets Valve's 9px minimum character height at 1280×800 needs measuring, including the original bitmap fonts.
- Text input: use the Steam on-screen keyboard. The search field uses a Win32 EDIT control and IME, so whether Korean composition works under Proton with the Steam keyboard is unconfirmed.
- Saves: saves are written under the EXE folder, which is fine if the user extracted the game to a writable location.

**B. Native Linux**: Requires the Ubuntu port first. Deck-specific work is limited to Steam Linux Runtime compatibility (glibc version) if distributed through Steam, gamescope, and checking the Vulkan surface.

**C. Verified level**: Valve requires access to all content with the controller alone, glyphs matching the input device, on-screen keyboard integration, and a 9px minimum text size ([Steam Deck compatibility review](https://partner.steamgames.com/doc/steamdeck/compat)). The current code cannot meet these, and a Steam store listing itself needs permission from the original rights holder. A realistic goal is "Playable level plus setup instructions".

**Gamepad evidence (confirmed)**: The SDL runtime opens SDL joysticks at startup and adds them to the list ([`runtime.sdl.cpp:110-121`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L110-L121)), but its input driver reports zero joysticks ([`:28-30`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L28-L30)), and `bbEnumInput` rebuilds the list from that driver ([`input.cpp:26-36`](../../src/blitz3d/src/modules/bb/input/input.cpp#L26-L36)). There is no GameController mapping or hotplug handling. Game code reads a joystick only in one `GetJoy()` call that skips the splash screen ([`unrealsoftware.bb:41`](../../src/engine/includes/unrealsoftware.bb#L41)). On the other hand, game actions are abstracted as `in_keys(0..20)` plus mouse codes 256–262 ([`input.bb:70-96`](../../src/engine/includes/input.bb#L70-L96)), so the place to insert a controller is clear.

**Effort and recommendation**: A takes 0.5–1 week (+0.5–1 week for a backend setting); the gamepad layer takes 4–8 weeks. A is recommended as the first priority.

### Web: Firefox, Safari and mobile browsers

**Current state**: Startup requires `navigator.gpu`, `WebAssembly.Suspending` and a GPU adapter ([`shell.js:418-423`](../../src/web/shell.js#L418-L423)). The error message mentions only Chrome and Edge, but the check itself is feature detection. Link settings are `-sJSPI=1`, 512MiB initial memory and WASM exceptions ([`linker_lld.cpp:384-388`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L384-L388)). Timestamp queries are requested only when the adapter supports them, and a failed device request is retried without that feature ([`graphics.webgpu.cpp:277-293`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L277-L293)).

| Browser | WebGPU | JSPI | Verdict |
| --- | --- | --- | --- |
| Chrome/Edge (Windows x64, macOS, ChromeOS) | 113 | 137 | Current target |
| Chrome (Linux) | 144 (Intel Gen12+), 147 (NVIDIA on Wayland), otherwise behind a flag | 137 | Conditional |
| Chrome/Edge (Windows ARM64) | Behind a flag | 137 | Not with default settings |
| Chrome (Android) | 121 (Android 12+, Qualcomm/ARM), Imagination on Android 16+ | 137 | Startup conditions may pass; not playable |
| Firefox (Windows) | 141 | 153 | Conditional (unverified) |
| Firefox (macOS) | 145 (macOS 26+), wider in 147 (exact scope unconfirmed) | 153 | Conditional (unverified) |
| Firefox (Linux, Android) | Nightly / behind a flag | 153 | Not possible |
| Safari (macOS, iOS, iPadOS) | 26 (OS 26 or later) | 27 | Desktop conditional (unverified); mobile not playable |

WebGPU data comes from the [gpuweb implementation status](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status) (edited 2026-10-02) and JSPI data from the [webassembly.org feature table](https://webassembly.org/features/) (retrieved 2026-10-30). Firefox 153 was released on 2026-07-21, and according to Apple's security notes Safari 27 was released on 2026-09-14 for macOS Sequoia and Tahoe. Safari's WebGPU requires OS 26 or later, so a Mac needs both macOS 26 and Safari 27. JSPI support in both browsers is based on the feature table; the wording of their official release notes is unconfirmed.

**Required work (desktop)**: Run `/checks/` and manual scenarios in Firefox and Safari; check per-browser device limits (dynamic-offset alignment, binding counts); check IndexedDB, Web Locks, Pointer Lock and audio resumption; update the startup error message and the supported scope in [How the web version works](../web-architecture.md). 1–2 weeks.

**Mobile browsers**: Startup conditions may pass, but entering the game assumes Pointer Lock and a keyboard ([`shell.js:208-229`](../../src/web/shell.js#L208-L229), [`:382-402`](../../src/web/shell.js#L382-L402)). The game is not playable before a touch input layer exists. Whether 512MiB of initial memory plus all assets in the in-memory filesystem fit within iOS Safari's per-tab memory limit is also unconfirmed.

### iOS / iPadOS

**Current state (confirmed)**: `ios.toolchain.cmake` (a copy of leetal/ios-cmake) and CMake `BB_IOS` branches exist ([`CMakeLists.txt:138-141`](../../src/blitz3d/CMakeLists.txt#L138-L141), [`:263-266`](../../src/blitz3d/CMakeLists.txt#L263-L266)); `system.ios.m` has only two screen-size functions; the linker's iOS path fixes minimum version 15.4 ([`linker_lld.cpp:249-253`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L249-L253)). These pieces date from the upstream GL runtime and are not connected to the current native game path. For Dawn, the xcframework includes iOS arm64 (minimum 14.0).

**No JIT needed**: Game code is native code compiled ahead of time with LLVM and linked; ORC JIT exists only inside the compiler tool ([`compiler/CMakeLists.txt:168-181`](../../src/blitz3d/src/tools/compiler/CMakeLists.txt#L168-L181)). Game scripts are interpreted by the engine's built-in parser, so there is no runtime code generation to conflict with iOS JIT restrictions. However, changing functionality through externally obtained mods or maps may conflict with App Store Review Guideline 2.5.2.

**Required work**

- iOS runtime: UIKit lifecycle, SDL entry point, a surface (`SDL_Metal_CreateView`) and a Metal backend branch, rotation and Safe Area.
- Resource installation: reuse the macOS approach of copying from the bundle into a writable data folder ([`services.mm:134-180`](../../src/native/macos/services.mm#L134-L180)), adapted to the app container.
- Audio: Apple's OpenAL framework is deprecated, and the macOS build already suppresses the warnings ([`audio.openal/CMakeLists.txt:16-18`](../../src/blitz3d/src/modules/bb/audio.openal/CMakeLists.txt#L16-L18)). Switching to OpenAL Soft requires reviewing LGPL static-linking conditions.
- Input and text: support iPad keyboard and trackpad first (relative mouse behaviour needs device checks), handle Korean composition in the search and name fields with `UITextField`, and add a touch layer for iPhone.
- Signing, provisioning and TestFlight. The existing Apple developer account used for notarization can be used.

**Non-technical barriers**: App Store Review Guideline 5.2.1 requires permission to use third-party works, so permission from the rights holder of the original assets is needed. CC BY-NC-SA 3.0 DE sections 4.a) and 4.b) prohibit technical measures that restrict users from exercising their rights, so whether this conflicts with App Store distribution terms needs legal review. The noncommercial condition allows free distribution only.

**Effort and recommendation**: 6–10 weeks for iPadOS (keyboard and trackpad), plus 6–10 weeks for iPhone touch UX. Proceed after rights are confirmed and the input layer exists.

### Android

**Current state (confirmed)**: CMake `ANDROID` branches ([`CMakeLists.txt:35-40`](../../src/blitz3d/CMakeLists.txt#L35-L40), [`:142-144`](../../src/blitz3d/CMakeLists.txt#L142-L144)), the linker's NDK path ([`linker_lld.cpp:50-80`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L50-L80)), an APK packager, `filesystem.ndk` reading through AAssetManager, and the `Game.java` and manifest templates in `runtime.sdl/android/` exist, but all date from the upstream GL era. Only the CMakeLists of `deps/android_native_app_glue` is tracked, so its sources must be prepared separately. Dawn provides Android static libraries for four ABIs.

**Required work**

- An Android runtime, an `ANativeWindow` surface and a Vulkan backend branch.
- Lifecycle: destroy and recreate the surface when backgrounded, handle device loss, map the Back button to Esc.
- Audio: `audio.openal` links a system `openal` on non-Apple, non-Windows targets, which Android lacks, so OpenAL Soft must be built with the NDK.
- Files: APK assets are read-only, so copy them to internal storage on first launch (the macOS approach), and handle save export/import through the Storage Access Framework.
- Touch UI, AAB packaging and Play signing.
- Device variation: Chrome limiting Android WebGPU to Android 12+ with Qualcomm/ARM GPUs suggests quality differences across devices on the Dawn Vulkan path. Using the same range as the initial target is safer.

**Risk**: On AArch64 Linux and Android, `char` is unsigned by default (signed on Windows and Apple arm64). The path that passes SDL text-input bytes as `char` ([`runtime.sdl.cpp:279-288`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L279-L288)) may produce different values and needs checking.

**Effort and recommendation**: 10–16 weeks. The same build can serve as an Android app on ChromeOS, where Chromebooks have keyboards and mice and depend less on touch. Recommended after or alongside iPadOS.

### Consoles: Nintendo Switch, Xbox and PlayStation

**Technical side**: Dawn's backends cover D3D12, D3D11, Vulkan, Metal and OpenGL, with none for PlayStation's proprietary graphics API. The upstream `runtime.nx` and `input.nx` target homebrew through devkitPro libnx and use EGL/GL ([`runtime.nx/CMakeLists.txt:5-9`](../../src/blitz3d/src/modules/bb/runtime.nx/CMakeLists.txt#L5-L9)). They are not the official Nintendo SDK and cannot be a legitimate distribution path. Xbox is based on D3D12 and technically closest, but GDK details are under NDA and cannot be confirmed from public sources.

**Non-technical barriers**

- Developer registration, NDAs, development kits, platform certification and age ratings.
- Controller-only UX and platform save APIs are mandatory.
- NDA SDK code cannot live in a public repository, so a private fork is needed.
- The original assets are not relicensed ([`THIRD-PARTY-NOTICES.txt:17-19`](../../licenses/THIRD-PARTY-NOTICES.txt#L17-L19)), so written permission from the rights holder is needed. Commercial use of the code also needs permission from Peter Schauß / Unreal Software ([`StrandedII-source-README.txt:30-46`](../../licenses/StrandedII-source-README.txt#L30-L46)).
- The relationship between the technical-measures prohibition in CC BY-NC-SA 3.0 DE sections 4.a) and 4.b) and console DRM needs legal review.

**Verdict**: Not possible as things stand. Recommended not to start before agreement with the rights holder and securing a publisher.

### ChromeOS

- **Web build**: With WebGPU in Chrome 113+ and JSPI in 137+, the current web build is the most likely to work as is. About 0.5 week of checks on a Chromebook with keyboard and mouse is enough.
- **Linux container**: Graphics acceleration and Vulkan availability inside the VM vary by device (unconfirmed), and this path also depends on a native Linux port, so it is not recommended.
- **Android app**: Can reuse the Android port directly.

## Common challenges

### Input: a keyboard-and-mouse UI

- Game actions consist of 21 bindable keys plus five mouse buttons and the wheel ([`input.bb:70-96`](../../src/engine/includes/input.bb#L70-L96)); menus depend on absolute cursor coordinates and on how long the mouse rests (tooltips), and also use right-click ([`input.bb:18-45`](../../src/engine/includes/input.bb#L18-L45)).
- The recommended design is a runtime-level "virtual mouse plus actions" layer. It receives input through SDL GameController and maps the right stick to relative mouse motion during play, the stick to cursor movement in menus, and buttons to existing key and mouse events. Engine changes stay limited to extending the `input.bb` table, preserving save and script semantics.
- Touch adds, on top of the same layer, a virtual stick, drag-to-look, tap = left click, long press = right click / tooltip, pinch = wheel, and on-screen buttons.
- Key name display ([`load_keynames.bb`](../../src/engine/includes/load_keynames.bb)) must switch to device-specific glyphs to meet Steam Deck Verified and console requirements.

### Text input and IME

- The search field uses a platform-native control (Win32 EDIT, `NSTextField`, HTML `input`). Each new platform needs the same implementation.
- General text input receives only SDL's committed strings and does not show text being composed.
- In controller and touch environments, the moments that open the Steam on-screen keyboard or a mobile soft keyboard must be connected.

### Save locations

| Platform | Location |
| --- | --- |
| Windows, Proton (Steam Deck) | `mods/Stranded II/saves/` under the EXE folder |
| macOS | `~/Library/Application Support/Stranded II Native Preview/` |
| Web | Browser IndexedDB |
| iOS, iPadOS (proposed) | Application Support in the app container; expose only the save folder to the Files app if needed |
| Android (proposed) | App internal storage; provide export given auto-backup limits |
| Consoles | Platform save APIs |

Native macOS save replacement uses POSIX `rename` and `fsync`, so it can be reused on mobile. Keep the current principle of never moving settings or saves between platforms automatically.

### Original resources and licenses

- The game code and its modifications are under CC BY-NC-SA 3.0 DE, and commercial use needs the rights holder's permission. Original assets are taken unchanged from the official English distribution and are not relicensed.
- Direct distribution as ZIP, DMG and web, as now, can continue. Stores (App Store, Google Play, Steam, consoles) need rights documentation, free distribution and a review of the technical-measures clause.
- Third-party components: OpenAL Soft is LGPL, so relinking conditions must be reviewed where only static linking is possible (iOS, consoles). Dawn, SDL2, FreeType and others need their notices retained.

## Sources

All retrieved on 2026-10-30.

- [gpuweb implementation status](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status) — revision edited 2026-10-02
- [webassembly.org feature table](https://webassembly.org/features/) — JSPI: Chrome 137, Firefox 153, Safari 27
- [Firefox 153 release notes](https://www.firefox.com/en-US/firefox/153.0/releasenotes/) — released 2026-07-21
- [Safari 27 security content](https://support.apple.com/149039) — released 2026-09-14
- [Steam Deck compatibility review criteria](https://partner.steamgames.com/doc/steamdeck/compat)
- [Dawn release v20260423.175430](https://github.com/google/dawn/releases/tag/v20260423.175430) — artifacts downloaded to check architectures, minimum OS and backends
- [blitz3d-ng/build-llvm `llvm.cmake`](https://github.com/blitz3d-ng/build-llvm/blob/master/llvm.cmake) — `LLVM_TARGETS_TO_BUILD`
- [OpenAL Soft 1.25.2 binaries](https://openal-soft.org/openal-binaries/openal-soft-1.25.2-bin.zip), [DXC v1.8.2505.1](https://github.com/microsoft/DirectXShaderCompiler/releases/tag/v1.8.2505.1) — included architectures checked
- [Announcement of GitHub Actions runners for Windows on Arm](https://blogs.windows.com/windowsdeveloper/2025/04/14/github-actions-now-supports-windows-on-arm-runners-for-all-public-repos/) — 2025-04-14
- [Report that macOS 26 is the last release for Intel Macs](https://www.thurrott.com/?p=321965), [report on the end of Rosetta 2 support](https://www.macrumors.com/2026/02/16/macos-tahoe-26-4-rosetta-2-warnings/)
- [Mention of the `macos-15-intel` runner support period](https://core.trac.wordpress.org/changeset/61231)
