<!-- dev-docs-ai-translation: {"document":"docs/proposals/other-platforms.md","language":"ko","syncedHash":"43811cb582991bef41f1a96e8ff668998837b1a97e0fe5b1bd859a5bd30a54e3"} -->

# 추가 플랫폼 지원 가능성 조사

[English](other-platforms.md) · [한국어](other-platforms-ko.md)

[개발 가이드](../development-ko.md) · [macOS 구현 원리](../macos-architecture-ko.md) · [웹 버전 구현 원리](../web-architecture-ko.md) · [WebGPU 렌더러 마이그레이션](../wgpu-migration-ko.md)

현재 배포 대상은 **Windows x64(Dawn/D3D12)**, **Apple Silicon macOS 15 이상(Dawn/Metal)**, **WebGPU와 JSPI를 지원하는 데스크톱 Chrome·Edge**입니다. 이 문서는 그 밖의 플랫폼을 어디까지 지원할 수 있는지 코드 근거와 외부 현황으로 정리한 제안서입니다. 지원 범위를 바꾸지 않으며, Ubuntu/Linux 데스크톱은 별도 제안(`docs/proposals/ubuntu-desktop.md`)에서 다루므로 여기서는 Steam Deck과 겹치는 부분만 언급합니다.

> **조사 기준**: 커밋 `3a9dfc7`, 2026-10-30. 빌드와 실기 실행은 하지 않았습니다. "확인함"은 저장소 코드나 고정된 외부 아티팩트를 직접 열어 본 내용이고, "미확인"은 공개 자료로 확인하지 못한 내용입니다. 공수는 이 저장소에 익숙한 1인 기준 대략치입니다.

## 결론 요약

| 플랫폼 | 판정 | 예상 공수 | 핵심 근거·블로커 | 우선순위 |
| --- | --- | --- | --- | --- |
| Steam Deck — Proton으로 기존 Windows 빌드 실행 | 조건부 가능(미검증) | 실기 확인 0.5–1주, Vulkan 백엔드 선택지 +0.5–1주 | D3D12 → vkd3d-proton 경로 미검증, 게임패드 미지원이라 Steam Input 키보드·마우스 템플릿 필요 | 1 |
| 웹 — Firefox·Safari 데스크톱 | 조건부 가능(미검증) | 1–2주 | 두 기능 모두 지원 표에 올라 있으나 실제 렌더링·저장 미검증, Firefox Linux는 WebGPU 미출시 | 2 |
| ChromeOS — 웹판 | 조건부 가능(미검증) | 0.5주 | Chrome 113+ WebGPU, 137+ JSPI | 2와 함께 |
| Windows on ARM64 — x64 에뮬레이션 | 조건부 가능(미검증) | 0.5주 | Windows 11 전용, ARM64 GPU 드라이버 위의 Dawn D3D12 검증 이력 부족 | 3 |
| 게임패드·가상 커서 입력 계층(Steam Deck 네이티브 UX, 모바일·콘솔 선행 조건) | 조건부 | 4–8주 | 런타임이 컨트롤러를 게임에 전달하지 않음, UI가 절대 커서·호버 기반 | 4 |
| Windows on ARM64 — 네이티브 | 조건부 | 2–4주 | Dawn·OpenAL Soft ARM64 사전 빌드 없음, 도구 체인·링커 x64 고정 | 에뮬레이션 문제 확인 시 |
| Intel Mac / Universal 2 | 가능 | 1–2주 + CI | Dawn x86_64 아티팩트 존재, 도구 체인·검증이 arm64 고정, Intel Mac 수요 감소 | 5 |
| (부수) Apple Silicon macOS 12–14 | 조건부 | 1–2주 | 같은 Dawn 리비전의 xcframework는 macOS 최소 12.0 | 5와 함께 검토 |
| SteamOS 네이티브 Linux | 조건부 | Ubuntu 포팅 + 1–2주 | Linux 런타임·X11/Wayland 표면 없음 | Ubuntu 제안 이후 |
| iPadOS(키보드·트랙패드 전제) | 조건부 | 6–10주 | iOS 런타임·Metal 표면·자원 설치·App Store 권리 증빙 | 6 |
| iOS(iPhone, 터치) | 조건부 | iPadOS + 6–10주 | 터치 UI, 작은 화면, 심사 | 7 |
| Android(+ChromeOS의 Android 앱) | 조건부 | 10–16주 | 새 런타임, Dawn Vulkan, OpenAL Soft, 자산 복사, 터치, 기기 편차 | 7 |
| 모바일 브라우저 | 불가(현재 입력 모델) | 터치 계층 이후 | Pointer Lock·키보드 전제 | 보류 |
| ChromeOS — Linux 컨테이너 | 비추천 | — | VM 그래픽 가속·Vulkan 가용성이 기기마다 다름(미확인) | 제외 |
| 콘솔(Switch·Xbox·PlayStation) | 불가(현 상태) | 비기술 장벽 해소 후 플랫폼당 수개월 | NDA SDK, 원작 권리자 허가, CC 라이선스 기술 조치 조항, 인증 | 보류 |

## 추천 우선순위

1. **Steam Deck에서 기존 Windows 빌드를 Proton으로 실행해 확인합니다.** 코드 변경 없이 대표적인 휴대용 PC를 점검할 수 있고, 같은 결과가 일반 Linux의 Proton·Wine 실행에도 참고가 됩니다. D3D12 경로가 문제를 보이면, 이미 고정된 Windows Dawn 라이브러리에 들어 있는 Vulkan 백엔드를 고르는 설정 하나로 우회할 수 있는지 확인합니다.
2. **웹판을 Firefox·Safari 데스크톱과 ChromeOS에서 확인합니다.** 빌드는 그대로이고 시작 검사도 기능 감지 방식이라, 검증과 안내 문구 수정만으로 도달 범위가 넓어질 수 있습니다.
3. **Windows on ARM64는 x64 에뮬레이션 실기 확인을 먼저 합니다.** 네이티브 빌드는 Dawn·OpenAL Soft 소스 빌드가 필요하므로, 에뮬레이션에서 렌더링 오류나 큰 성능 저하가 확인될 때만 진행합니다.
4. **게임패드·가상 커서 입력 계층을 만듭니다.** Steam Deck을 키보드·마우스 템플릿 없이 쓰게 하고, 이후 Android·iOS·콘솔의 공통 선행 작업이 됩니다.
5. **Mac은 수요를 보고 결정합니다.** Intel Mac은 macOS 26이 마지막이라 효용이 줄고 있습니다. 같은 Dawn 리비전의 xcframework로 Apple Silicon의 최소 OS를 낮추는 쪽이 더 많은 사용자에게 닿을 수 있습니다.
6. **모바일은 권리 확인과 입력 계층 이후에 iPadOS → iPhone·Android 순으로 진행합니다.** 키보드·트랙패드가 있는 iPad가 원작 조작을 가장 적게 바꿉니다.
7. **콘솔은 원작 권리자 허가와 퍼블리셔가 확보되기 전에는 착수하지 않습니다.**

## 플랫폼을 늘릴 때 바뀌는 곳

새 플랫폼은 대체로 아래 지점을 거칩니다. 모두 "확인함"입니다.

| 계층 | 현재 상태 | 근거 |
| --- | --- | --- |
| 도구 체인 호스트·대상 | Windows 또는 Apple Silicon macOS만 허용하고, `BB_PLATFORM`·`ARCH`를 `win64/x86_64` 또는 `macos/arm64`로 고정 | [`blitz3d_toolchain.py:26-31`](../../src/devtools/blitz3d_toolchain.py#L26-L31), [`:213-245`](../../src/devtools/blitz3d_toolchain.py#L213-L245) |
| 개발 명령 | `--platform windows\|web\|macos` | [`dev.py:125`](../../src/devtools/dev.py#L125) |
| 네이티브 런타임 선택 | 네이티브 게임이면 Windows는 `s2windows`, 그 밖은 모두 `s2macos` | [`runtime/CMakeLists.txt:236-243`](../../src/blitz3d/src/runtime/CMakeLists.txt#L236-L243) |
| 렌더 표면·백엔드 | macOS Metal 레이어, Windows HWND, 그 밖은 Emscripten 캔버스의 세 갈래. 백엔드는 Metal·D3D12 고정 | [`graphics.webgpu.cpp:227-245`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L227-L245), [`:259-264`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L259-L264) |
| 그래픽 시스템 라이브러리 | macOS·Windows 조합만 지정 | [`graphics.webgpu/CMakeLists.txt:9-13`](../../src/blitz3d/src/modules/bb/graphics.webgpu/CMakeLists.txt#L9-L13) |
| 오디오 | Apple은 시스템 OpenAL 프레임워크, Windows는 OpenAL Soft, 그 밖은 시스템 `openal` | [`audio.openal/CMakeLists.txt:6-12`](../../src/blitz3d/src/modules/bb/audio.openal/CMakeLists.txt#L6-L12) |
| 플랫폼 서비스 | 플랫폼별 `services.bb`가 시간·창·파일 교체·검색창 같은 `modern_*` 함수를 구현 | [`platforms/macos/services.bb`](../../src/engine/platforms/macos/services.bb), [`services.mm`](../../src/native/macos/services.mm) |
| 링커 | Windows 대상은 `/machine:X64` 고정 | [`linker_lld.cpp:328-329`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L328-L329) |

이미 갖춰진 부분도 있습니다.

- CMake에 업스트림의 `woa64`·`linux`·`ios`·`android`·`nx` 분기가 남아 있습니다([`CMakeLists.txt:116-166`](../../src/blitz3d/CMakeLists.txt#L116-L166)).
- 고정 LLVM 19.1.5는 `AArch64;ARM;WebAssembly;X86` 타깃으로 빌드되며([blitz3d-ng/build-llvm `llvm.cmake`](https://github.com/blitz3d-ng/build-llvm/blob/master/llvm.cmake)), 컴파일러는 호스트가 아닌 트리플이면 모든 타깃을 초기화합니다([`codegen_llvm.cpp:86-119`](../../src/blitz3d/src/tools/compiler/codegen_llvm/codegen_llvm.cpp#L86-L119)). 코드 생성 단계의 교차 컴파일은 막혀 있지 않습니다.
- 32비트 숫자·64비트 포인터 ABI는 AArch64(macOS)와 x86_64(Windows)에서 이미 원본 결과와 비교합니다([macOS 구현 원리 §4](../macos-architecture-ko.md)).
- POSIX에서는 파일을 열지 못하면 대소문자를 무시하고 경로를 다시 찾습니다([`filesystem.cpp:16-51`](../../src/blitz3d/src/modules/bb/filesystem/filesystem.cpp#L16-L51)). 원본 리소스의 대소문자 섞인 경로가 Linux·Android에서 바로 막히지는 않습니다.
- iOS·Android·Switch 홈브루용 업스트림 조각(`ios.toolchain.cmake`, `system.ios.m`, `runtime.sdl/android/`, `filesystem.ndk`, `package_apk.cpp`, `package_nro.cpp`, `runtime.nx`, `input.nx`)이 있지만 예전 GL 런타임용이며 현재 네이티브 게임 경로와 연결돼 있지 않습니다.

### 고정 Dawn 리비전의 사전 빌드 아티팩트

`config/blitz3d-toolchain.json`이 고정한 Dawn 릴리스 `v20260423.175430`(리비전 `31e25af2`)의 아티팩트를 내려받아 `lipo -info`, `otool -l`(`LC_BUILD_VERSION`의 `minos`), 정적 라이브러리 심볼 문자열로 확인했습니다.

| 아티팩트 | 아키텍처 | 최소 OS·내용 | 현재 사용 |
| --- | --- | --- | --- |
| `windows-latest-Release` | x64 | D3D12·D3D11·Vulkan 백엔드 포함, OpenGL 없음 | 사용 중 |
| `macos-latest-Release` | arm64 | macOS 15.0 | 사용 중 |
| `macos-15-intel-Release` | x86_64 | macOS 15.0, CMake 설정 포함 | 미사용 |
| `dawn-apple…xcframework` | macOS arm64+x86_64, iOS arm64, iOS 시뮬레이터 | macOS 12.0, iOS 14.0, CMake 설정 없음 | 미사용 |
| `dawn-android` | arm64-v8a, armeabi-v7a, x86, x86_64 | 정적 라이브러리 | 미사용 |
| `ubuntu-latest-Release` | x64 | — (Ubuntu 제안 참고) | 미사용 |
| Windows ARM64 | 없음 | — | — |

## 플랫폼별 검토

### Windows on ARM64

**현재**: 배포 ZIP은 x64 EXE와 x64 DLL(VC 런타임, OpenAL Soft, DXC)로 구성됩니다([`blitz3d_toolchain.py:251-262`](../../src/devtools/blitz3d_toolchain.py#L251-L262)). Windows 11 on Arm의 x64 에뮬레이션으로 실행될 가능성이 높지만 실기에서 확인한 적은 없습니다. x64 에뮬레이션이 없는 Windows 10 on Arm은 대상이 아닙니다.

**에뮬레이션 리스크**

- Chrome은 Windows ARM64에서 WebGPU를 아직 플래그 뒤에 두고 있습니다([gpuweb 구현 현황](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status), 2026-10-02 편집). ARM64 GPU 드라이버 위의 Dawn D3D12 경로는 검증 이력이 적다는 신호입니다.
- DXC도 x64로 에뮬레이션되므로 첫 셰이더 컴파일과 파이프라인 생성이 느려질 수 있습니다.
- 렌더러의 CPU 쪽 작업(배칭·컬링)은 에뮬레이션 비용을 그대로 받습니다.

**네이티브 ARM64 블로커**

1. 고정 Dawn 릴리스에 Windows ARM64 사전 빌드가 없습니다. 소스 빌드, 해시 고정, CI 캐시가 필요합니다.
2. OpenAL Soft 1.25.2 바이너리 ZIP은 `Win32`·`Win64`만 포함합니다. ARM64는 소스 빌드가 필요합니다.
3. 도구 체인이 `vcvars64.bat`, `win64`/`x86_64`, `libs/Win64`, `bin/x64`, VC 재배포 `x64`를 고정합니다([`:153`](../../src/devtools/blitz3d_toolchain.py#L153), [`:213-245`](../../src/devtools/blitz3d_toolchain.py#L213-L245), [`:251-262`](../../src/devtools/blitz3d_toolchain.py#L251-L262)).
4. 링커가 `/machine:X64`를 고정합니다([`linker_lld.cpp:328-329`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L328-L329)).

**가능 요소**: CMake의 `woa64`는 `BB_MSVC`·`BB_WINDOWS`로 이어지고([`CMakeLists.txt:122-124`](../../src/blitz3d/CMakeLists.txt#L122-L124), [`:168-180`](../../src/blitz3d/CMakeLists.txt#L168-L180)), 고정 DXC ZIP에는 `bin/arm64`가 있으며, LLVM에는 AArch64 타깃이 있습니다. GitHub는 2025-04에 공개 저장소용 `windows-11-arm` 실행기를 발표했습니다(현재 정식 제공 여부는 미확인).

**공수·추천**: 에뮬레이션 실기 확인 0.5주. 네이티브는 의존성 소스 빌드 파이프라인을 포함해 2–4주이며 CI 시간이 늘어납니다. 에뮬레이션을 먼저 확인하고, ARM64 드라이버 문제가 보이면 같은 Dawn 라이브러리의 D3D11 백엔드와 비교해 봅니다(렌더러 호환성은 미검증).

### Intel Mac / Universal 2

**현재**: [`config/macos-toolchain.json`](../../config/macos-toolchain.json)이 `arm64`·`15.0`을 고정하고, 호스트 검사([`blitz3d_toolchain.py:29-31`](../../src/devtools/blitz3d_toolchain.py#L29-L31), [`macos_toolchain.py:28`](../../src/devtools/macos_toolchain.py#L28)), CMake 아키텍처([`blitz3d_toolchain.py:207`](../../src/devtools/blitz3d_toolchain.py#L207)), 앱 검증([`macos_validation.py:99-101`](../../src/devtools/macos_validation.py#L99-L101)), 배포 파일명(`macos-arm64`, [`release.py:112`](../../src/devtools/release.py#L112))이 모두 arm64 단일 아키텍처를 전제합니다.

**가능 요소**: Dawn에 x86_64 아티팩트가 있습니다(위 표). LLVM에 X86 타깃이 있어 Apple Silicon 호스트에서 x86_64 코드를 만들 수 있고, CMake는 호스트와 대상 아키텍처가 다르면 `-arch`를 붙이며([`CMakeLists.txt:85-91`](../../src/blitz3d/CMakeLists.txt#L85-L91)), 링커도 대상 아키텍처를 넘깁니다([`linker_lld.cpp:281-285`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L281-L285)).

**필요 작업**: x86_64 런타임·호환성 런타임 빌드, x86_64 게임 컴파일·링크, `lipo`로 병합, 검증·패키지·릴리스 이름 갱신, x86_64 검사. Apple Silicon에서는 Rosetta 2로 x86_64 슬라이스의 기본 실행만 볼 수 있고, 실제 Intel GPU 검증은 따로 필요합니다. GitHub의 `macos-15-intel`은 마지막 Intel 실행기 이미지이며 2027년까지 제공된다고 알려져 있습니다(8월인지 가을인지는 출처마다 다름).

**리스크·수요**: Apple은 macOS 26 Tahoe가 Intel Mac의 마지막 macOS라고 발표했고, Rosetta 2의 일반 앱 지원은 macOS 27 이후 축소됩니다. 고정 Intel Dawn은 macOS 15 이상이라 지원 Intel 모델은 macOS 15·26을 쓰는 기기로 한정됩니다.

**부수 발견**: 같은 리비전의 xcframework는 macOS 최소 12.0입니다. 이 아티팩트로 바꾸면 Apple Silicon의 macOS 12–14 사용자도 지원할 여지가 있습니다. CMake 연동(설정 파일 없음)과 구형 macOS의 Metal 기능 차이를 확인해야 합니다.

**공수·추천**: 1–2주 + CI. 우선순위는 낮습니다. 최소 OS를 낮추는 작업과 함께 검토하는 편이 낫습니다.

### Steam Deck / SteamOS

**A. Proton으로 Windows 빌드 실행(코드 변경 없음)**

- Proton에서 D3D12는 vkd3d-proton을 거쳐 Vulkan(RADV)으로 갑니다. Dawn D3D12 백엔드가 이 경로에서 정상인지는 미검증입니다.
- 대안: 고정 Windows Dawn 라이브러리에 Vulkan 백엔드가 들어 있습니다(확인함). 백엔드를 고르는 설정을 추가하면([`graphics.webgpu.cpp:262-263`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L262-L263) 한 곳) 새 의존성 없이 Wine의 Vulkan 경로를 바로 쓸 수 있습니다.
- 입력: 게임패드를 지원하지 않으므로 Steam에 비 Steam 게임으로 추가한 뒤 Steam Input의 키보드(WASD)·마우스 템플릿과 트랙패드로 조작합니다.
- 화면: 1280×800은 원작 UI 최소치 800×600보다 크고([`modern_settings.bb:8-18`](../../src/engine/includes/modern_settings.bb#L8-L18)), "모니터에 맞춤"이 데스크톱 해상도를 고릅니다. 번역 글꼴은 16px·12px로 렌더링합니다([`fonts.py:22`](../../src/devtools/fonts.py#L22), [`:74`](../../src/devtools/fonts.py#L74)). Valve의 최소 글자 높이 9px(1280×800 기준) 충족 여부는 원본 비트맵 글꼴을 포함해 실측이 필요합니다.
- 텍스트 입력: Steam 화상 키보드로 입력합니다. 검색창은 Win32 EDIT·IME를 쓰므로 Proton과 Steam 키보드에서 한글 조합이 되는지는 미확인입니다.
- 저장: 세이브는 EXE 폴더 아래에 쓰므로 사용자가 압축을 푼 위치가 쓰기 가능하면 문제없습니다.

**B. 네이티브 Linux**: Ubuntu 포팅이 선행돼야 합니다. Deck 고유 작업은 Steam으로 배포할 경우의 Steam Linux Runtime(glibc 버전) 호환, gamescope, Vulkan 표면 확인 정도입니다.

**C. Verified 수준**: Valve 기준은 컨트롤러만으로 모든 콘텐츠 접근, 입력 장치에 맞는 글리프, 화상 키보드 연동, 최소 9px 글자를 요구합니다([Steam Deck 호환성 검토](https://partner.steamgames.com/doc/steamdeck/compat)). 현재 코드로는 충족할 수 없고, Steam 스토어 등록 자체에 원작 권리자 허가가 필요합니다. 현실적인 목표는 "Playable 수준 + 설정 안내"입니다.

**게임패드 근거(확인함)**: SDL 런타임은 시작할 때 SDL Joystick을 열어 목록에 넣지만([`runtime.sdl.cpp:110-121`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L110-L121)) 입력 드라이버는 조이스틱 0개를 보고하고([`:28-30`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L28-L30)), `bbEnumInput`은 이 드라이버 기준으로 목록을 다시 만듭니다([`input.cpp:26-36`](../../src/blitz3d/src/modules/bb/input/input.cpp#L26-L36)). GameController 매핑과 핫플러그 처리도 없습니다. 게임 코드는 스플래시 건너뛰기의 `GetJoy()` 한 곳에서만 조이스틱을 읽습니다([`unrealsoftware.bb:41`](../../src/engine/includes/unrealsoftware.bb#L41)). 대신 게임 동작이 `in_keys(0..20)`과 마우스 코드 256–262로 추상화돼 있어([`input.bb:70-96`](../../src/engine/includes/input.bb#L70-L96)) 컨트롤러를 끼워 넣을 지점은 분명합니다.

**공수·추천**: A는 0.5–1주(+백엔드 설정 0.5–1주), 게임패드 계층은 4–8주. A를 1순위로 권합니다.

### 웹: Firefox·Safari·모바일 브라우저

**현재**: 시작 조건은 `navigator.gpu`, `WebAssembly.Suspending`, GPU 어댑터입니다([`shell.js:418-423`](../../src/web/shell.js#L418-L423)). 오류 문구는 Chrome·Edge만 언급하지만 검사 자체는 기능 감지입니다. 링크 설정은 `-sJSPI=1`, 초기 메모리 512MiB, WASM 예외입니다([`linker_lld.cpp:384-388`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L384-L388)). 타임스탬프 쿼리는 어댑터가 지원할 때만 요청하고, 장치 요청이 실패하면 이 기능 없이 다시 요청합니다([`graphics.webgpu.cpp:277-293`](../../src/blitz3d/src/modules/bb/graphics.webgpu/graphics.webgpu.cpp#L277-L293)).

| 브라우저 | WebGPU | JSPI | 판정 |
| --- | --- | --- | --- |
| Chrome·Edge(Windows x64, macOS, ChromeOS) | 113 | 137 | 현재 대상 |
| Chrome(Linux) | 144(Intel Gen12+), 147(NVIDIA·Wayland), 그 밖은 플래그 | 137 | 조건부 |
| Chrome·Edge(Windows ARM64) | 플래그 | 137 | 기본 설정으로 불가 |
| Chrome(Android) | 121(Android 12+, Qualcomm·ARM), Imagination은 Android 16+ | 137 | 시작 조건은 충족 가능, 조작 불가 |
| Firefox(Windows) | 141 | 153 | 조건부(미검증) |
| Firefox(macOS) | 145(macOS 26+), 147에서 범위 확대(세부 범위 미확인) | 153 | 조건부(미검증) |
| Firefox(Linux·Android) | Nightly·플래그 | 153 | 불가 |
| Safari(macOS·iOS·iPadOS) | 26(OS 26 이상) | 27 | 데스크톱 조건부(미검증), 모바일 조작 불가 |

WebGPU는 [gpuweb 구현 현황](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status)(2026-10-02 편집), JSPI는 [webassembly.org 기능 표](https://webassembly.org/features/)(2026-10-30 조회) 기준입니다. Firefox 153은 2026-07-21에 출시됐고, Safari 27은 Apple 보안 공지 기준 2026-09-14에 macOS Sequoia·Tahoe용으로 출시됐습니다. Safari의 WebGPU는 OS 26 이상이므로 Mac에서는 macOS 26과 Safari 27이 함께 필요합니다. 두 브라우저의 JSPI 지원은 기능 표 기준이며 공식 릴리스 노트 문구는 미확인입니다.

**필요 작업(데스크톱)**: Firefox·Safari에서 `/checks/`와 수동 시나리오 실행, 브라우저별 장치 한도(동적 오프셋 정렬, 바인딩 수) 확인, IndexedDB·Web Locks·Pointer Lock·오디오 재개 확인, 시작 오류 문구와 [웹 버전 구현 원리](../web-architecture-ko.md)의 지원 범위 갱신. 1–2주입니다.

**모바일 브라우저**: 시작 조건은 충족할 수 있지만 게임 진입이 Pointer Lock과 키보드를 전제합니다([`shell.js:208-229`](../../src/web/shell.js#L208-L229), [`:382-402`](../../src/web/shell.js#L382-L402)). 터치 입력 계층 전에는 플레이할 수 없습니다. 초기 메모리 512MiB와 메모리 파일 시스템에 올리는 전체 자산이 iOS Safari의 탭 메모리 한도 안에 드는지도 미확인입니다.

### iOS / iPadOS

**현재(확인함)**: `ios.toolchain.cmake`(leetal/ios-cmake 사본)와 CMake `BB_IOS` 분기([`CMakeLists.txt:138-141`](../../src/blitz3d/CMakeLists.txt#L138-L141), [`:263-266`](../../src/blitz3d/CMakeLists.txt#L263-L266))가 있고, `system.ios.m`은 화면 크기 함수 두 개뿐이며, 링커의 iOS 경로는 최소 버전 15.4를 고정합니다([`linker_lld.cpp:249-253`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L249-L253)). 이 조각들은 업스트림 GL 런타임 시절 것이라 현재 네이티브 게임 경로와 연결돼 있지 않습니다. Dawn은 xcframework에 iOS arm64(최소 14.0)가 있습니다.

**JIT 불필요**: 게임 코드는 LLVM으로 미리 컴파일해 링크하는 네이티브 코드이고, ORC JIT는 컴파일러 도구에만 들어 있습니다([`compiler/CMakeLists.txt:168-181`](../../src/blitz3d/src/tools/compiler/CMakeLists.txt#L168-L181)). 게임 스크립트는 엔진 내장 파서가 해석하므로 실행 중 코드 생성이 없어 iOS의 JIT 제한과 충돌하지 않습니다. 다만 외부에서 받은 모드·맵으로 기능을 바꾸는 흐름은 App Store 심사 지침 2.5.2와 충돌할 여지가 있습니다.

**필요 작업**

- iOS 런타임: UIKit 수명주기, SDL 진입점, 표면(`SDL_Metal_CreateView`)과 Metal 백엔드 분기, 회전·Safe Area.
- 자원 설치: macOS의 번들 → 쓰기 가능한 데이터 폴더 복사 방식([`services.mm:134-180`](../../src/native/macos/services.mm#L134-L180))을 앱 컨테이너에 맞게 재사용.
- 오디오: Apple OpenAL 프레임워크는 deprecated라 macOS 빌드도 경고를 억제하고 있습니다([`audio.openal/CMakeLists.txt:16-18`](../../src/blitz3d/src/modules/bb/audio.openal/CMakeLists.txt#L16-L18)). OpenAL Soft로 바꾸면 LGPL 정적 링크 조건을 검토해야 합니다.
- 입력·텍스트: iPad 키보드·트랙패드를 먼저 지원하고(상대 마우스 동작은 실기 확인 필요), 검색창·이름 입력은 `UITextField`로 한글 조합을 처리, iPhone은 터치 계층.
- 서명·프로비저닝·TestFlight. 기존 공증용 Apple 개발자 계정을 쓸 수 있습니다.

**비기술 장벽**: App Store 심사 지침 5.2.1은 제3자 저작물 사용 권한을 요구하므로 원작 자산 권리자의 허가가 필요합니다. CC BY-NC-SA 3.0 DE 4.a)·4.b)는 이용자의 권리 행사를 막는 기술적 조치를 금지하므로 App Store 배포 조건과 충돌하는지 법률 검토가 필요합니다. 비상업 조건 때문에 무료 배포만 가능합니다.

**공수·추천**: iPadOS(키보드·트랙패드) 6–10주, iPhone 터치 UX 6–10주 추가. 권리 확인과 입력 계층 이후에 진행합니다.

### Android

**현재(확인함)**: CMake `ANDROID` 분기([`CMakeLists.txt:35-40`](../../src/blitz3d/CMakeLists.txt#L35-L40), [`:142-144`](../../src/blitz3d/CMakeLists.txt#L142-L144)), 링커의 NDK 경로([`linker_lld.cpp:50-80`](../../src/blitz3d/src/tools/compiler/linker_lld/linker_lld.cpp#L50-L80)), APK 패키저, AAssetManager로 읽는 `filesystem.ndk`, `runtime.sdl/android/`의 `Game.java`·매니페스트 템플릿이 있지만 모두 업스트림 GL 시절 것입니다. `deps/android_native_app_glue`는 CMakeLists만 추적하므로 소스는 따로 준비해야 합니다. Dawn에는 4개 ABI의 Android 정적 라이브러리가 있습니다.

**필요 작업**

- Android 런타임, `ANativeWindow` 표면과 Vulkan 백엔드 분기.
- 수명주기: 백그라운드 전환 시 표면 파괴·재생성, 장치 손실 처리, 뒤로 버튼을 Esc로 연결.
- 오디오: `audio.openal`은 Apple·Windows가 아니면 시스템 `openal`을 링크하는데 Android에는 없으므로 NDK용 OpenAL Soft 빌드가 필요합니다.
- 파일: APK 자산은 읽기 전용이므로 첫 실행 때 내부 저장소로 복사하고(macOS 방식), 세이브 내보내기·가져오기는 Storage Access Framework로 처리.
- 터치 UI, AAB 패키징·Play 서명.
- 기기 편차: Chrome이 Android WebGPU를 Android 12 이상 Qualcomm·ARM GPU로 제한한다는 점은 Dawn Vulkan 경로의 기기별 품질 차이를 시사합니다. 같은 범위를 1차 대상으로 잡는 편이 안전합니다.

**리스크**: AArch64 Linux·Android의 `char`는 기본이 unsigned입니다(Windows와 Apple arm64는 signed). SDL 텍스트 입력 바이트를 `char`로 넘기는 경로([`runtime.sdl.cpp:279-288`](../../src/blitz3d/src/modules/bb/runtime.sdl/runtime.sdl.cpp#L279-L288))가 다른 값을 낼 수 있어 확인이 필요합니다.

**공수·추천**: 10–16주. 같은 결과물을 ChromeOS의 Android 앱으로도 쓸 수 있고, 크롬북은 키보드·마우스가 있어 터치 의존도가 낮습니다. iPadOS 이후 또는 병행을 권합니다.

### 콘솔: Nintendo Switch·Xbox·PlayStation

**기술 측면**: Dawn 백엔드는 D3D12·D3D11·Vulkan·Metal·OpenGL 계열이라 PlayStation 전용 그래픽 API용 백엔드가 없습니다. 업스트림 `runtime.nx`·`input.nx`는 devkitPro libnx 기반 홈브루용이고 EGL/GL을 씁니다([`runtime.nx/CMakeLists.txt:5-9`](../../src/blitz3d/src/modules/bb/runtime.nx/CMakeLists.txt#L5-L9)). 정식 Nintendo SDK가 아니므로 정식 배포 경로가 될 수 없습니다. Xbox는 D3D12 기반이라 기술적으로 가장 가깝지만 GDK 세부 사항은 NDA 범위라 공개 자료로 확인할 수 없습니다.

**비기술 장벽**

- 개발자 등록, NDA, 개발 키트, 플랫폼 인증, 연령 등급 심의.
- 컨트롤러 전용 UX와 플랫폼 저장 API가 필수입니다.
- NDA SDK 코드는 공개 저장소에 둘 수 없어 비공개 포크가 필요합니다.
- 원작 자산은 재라이선스되지 않았으므로([`THIRD-PARTY-NOTICES.txt:17-19`](../../licenses/THIRD-PARTY-NOTICES.txt#L17-L19)) 권리자의 서면 허가가 필요합니다. 코드도 상업적 이용은 Peter Schauß / Unreal Software의 허가가 필요합니다([`StrandedII-source-README.txt:30-46`](../../licenses/StrandedII-source-README.txt#L30-L46)).
- CC BY-NC-SA 3.0 DE 4.a)·4.b)의 기술적 조치 금지 조항과 콘솔 DRM의 관계는 법률 검토가 필요합니다.

**판정**: 현재 상태로는 불가입니다. 권리자 협의와 퍼블리셔 확보 전에는 착수하지 않기를 권합니다.

### ChromeOS

- **웹판**: Chrome 113+ WebGPU, 137+ JSPI로 현재 웹판이 그대로 동작할 가능성이 가장 높습니다. 키보드·마우스가 있는 크롬북에서 0.5주 정도 확인하면 됩니다.
- **Linux 컨테이너**: VM 안의 그래픽 가속과 Vulkan 가용성이 기기마다 다르고(미확인), Linux 네이티브 포팅에도 의존하므로 권하지 않습니다.
- **Android 앱**: Android 포팅 결과를 그대로 쓸 수 있습니다.

## 공통 과제

### 입력: 키보드·마우스 전제 UI

- 게임 동작은 바인딩 가능한 21개 키와 마우스 5버튼·휠로 이뤄지고([`input.bb:70-96`](../../src/engine/includes/input.bb#L70-L96)), 메뉴는 절대 커서 좌표와 마우스를 멈춘 시간(툴팁)에 의존하며 우클릭도 씁니다([`input.bb:18-45`](../../src/engine/includes/input.bb#L18-L45)).
- 권장 설계는 런타임 단의 "가상 마우스 + 동작" 계층입니다. SDL GameController로 입력을 받아 게임 중에는 오른쪽 스틱을 상대 마우스 이동으로, 메뉴에서는 스틱을 커서 이동으로, 버튼은 기존 키·마우스 이벤트로 바꿉니다. 엔진 수정은 `input.bb` 테이블을 넓히는 수준으로 제한해 세이브·스크립트 의미를 유지합니다.
- 터치는 같은 계층 위에 가상 스틱, 드래그 시점 조작, 탭=왼쪽 클릭, 길게 누르기=오른쪽 클릭·툴팁, 핀치=휠, 화면 버튼을 얹습니다.
- 키 이름 표시([`load_keynames.bb`](../../src/engine/includes/load_keynames.bb))를 입력 장치별 글리프로 바꿔야 Steam Deck Verified와 콘솔 기준을 맞출 수 있습니다.

### 텍스트 입력과 IME

- 검색창은 플랫폼별 네이티브 컨트롤(Win32 EDIT, `NSTextField`, HTML `input`)을 씁니다. 새 플랫폼마다 같은 구현이 필요합니다.
- 일반 텍스트 입력은 SDL이 확정한 문자열만 받아 조합 중인 글자를 보여 주지 않습니다.
- 컨트롤러·터치 환경에서는 Steam 화상 키보드나 모바일 소프트 키보드를 띄울 시점을 연결해야 합니다.

### 저장 위치

| 플랫폼 | 위치 |
| --- | --- |
| Windows, Proton(Steam Deck) | EXE 폴더 아래 `mods/Stranded II/saves/` |
| macOS | `~/Library/Application Support/Stranded II Native Preview/` |
| 웹 | 브라우저 IndexedDB |
| iOS·iPadOS(제안) | 앱 컨테이너의 Application Support. 필요하면 파일 앱에 세이브 폴더만 노출 |
| Android(제안) | 앱 내부 저장소. 자동 백업 한도를 고려해 내보내기 기능 제공 |
| 콘솔 | 플랫폼 저장 API |

macOS 네이티브의 세이브 교체는 POSIX `rename`·`fsync` 방식이라 모바일에서도 재사용할 수 있습니다. 플랫폼 간에 설정·세이브를 자동으로 옮기지 않는 현재 원칙은 그대로 둡니다.

### 원본 리소스와 라이선스

- 게임 코드와 수정본은 CC BY-NC-SA 3.0 DE이며 상업적 이용은 권리자 허가가 필요합니다. 원작 자산은 공식 영어 배포본에서 그대로 가져오며 재라이선스하지 않습니다.
- 지금처럼 직접 배포하는 ZIP·DMG·웹은 유지할 수 있습니다. 스토어(App Store, Google Play, Steam, 콘솔)는 권리 증빙, 무료 배포, 기술적 조치 조항 검토가 필요합니다.
- 제3자 구성 요소: OpenAL Soft는 LGPL이라 정적 링크만 가능한 환경(iOS·콘솔)에서는 재링크 조건을 검토해야 합니다. Dawn·SDL2·FreeType 등은 고지 의무를 지키면 됩니다.

## 출처

조회일은 모두 2026-10-30입니다.

- [gpuweb 구현 현황](https://github.com/gpuweb/gpuweb/wiki/Implementation-Status) — 2026-10-02 편집본
- [webassembly.org 기능 표](https://webassembly.org/features/) — JSPI: Chrome 137, Firefox 153, Safari 27
- [Firefox 153 릴리스 노트](https://www.firefox.com/en-US/firefox/153.0/releasenotes/) — 2026-07-21 출시
- [Safari 27 보안 콘텐츠](https://support.apple.com/149039) — 2026-09-14 출시
- [Steam Deck 호환성 검토 기준](https://partner.steamgames.com/doc/steamdeck/compat)
- [Dawn 릴리스 v20260423.175430](https://github.com/google/dawn/releases/tag/v20260423.175430) — 아티팩트를 내려받아 아키텍처·최소 OS·백엔드 확인
- [blitz3d-ng/build-llvm `llvm.cmake`](https://github.com/blitz3d-ng/build-llvm/blob/master/llvm.cmake) — `LLVM_TARGETS_TO_BUILD`
- [OpenAL Soft 1.25.2 바이너리](https://openal-soft.org/openal-binaries/openal-soft-1.25.2-bin.zip), [DXC v1.8.2505.1](https://github.com/microsoft/DirectXShaderCompiler/releases/tag/v1.8.2505.1) — 포함된 아키텍처 확인
- [Windows on Arm용 GitHub Actions 실행기 발표](https://blogs.windows.com/windowsdeveloper/2025/04/14/github-actions-now-supports-windows-on-arm-runners-for-all-public-repos/) — 2025-04-14
- [macOS 26이 Intel Mac의 마지막 릴리스라는 발표 보도](https://www.thurrott.com/?p=321965), [Rosetta 2 지원 종료 안내 보도](https://www.macrumors.com/2026/02/16/macos-tahoe-26-4-rosetta-2-warnings/)
- [`macos-15-intel` 실행기 지원 기간 언급](https://core.trac.wordpress.org/changeset/61231)
