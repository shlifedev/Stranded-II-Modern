<!-- dev-docs-ai-translation: {"document":"docs/development.md","language":"ko","syncedHash":"ee4c76a709f016d773d34fe0c4fc8df766ab7d00cd9753bd5eca7e1188ae8037"} -->

# 개발 가이드

[English](development.md) · [한국어](development-ko.md)

[README로 돌아가기](../README-ko.md) · [실행 및 설정](playing-ko.md) · [현지화 가이드](localization-ko.md)

## 어디를 수정하나요?

**게임 기능은 `src/engine/`에서 구현합니다.** `build/` 안의 코드는 검사 도구가 생성한 임시 코드이며, 게임 소스의 원본은 `src/engine/`입니다.

```text
src/engine/ 수정 → build 명령 → build/runtime/StrandedII.exe → Play.cmd
locales/ 번역·언어·폰트 수정 → localize 명령 → build/runtime/core/locales/ 갱신
resources/ 수정 → setup 명령 → build/runtime/의 리소스 갱신
```

| 경로 | 역할 | Git 관리 |
| --- | --- | --- |
| `src/engine/` | 직접 수정하는 Blitz3D 게임 소스. 진입점은 `StrandedII.bb` | 포함 |
| `locales/` | 언어별 번역·이름·폰트 설정과 기존 문구 번호 대응 | 포함 |
| `resources/` | 원본 문구의 번역 키 대응, 수정할 리소스 | 포함 |
| `scripts/`, `dev.ps1` | 준비·빌드·실행·검증 도구 | 포함 |
| `config/` | 초기 설정, 의존성 버전·해시, Blitz 외부 함수 선언 | 포함 |
| `docs/`, `licenses/` | 문서와 저작권 고지 | 포함 |
| `deps/` | 직접 준비하는 원본 게임·컴파일러·글꼴 | 제외 |
| `.venv/` | Python 가상환경 | 제외 |
| `build/toolchain/` | 프로젝트 전용 컴파일러 복사본 | 제외 |
| `build/check-*/` | 자동 검사가 생성한 임시 코드·실행 파일·보고서 | 제외 |
| `build/runtime/` | 빌드된 게임, 리소스, 사용자 설정과 저장 파일 | 제외 |

생성 결과를 수정하면 다음 `setup` 또는 `build`에서 덮어쓸 수 있습니다. 유지할 코드·번역·리소스 변경은 위 원본 폴더에 반영합니다. Python 도구는 리소스와 검증 코드를 생성하며, `src/engine/`의 게임 소스는 재생성하지 않습니다.

## 기본판과 확장판

`classic`은 기존 동작을 유지하는 기본판, `extended`는 별도 기능·콘텐츠를 개발할 확장판입니다. 현재는 분리 기반만 있으며 QOL·리밸런싱은 아직 추가하지 않았습니다. 공통 코드는 한 번 수정해 두 판에서 검증하고, 확장판 전용 변경은 해당 판의 코드·자료에만 작성합니다.

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
resources/overrides/              공통 리소스 변경분(필요할 때 생성)
resources/editions/classic/overrides/   기본판 리소스 변경분
resources/editions/extended/overrides/  확장판 리소스 변경분
build/runtime/                   기본판 실행·설정·세이브
build/extended/runtime/          확장판 실행·설정·세이브
```

두 `modules.bb`는 현재 같은 기존 구현을 포함합니다. 기능이 달라질 때만 확장판 폴더에 해당 모듈을 만들고 확장판의 `Include`를 바꿉니다. 컴파일은 직접 작성한 소스를 사용하며 Python이 엔진 코드를 생성하지 않습니다. 빌드 보고서는 해당 진입점의 실제 `Include` 그래프만 기록합니다. 다른 판의 전용 소스를 포함하면 검사에 실패합니다.

리소스는 공식 입력 → 현지화 준비 → 공통 변경분 → 선택한 판의 변경분 순서로 준비합니다. 판별 변경분은 실행 폴더 기준 `mods/Stranded II/...` 경로로 배치합니다. 아이템·조합·건축·능력치·맵·스크립트 리밸런싱은 확장판 변경분에 작성합니다. 설정·세이브·실행 파일은 리소스 변경분으로 덮어쓸 수 없습니다. `.s2` 수정은 기존 바이너리 보존 규칙을 따릅니다. 판별 번역은 [현지화 가이드](localization-ko.md#판별-번역)를 참고하세요.

모든 개발 명령은 `--edition classic|extended`를 받으며 생략하면 기본판입니다. 의존성과 컴파일러는 공유하고, 실행 폴더·보고서·검증 산출물은 분리합니다. 기본판 데이터는 이동하지 않으며 확장판으로 자동 복사하지 않습니다.

```powershell
python -X utf8 scripts/dev.py setup --edition extended
python -X utf8 scripts/dev.py build --edition extended
python -X utf8 scripts/dev.py check --edition extended
python -X utf8 scripts/dev.py run --edition extended -- -win
.\Play-Extended.cmd
```

첫 `setup`에는 아래의 의존성 인자가 필요합니다. 이후에는 저장된 공통 의존성 경로를 사용합니다. 기본판은 기존 `Play.cmd`로 실행합니다. 확장판 기본 설정은 `config/editions/extended/modern.ini`이며, 이미 존재하는 개인 설정은 `setup`으로 교체하지 않습니다.

## 개발 환경 준비

Windows 10 이상, Python 3.10 이상, [requirements.txt](../requirements.txt)의 Pillow와 fontTools가 필요합니다. Blitz의 파일 경로 처리를 고려해 `C:\Stranded II`처럼 영문 경로를 사용하세요. 경로에 공백은 있어도 됩니다.

아래 의존성은 저장소에 포함하지 않으므로 직접 준비합니다.

| 의존성 | 다운로드 | 배치 경로 |
| --- | --- | --- |
| Stranded II Gold 1.0.0.1 English ZIP | [공식 다운로드](https://www.strandedonline.de/s2_download.php) | `deps/game/` |
| Blitz3D 1.118 | [Blitz Research 배포](https://blitzresearch.itch.io/blitz3d)에서 `Blitz3D-V1.118.zip` | `deps/Blitz3D-V1.118/` |
| Noto Sans KR | [고정 버전 글꼴](https://raw.githubusercontent.com/google/fonts/4efc2774c63917927efe769ca845def6bd6debae/ofl/notosanskr/NotoSansKR%5Bwght%5D.ttf) | `deps/fonts/NotoSansKR[wght].ttf` |

압축을 푼 결과는 다음처럼 배치합니다. 게임은 수정되지 않은 영어 원본을 사용합니다.

```text
deps/
  game/
    StrandedII.exe
    core/
    mods/
  Blitz3D-V1.118/
    bin/
      blitzcc.exe
    ...
  fonts/
    NotoSansKR[wght].ttf
```

의존성 버전과 SHA-256은 [config/dependencies.json](../config/dependencies.json), 게임 파일 목록은 [config/game-files.json](../config/game-files.json), 컴파일러 구성 파일은 [config/toolchain-files.json](../config/toolchain-files.json)에 기록돼 있습니다. 준비 단계에서 일치 여부를 검사하므로 지정된 배포본을 사용하세요. 별도 원본 소스 ZIP은 필요하지 않습니다.

## 최초 빌드

저장소 루트의 PowerShell에서 실행합니다.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --game deps/game --blitz deps/Blitz3D-V1.118 --font 'deps/fonts/NotoSansKR[wght].ttf'
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check
.\Play.cmd
```

`setup`은 원본 리소스를 실행 폴더에 복사하고 UTF-8 문구 참조와 글꼴을 준비합니다. 컴파일러도 `build/toolchain/`에 복사하고 이 프로젝트의 외부 함수 선언을 추가합니다. 원본 의존성은 변경하지 않습니다.

다른 위치에 준비한 의존성을 `--game`, `--blitz`, `--font`로 지정해도 됩니다. 첫 `setup`이 경로를 `build/dependencies.local.json`에 기록하므로 이후에는 인자를 생략할 수 있습니다. 해당 파일은 개인 경로를 담으므로 Git에서 제외합니다.

## 변경 후 실행

다음 명령도 저장소 루트에서 사용합니다. 리소스를 갱신하거나 실행 파일을 교체하기 전에 개발 게임을 종료하세요.

| 변경 내용 | 실행 명령 |
| --- | --- |
| `src/engine/**/*.bb` 수정 | `.\dev.ps1 build -Python .\.venv\Scripts\python.exe` |
| `locales/` 번역·언어·폰트 수정 | `.\dev.ps1 localize -Python .\.venv\Scripts\python.exe` |
| `resources/` 수정 | `.\dev.ps1 setup -Python .\.venv\Scripts\python.exe` |
| 자동 검사 | `.\dev.ps1 check -Python .\.venv\Scripts\python.exe` |
| 게임 실행 | `.\Play.cmd` |

코드와 리소스를 함께 수정했다면 `setup → build → check → 실행` 순서로 진행합니다. 번역·새 언어·폰트만 수정했다면 `localize → check → 실행`으로 반영되며 EXE 재컴파일은 필요하지 않습니다. `setup`도 같은 언어팩 준비 과정을 포함합니다. 기존 개발 환경은 `pip install -r requirements.txt`로 fontTools를 추가하세요. 폰트 설정은 [폰트 가이드](fonts-ko.md)에 있습니다.

`setup`은 기존 실행 폴더의 세이브, `.cfg`, `settings.inf`, `core/modern.ini`를 보존합니다. 따라서 `config/modern.ini`를 바꿔도 기존 실행본의 설정이 자동으로 바뀌지 않습니다. 개인 설정은 [실행 및 설정](playing-ko.md)에 따라 수정합니다.

`build`는 임시 실행 파일을 컴파일한 뒤 `build/runtime/StrandedII.exe`를 교체합니다. 실행 중인 게임 때문에 교체에 실패했다면 게임을 종료하고 `build`를 다시 실행합니다.

Python으로 직접 실행할 수도 있습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py run -- -win -fps 120
```

## 컴파일러 직접 사용

일반적으로 `build` 명령을 사용하면 됩니다. 직접 Blitz 컴파일러를 호출할 필요가 있다면 최초 `setup` 후 게임을 종료하고 다음을 실행합니다.

```powershell
$projectRoot = (Get-Location).Path
$env:blitzpath = Join-Path $projectRoot 'build/toolchain'
Push-Location 'src/engine'
try {
    & "$env:blitzpath/bin/blitzcc.exe" -o "$projectRoot/build/runtime/StrandedII.exe" 'StrandedII.bb'
    if ($LASTEXITCODE -ne 0) { throw 'Blitz build failed' }
} finally {
    Pop-Location
}
```

게임 리소스의 기준 위치는 EXE가 있는 `build/runtime/`입니다. 작업 디렉터리를 리소스 위치로 사용하는 옛 `-inbb` 옵션은 이 구조에서 지정하지 않습니다.

## 변경 검증

게임 코드가 바뀌면 `build`와 `check`를 실행하고 변경한 기능을 게임에서 확인합니다. 번역·폰트는 `localize`와 `check`, 리소스는 `setup`과 `check` 후 표시를 확인합니다. 문서만 바꾼 경우에는 링크와 안내 내용의 일치 여부를 확인합니다.

| 검사 | 확인 범위 | 결과 위치 |
| --- | --- | --- |
| `setup` | 의존성 해시, 카탈로그, 공식 지도 13개의 문자열 변환과 그 외 바이트·첨부 자료 보존 | `build/setup-report.json` |
| `localize` | 등록 언어·카탈로그·TTF/OTF 글리프와 보완 출처, 실패 시 기존 언어팩 보존 | `build/localization-report.json`, `build/runtime/core/locales/fonts-report.json` |
| `build` | 실제 Blitz3D 컴파일, 실행 파일·소스 해시 | `build/build-report.json` |
| `check` | 문구 참조·치환 변수·글꼴, 실제 Blitz 로더와 파서, 설정 보존, 시간 보정·프레임 대기, 저장 교체·잠금 실패·백업·5슬롯·저장 큐·계속하기 선택 | `build/check-*/report.json`과 같은 폴더의 상세 결과 |
| `python -X utf8 scripts/verify_game.py` | 격리된 게임에서 실제 지도·저장·백업·빠른 저장/불러오기·자동 저장 상태 복구 | `build/game-smoke-*/report.json` |

`check`의 타이머 검사는 그래픽 렌더링 없이 실행합니다. 실제 Windows 타이머로 60/120/144 FPS 대기를 측정하고, 게임 시간만 1→3→5→10→1배속으로 바꿔 프레임 대기가 영향을 받지 않는지 검사합니다. `verify_clock_hooks.py`는 별도 검사 프로세스 안에서 `timeGetTime`을 `GetTickCount`로 연결해, Cheat Engine이 1배속에서도 유지하는 저해상도 시계 변경을 재현합니다. 이전 `Delay` 경로와 새 대기를 비교하고, 게임 시계 정밀도·카운터 경계·포커스 복귀 시 시간 제외도 확인합니다. 다른 프로세스나 디스크의 DLL은 수정하지 않습니다. 실제 Cheat Engine 주입과 화면의 부드러움은 별도로 확인해야 합니다.

파일 검사에서는 실제 Windows 교체 함수를 사용하고 저장 직렬화만 시험용으로 대체합니다. `verify_game.py`는 이를 보완하는 선택 검사입니다. 실행 폴더를 복사하고 빈 저장 폴더에서 실제 게임 직렬화·불러오기를 실행합니다. 브랜드 화면·포커스 대기·대화형 루프를 검증 코드로 대체하므로 UI 검사는 하지 않습니다. 그래픽 장치가 필요하며 사용자 게임·저장 파일에는 쓰지 않습니다.

기본 검사는 800×600 창 모드입니다. `python -X utf8 scripts/verify_game.py --desktop`으로 주 모니터에 맞춘 전체 창 모드의 초기화와 저장도 검사할 수 있습니다.

프레임 대기는 `modern_runtime.bb`의 `QueryUnbiasedInterruptTimePrecise`와 Windows `Sleep`을, 게임 시간은 `QueryPerformanceCounter`를 사용합니다. Blitz의 `Delay`는 내부적으로 `timeGetTime`을 기다리므로 프레임 대기에 쓰지 않습니다. 포커스 대기에서만 `Delay 0`으로 창 메시지를 처리합니다. QPC는 큰 절대값을 실수로 변환하지 않고 정수 차이를 먼저 계산하며 소수 밀리초를 누적합니다. 따라서 Windows 10 이상이 필요합니다. `modern_settings.bb`는 Direct3D 초기화 실패를 피하기 위해 렌더링 크기를 각 변 2048픽셀 이하로 보정합니다. 전체 창의 물리 크기는 주 모니터 크기를 따릅니다.

변경과 관련된 항목을 아래에서 골라 실제 게임으로 확인합니다.

- 언어: 한국어 → 영어 → 추가 언어 선택·적용·재시작, 좌우 클릭 순환, 취소 시 기존 선택 유지, 미번역 항목의 영어 표시.
- 폰트: 일반 TTF/OTF와 가변 폰트, 영문 전용 폰트의 한글 보완, 영문·숫자·공백·커서·줄바꿈과 미리보기 확인.
- 표시: 메뉴, 일지, 아이템, 조합, 대사, 긴 문구 스크롤, 지도 편집기 도움말.
- 저장: 새 저장 파일을 두 언어에서 불러오고 재저장. 기존 한국어판 저장은 복사본으로 불러오기 → 별도 이름 저장 → 재불러오기.
- 프레임·화면: 60/120/144 FPS에서 이동·시간 진행, 반복 Alt-Tab, 오래 백그라운드에 둔 뒤 복귀, 관련된 다중 모니터·DPI 환경.
- 화면 설정: 800×600과 모니터에 맞춤, 선택 취소·적용·재시작, 2048픽셀을 넘는 모니터에서 실제 렌더링 크기 확인.
- 저장 기능: 빠른 저장/불러오기 키, 6회 이상 자동 저장 후 5슬롯 순환, 메인 메뉴 계속하기, `.bak`을 별도 이름으로 복사해 복구.
- 외부 배속: Cheat Engine 3·5·10배속에서 각각 1배속으로 복귀한 뒤 부드러움과 `diagnostics=1`의 프레임 기록 비교.

검증 결과를 공유할 때는 사용한 빌드, 화면 설정, 확인한 동작과 남은 항목을 함께 적습니다. 자동 검사 통과만으로 전체 플레이나 모든 PC의 화면 복귀를 검증했다고 간주하지 않습니다.

## 프로젝트 문서 수정

문서의 기본 언어는 영어지만, 해당 `-ko.md` 파일을 직접 수정하거나 `example-ko.md`처럼 새 한국어 문서를 작성해도 됩니다. AI 에이전트가 저장소 수정 작업 중 선택된 편집 언어를 기준으로 영어와 다른 설정 언어에 반영합니다. 별도 백그라운드 서비스로 실행되지는 않습니다.

루트 [.dev-docs-ai-translation.json](../.dev-docs-ai-translation.json)의 `defaultLanguage`, `preferredEditLanguage`, `targetLanguages`로 관리합니다. 초기값은 각각 `en`, `ko`, `["ko"]`입니다. 문서 언어를 추가해도 게임 지원 언어는 바뀌지 않습니다.

에이전트는 Git 변경점과 문서의 숨김 동기화 해시를 함께 확인합니다. 한국어와 영어가 모두 바뀌었으면 한국어를 우선하고, 영어만 실제로 수정됐다면 영어를 기준으로 반영합니다. 이전 AI 번역이 Git 변경점에 남아 있더라도 새로운 편집으로 취급하지 않습니다. 같은 작업에서도 문서마다 편집 기준 언어가 다를 수 있습니다.

문서 본문은 직접 편집하고, 동기화 해시는 에이전트가 모든 언어 반영을 마친 뒤 갱신하도록 둡니다. 문서 링크는 같은 언어의 안내서로 연결합니다. 빈 문서는 내용이 생길 때까지 비워 둡니다. 전체 에이전트 규칙은 [AGENTS-ko.md](../AGENTS-ko.md)에 있습니다.

## GitHub CI와 배포 ZIP

[Windows 워크플로](../.github/workflows/windows.yml)는 `main`·`dev` 푸시, PR, 수동 실행에서 두 판을 각각 빌드·검사하고 게임 ZIP과 SHA-256 파일을 산출물로 저장합니다. 푸시·PR은 `-local` 검증용 ZIP만 만들며 Release를 발행하거나 번호를 소모하지 않습니다.

실제 출시는 Actions에서 `main` 또는 `dev`를 선택하고 `edition=classic|extended`, `publish=true`로 수동 실행합니다. 두 판 검사가 모두 성공해야 선택한 판만 출시하며, `main`은 정식판, `dev`는 시험판입니다. 기본 수동 실행의 `publish=false`는 검증만 수행합니다. 다른 브랜치에서는 출시할 수 없으며 태그 푸시는 배포하지 않습니다.

CI는 Windows Server 2022와 Python 3.13에서 `setup → build → check → package`를 실행합니다. `check`는 비그래픽 런타임 검사이며 실제 화면, 렌더링 FPS, Alt-Tab과 전체 플레이 검증을 대신하지 않습니다. 로컬 검사와 별도로 실제 GitHub Actions 실행도 확인해야 합니다.

원본 사이트에 반복 다운로드 트래픽을 발생시키지 않도록 게임 리소스·컴파일러·글꼴은 같은 저장소의 의존성용 Release에 한 번 보관합니다. CI는 이 Release와 Actions 캐시만 사용합니다. 파일이 없으면 준비 방법을 안내하고 실패하며, 원본 사이트로 자동 다운로드하지 않습니다.

### 버전 지정과 자동 증가

기본판 기준 버전은 `main`의 [config/version.json](../config/version.json)에 있으며 초기값은 `1.1.0`입니다. 확장판은 [config/editions/extended/version.json](../config/editions/extended/version.json)의 `0.1.0`부터 독립적으로 시작합니다. CI는 앞 두 숫자나 설정 파일을 수정하지 않고 버전 커밋을 만들지 않습니다. patch는 해당 판·계열의 시작값이며 기존 정식 태그가 있으면 다음 번호부터 배포합니다.

| 배포 상황 | 메뉴 표시 | GitHub 태그 |
|---|---|---|
| 기본판 첫 정식 배포 | `1.1.0-main` | `v1.1.0` |
| 이후 기본판 개발 배포 | `1.1.1-dev.1` | `v1.1.1-dev.1` |
| 기본판 다음 정식 배포 | `1.1.1-main` | `v1.1.1` |
| 확장판 첫 정식 배포 | `extended-0.1.0-main` | `extended-v0.1.0` |
| 이후 확장판 개발 배포 | `extended-0.1.1-dev.1` | `extended-v0.1.1-dev.1` |

출시할 때 `dev`는 `main`에 있는 해당 판의 기준 파일을 읽고 그 커밋 SHA를 고정합니다. 정식 배포 전에는 기준 버전의 `-dev.1`부터 시작하며, 정식 배포가 있으면 다음 patch를 목표로 `dev.N` 순번을 올립니다. 개발 배포는 정식 patch를 소모하지 않습니다. `main`은 배포 대상 커밋의 기준 파일을 사용합니다. 확장판을 `dev`에서 처음 출시하기 전에 해당 기준 파일이 `main`에 있어야 합니다.

출시 실행은 버전 계산부터 발행까지 직렬 처리합니다. 빌드·검사 실패는 태그를 만들지 않으므로 번호를 소모하지 않습니다. 발행 중 중단되면 번호가 예약될 수 있지만 같은 판·소스 커밋·채널로 재실행하면 태그와 초안을 복구합니다. 공개된 Release는 덮어쓰지 않으며 재실행 시 검증 후 기존 발행을 유지합니다. 다른 판의 태그와 `ci-deps-v1`은 번호 계산에 영향을 주지 않습니다. 개발판과 확장판은 기본판의 최신 정식 Release 표시를 대체하지 않습니다.

빌드는 `core/version.txt`와 `core/version.json`을 생성합니다. 게임은 표시값을 메인 메뉴에 표시합니다. `build-info.json`에는 배포판·버전·채널·소스 커밋·기준 파일 커밋과 실행 파일 해시가 포함됩니다. 패키징은 해당 판의 빌드 보고서·소스 그래프와 일치하는지 검사하며 판이 다른 실행 폴더나 오래된 리소스 변경분을 거부합니다. 기존 `Cversion="1.0.0.1"`은 맵·스크립트 호환성을 위해 유지합니다. 로컬 빌드는 `-local`로 표시합니다.

### 의존성 Release 최초 준비

먼저 위 안내대로 로컬 의존성을 준비한 뒤 저장소 루트에서 실행합니다. 이미 준비한 `deps/`가 있으면 다시 다운로드할 필요가 없습니다.

```powershell
python -X utf8 scripts/ci.py bundle
```

`build/ci-upload/StrandedII-ci-deps-v1.zip`과 `.zip.sha256`이 생성됩니다. 게임·컴파일러 파일 목록과 글꼴의 기존 해시를 모두 검증한 뒤 필요한 파일과 원본 라이선스만 묶습니다. 개인 세이브·설정과 Blitz 편집기의 예제·미디어는 포함하지 않습니다. 의존성 ZIP 전체의 SHA-256, Release 태그와 파일명은 [config/ci-dependencies.json](../config/ci-dependencies.json)에 고정돼 있습니다.

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

이후 Actions의 `Windows build and release`를 수동 실행하면 ZIP을 받을 수 있습니다. 의존성을 갱신할 때는 ZIP을 다시 만들고 출력된 SHA-256과 새 Release 태그·파일명을 설정에 함께 반영합니다. 해시가 다른 ZIP은 캐시에 있어도 압축을 풀지 않습니다. 기존 동기화된 문서나 소스 변경만으로 의존성 ZIP을 다시 만들 필요는 없습니다. 단, 번들에 포함된 라이선스를 수정하면 의존성 ZIP과 설정 해시도 함께 갱신합니다.

### 배포 ZIP 구성

기본판 ZIP의 `StrandedII/`, 확장판 ZIP의 `StrandedII-Extended/` 폴더에는 빌드된 EXE, 원본 게임 리소스, 현지화 리소스, 언어별 비트맵 글꼴, 초기 설정과 라이선스가 포함됩니다. 전체 압축을 풀고 그 폴더의 `Play.cmd` 또는 `StrandedII.exe`를 실행합니다. 플레이어는 Python·Blitz3D·글꼴을 별도로 설치할 필요가 없습니다.

패키징은 파일 목록을 사용하므로 사용한 개발 런타임에 있는 개인 세이브, 사용자 지도, 스크린샷, `preserved/`, 로그와 검사 보고서를 포함하지 않습니다. 키 설정은 원본 의존성에서, `core/modern.ini`는 저장소 초기 설정에서 가져옵니다. 개인 `sys/settings.cfg`도 제외하므로 새 설치의 그래픽 품질과 음량은 엔진의 초기값을 사용합니다. 초기값은 [실행 및 설정](playing-ko.md#그래픽-품질과-소리)에 설명되어 있습니다. 개발 런타임은 변경하지 않습니다. 로컬 패키징도 가능합니다.

```powershell
python -X utf8 scripts/ci.py package --game deps/game
python -X utf8 scripts/ci.py package --edition extended --game deps/game
python -X utf8 -m unittest discover -s tests -p 'test_ci*.py'
```

출력 이름은 `build/release/StrandedII-<표시 버전>-windows.zip`과 `.zip.sha256`입니다. 예를 들어 기본판 로컬 빌드는 `StrandedII-1.1.0-local-windows.zip`, 확장판은 `StrandedII-extended-0.1.0-local-windows.zip`입니다. 패키징은 컴파일이나 게임 검사를 실행하지 않으므로 먼저 같은 판에서 최신 소스로 `setup → build → check`를 완료하세요. CI 버전 정보는 `version-context-classic`·`version-context-extended`, 게임 ZIP은 `StrandedII-classic-windows`·`StrandedII-extended-windows` 산출물로 저장됩니다.

## 생성물과 사용자 데이터 관리

`deps/`, `build/`, `.venv/`, Python 캐시와 로그는 [.gitignore](../.gitignore)로 제외합니다. 소스·번역·수정 리소스·스크립트·설정·문서·라이선스는 Git에서 관리합니다.

`build/runtime/`에는 실행 결과뿐 아니라 실제 저장 파일과 사용자 설정도 있습니다. 폴더를 초기화하기 전에는 세이브·직접 만든 지도·스크린샷과 `preserved/`가 있다면 그 내용도 백업하세요. 위치는 [실행 및 설정](playing-ko.md)에 정리돼 있습니다.
