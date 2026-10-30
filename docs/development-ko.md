<!-- dev-docs-ai-translation: {"document":"docs/development.md","language":"ko","syncedHash":"a4db365bf67a15aeda889d333ae63a6cea1f1e593742d328215034a2f0b3c91d"} -->

# 개발 가이드

[English](development.md) · [한국어](development-ko.md)

[README로 돌아가기](../README-ko.md) · [현지화 가이드](localization-ko.md) · [WebGPU 렌더러 마이그레이션](wgpu-migration-ko.md)

처음이라면 **개발 환경 준비 → 최초 빌드** 순서로 진행하세요. 준비를 마쳤다면 [변경 후 실행](#변경-후-실행)에서 수정 내용에 맞는 명령을 고르면 됩니다. 번역만 기여하려면 [현지화 가이드](localization-ko.md)부터 읽으세요.

## 개발 환경 준비

Windows 10/11 x64와 Direct3D 12 지원 GPU, Python 3.12 이상, [requirements.txt](../requirements.txt)이 필요합니다. 작업 폴더는 `C:\Stranded II`처럼 영문 경로를 사용하세요. 공백은 있어도 됩니다. `dev.py`는 Python 3.12 미만이나 검증 assert를 건너뛰는 `-O`/`PYTHONOPTIMIZE` 환경에서는 오류로 중단합니다.

Visual Studio의 MSVC x64 C++ 빌드 도구, Windows SDK, CMake/Ninja 도구, DIA SDK를 설치합니다. `build`는 저장소의 `src/blitz3d/` 엔진을 LLVM과 함께 빌드해 게임을 x64 네이티브 실행 파일로 연결합니다. 그래픽은 Dawn/D3D12, 오디오는 OpenAL Soft를 사용합니다. Unicode 입력·IME·Windows 서비스는 런타임에 내장하며 `QolInput.dll`은 필요하지 않습니다. 실행에 필요한 DLL과 라이선스는 각 판의 EXE 옆에 복사합니다.

네이티브 런타임은 속도 우선 `Release` 설정(MSVC의 `/O2`)을 사용하며 기존 부동소수점 연산 규칙을 유지합니다. 배치·버퍼 수명·지형 및 가시성 캐시·평면 반사·기존 큐브 텍스처의 한계는 [렌더러 가이드](wgpu-migration-ko.md)를 참고하세요. 렌더러 변경은 반사 갱신 주기·풀 밀도·사용자 그래픽 설정을 유지하고 영향을 받는 플랫폼별로 검증합니다.

아래 의존성은 저장소에 포함하지 않으므로 직접 준비합니다.

| 의존성 | 다운로드 | 배치 경로 |
| --- | --- | --- |
| Stranded II Gold 1.0.0.1 English ZIP | [공식 다운로드](https://www.strandedonline.de/s2_download.php) | `deps/game/` |
| Noto Sans KR | [고정 버전 글꼴](https://raw.githubusercontent.com/google/fonts/4efc2774c63917927efe769ca845def6bd6debae/ofl/notosanskr/NotoSansKR%5Bwght%5D.ttf) | `deps/fonts/NotoSansKR[wght].ttf` |

압축을 푼 결과는 다음처럼 배치합니다. 게임은 수정되지 않은 영어 원본을 사용합니다.

```text
deps/
  game/
    StrandedII.exe
    core/
    mods/
  fonts/
    NotoSansKR[wght].ttf
```

게임·글꼴 버전과 해시는 [config/dependencies.json](../config/dependencies.json), [config/game-files.json](../config/game-files.json)에 있습니다. `setup`은 [config/blitz3d-toolchain.json](../config/blitz3d-toolchain.json)에 고정한 SDK·라이브러리를 `deps/blitz3d/`에 받습니다. 고정값이 바뀌어 기존에 받아 둔 압축 파일이나 UI 글꼴이 맞지 않으면 다시 받고, 새 해시가 일치할 때만 교체합니다. Blitz3D 1.118은 비교용 선택 의존성이며 실제 빌드에는 사용하지 않습니다. 엔진 출처와 통합한 변경은 [src/blitz3d/upstream.json](../src/blitz3d/upstream.json)에 기록합니다.

## 최초 빌드

저장소 루트의 PowerShell에서 실행합니다.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py setup --game deps/game --font 'deps/fonts/NotoSansKR[wght].ttf'
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py build
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py check
.\scripts\Play.cmd
```

`setup`은 원본 리소스·UTF-8 참조·글꼴·엔진 외부 의존성을 준비합니다. `src/blitz3d/`를 다운로드나 패치로 덮어쓰지 않습니다. 컴파일러·런타임 라이브러리·CMake 생성 헤더는 `build/engine/<target>/`에, 게임 산출물은 각 판의 빌드 폴더에 저장합니다.

외부 리소스 경로는 `--game`, `--font`로 지정합니다. `setup`이 Git에서 제외한 `build/dependencies.local.json`에 기록하므로 이후에는 생략할 수 있습니다. 이전 `--blitz` 인자는 허용하지만 컴파일러 선택에는 사용하지 않습니다.

## 변경 후 실행

다음 명령도 저장소 루트에서 사용합니다. 리소스를 갱신하거나 실행 파일을 교체하기 전에 개발 게임을 종료하세요. **판을 생략하면 기본판**이며 확장판은 `--edition extended`를 붙입니다.

| 변경 내용 | 실행 명령 |
| --- | --- |
| `src/engine/**/*.bb` 또는 `src/blitz3d/` 수정 | `.\scripts\dev.ps1 build -Python .\.venv\Scripts\python.exe` |
| `resources/locales/` 번역·언어·폰트 수정 | `.\scripts\dev.ps1 localize -Python .\.venv\Scripts\python.exe` |
| `resources/`의 번역 외 리소스 수정 | `.\scripts\dev.ps1 setup -Python .\.venv\Scripts\python.exe` |
| 자동 검사 | `.\scripts\dev.ps1 check -Python .\.venv\Scripts\python.exe` |
| 게임 실행 | `.\scripts\Play.cmd` |

저장소의 `scripts/Play.cmd`와 `scripts/Play-Extended.cmd`는 빌드된 실행 폴더를 여는 개발용 편의 스크립트입니다. 배포 ZIP에는 포함하지 않으며 플레이어는 해당 판의 EXE를 직접 실행합니다.

코드와 리소스를 함께 수정했다면 `setup → build → check → 실행` 순서로 진행합니다. 번역·새 언어·폰트만 수정했다면 `localize → check → 실행`으로 반영되며 EXE 재컴파일은 필요하지 않습니다. `setup`도 같은 언어팩 준비 과정을 포함합니다. 폰트 설정은 [현지화 가이드](localization-ko.md#폰트-지정)에 있습니다.

`setup`은 기존 실행 폴더의 세이브, `.cfg`, `settings.inf`, `core/modern.ini`를 보존합니다. 따라서 `config/modern.ini`를 바꿔도 기존 실행본의 설정이 자동으로 바뀌지 않습니다. 개인 설정은 게임 메뉴에서 바꾸거나 게임을 종료한 뒤 실행 폴더의 `core/modern.ini`를 수정합니다.

`build`는 임시 실행 파일을 컴파일한 뒤 `build/runtime/StrandedII.exe`를 교체합니다. 실행 중인 게임 때문에 교체에 실패했다면 게임을 종료하고 `build`를 다시 실행합니다.

Windows 테두리 없는 모드는 기본 모니터에서 SDL 데스크톱 전체화면을 사용해 SDL과 Win32의 창 상태를 일치시킵니다. 선택한 렌더링 해상도는 데스크톱 크기의 창과 별도로 유지합니다. SDL 게임 창의 스타일·위치를 Win32 API로 직접 변경하지 마세요. `verify_engine.py --graphics`는 실제 클라이언트 원점·영역, 반복 전체화면 전환, 데스크톱 표시 모드 유지, 렌더링 해상도 유지를 검사합니다. 관련 DPI 배율마다 실행하고, 실제 Alt-Tab 복귀는 게임에서 별도로 확인하세요.

Python으로 직접 실행할 수도 있습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py run -- -win -fps 120
```

## 개발자 메뉴

두 에디션이 공유하는 Dear ImGui 메뉴입니다. **설정 → 조작 → 개발자 메뉴**에서 단축키를 지정합니다. 배포본 기본값은 `None`이며, 로컬 개발 빌드만 처음에 `Insert`를 사용합니다. 우클릭으로 지정을 해제할 수 있고, 저장한 `None`과 사용자 단축키는 개발 빌드에서도 유지합니다.

메뉴를 열어도 게임은 진행되며 게임 입력은 차단됩니다. 상태 탭의 일시정지는 메뉴를 닫아도 유지됩니다. 싱글 플레이 치트, 아이템 아이콘 격자, 좌표·시간 이동, 설정 미리 적용, 검색·복사 가능한 로그를 제공합니다. 설정은 **저장**과 **저장된 설정으로 되돌리기**를 구분하며 자동 저장하지 않습니다. 화면 모드·해상도·FPS 제한·언어는 저장 후 재시작이 필요합니다. 로그는 최대 2,048건 / 2MiB이며 위로 스크롤하면 최신 로그 따라가기를 멈춥니다.

공유 게임 UI는 `src/engine/includes/devmenu.bb`, 네이티브 ImGui·SDL 입력·WebGPU 출력 연결은 `src/blitz3d/src/modules/bb/debugui/`, 브라우저 IME 연결은 `src/web/debugui.js`에 있습니다. Windows 두 에디션, macOS, Web 및 Studio가 같은 구현을 사용합니다. Windows에서 `python src/devtools/verify_engine.py --graphics --input`은 입력 소유권과 창·컨텍스트 수명을 검사합니다. `s2_debugui_check` CMake 타깃은 준비된 `core/locales/<code>/ui.ttf` 경로를 인수로 받아 유니코드 글리프·슬라이더·로그 따라가기를 창 없이 검사합니다. 실제 화면, 한글 조합·붙여넣기와 각 플랫폼 실행은 별도로 확인합니다.

### CPU·GPU 프로파일러와 성능 테스트 장면

**개발 메뉴 → 프로파일러**에서 기록을 시작하고 확인할 동작을 재현한 뒤 기록을 정지하여 프레임을 선택하거나 분석 결과를 복사합니다. 메뉴를 닫아도 기록은 계속되며 최근 2,048프레임을 보관합니다. CPU 전체·자체 시간과 GPU 렌더 패스 시간은 서로 겹치므로 합산하지 않습니다. GPU timestamp 미지원 장치에서도 CPU·그리기 통계는 확인할 수 있습니다. 측정 원리는 [렌더러 가이드](wgpu-migration-ko.md#181-선택형-cpugpu-프로파일링)를 참고하세요.

메인 메뉴에서 개발 메뉴의 **성능 테스트 → 테스트 장면 열기**를 선택하면 임시 장면에 오브젝트·유닛·아이템 그룹을 추가하고 그래픽 설정을 비교할 수 있습니다. 자동 비교는 조건마다 준비 3초·측정 8초를 3회 반복합니다. CSV·JSON은 실행 폴더의 `core/benchmarks/`에 내보내며 메인 메뉴로 돌아가면 테스트 변경을 저장하지 않고 이전 설정을 복원합니다. 완료 FPS는 마지막 비동기 GPU 완료 대기를 포함한 처리량이며 모니터 표시 FPS가 아닙니다.

## 네이티브 렌더링 벤치마크

Windows 두 에디션의 설정 → 그래픽 개선에서 효과를 개별 선택합니다. `render_antialias`는 끔(`0`), 저비용 FXAA(`1`, 기본값), 더 선명한 MSAA 4x(`4`)를 선택합니다. 저장된 선택을 보존하며 누락·잘못된 값에는 `1`을 사용합니다. FXAA는 일반 `RenderWorld` 후 한 번 적용합니다. MSAA는 주 화면의 색상·깊이를 네 샘플로 렌더링하고 비트맵 글자·HUD를 그리기 전에 합칩니다. 둘 다 반사·그림자 캡처를 제외합니다. MSAA에는 FXAA를 겹쳐 적용하지 않으며 GPU 시간·메모리 비용이 더 큽니다. 물·조명·대기는 기본 ON(`render_water=1`, `render_lighting=1`, `render_atmosphere=1`), 태양 그림자는 기본 OFF(`render_shadows=0`)입니다. 키가 누락돼도 이 기본값을 사용하며 기존에 저장한 선택이 우선합니다. 설정 저장에 성공한 뒤 적용하면 현재 장면에 반영합니다. 물은 기존 반사 해상도·갱신 주기를 유지하며 그림자는 렌더링 비용을 늘립니다. FPS는 제한 없음(`fps=0` 또는 `-fps 0`)과 60/120/144를 제공하며 화면·FPS 변경은 재시작이 필요합니다.

조명이나 그림자를 켜면 원작 주변광 예산을 태양광과 나눠 낮의 과도한 밝기를 줄이고 일출·일몰에 야간 값과 부드럽게 연결합니다. `check`의 `verify_lighting.py`가 실제 Blitz 함수의 시간·하늘 색·옵션 조합을 검사하며, `verify_engine.py --render`가 한낮 지면 밝기와 그림자 명암을 실제 D3D12 픽셀로 확인합니다.

Windows에서 선택한 에디션의 실행 환경을 준비한 뒤 격리된 벤치마크를 실행합니다. 고정 시드로 나무·바위 0/256/768/1536개를 배치한 `.s2` 스트레스 지도를 생성·재로드하고 첫 모험 섬도 검사합니다. 실제 게임 루프·네이티브 렌더러·시계를 사용하며 카메라와 입력은 고정합니다. GUI 조작이 필요 없으며 기존 게임·세이브·설정을 보존합니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/benchmark_native.py --run
.\.venv\Scripts\python.exe -X utf8 src/devtools/benchmark_native.py --existing build/benchmark-<name> --run --suite resolution --width 1280 --height 720 --label lowres
.\.venv\Scripts\python.exe -X utf8 src/devtools/benchmark_native.py --existing build/benchmark-<name> --rebuild --run --cases baseline,island,objects_1536 --label optimized
.\.venv\Scripts\python.exe -X utf8 src/devtools/benchmark_native.py --run --cases baseline,island --width 1920 --height 1080 --render water,lighting,shadows,atmosphere --label remaster
```

기본값은 2048×1152, 조건별 3회 반복, 준비 3초·측정 8초이며 매회 프로세스를 새로 시작합니다. 물·풀·시야 거리·지형·바람·오브젝트 밀도·하늘 방향을 비교합니다. `--suite smoke`는 대조 조건 5개, `--cases baseline,water_3,grass_0`는 개별 조건을 선택합니다. 기존 결과를 남기고 다시 측정하려면 새 `--label`을 지정합니다. `--edition extended`는 해당 에디션의 리소스·설정만 사용하며 기존 폴더를 재사용할 때도 지정해야 합니다.

각 측정값은 `build/benchmark-<name>/results.json`, 조건별 중앙값·범위·완료한 반복 횟수는 `summary.json`에 저장합니다. 소스 해시·실행 계획·로그·프레임별 CSV·캡처·생성 지도도 함께 남깁니다. 측정 전후 GPU 픽셀 읽기로 제출한 작업의 완료를 기다립니다. FPS에는 마지막 완료 대기가 포함됩니다. 구간별 시간과 p95/p99 프레임 간격은 호스트에서 잰 경과 시간이며 GPU 타임스탬프가 아닙니다. 반사 시간은 물 시간에 포함됩니다. `sync_control`은 매 프레임 비용이 큰 픽셀 읽기를 추가하는 완료 확인용 조건입니다.

테스트 복사본만 포커스 없이 렌더링을 계속하며 최소화된 측정은 무효 처리합니다. 백그라운드 결과는 렌더링 완료 처리량이며 전경 플레이나 실제 화면 출력 FPS와 다릅니다. 같은 조건에서 옵션을 비교하고 캡처를 확인하세요. 입력 반응·카메라 이동·전체 플레이 검증을 대신하지 않습니다.

`--render`는 임시 실행 환경에서 지정한 물·조명·그림자·대기 효과만 켭니다. 계단현상 완화는 독립 옵션이며 FXAA(`--antialias on`)가 기본값입니다. 비교할 때는 `--antialias off` 또는 `--antialias msaa4x`를 사용합니다. 실행 계획과 측정 메타데이터에 두 선택을 모두 기록합니다. 144 FPS 목표를 평가할 때는 장면·해상도·그래픽 설정을 맞춰 비교합니다.

## 실험용 WebGPU 빌드

모든 Python 개발 명령은 `--platform windows|web|macos`를 받으며 기본값은 `windows`입니다. 웹은 기본판만 빌드합니다. `--platform web --edition extended`는 오류를 반환하며 Extended CI는 계속 비활성 상태입니다.

`src/engine/StrandedIIWeb.bb`는 공통 게임과 브라우저 서비스를 포함합니다. Windows·웹·macOS·Studio가 모두 `src/blitz3d/`의 수정 가능한 엔진을 빌드하며 웹 타깃은 [Emscripten](../config/web-toolchain.json)을 사용합니다. 엔진 소스를 직접 수정한 뒤 다시 빌드합니다. WASM·WebGPU·JSPI·브라우저 저장은 [웹 버전 구현 원리](web-architecture-ko.md)를 참고하세요.

웹 개발에는 Git도 필요합니다. 준비 과정은 `deps/blitz3d/`, `deps/emsdk/`, `deps/ffmpeg-web/`에 고정 의존성을 받으므로 첫 빌드에는 시간과 디스크 공간이 필요합니다. 먼저 Windows 준비를 마친 뒤 실행합니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py setup --platform web
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py build --platform web
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py check --platform web
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py run --platform web
```

데스크톱 Chrome/Edge에서 `http://127.0.0.1:8765/`를 엽니다. WebGPU와 WebAssembly JSPI 지원 여부를 확인한 뒤 리소스·저장소 준비가 끝나면 원래 게임이 자동 실행됩니다. 별도 시작·언어 선택 화면은 없으며, HTML 안내는 영어입니다. 게임 언어는 게임 설정에서 변경하고 저장된 선택을 유지합니다. 첫 실행 기본값은 영어입니다. 첫 화면 클릭으로 마우스와 오디오를 활성화하며 이 클릭은 게임 입력으로 전달하지 않습니다.

전체화면 버튼이나 `Alt+Enter`로 전체화면을 전환합니다. 전체화면에서 브라우저가 키보드 잠금을 허용하면 짧은 `Esc`는 원래 게임에 전달되고 길게 누르면 잠금에서 빠져나옵니다. 로딩·메인 메뉴는 잠금 없이 표시되며, 플레이 중 잠금 해제나 창 전환 시에는 화면을 가리지 않고 일시정지합니다. 게임 화면을 클릭하면 돌아오고, 브라우저가 잠금 재요청을 거부하면 잠시 후 다시 클릭합니다. 서버 포트가 차단돼 있으면 `run --platform web --port 4173`처럼 지정합니다. 게임 인자는 웹 `run`에 전달하지 않습니다.

웹 출력은 `build/web/site/`, 준비된 리소스는 `build/web/runtime/`입니다. 리소스는 검증한 공식 원본과 공통 언어팩에서 준비하며 Windows 실행 폴더의 저장·설정을 복사하지 않습니다. CI는 의존성 ZIP을 `build/ci/deps/game`에 풀어 사용하며 웹 준비 단계에서 리소스·글꼴 해시를 다시 검사합니다. 생성된 실행 폴더는 리소스 입력으로 사용할 수 없습니다. 번역·폰트 수정은 `localize --platform web` 뒤 `build --platform web`로 다시 패키징합니다.

웹에서 지원하지 않는 WAV 인코딩은 해시를 검증한 FFmpeg로 `build/web/audio/`에 PCM 변환하고 웹 패키지에만 반영합니다. 파일 경로·샘플레이트·채널 수를 유지하며 원본 리소스는 수정하지 않습니다. FFmpeg는 준비 도구이며 배포 파일에 포함하지 않습니다.

저장·설정은 브라우저의 IndexedDB에 기본판 전용으로 보관합니다. 저장 교체와 백업은 기록 완료를 기다리며, 설정은 첫 실행 때만 기본값을 적용합니다. 같은 브라우저·프로필·주소·포트로 접속해야 같은 저장소를 사용합니다. 사이트 데이터를 지우면 저장도 삭제됩니다. 같은 주소에서 두 탭이 동시에 저장소를 사용하지 못하도록 잠급니다.

`check --platform web`는 산출물·소스 해시, 배포 리소스 경계, 변환 오디오, Windows/wasm 숫자·문자열·바이너리 일치, 실제 메뉴·인트로 스크립트의 줄 분리와 웹 파일시스템의 저장 교체·실패 복구를 검사합니다. CPU 버퍼 검사에서는 실제 지형 색상 생성 함수에 고정된 높이 구간을 입력해 색상을 확인하고, 전체 버퍼 전송 횟수를 제한해 픽셀마다 GPU를 동기화하는 문제를 방지합니다. 자동 시작, 신규 영어 설정·기존 한국어 설정 보존, 입력·전체화면 상태 전환은 모의 DOM으로 검사합니다. 결과는 `build/web/verification.json`에 기록합니다. CPU 검사·모의 DOM·저장 거래 검사는 실제 GPU·브라우저·IndexedDB 검증을 대신하지 않습니다.

실제 엔진 비교는 웹 빌드 후 아래 명령으로 준비합니다. 별도 복사본에서 Windows 기준 검사를 실행하며 현재 게임과 저장을 사용하지 않습니다. 오디오 비교에는 양쪽 모두 웹용으로 준비한 파일을 사용합니다. 웹 서버의 `/checks/`를 열고 검사 버튼을 누르면 같은 시나리오의 WebGPU 결과를 비교합니다. 한글 비트맵·이미지 투명색, 화면 복사와 모션 블러의 상하 방향, 오디오 디코딩, 에디터·랜덤 섬 초기화(지형·풀 색상, 플레이어 생성, 월드별 실제 소요 시간 30초 제한), 메뉴 카메라 반복, 원본 인트로의 배·카메라·이벤트, 첫 섬 전환과 건너뛰기를 검사합니다. 인위적으로 진행시킨 시퀀스 시간은 실제 재생 속도나 FPS 검증을 대신하지 않습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/verify_web_game.py
```

WebGPU의 동일 버퍼 `CopyRect`는 임시 텍스처를 재사용하고, 앞선 쓰기와 겹치지 않는 원본 영역들을 묶어 물결·수중 효과의 복사와 그리기를 줄입니다. 겹치는 복사는 앞선 결과를 반영하며 확대·축소는 기존 경로를 사용합니다. `check --platform web`의 C++ CPU 검사에서는 실제 복사 함수를 실행해 픽셀·투명색·잘림·순서와 물결 갱신당 스냅샷 2회, 텍스처 재사용을 확인합니다. 실제 GPU 성능은 플레이로 확인해야 합니다.

프레임 드랍 비교는 `src/devtools/profile_web.py --serve`로 준비하고 Chrome/Edge에서 `http://127.0.0.1:8766/`를 열어 시작합니다. 실제 WebGPU와 실제 시계를 사용하는 별도 엔진 복사본이며 IndexedDB나 사용자 저장을 연결하지 않습니다. 섬 또는 메뉴 장면과 해상도를 선택하면 물 품질 6·3·0(144 FPS 제한), 물 품질 0(60 FPS 제한)을 준비 5초 후 각각 30초씩 3회 측정합니다. 조건마다 새 WASM 인스턴스를 사용하며 창이 포커스를 잃으면 해당 측정은 무효입니다. 장면당 약 7분 동안 창을 앞에 두세요.

결과는 `build/web/profile-*/report.json`에 저장됩니다. 완료된 Flip 사이의 FPS·p95/p99 간격, 물·반사·주 렌더링·프레임 대기의 CPU 시간, 텍스처 생성·복사·그리기 호출 수를 기록합니다. 반사 시간은 물 처리 시간의 일부이며 더해서 계산하지 않습니다. 이 값은 GPU 실행 시간이나 실제 화면 스캔아웃 FPS가 아니며, 계측에도 비용이 듭니다. 자동 카메라와 음소거 상태의 비교는 조작·오디오·전체 플레이 검증을 대신하지 않습니다. `--existing build/web/profile-<이름>`으로 준비한 측정 서버를 다시 열 수 있습니다.

브라우저에서는 메뉴, `maps/adventure/map01.s2`의 약 53초 인트로, 첫 플레이 섬인 `map02.s2`를 순서대로 확인합니다. 지형·모델·한국어 UI·실제 소리, 이동·충돌·채집·제작, 저장·불러오기, 새로고침·브라우저 재시작 후 복구, 포커스·마우스 잠금 복귀는 별도 플레이 검증 대상입니다. 빌드 성공은 이 검증의 완료를 뜻하지 않습니다.

현재 범위는 실험용 싱글플레이 웹 빌드입니다. 모바일, 웹 Extended, 에디터, 전체 캠페인은 지원 완료 범위에 포함하지 않습니다. 브라우저에 없는 원시 TCP/UDP 기능은 연결 실패로 반환합니다.

### GitHub Pages 배포

[WebPlay 워크플로](../.github/workflows/web.yml)는 main 푸시와 main 대상 수동 실행에서 빌드·검사 후 Pages에 배포합니다. 변경 파일이 모두 소문자 `.md`인 푸시·PR은 제외하며, 수동 실행에는 이 필터를 적용하지 않습니다. PR은 검사와 아티팩트 생성만 수행하며, 같은 PR에 새 푸시가 오면 진행 중인 검사를 취소합니다. Windows에서 기존 CI 의존성 ZIP과 고정한 웹 도구로 빌드하고 Ubuntu에서 게시하며, 웹 워크플로는 Release·태그를 생성하지 않습니다. 기존 Windows 릴리스 정책은 유지됩니다.

최초 게시 전에 저장소 **Settings → Pages → Build and deployment → Source**를 **GitHub Actions**로 설정하고 `github-pages` 환경에서 main 배포를 허용합니다. 주소는 `https://shlifedev.github.io/Stranded-II-Modern/`입니다. 로컬 주소와 Pages의 브라우저 저장소는 별개이며 자동 이전하지 않습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py build --platform web
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py check --platform web
.\.venv\Scripts\python.exe -X utf8 src/devtools/web_pages.py
```

`web_pages.py`는 검사를 통과한 현재 빌드의 `debugui.js`를 포함한 실행 파일 9개만 `build/web/pages/`에 모읍니다. `/checks/`, 로그, 개인 저장은 게시하지 않습니다. 상대 경로와 배포별 캐시 키를 사용하며 배포 목록·해시는 `build/web/pages-report.json`에 기록합니다. 출력 폴더에 예상하지 못한 파일이 있으면 삭제하지 않고 중단합니다. 배포 작업은 직렬화하고, 빌드·검사 실패 시 기존 사이트를 유지합니다. 게시 후 HTTP 파일 해시·WASM 형식을 확인하지만 실제 플레이 검증은 별도로 수행해야 합니다.

## 실험용 macOS 네이티브 빌드

기본판의 Apple Silicon 실험용 빌드입니다. 게임 코드는 ARM64 네이티브 실행 파일로 컴파일하고, WebGPU 렌더러는 Dawn을 통해 Metal을 사용합니다. 컴파일·렌더링·운영체제 연동의 원리는 [macOS 구현 원리](macos-architecture-ko.md)에 정리했습니다. [타깃 설정](../config/macos-toolchain.json), 공통 `src/blitz3d/` 엔진, `src/native/macos/`에서 이 타깃을 관리합니다. 기본 플랫폼은 계속 Windows이며 Extended Mac 빌드는 오류를 반환합니다.

Apple Silicon Mac에 Xcode Command Line Tools, Git, Python 3.12 이상, CMake, Ninja, FFmpeg/ffprobe, zstd가 필요합니다. 포함하는 Dawn 라이브러리에 맞춰 배포 타깃은 macOS 15.0입니다. CI는 macOS 15에서 빌드와 비그래픽 실행을 검사하며 실제 그래픽 플레이는 별도 검증이 필요합니다. 준비 과정은 `deps/blitz3d/`에 공통 고정 의존성을 받고 `build/engine/`에 컴파일러·런타임을 빌드하므로 시간과 디스크 공간이 필요합니다. 입력은 해시가 일치하는 공식 게임 리소스와 고정 글꼴이어야 하며 플레이어 데이터가 있는 실행 폴더를 지정하지 마세요.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python src/devtools/dev.py setup --platform macos --game deps/game --font "deps/fonts/NotoSansKR[wght].ttf"
.venv/bin/python src/devtools/dev.py build --platform macos
.venv/bin/python src/devtools/dev.py check --platform macos
.venv/bin/python src/devtools/macos_package.py
.venv/bin/python src/devtools/dev.py run --platform macos
```

`build`는 로컬 서명한 `build/macos/apps/<build-id>/Stranded II.app` 경로를 출력하고 `build/macos/build-report.json`에 기록합니다. 앱을 더블클릭하면 `~/Library/Application Support/Stranded II Native Preview/`에 리소스를 준비하고 설정·세이브를 저장합니다. 리소스를 갱신해도 기존 설정·세이브를 유지합니다. `run --platform macos`는 같은 앱을 격리된 `build/macos/runtime/` 프로필로 실행합니다. 두 경로 모두 Windows·브라우저 데이터를 가져오지 않습니다. 새 프로필은 1280×720 창 모드로 시작합니다. `localize --platform macos`는 개발용 언어팩을 갱신하며, 앱에 반영하려면 다시 빌드합니다. 지원하지 않는 원본 WAV 인코딩은 네이티브 실행 폴더에서 PCM으로 변환하고 원본 파일은 보존합니다.

`check --platform macos --suite core`는 화면을 열지 않고 원본 Blitz3D 1.118 결과 613바이트, Type 메모리 재사용, 앱 리소스·서명·실행 권한, ARM64·최소 OS와 시스템 라이브러리 의존성을 검사합니다. `--suite full`이 기본값이며 실제 Metal 렌더링·픽셀 읽기, 공통 메뉴·인트로·섬, 한국어 글꼴, 오디오 디코딩, 저장·백업·재불러오기와 저장·맵 목록 미리보기, Cocoa 문자열 왕복, 새 프로필 설치와 리소스 갱신 시 데이터 보존을 추가로 검사합니다. `--suite`는 Mac 검사에만 사용할 수 있습니다. 최신 결과는 `build/macos/verification.json`, 검사별 결과는 `verification-core.json`·`verification-full.json`에 기록합니다. 게임 시간은 가상으로 진행하므로 실제 FPS·소리·IME 조합 입력·포커스·전체 화면·캠페인 검증을 대신하지 않습니다.

`macos_package.py`는 최신 성공 검사와 현재 소스·도구·실행 파일·앱이 일치할 때만 DMG와 `.dmg.sha256`을 만듭니다. 로컬 빌드는 `build/macos/release/StrandedII-<version>-macos-arm64-preview-<build-id>.dmg`, 출시 빌드는 `StrandedII-<display_version>-macos-arm64.dmg`입니다. 출시 버전은 `build --platform macos --version-info build/ci/version.json`으로 전달하며 현재 커밋과 일치해야 합니다. 출시는 `macos_package.py --notarize`가 필수입니다. 압축된 읽기 전용 HFS+ 이미지에는 앱, 드래그 설치용 Applications 바로가기, 영어·한국어 안내와 로컬 경로·개인 설정·세이브를 제외한 검증 요약이 들어갑니다. 이미지를 읽기 전용으로 마운트해 내용·권한을 비교하고 설치용으로 복사한 앱을 검사합니다. 성공·실패 모두 마운트를 해제합니다. 요약에는 공증 티켓을 붙인 앱 해시를 기록하고 `core` 패키지의 그래픽 검사는 `not_run`으로 명시합니다. 파일시스템 메타데이터의 바이트 일치를 요구하지 않고 내용이 같은 기존 이미지를 재사용할 수 있습니다. 결과 경로는 `build/macos/distribution-report.json`에 기록합니다.

[Mac CI](../.github/workflows/macos.yml)는 수동 테스트와 데스크톱 출시 워크플로의 재사용 빌드를 지원합니다. `macos-15` ARM64에서 고정 의존성을 `deps/ci-macos/`에 검증·준비하고 준비 → 빌드 → `core` 검사 → DMG 생성을 실행합니다. **Actions → macOS build → Run workflow**에서 브랜치를 선택하세요. 기본값인 `notarize=true`는 앱을 서명·공증하고 티켓을 붙인 뒤 최종 DMG도 서명·공증하고 티켓을 붙입니다. 둘 다 Gatekeeper 검사를 통과해야 합니다. `false`는 임시 서명한 앱을 넣은 미리보기 DMG를 만듭니다. 수동 실행은 `ci.py version`으로 `-local` 버전을 생성하며 Release·태그를 만들거나 출시 번호를 소모하지 않습니다. 출시 빌드는 Windows의 버전 아티팩트와 소스 커밋을 사용합니다. DMG·체크섬과 보고서는 Actions 아티팩트로 14일 보관하며 실제 게시는 아래 데스크톱 출시 절차에서 담당합니다.

출시용 저장소 Secrets는 `APPLE_CERTIFICATE`(Developer ID Application 인증서·개인 키가 든 P12의 Base64), `APPLE_CERTIFICATE_PASSWORD`(P12 암호), `APPLE_ID`, `APPLE_PASSWORD`(앱 전용 암호), `APPLE_TEAM_ID`입니다. 데스크톱 워크플로가 Mac 재사용 워크플로에 명시적으로 전달합니다. 로컬 `--notarize` 검사에도 같은 환경 변수를 사용합니다. 임시 서명 키체인을 사용자 검색 목록에 추가하며 성공·실패 모두 기존 목록을 복원한 뒤 삭제합니다. 앱은 Hardened Runtime을 사용하며 앱·DMG 모두 보안 타임스탬프와 같은 팀 ID를 사용합니다. 각 공증은 최대 20분 기다립니다. 티켓 첨부·검증은 일시적인 Apple 네트워크 오류에 한해 5·15·30초 간격으로 최대 4회 시도합니다. 유효하지 않은 티켓·서명, 공증 거부와 재시도 소진은 패키징을 중단합니다. 앱 결과·로그는 `build/macos/notarization-{report,log}.json`, DMG 결과·로그는 같은 폴더의 `notarization-dmg-{report,log}.json`에 기록합니다. [Apple 공증 안내](https://developer.apple.com/documentation/security/customizing-the-notarization-workflow)를 참고하세요.

테스트 DMG는 별도 Apple Silicon Mac, 특히 macOS 15에서 열고 앱을 Applications로 드래그한 뒤 이미지를 꺼내세요. 실행·재실행과 앱 교체 후 데이터 보존을 확인하세요. 한글 조합 입력, 전체 화면·앱 전환 후 입력 복귀, 배경음·효과음과 30분 플레이 후 저장·종료·재불러오기는 수동 검사 대상입니다. 기본 로컬 빌드와 `notarize=false`인 수동 CI는 임시 서명이며 공증하지 않습니다. 개발 메뉴용 `development.flag`는 로컬 개발 실행 폴더에만 유지하고 앱·DMG에서는 제외합니다. 네이티브 동영상·원시 TCP/UDP, Intel과 네이티브 Extended는 지원하지 않습니다.

## Extended 제작 앱

[Stranded II Studio](studio-ko.md)는 독립 GUI와 JSON CLI로 콘텐츠·맵을 편집합니다. 실제 Blitz/Extended 엔진을 사용하는 3D 화면, 실행 취소, 검증, 네이티브 리소스 내보내기를 제공합니다. 전용 명령은 `python src/devtools/studio.py`이며, Windows는 `scripts/Studio.cmd`, Apple Silicon macOS는 `sh ./scripts/Studio.sh`로 의존성·엔진 준비부터 앱 실행까지 자동 처리합니다. 자세한 사용법은 Studio 가이드를 따릅니다. 엔진 산출물은 `build/studio/`, 앱 산출물은 `src/studio/dist/`에 저장하고 일반 게임·CI·릴리스 에디션 선택은 변경하지 않습니다.

Studio는 편집·플레이에 동일한 iframe과 Blitz 엔진을 사용합니다. `src/studio/engine/studio.js`가 기존 차분 ACK를 기다린 뒤 최신 저장 맵을 확인하고, `src/engine/studio/runtime.bb`의 `studio_load`가 엔진·정의를 유지한 채 맵을 다시 불러옵니다. 리소스 변경은 명시적인 **리소스 적용**으로 초기화합니다. 정지는 메모리 파일 시스템의 편집 맵 원본과 카메라를 복원하며 프로젝트에는 쓰지 않습니다. 이 경로를 수정하면 Studio 빌드·검사를 실행하고, 공통 엔진도 수정했다면 두 판의 빌드·검사와 관련 게임 검증을 추가합니다.

## 어디를 수정하나요?

**게임 기능은 `src/engine/`, 컴파일러·런타임 변경은 `src/blitz3d/`에서 구현합니다.** `build/`는 실행 파일·검사 결과가 생기는 곳이며 원본 소스는 아닙니다. 아래 경로와 명령은 기본판 기준입니다. 확장판 경로는 [다음 절](#기본판과-확장판)을 참고하세요.

```text
src/engine/ 수정 → build 명령 → build/runtime/StrandedII.exe → scripts/Play.cmd
resources/locales/ 번역·언어·폰트 수정 → localize 명령 → build/runtime/core/locales/ 갱신
resources/의 번역 외 리소스 수정 → setup 명령 → build/runtime/의 리소스 갱신
```

| 경로 | 역할 | Git 관리 |
| --- | --- | --- |
| `src/engine/` | 직접 수정하는 Blitz3D 게임 소스. 진입점은 `StrandedII.bb` | 포함 |
| `src/blitz3d/` | 직접 수정하는 공통 컴파일러·런타임·그래픽/입력/오디오 모듈과 원본 라이선스 | 포함 |
| `resources/locales/` | 언어별 번역·이름·폰트 설정과 기존 문구 번호 대응 | 포함 |
| `resources/` | 원본 문구의 번역 키 대응, 수정할 리소스 | 포함 |
| `scripts/` | 셸·PowerShell·CMD 실행 파일 | 포함 |
| `src/devtools/` | Python 준비·빌드·검증·Studio 도구 | 포함 |
| `src/studio/` | Studio UI·Electron 실행부·앱 테스트 | 포함 |
| `src/tests/` | 통합 테스트와 네이티브·웹 검증 자료 | 포함 |
| `AGENTS.md`, `docs/reference/AGENTS-ko.md` | 에이전트 지침과 한국어 보기용 번역 | 포함 |
| `config/` | 게임 기본값·버전, 빌드·의존성 설정과 검증 해시, 문서 번역 설정, 구버전 비교용 선언 | 포함 |
| `docs/`, `licenses/` | 문서와 저작권 고지 | 포함 |
| `deps/` | 원본 게임·글꼴과 고정한 외부 SDK·라이브러리 | 제외 |
| `.venv/` | Python 가상환경 | 제외 |
| `build/engine/` | 타깃별 컴파일러·런타임 빌드와 생성 헤더 | 제외 |
| `build/check-*/` | 자동 검사가 생성한 임시 코드·실행 파일·보고서 | 제외 |
| `build/runtime/` | 빌드된 게임, 리소스, 사용자 설정과 저장 파일 | 제외 |

생성 결과를 수정하면 다음 `setup` 또는 `build`에서 덮어쓸 수 있습니다. 유지할 코드·번역·리소스 변경은 위 원본 폴더에 반영합니다. Python 도구는 리소스와 검증 코드를 생성하며, `src/engine/`의 게임 소스는 재생성하지 않습니다.

## 기본판과 확장판

`classic`은 선택형 QOL을 기본 OFF로 제공하며 원작 게임플레이를 유지하는 기본판입니다. `extended`는 같은 QOL을 기본 ON으로 제공하며 향후 별도 기능·밸런스·콘텐츠를 개발할 확장판으로 유지합니다. 두 판 모두 설정 → QOL에서 다섯 옵션을 각각 선택합니다. QOL은 기존 제작·이동·건축 규칙을 호출하며 재료 비용이나 도구 속도를 바꾸지 않습니다. 공통 코드는 한 번 수정해 두 판에서 검증하고, 확장판 전용 변경은 해당 판의 코드·자료에만 작성합니다.

`classic`은 내부 구분용입니다. 기본판 창 제목·실행 파일명은 `Stranded II`·`StrandedII.exe`로 유지하고, 확장판만 `Stranded II - Extended`·`StrandedII-Extended.exe`를 사용합니다. 빌드·실행·패키징은 이 이름을 함께 사용합니다.

```text
src/engine/
  StrandedII.bb                    기본판 진입점
  StrandedIIExtended.bb            확장판 진입점
  includes/engine_init.bb          공통 초기화·기능
  includes/engine_loop.bb          공통 실행 루프
  includes/                       현재 공유하는 엔진 구현
  editions/classic/profile.bb     기본판 식별·창 제목
  editions/classic/modules.bb     기본판의 UI·입력·건축 구현 선택
  editions/extended/profile.bb    확장판 식별·창 제목
  editions/extended/modules.bb    확장판의 UI·입력·건축 구현 선택
resources/overrides/              QOL 안내 등을 포함한 공통 리소스 변경분
resources/editions/classic/overrides/   기본판 리소스 변경분
resources/editions/extended/overrides/  확장판 리소스 변경분
build/runtime/                   기본판 실행·설정·세이브
build/extended/runtime/          확장판 실행·설정·세이브
```

두 `modules.bb`는 공통 UI·입력·건축과 `includes/qol*.bb` 구현을 선택합니다. 판별 프로필의 `Cedition_qol_default`는 기본판 0, 확장판 1입니다. 저장된 `qol_*` 값이 우선하며 누락·잘못된 값에는 해당 판의 기본값을 사용합니다. QOL은 설정 저장에 성공하면 즉시 적용하고, 언어·화면 변경은 계속 재시작이 필요합니다. 검색 입력은 Win32 Unicode EDIT와 IME를 사용하며 연결 함수는 `src/blitz3d/src/modules/bb/platform.windows/`에 내장합니다. 기능이 달라질 때만 필요한 모듈을 분리합니다. Python은 엔진 코드를 생성하지 않습니다. 빌드 보고서는 해당 진입점의 실제 `Include` 그래프를 기록하며 다른 판의 전용 소스를 포함하면 검사에 실패합니다.

조합법 도감은 로드된 `Tcom` 데이터를 읽습니다. 조리·물 사용 등의 안내는 공통 변경분의 `mods/Stranded II/sys/qol_guides.inf`에 별도로 작성하며, `source`에 근거 스크립트와 이벤트를 기록합니다. 안내는 스크립트를 대신 실행하지 않습니다. 원본 이벤트를 수정할 때 안내의 조건·수량·확률 설명도 함께 확인하세요. QOL 설정은 해당 판의 `core/modern.ini`에만 저장합니다.

검색창 연결부는 게임 스레드의 해당 Unicode EDIT 메시지만 문자 변환 후 전달합니다. Windows의 `HidePointer`는 커서만 숨기고 상대 마우스 고정을 켜지 않아 자식 입력창이 클릭을 받을 수 있습니다. EDIT에 포커스가 있으면 게임 키·문자 처리는 입력창에 맡깁니다. `WS_CLIPCHILDREN`으로 입력창을 보호하며 크기가 달라질 때만 위치를 갱신합니다. 닫을 때는 훅과 추가한 스타일을 복원합니다.

Windows는 플레이 중 시점을 조작할 때만 상대 마우스 입력을 켭니다. 이때 `MoveMouse`는 OS 커서를 옮기지 않고 가상 기준점을 옮기며, 마지막 `MouseXSpeed`·`MouseYSpeed` 호출 뒤에 들어온 미처리 이동량을 보존합니다. 캡처·포커스가 바뀔 때만 이전 이동량을 버립니다. 메뉴·콘솔·검색창에서는 캡처를 해제합니다. Enter와 숫자패드 Enter는 원래 Blitz 문자 코드 13을 전달합니다. 테두리 없는 화면에서 선택한 렌더 해상도를 확대·축소하면 선형 필터로 표시하고 마우스 좌표도 해당 렌더 해상도로 변환합니다.

메뉴 커서는 활성 게임 창에서 Windows의 현재 커서 위치를 직접 읽어 렌더 좌표로 변환합니다. 외부 배속 도구가 `GetTickCount`와 Windows 메시지 시간의 기준을 어긋나게 만들어 SDL 이동 이벤트가 누락돼도 좌표를 갱신합니다. 상대 입력·검색창 포커스·비활성·최소화 상태에서는 이 조회를 적용하지 않습니다. 게임 시간은 기존 QPC 경로를 유지합니다.

두 에디션에서 상시 모션블러와 그래픽 설정의 켜기/끄기·강도 항목을 제거했습니다. 시나리오 시작·어지럼·취함·사망 등 스크립트와 상태가 요청하는 연출은 원래 스프라이트 누적 방식으로 유지합니다. 기존 `settings.cfg`의 마지막 두 모션블러 값은 호환을 위해 읽고 보존하지만 렌더링에는 사용하지 않습니다. 자세한 내용은 [렌더러 설명](wgpu-migration-ko.md#15-copyrect물-왜곡motion-blur의-readwrite-hazard)을 참고하세요.

리소스는 공식 입력 → 현지화 준비 → 공통 변경분 → 선택한 판의 변경분 순서로 준비합니다. 판별 변경분은 실행 폴더 기준 `mods/Stranded II/...` 경로로 배치합니다. 아이템·조합·건축·능력치·맵·스크립트 리밸런싱은 확장판 변경분에 작성합니다. 설정·세이브·실행 파일은 리소스 변경분으로 덮어쓸 수 없습니다. `.s2` 지도 수정은 [리소스 현지화의 바이너리 규칙](#새-문구와-리소스-현지화)을 따릅니다. 판별 번역은 [현지화 가이드](localization-ko.md#기본판확장판-전용-번역)를 참고하세요.

모든 개발 명령은 `--edition classic|extended`를 받으며 생략하면 기본판입니다. 의존성과 컴파일러는 공유하고, 실행 폴더·보고서·검증 산출물은 분리합니다. 기본판 데이터는 이동하지 않으며 확장판으로 자동 복사하지 않습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py setup --edition extended
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py build --edition extended
.\.venv\Scripts\python.exe -X utf8 src/devtools/dev.py check --edition extended
.\scripts\Play-Extended.cmd
```

위 명령은 [최초 빌드](#최초-빌드)에서 의존성 경로를 등록한 뒤 사용합니다. 확장판부터 준비한다면 최초 `setup` 명령에 `--edition extended`를 붙이세요. 확장판 기본 설정은 `config/editions/extended/modern.ini`이며, 이미 존재하는 개인 설정은 `setup`으로 교체하지 않습니다.

## 새 문구와 리소스 현지화

이 절은 새 UI·퀘스트를 만드는 개발자용입니다. 기존 문장 번역은 [현지화 가이드](localization-ko.md)를 따르세요.

1. `resources/locales/en.tsv`에 `quest.island1.arrival`처럼 용도를 알 수 있는 새 키와 영어 문구를 추가합니다. 확장판 전용 문구는 `resources/locales/editions/extended/en.tsv`에 넣습니다.
2. 번역할 언어의 같은 경로에 동일한 키와 번역을 넣습니다.
3. 표시할 코드나 스크립트에서 `[[loc:키]]`로 참조합니다.

```blitzbasic
bmpf_txt(20,20,"[[loc:ui.language]]")
```

```text
msg "[[loc:quest.island1.arrival]]";
```

키는 영문자·숫자·`_`·`.`·`-`를 사용하고 중복하지 않습니다. 기존 `legacy.*`·해시 키는 유지하세요. TSV의 빈 줄과 `#` 시작 줄은 무시하고, 문자열 이스케이프는 `\n`, `\r`, `\t`, `\\`를 지원합니다. 참조를 중첩할 때는 최대 8단계이며 순환 참조를 만들지 않습니다. 양쪽 언어에 키가 없으면 `[missing: 키]`와 실행 폴더의 `core/localization.log`로 진단합니다. 배포 전에 `check` 오류를 해결하세요.

같은 영어 단어라도 의미가 다르면 키를 분리합니다. 기본 문자열 로더는 나침반의 동쪽, 키보드 키, 낚시 동작을 각각 E 키, 아이템 열쇠, 물고기의 기존 키와 구분하며, 모드가 지정한 자체 문구는 유지합니다.

| 수정 대상 | 위치와 규칙 |
| --- | --- |
| 원본 표시 문구와 키의 연결 | `resources/source-keys.json`, 도움말은 `resources/help-terms.json` |
| 기존 번역 번호와 키의 대응 | `resources/locales/legacy-index.json` — 기존 번호 유지 |
| 리소스 수정본 | 공통 `resources/overrides/mods/Stranded II/...` 또는 판별 `resources/editions/<edition>/overrides/mods/Stranded II/...` |
| `.s2` 지도 | 길이 정보가 있는 바이너리. 지도 편집기 또는 [src/devtools/s2map.py](../src/devtools/s2map.py)로 문자열만 수정하고 지형·ID·나머지 필드·첨부 자료 보존 |

리소스 수정은 `setup`, 코드 수정은 `build`, 번역만 수정하면 `localize`로 반영하고 `check`를 실행합니다. 새 리소스 파일은 게임에서 읽도록 별도로 연결해야 합니다. 설정·세이브·실행 파일을 리소스 변경분으로 덮어쓰지 않습니다.

원본 입력은 CP1252이며 준비된 리소스와 엔진 소스는 UTF-8입니다. 기존 구분자 166/182/191을 31/30/29로 바꿀 때는 문자열에만 적용하고 숫자 ID는 보존합니다. 일지 제목은 조회 키이므로 제목 자체가 아닌 표시 별칭을 번역합니다.

엔진은 스크립트 변수를 `[[loc:key|name=HEX_UTF8_BYTES]]` 형태로 저장해 실행 시점의 값을 보존합니다. 이 형태는 직접 작성하지 않습니다. 기존 세이브에 일반 문장으로 저장된 번역은 언어를 바꿔도 남을 수 있으므로 복사본에서 호환성을 확인하세요.

## 변경 검증

게임 코드가 바뀌면 `build`와 `check`를 실행하고 변경한 기능을 게임에서 확인합니다. 번역·폰트는 `localize`와 `check`, 리소스는 `setup`과 `check` 후 표시를 확인합니다. 문서만 바꾼 경우에는 링크와 안내 내용의 일치 여부를 확인합니다.

| 검사 | 확인 범위 | 결과 위치 |
| --- | --- | --- |
| `setup` | 의존성 해시, 카탈로그, 공식 지도 13개의 문자열 변환과 그 외 바이트·첨부 자료 보존 | `build/setup-report.json` |
| `localize` | 등록 언어·카탈로그·TTF/OTF 글리프와 보완 출처, 실패 시 기존 언어팩 보존 | `build/localization-report.json`, `build/runtime/core/locales/fonts-report.json` |
| `build` | 내장 컴파일러·런타임 빌드, 실제 Blitz 컴파일과 실행 파일·소스·DLL 해시 | `build/build-report.json` |
| `check` | 문구 참조·치환 변수·글꼴, 실제 Blitz 로더와 파서, 설정 보존, 시간 보정·프레임 대기, 저장 교체·잠금 실패·백업·5슬롯·저장 큐·계속하기 선택 | `build/check-*/report.json`과 같은 폴더의 상세 결과 |
| `.\.venv\Scripts\python.exe -X utf8 src/devtools/verify_game.py` | 격리된 게임에서 저장·맵 목록 미리보기와 실제 지도·저장·백업·빠른 저장/불러오기·자동 저장 상태 복구 | `build/game-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 src/devtools/verify_qol_logic.py --language ko` | 실제 Blitz의 QOL 로직·제작·이동·정렬·설정 검사. 그래픽 엔티티와 스크립트는 대역 사용 | `build/qol-logic-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 src/devtools/verify_qol.py --language ko` | 복사한 실행 환경에서 실제 제작·이동·스크립트 거부·저장/불러오기 검사. 그래픽 장치 필요 | `build/qol-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 src/devtools/verify_collisions.py --edition classic` | 격리된 Blitz 실행 환경에서 바위 메시 정리, 경사면 접근·후퇴, 점프·착지, 통로·수영, 제어 전환·투사체 충돌 | `build/collision-smoke-*/report.json` |

이동·충돌 변경에는 `verify_collisions.py`를 두 에디션에서 실행합니다. Extended는 `--edition extended`를 사용하고 결과를 `build/extended/collision-smoke-*/`에 저장합니다. 이 검사는 실제 지형과 바위 모델에 60·120·144 Hz 및 100ms 시간 간격을 적용하며 GUI 입력을 보내지 않습니다. 실제 렌더링 FPS와 사용자가 보고한 장소는 별도로 확인합니다. `core/modern.ini`의 기존 `diagnostics=1` 옵션을 켜면 이동 요청·결과, 지형 보정량, 접촉점·법선과 이동 취소 여부가 `core/modern.log`에 최대 초당 4회 기록됩니다.

`check`는 원본 Blitz3D 1.118의 고정 결과와 네이티브 ABI를 비교하고 렌더링 없이 타이머·파서·저장 파일 처리 등을 검사합니다. 시야 거리도 실제 설정 저장·재로드 경로를 거쳐 모든 단계에서 동일하게 적용되는지 확인합니다. `python src/devtools/verify_engine.py --input`은 창이나 OS 입력 없이 실제 SDL 큐를 통해 콘솔 문자 코드·키 누름 유지·확정된 텍스트·마우스 이벤트 전달을 검사합니다. 캡처 상태를 대체해 읽기와 중앙 복귀 사이의 1단위 이동 보존, 축별 소비, 방향 반전과 0.02도 회전도 확인합니다. 커서 조회를 대체하고 2·3·5·10배속을 1초·10초 사용한 뒤 남는 시간 차이와 이동 이벤트 누락을 모사해 즉시 좌표 복구, 중복 방지, 드래그·클릭·휠과 조회 실패도 검사합니다. 실제 Cheat Engine·Win32 커서 조회·포커스 전환은 별도로 확인합니다.

`python src/devtools/verify_engine.py --render`는 창 없이 실제 D3D12 장치와 엔진 셰이더로 텍스처에 그려 무광·광택 색, 텍스처, 안개, 거리 페이드, 반투명 인스턴싱, 알파 테스트 윤곽과 카메라별 깊이 순서를 검사합니다. 최적화한 시야 검사도 변환된 경계 상자의 꼭짓점 및 기존 계산과 비교합니다. 6방향 반사는 기존 개별 렌더링과 픽셀을 비교하고, 준비 횟수·호출 사이 장면 변경·카메라 자식·보간값도 검사합니다. 광택은 기존 D3D7 재질의 강도·지수를 따르며 텍스처 처리 후 합성합니다. `--graphics`는 창을 사용하는 격리된 테스트에서 D3D12 그리기·픽셀 읽기·그래픽 컨텍스트 수명도 검사합니다. 실제 렌더링 FPS, 상대 마우스 반응, Alt-Tab, 화면의 부드러움과 전체 진행은 게임에서 따로 확인해야 합니다.

두 판의 `check`는 `verify_qol_logic.py`를 실행합니다. 판별 기본값, 누락·잘못된 설정, 옵션 조합 32가지와 공통 엔진 함수를 엔티티·스크립트 대역으로 검사하며 화면, 실제 스크립트, 지도 저장, IME는 검증하지 않습니다. 두 QOL 검증 명령 모두 `--edition classic|extended`를 받으며 생략하면 기본판, 확장판 결과는 `build/extended/` 아래입니다. `verify_qol.py --interactive`는 QOL을 켠 별도 테스트 게임을 개인 세이브 없이 준비하며 자동 실행하지 않습니다. 이 복사본에서 한글 조합·확정·취소, 검색창 포커스와 Alt-Tab, 긴 이름·상세 스크롤, Shift 클릭·R·Shift+R, 옵션별 켜기/끄기, 건축 반복 취소를 확인하세요. `--language en`은 영어, `--desktop`은 전체 창입니다. 시뮬레이션한 건축 간격은 실제 렌더링 FPS 측정과 다릅니다.

저장 동작을 바꿨다면 `src/devtools/verify_game.py`로 실제 지도 로딩과 저장·불러오기도 확인하세요. macOS `--suite full`과 `src/tests/save_menu.bb`를 공유하며 실제 목록 로딩 함수를 호출해 미리보기 교체, 반복 진입, 빈 목록, 마지막 저장 삭제 후 갱신을 검사합니다. 실행 폴더의 복사본과 빈 세이브 폴더를 사용하며 사용자 게임·세이브에는 쓰지 않습니다. 그래픽 장치가 필요하고 UI 조작은 검사하지 않습니다. 기본은 800×600 창이며, `--desktop`은 모니터에 맞춘 전체 창, `--edition extended`는 확장판을 검사합니다. 확장판 보고서는 `build/extended/` 아래에 생성됩니다.

타이밍 코드를 수정할 때는 `modern_runtime.bb`, `modern_math.bb`, `modern_ui.bb`와 호출부를 함께 확인하세요. 게임 시간(QPC)과 프레임 대기 시계를 분리하고, 전체 프레임 경과 시간·100ms 상한·소수 시간 누적·공통 포커스/화면 표시 경로를 유지합니다. `verify_clock_hooks.py`의 외부 배속 재현은 별도 검사 프로세스에서만 실행됩니다.

변경과 관련된 항목을 아래에서 골라 실제 게임으로 확인합니다.

- 언어: 한국어 → 영어 → 추가 언어 선택·적용·재시작, 좌우 클릭 순환, 취소 시 기존 선택 유지, 미번역 항목의 영어 표시.
- 폰트: 일반 TTF/OTF와 가변 폰트, 영문 전용 폰트의 한글 보완, 영문·숫자·공백·커서·줄바꿈과 미리보기 확인.
- 표시: 메뉴, 일지, 아이템, 조합, 대사, 긴 문구 스크롤, 지도 편집기 도움말.
- 저장: 새 저장 파일을 두 언어에서 불러오고 재저장. 기존 한국어판 저장은 복사본으로 불러오기 → 별도 이름 저장 → 재불러오기.
- 프레임·화면: 60/120/144 FPS에서 이동·시간 진행, 반복 Alt-Tab, 오래 백그라운드에 둔 뒤 복귀, 관련된 다중 모니터·DPI 환경.
- 화면 설정: 800×600과 모니터에 맞춤, 선택 취소·적용·재시작, 2048픽셀을 넘는 모니터에서 실제 렌더링 크기 확인.
- 저장 기능: 빠른 저장/불러오기 키, 6회 이상 자동 저장 후 5슬롯 순환, 메인 메뉴 계속하기, `.bak`을 별도 이름으로 복사해 복구.
- 외부 배속: Cheat Engine 2·3·5·10배속을 각각 1초·10초 사용한 뒤 1배속으로 복귀해 메뉴·인벤 커서가 즉시 움직이는지 확인하고, 시점 조작·드래그·Alt-Tab과 `diagnostics=1`의 프레임 기록 비교.

검증 결과를 공유할 때는 사용한 빌드, 화면 설정, 확인한 동작과 남은 항목을 함께 적습니다. 자동 검사 통과만으로 전체 플레이나 모든 PC의 화면 복귀를 검증했다고 간주하지 않습니다.

공통 기능과 단축키는 [QOL 설정](qol-ko.md), 기본값 차이와 개발 상태는 [확장판 차이](extend-features-ko.md)를 참고하세요.

## 프로젝트 문서 수정

영어 `.md` 또는 한국어 `-ko.md`를 직접 수정하세요. 새 한국어 문서는 `example-ko.md`처럼 이름을 붙입니다. AI 에이전트가 저장소 작업을 할 때 다른 언어에도 반영하며, 상시 실행되는 번역 서비스는 아닙니다.

문서 언어는 [config/dev-docs-ai-translation.json](../config/dev-docs-ai-translation.json)에서 관리하며 게임 언어와는 별개입니다. 영어만 수정하면 영어를 기준으로, 두 언어를 모두 수정하면 한국어를 기준으로 동기화합니다. 문서 첫 줄의 해시는 직접 고치지 마세요. 세부 규칙은 [AGENTS-ko.md](reference/AGENTS-ko.md)에 있습니다.

예외적으로 `README-ko.md`는 사용자가 직접 관리하며, 명시적인 편집 요청 없이는 에이전트가 변경하지 않습니다. 다른 언어 README는 한국어 원문을 기준으로 동기화합니다. 문서 작성·갱신 시에는 [auto-gen-please.json](auto-gen-please.json)의 문서별 목적·문체를 따르며 필요한 문서만 갱신합니다.

## GitHub CI와 배포 패키지

[데스크톱 워크플로](../.github/workflows/windows.yml)는 `main`·`dev` 푸시, PR, 수동 실행에서 기본 게임의 Windows 빌드·검사를 수행합니다. `main` 푸시는 같은 버전의 Mac 빌드도 실행하고, 양쪽 검사 성공 후 Windows ZIP·Mac DMG와 각 SHA-256 파일을 하나의 안정판 Release에 게시하며 patch 번호를 올립니다. 어느 한쪽이 실패하면 게시하지 않습니다. `dev` 푸시와 PR은 Windows `-local` 검증용 ZIP만 만들며 Release를 발행하거나 번호를 소모하지 않습니다. Extended의 CI 판 선택·빌드·배포 경로는 주석 처리되어 있으며 당분간 로컬에서만 개발합니다.

데스크톱·웹의 푸시·PR에는 `paths-ignore: ["**.md"]`를 적용합니다. 루트와 하위 폴더의 변경 파일이 모두 소문자 `.md`이면 자동 CI를 건너뛰므로 `main`의 Mac 빌드·Release 게시·Pages 재배포도 실행하지 않습니다. 다른 확장자의 파일이 하나라도 포함되면 기존 조건대로 실행하며, 수동 실행은 이 필터와 관계없이 동작합니다.

개발판을 출시하거나 안정판 출시를 수동 실행하려면 GitHub에서 다음 순서로 진행합니다.

1. **Actions → Desktop build and release → Run workflow**를 엽니다.
2. 정식판은 `main`, 시험판은 `dev` 브랜치를 선택합니다.
3. `publish`를 켭니다.
4. Windows·Mac 빌드와 검사가 모두 성공하면 Release가 발행됩니다.

수동 실행의 `publish=false`는 ZIP 생성과 검증만 수행합니다. 다른 브랜치나 태그 푸시에서는 출시하지 않습니다.

공개 릴리스 제목과 설명은 영어로 작성합니다.

Windows CI는 Windows Server 2022와 Python 3.13에서 `setup → build → check → package`를 실행합니다. 최종 게시 작업은 `macos-15`의 Bash에서 `src/devtools/ci.py release --include-macos`를 실행해 양쪽 버전·커밋·체크섬을 검증합니다. DMG를 읽기 전용으로 마운트하고 앱 해시·실행 권한·ARM64 형식·네이티브 검사 결과와 실제 앱·DMG 서명, 공증 티켓, Gatekeeper 승인을 검사합니다. 과거 Mac ZIP을 포함한 공개 릴리스는 불변이며 중단된 초안의 원래 자산 구성을 자동 교체하지 않습니다. Mac 파일이 없는 기존 공개 릴리스에는 나중에 덧붙이지 않습니다. Windows `check`와 Mac `core`는 화면·렌더링 FPS·앱 전환·전체 플레이를 검증하지 않습니다. 실제 GitHub Actions 실행은 로컬 검사와 별도로 확인하세요.

원본 사이트에 반복 다운로드 트래픽을 발생시키지 않도록 게임 리소스·컴파일러·글꼴은 같은 저장소의 의존성용 Release에 한 번 보관합니다. 이 빌드 입력에는 해당 Release와 Actions 캐시만 사용합니다. 파일이 없으면 준비 방법을 안내하고 실패하며, 해당 입력을 원본 사이트에서 자동 다운로드하지 않습니다. Windows 데스크톱·웹 CI는 엔진 SDK와 고정 UI 글꼴을 고정값별로 캐시하고, 캐시가 없을 때만 고정 URL에서 받습니다.

### 버전 지정과 자동 증가

기준 버전은 `main`의 [config/version.json](../config/version.json)에 있으며 초기값은 `1.1.0`입니다. CI는 앞 두 숫자나 설정 파일을 수정하지 않고 버전 커밋을 만들지 않습니다. patch는 해당 계열의 시작값이며 기존 정식 태그가 있으면 다음 번호부터 배포합니다. Extended의 로컬 기준 버전은 [config/editions/extended/version.json](../config/editions/extended/version.json)에서 별도로 관리합니다.

| 배포 상황 | 메뉴 표시 | GitHub 태그 |
|---|---|---|
| 기본판 첫 정식 배포 | `1.1.0-main` | `v1.1.0` |
| 이후 기본판 개발 배포 | `1.1.1-dev.1` | `v1.1.1-dev.1` |
| 기본판 다음 정식 배포 | `1.1.1-main` | `v1.1.1` |

출시할 때 `dev`는 `main`의 기준 파일을 읽고 그 커밋 SHA를 고정합니다. 정식 배포 전에는 기준 버전의 `-dev.1`부터 시작하며, 정식 배포가 있으면 다음 patch를 목표로 `dev.N` 순번을 올립니다. 개발 배포는 정식 patch를 소모하지 않습니다. `main`은 배포 대상 커밋의 기준 파일을 사용합니다.

자동·수동 출시 실행은 같은 대기열에서 버전 계산부터 발행까지 직렬 처리합니다. 빌드·검사 실패는 태그를 만들지 않으므로 번호를 소모하지 않습니다. 발행 중 중단되면 번호가 예약될 수 있지만 같은 판·소스 커밋·채널로 재실행하면 태그와 초안을 복구합니다. 초안을 재시도할 때는 원래 산출물 이름과 해시가 일치해야 합니다. 공개된 Release는 덮어쓰지 않으며 지원이 종료된 대상의 산출물을 포함해 기존 발행을 검증하고 유지합니다. 다른 판의 태그와 `ci-deps-v1`은 번호 계산에 영향을 주지 않습니다. 개발판은 최신 정식 Release 표시를 대체하지 않습니다.

빌드는 `core/version.txt`와 `core/version.json`을 생성합니다. 게임은 표시값을 메인 메뉴에 표시합니다. `build-info.json`에는 배포판·버전·채널·소스 커밋·기준 파일 커밋과 실행 파일 해시가 포함됩니다. 패키징은 해당 판의 빌드 보고서·소스 그래프와 일치하는지 검사하며 판이 다른 실행 폴더나 오래된 리소스 변경분을 거부합니다. 기존 `Cversion="1.0.0.1"`은 맵·스크립트 호환성을 위해 유지합니다. 로컬 빌드는 `-local`로 표시합니다. 빌드 보고서에는 네이티브 소스·DLL 해시도 기록하며 패키징 시 현재 공통 소스와 선택한 실행 환경의 DLL이 일치하는지 검사합니다.

### 의존성 Release 최초 준비

먼저 위 안내대로 로컬 의존성을 준비한 뒤 저장소 루트에서 실행합니다. 이미 준비한 `deps/`가 있으면 다시 다운로드할 필요가 없습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/ci.py bundle
```

`build/ci-upload/StrandedII-ci-deps-v1.zip`과 `.zip.sha256`이 생성됩니다. 게임·컴파일러 파일 목록과 글꼴의 기존 해시를 모두 검증한 뒤 필요한 파일과 원본 라이선스만 묶습니다. 번들용 라이선스 목록과 해시는 [config/dependency-license-files.json](../config/dependency-license-files.json)에 고정하며, 배포용 라이선스를 추가해도 기존 의존성 ZIP 검증에는 영향을 주지 않습니다. 최종 게임 배포에는 현재 `licenses/`의 모든 파일을 포함합니다. 개인 세이브·설정과 Blitz 편집기의 예제·미디어는 포함하지 않습니다. 의존성 ZIP 전체의 SHA-256, Release 태그와 파일명은 [config/ci-dependencies.json](../config/ci-dependencies.json)에 고정돼 있습니다.

원격 저장소를 만들고 사용할 커밋을 직접 푸시한 뒤 아래 명령으로 업로드합니다. `OWNER/REPO`와 `COMMIT_SHA`를 실제 저장소와 업로드한 커밋 SHA로 바꿉니다. GitHub CLI 로그인이 필요합니다. 의존성 Release는 게임의 최신 Release로 표시하지 않습니다.

```powershell
gh release create ci-deps-v1 `
  "build/ci-upload/StrandedII-ci-deps-v1.zip" `
  "build/ci-upload/StrandedII-ci-deps-v1.zip.sha256" `
  --repo OWNER/REPO --target COMMIT_SHA `
  --title "CI dependencies" `
  --notes "Pinned game resources, Blitz3D 1.118 and Noto Sans KR. Original licenses are included in the ZIP." `
  --latest=false
```

이후 Actions의 `Desktop build and release`를 수동 실행하면 ZIP을 받을 수 있습니다. 의존성을 갱신할 때는 ZIP을 다시 만들고 출력된 SHA-256과 새 Release 태그·파일명을 설정에 함께 반영합니다. 해시가 다른 ZIP은 캐시에 있어도 압축을 풀지 않습니다. 기존 동기화된 문서나 소스 변경만으로 의존성 ZIP을 다시 만들 필요는 없습니다. 단, 번들용 라이선스의 목록이나 내용을 변경하면 `config/dependency-license-files.json`을 갱신하고 의존성 ZIP을 다시 만든 뒤 전체 해시도 함께 갱신합니다.

### 배포 ZIP 구성

배포 ZIP의 `StrandedII/` 폴더에는 `StrandedII.exe`, 원본 게임 리소스, 현지화 리소스, 언어별 비트맵 글꼴, 초기 설정과 라이선스가 포함됩니다. 전체 압축을 풀고 `StrandedII.exe`를 더블클릭하면 됩니다. 실행용 CMD는 포함하지 않습니다. 플레이어는 Python·Blitz3D·글꼴을 별도로 설치할 필요가 없으며 화면·FPS·언어는 게임의 설정 메뉴에서 바꿉니다.

패키징은 파일 목록을 사용하므로 사용한 개발 런타임에 있는 개인 세이브, 사용자 지도, 스크린샷, `preserved/`, 로그와 검사 보고서를 포함하지 않습니다. 키 설정은 원본 의존성에서, `core/modern.ini`는 저장소 초기 설정에서 가져옵니다. 개인 `sys/settings.cfg`도 제외하므로 새 설치의 그래픽 품질과 음량은 엔진의 초기값을 사용합니다. 개발 런타임은 변경하지 않습니다. 로컬 패키징도 가능합니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 src/devtools/ci.py package --game deps/game
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s src/tests -p 'test_ci*.py'
```

출력 이름은 `build/release/StrandedII-<표시 버전>-windows.zip`과 `.zip.sha256`입니다. 로컬 빌드의 예시는 `StrandedII-1.1.0-local-windows.zip`입니다. 패키징은 컴파일이나 게임 검사를 실행하지 않으므로 먼저 최신 소스로 `setup → build → check`를 완료하세요. CI 버전 정보는 `version-context`, 게임 ZIP은 `StrandedII-windows` 산출물로 저장됩니다.

## 생성물과 사용자 데이터 관리

`deps/`, `build/`, `.venv/`, Python 캐시와 로그는 [.gitignore](../.gitignore)로 제외합니다. 소스·번역·수정 리소스·스크립트·설정·문서·라이선스는 Git에서 관리합니다.

`build/runtime/`과 `build/extended/runtime/`에는 실행 결과뿐 아니라 실제 저장 파일과 사용자 설정도 있습니다. 폴더를 초기화하기 전에는 각 실행 폴더의 `mods/Stranded II/saves/`와 직접 만든 지도·스크린샷, `preserved/`가 있다면 그 내용도 백업하세요.
