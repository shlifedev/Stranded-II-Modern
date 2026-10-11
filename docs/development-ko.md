<!-- dev-docs-ai-translation: {"document":"docs/development.md","language":"ko","syncedHash":"e9047b65e5a8f9dfc6fd1145017d5e86f78fcd1fd131052dc2268b5b73f10081"} -->

# 개발 가이드

[English](development.md) · [한국어](development-ko.md)

[README로 돌아가기](../README-ko.md) · [현지화 가이드](localization-ko.md)

처음이라면 **개발 환경 준비 → 최초 빌드** 순서로 진행하세요. 준비를 마쳤다면 [변경 후 실행](#변경-후-실행)에서 수정 내용에 맞는 명령을 고르면 됩니다. 번역만 기여하려면 [현지화 가이드](localization-ko.md)부터 읽으세요.

## 개발 환경 준비

Windows 10 이상, Python 3.10 이상, [requirements.txt](../requirements.txt)의 Pillow와 fontTools가 필요합니다. Blitz의 파일 경로 처리를 고려해 `C:\Stranded II`처럼 영문 경로를 사용하세요. 경로에 공백은 있어도 됩니다.

두 판 모두 Visual Studio의 MSVC x86/x64 C++ 빌드 도구와 Windows SDK가 필요합니다. `build`는 QOL 활성화 여부와 관계없이 공통 `src/native/qol_input.c`를 32비트 `QolInput.dll`로 컴파일합니다. 별도 C 런타임 설치는 필요하지 않습니다. 두 판의 패키지에 DLL을 포함하며 네이티브 산출물·보고서는 선택한 판의 `build` 디렉터리 아래 `native/`에 저장합니다.

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

다음 명령도 저장소 루트에서 사용합니다. 리소스를 갱신하거나 실행 파일을 교체하기 전에 개발 게임을 종료하세요. **판을 생략하면 기본판**이며 확장판은 `--edition extended`를 붙입니다.

| 변경 내용 | 실행 명령 |
| --- | --- |
| `src/engine/**/*.bb` 수정 | `.\dev.ps1 build -Python .\.venv\Scripts\python.exe` |
| `locales/` 번역·언어·폰트 수정 | `.\dev.ps1 localize -Python .\.venv\Scripts\python.exe` |
| `resources/` 수정 | `.\dev.ps1 setup -Python .\.venv\Scripts\python.exe` |
| 자동 검사 | `.\dev.ps1 check -Python .\.venv\Scripts\python.exe` |
| 게임 실행 | `.\Play.cmd` |

저장소의 `Play.cmd`와 `Play-Extended.cmd`는 빌드된 실행 폴더를 여는 개발용 편의 스크립트입니다. 배포 ZIP에는 포함하지 않으며 플레이어는 해당 판의 EXE를 직접 실행합니다.

코드와 리소스를 함께 수정했다면 `setup → build → check → 실행` 순서로 진행합니다. 번역·새 언어·폰트만 수정했다면 `localize → check → 실행`으로 반영되며 EXE 재컴파일은 필요하지 않습니다. `setup`도 같은 언어팩 준비 과정을 포함합니다. 폰트 설정은 [현지화 가이드](localization-ko.md#폰트-지정)에 있습니다.

`setup`은 기존 실행 폴더의 세이브, `.cfg`, `settings.inf`, `core/modern.ini`를 보존합니다. 따라서 `config/modern.ini`를 바꿔도 기존 실행본의 설정이 자동으로 바뀌지 않습니다. 개인 설정은 게임 메뉴에서 바꾸거나 게임을 종료한 뒤 실행 폴더의 `core/modern.ini`를 수정합니다.

`build`는 임시 실행 파일을 컴파일한 뒤 `build/runtime/StrandedII.exe`를 교체합니다. 실행 중인 게임 때문에 교체에 실패했다면 게임을 종료하고 `build`를 다시 실행합니다.

Python으로 직접 실행할 수도 있습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py run -- -win -fps 120
```

## 어디를 수정하나요?

**게임 기능은 `src/engine/`에서 구현합니다.** `build/`는 실행 파일·검사 결과가 생기는 곳이며 원본 소스는 아닙니다. 아래 경로와 명령은 기본판 기준입니다. 확장판 경로는 [다음 절](#기본판과-확장판)을 참고하세요.

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
resources/overrides/              공통 리소스 변경분(필요할 때 생성)
resources/editions/classic/overrides/   기본판 리소스 변경분
resources/editions/extended/overrides/  확장판 리소스 변경분
build/runtime/                   기본판 실행·설정·세이브
build/extended/runtime/          확장판 실행·설정·세이브
```

두 `modules.bb`는 공통 UI·입력·건축과 `includes/qol*.bb` 구현을 선택합니다. 판별 프로필의 `Cedition_qol_default`는 기본판 0, 확장판 1입니다. 저장된 `qol_*` 값이 우선하며 누락·잘못된 값에는 해당 판의 기본값을 사용합니다. QOL은 설정 저장에 성공하면 즉시 적용하고, 언어·화면 변경은 계속 재시작이 필요합니다. 검색 입력은 Win32 Unicode EDIT와 IME를 사용하며 선언은 `config/userlibs/qol_*.decls`에 있습니다. 기능이 달라질 때만 필요한 모듈을 분리합니다. Python은 엔진 코드를 생성하지 않습니다. 빌드 보고서는 해당 진입점의 실제 `Include` 그래프를 기록하며 다른 판의 전용 소스를 포함하면 검사에 실패합니다.

조합법 도감은 로드된 `Tcom` 데이터를 읽습니다. 조리·물 사용 등의 안내는 공통 변경분의 `mods/Stranded II/sys/qol_guides.inf`에 별도로 작성하며, `source`에 근거 스크립트와 이벤트를 기록합니다. 안내는 스크립트를 대신 실행하지 않습니다. 원본 이벤트를 수정할 때 안내의 조건·수량·확률 설명도 함께 확인하세요. QOL 설정은 해당 판의 `core/modern.ini`에만 저장합니다.

검색창의 네이티브 연결부는 현재 게임 스레드의 해당 EDIT 컨트롤 메시지만 문자 변환 후 Unicode로 전달합니다. Blitz의 기본 메시지 루프에는 이 문자 변환이 없으므로 단순히 EDIT를 만드는 것만으로는 입력할 수 없습니다. 부모 창의 `WS_CLIPCHILDREN`으로 검색창을 게임 화면 출력에서 제외하고, 크기 변경 때만 위치를 갱신합니다. 매 프레임 `InvalidateRect`로 배경을 지우거나 다시 그리지 마세요. 검색창을 닫으면 메시지 훅과 추가한 창 스타일을 복원합니다.

리소스는 공식 입력 → 현지화 준비 → 공통 변경분 → 선택한 판의 변경분 순서로 준비합니다. 판별 변경분은 실행 폴더 기준 `mods/Stranded II/...` 경로로 배치합니다. 아이템·조합·건축·능력치·맵·스크립트 리밸런싱은 확장판 변경분에 작성합니다. 설정·세이브·실행 파일은 리소스 변경분으로 덮어쓸 수 없습니다. `.s2` 지도 수정은 [리소스 현지화의 바이너리 규칙](#새-문구와-리소스-현지화)을 따릅니다. 판별 번역은 [현지화 가이드](localization-ko.md#기본판확장판-전용-번역)를 참고하세요.

모든 개발 명령은 `--edition classic|extended`를 받으며 생략하면 기본판입니다. 의존성과 컴파일러는 공유하고, 실행 폴더·보고서·검증 산출물은 분리합니다. 기본판 데이터는 이동하지 않으며 확장판으로 자동 복사하지 않습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py setup --edition extended
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py build --edition extended
.\.venv\Scripts\python.exe -X utf8 scripts/dev.py check --edition extended
.\Play-Extended.cmd
```

위 명령은 [최초 빌드](#최초-빌드)에서 의존성 경로를 등록한 뒤 사용합니다. 확장판부터 준비한다면 최초 `setup` 명령에 `--edition extended`를 붙이세요. 확장판 기본 설정은 `config/editions/extended/modern.ini`이며, 이미 존재하는 개인 설정은 `setup`으로 교체하지 않습니다.

## 새 문구와 리소스 현지화

이 절은 새 UI·퀘스트를 만드는 개발자용입니다. 기존 문장 번역은 [현지화 가이드](localization-ko.md)를 따르세요.

1. `locales/en.tsv`에 `quest.island1.arrival`처럼 용도를 알 수 있는 새 키와 영어 문구를 추가합니다. 확장판 전용 문구는 `locales/editions/extended/en.tsv`에 넣습니다.
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
| 기존 번역 번호와 키의 대응 | `locales/legacy-index.json` — 기존 번호 유지 |
| 리소스 수정본 | 공통 `resources/overrides/mods/Stranded II/...` 또는 판별 `resources/editions/<edition>/overrides/mods/Stranded II/...` |
| `.s2` 지도 | 길이 정보가 있는 바이너리. 지도 편집기 또는 [scripts/s2map.py](../scripts/s2map.py)로 문자열만 수정하고 지형·ID·나머지 필드·첨부 자료 보존 |

리소스 수정은 `setup`, 코드 수정은 `build`, 번역만 수정하면 `localize`로 반영하고 `check`를 실행합니다. 새 리소스 파일은 게임에서 읽도록 별도로 연결해야 합니다. 설정·세이브·실행 파일을 리소스 변경분으로 덮어쓰지 않습니다.

원본 입력은 CP1252이며 준비된 리소스와 엔진 소스는 UTF-8입니다. 기존 구분자 166/182/191을 31/30/29로 바꿀 때는 문자열에만 적용하고 숫자 ID는 보존합니다. 일지 제목은 조회 키이므로 제목 자체가 아닌 표시 별칭을 번역합니다.

엔진은 스크립트 변수를 `[[loc:key|name=HEX_UTF8_BYTES]]` 형태로 저장해 실행 시점의 값을 보존합니다. 이 형태는 직접 작성하지 않습니다. 기존 세이브에 일반 문장으로 저장된 번역은 언어를 바꿔도 남을 수 있으므로 복사본에서 호환성을 확인하세요.

## 변경 검증

게임 코드가 바뀌면 `build`와 `check`를 실행하고 변경한 기능을 게임에서 확인합니다. 번역·폰트는 `localize`와 `check`, 리소스는 `setup`과 `check` 후 표시를 확인합니다. 문서만 바꾼 경우에는 링크와 안내 내용의 일치 여부를 확인합니다.

| 검사 | 확인 범위 | 결과 위치 |
| --- | --- | --- |
| `setup` | 의존성 해시, 카탈로그, 공식 지도 13개의 문자열 변환과 그 외 바이트·첨부 자료 보존 | `build/setup-report.json` |
| `localize` | 등록 언어·카탈로그·TTF/OTF 글리프와 보완 출처, 실패 시 기존 언어팩 보존 | `build/localization-report.json`, `build/runtime/core/locales/fonts-report.json` |
| `build` | 실제 Blitz3D 컴파일, 실행 파일·소스 해시 | `build/build-report.json` |
| `check` | 문구 참조·치환 변수·글꼴, 실제 Blitz 로더와 파서, 설정 보존, 시간 보정·프레임 대기, 저장 교체·잠금 실패·백업·5슬롯·저장 큐·계속하기 선택 | `build/check-*/report.json`과 같은 폴더의 상세 결과 |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_game.py` | 격리된 게임에서 실제 지도·저장·백업·빠른 저장/불러오기·자동 저장 상태 복구 | `build/game-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol_logic.py --language ko` | 실제 Blitz의 QOL 로직·제작·이동·정렬·설정 검사. 그래픽 엔티티와 스크립트는 대역 사용 | `build/qol-logic-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_qol.py --language ko` | 복사한 실행 환경에서 실제 제작·이동·스크립트 거부·저장/불러오기 검사. 그래픽 장치 필요 | `build/qol-smoke-*/report.json` |
| `.\.venv\Scripts\python.exe -X utf8 scripts/verify_collisions.py --edition classic` | 격리된 Blitz 실행 환경에서 바위 메시 정리, 경사면 접근·후퇴, 점프·착지, 통로·수영, 제어 전환·투사체 충돌 | `build/collision-smoke-*/report.json` |

이동·충돌 변경에는 `verify_collisions.py`를 두 에디션에서 실행합니다. Extended는 `--edition extended`를 사용하고 결과를 `build/extended/collision-smoke-*/`에 저장합니다. 이 검사는 실제 지형과 바위 모델에 60·120·144 Hz 및 100ms 시간 간격을 적용하며 GUI 입력을 보내지 않습니다. 실제 렌더링 FPS와 사용자가 보고한 장소는 별도로 확인합니다. `core/modern.ini`의 기존 `diagnostics=1` 옵션을 켜면 이동 요청·결과, 지형 보정량, 접촉점·법선과 이동 취소 여부가 `core/modern.log`에 최대 초당 4회 기록됩니다.

`check`는 렌더링 없이 타이머·파서·저장 파일 처리 등을 검사합니다. 실제 렌더링 FPS, Alt-Tab, 화면의 부드러움과 전체 진행은 게임에서 따로 확인해야 합니다.

두 판의 `check`는 `verify_qol_logic.py`를 실행합니다. 판별 기본값, 누락·잘못된 설정, 옵션 조합 32가지와 공통 엔진 함수를 엔티티·스크립트 대역으로 검사하며 화면, 실제 스크립트, 지도 저장, IME는 검증하지 않습니다. 두 QOL 검증 명령 모두 `--edition classic|extended`를 받으며 생략하면 기본판, 확장판 결과는 `build/extended/` 아래입니다. `verify_qol.py --interactive`는 QOL을 켠 별도 테스트 게임을 개인 세이브 없이 준비하며 자동 실행하지 않습니다. 이 복사본에서 한글 조합·확정·취소, 검색창 포커스와 Alt-Tab, 긴 이름·상세 스크롤, Shift 클릭·R·Shift+R, 옵션별 켜기/끄기, 건축 반복 취소를 확인하세요. `--language en`은 영어, `--desktop`은 전체 창입니다. 시뮬레이션한 건축 간격은 실제 렌더링 FPS 측정과 다릅니다.

저장 동작을 바꿨다면 `scripts/verify_game.py`로 실제 지도 로딩과 저장·불러오기도 확인하세요. 실행 폴더의 복사본과 빈 세이브 폴더를 사용하며 사용자 게임·세이브에는 쓰지 않습니다. 그래픽 장치가 필요하고 UI 조작은 검사하지 않습니다. 기본은 800×600 창이며, `--desktop`은 모니터에 맞춘 전체 창, `--edition extended`는 확장판을 검사합니다. 확장판 보고서는 `build/extended/` 아래에 생성됩니다.

타이밍 코드를 수정할 때는 `modern_runtime.bb`, `modern_math.bb`, `modern_ui.bb`와 호출부를 함께 확인하세요. 게임 시간(QPC)과 프레임 대기 시계를 분리하고, 전체 프레임 경과 시간·100ms 상한·소수 시간 누적·공통 포커스/화면 표시 경로를 유지합니다. `verify_clock_hooks.py`의 외부 배속 재현은 별도 검사 프로세스에서만 실행됩니다.

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

공통 기능과 단축키는 [QOL 설정](qol-ko.md), 기본값 차이와 개발 상태는 [확장판 차이](extend-features-ko.md)를 참고하세요.

## 프로젝트 문서 수정

영어 `.md` 또는 한국어 `-ko.md`를 직접 수정하세요. 새 한국어 문서는 `example-ko.md`처럼 이름을 붙입니다. AI 에이전트가 저장소 작업을 할 때 다른 언어에도 반영하며, 상시 실행되는 번역 서비스는 아닙니다.

문서 언어는 [.dev-docs-ai-translation.json](../.dev-docs-ai-translation.json)에서 관리하며 게임 언어와는 별개입니다. 영어만 수정하면 영어를 기준으로, 두 언어를 모두 수정하면 한국어를 기준으로 동기화합니다. 문서 첫 줄의 해시는 직접 고치지 마세요. 세부 규칙은 [AGENTS-ko.md](../AGENTS-ko.md)에 있습니다.

예외적으로 `README-ko.md`는 사용자가 직접 관리하며, 명시적인 편집 요청 없이는 에이전트가 변경하지 않습니다. 다른 언어 README는 한국어 원문을 기준으로 동기화합니다. 문서 작성·갱신 시에는 [auto-gen-please.json](auto-gen-please.json)의 문서별 목적·문체를 따르며 필요한 문서만 갱신합니다.

## GitHub CI와 배포 ZIP

[Windows 워크플로](../.github/workflows/windows.yml)는 `main`·`dev` 푸시, PR, 수동 실행에서 기본 게임을 빌드·검사하고 게임 ZIP과 SHA-256 파일을 산출물로 저장합니다. `main` 푸시는 검사 성공 후 안정판 Release를 자동 발행하고 patch 번호를 올립니다. `dev` 푸시와 PR은 `-local` 검증용 ZIP만 만들며 Release를 발행하거나 번호를 소모하지 않습니다. Extended의 CI 판 선택·빌드·배포 경로는 주석 처리되어 있으며 당분간 로컬에서만 개발합니다.

개발판을 출시하거나 안정판 출시를 수동 실행하려면 GitHub에서 다음 순서로 진행합니다.

1. **Actions → Windows build and release → Run workflow**를 엽니다.
2. 정식판은 `main`, 시험판은 `dev` 브랜치를 선택합니다.
3. `publish`를 켭니다.
4. 빌드·검사가 성공하면 Release가 발행됩니다.

수동 실행의 `publish=false`는 ZIP 생성과 검증만 수행합니다. 다른 브랜치나 태그 푸시에서는 출시하지 않습니다.

CI는 Windows Server 2022와 Python 3.13에서 `setup → build → check → package`를 실행한 다음 Apple Silicon `macos-15` 실행기에서 같은 게임을 패키징합니다. `check`는 비그래픽 런타임 검사이며 실제 화면, 렌더링 FPS, Alt-Tab과 전체 플레이 검증을 대신하지 않습니다. 로컬 검사와 별도로 실제 GitHub Actions 실행도 확인해야 합니다.

원본 사이트에 반복 다운로드 트래픽을 발생시키지 않도록 게임 리소스·컴파일러·글꼴은 같은 저장소의 의존성용 Release에 한 번 보관합니다. 이 Windows 빌드 입력에는 해당 Release와 Actions 캐시만 사용합니다. 파일이 없으면 준비 방법을 안내하고 실패하며, 해당 입력을 원본 사이트에서 자동 다운로드하지 않습니다. macOS 런타임 다운로드는 아래 설명대로 별도 고정합니다.

### 버전 지정과 자동 증가

기준 버전은 `main`의 [config/version.json](../config/version.json)에 있으며 초기값은 `1.1.0`입니다. CI는 앞 두 숫자나 설정 파일을 수정하지 않고 버전 커밋을 만들지 않습니다. patch는 해당 계열의 시작값이며 기존 정식 태그가 있으면 다음 번호부터 배포합니다. Extended의 로컬 기준 버전은 [config/editions/extended/version.json](../config/editions/extended/version.json)에서 별도로 관리합니다.

| 배포 상황 | 메뉴 표시 | GitHub 태그 |
|---|---|---|
| 기본판 첫 정식 배포 | `1.1.0-main` | `v1.1.0` |
| 이후 기본판 개발 배포 | `1.1.1-dev.1` | `v1.1.1-dev.1` |
| 기본판 다음 정식 배포 | `1.1.1-main` | `v1.1.1` |

출시할 때 `dev`는 `main`의 기준 파일을 읽고 그 커밋 SHA를 고정합니다. 정식 배포 전에는 기준 버전의 `-dev.1`부터 시작하며, 정식 배포가 있으면 다음 patch를 목표로 `dev.N` 순번을 올립니다. 개발 배포는 정식 patch를 소모하지 않습니다. `main`은 배포 대상 커밋의 기준 파일을 사용합니다.

자동·수동 출시 실행은 같은 대기열에서 버전 계산부터 발행까지 직렬 처리합니다. 빌드·검사 실패는 태그를 만들지 않으므로 번호를 소모하지 않습니다. 발행 중 중단되면 번호가 예약될 수 있지만 같은 판·소스 커밋·채널로 재실행하면 태그와 초안을 복구합니다. 공개된 Release는 덮어쓰지 않으며 재실행 시 검증 후 기존 발행을 유지합니다. 다른 판의 태그와 `ci-deps-v1`은 번호 계산에 영향을 주지 않습니다. 개발판은 최신 정식 Release 표시를 대체하지 않습니다.

빌드는 `core/version.txt`와 `core/version.json`을 생성합니다. 게임은 표시값을 메인 메뉴에 표시합니다. `build-info.json`에는 배포판·버전·채널·소스 커밋·기준 파일 커밋과 실행 파일 해시가 포함됩니다. 패키징은 해당 판의 빌드 보고서·소스 그래프와 일치하는지 검사하며 판이 다른 실행 폴더나 오래된 리소스 변경분을 거부합니다. 기존 `Cversion="1.0.0.1"`은 맵·스크립트 호환성을 위해 유지합니다. 로컬 빌드는 `-local`로 표시합니다. 빌드 보고서에는 네이티브 소스·DLL 해시도 기록하며 패키징 시 현재 공통 소스와 선택한 실행 환경의 DLL이 일치하는지 검사합니다.

### 의존성 Release 최초 준비

먼저 위 안내대로 로컬 의존성을 준비한 뒤 저장소 루트에서 실행합니다. 이미 준비한 `deps/`가 있으면 다시 다운로드할 필요가 없습니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/ci.py bundle
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

배포 ZIP의 `StrandedII/` 폴더에는 `StrandedII.exe`, 원본 게임 리소스, 현지화 리소스, 언어별 비트맵 글꼴, 초기 설정과 라이선스가 포함됩니다. 전체 압축을 풀고 `StrandedII.exe`를 더블클릭하면 됩니다. 실행용 CMD는 포함하지 않습니다. 플레이어는 Python·Blitz3D·글꼴을 별도로 설치할 필요가 없으며 화면·FPS·언어는 게임의 설정 메뉴에서 바꿉니다.

패키징은 파일 목록을 사용하므로 사용한 개발 런타임에 있는 개인 세이브, 사용자 지도, 스크린샷, `preserved/`, 로그와 검사 보고서를 포함하지 않습니다. 키 설정은 원본 의존성에서, `core/modern.ini`는 저장소 초기 설정에서 가져옵니다. 개인 `sys/settings.cfg`도 제외하므로 새 설치의 그래픽 품질과 음량은 엔진의 초기값을 사용합니다. 개발 런타임은 변경하지 않습니다. 로컬 패키징도 가능합니다.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/ci.py package --game deps/game
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p 'test_ci*.py'
```

출력 이름은 `build/release/StrandedII-<표시 버전>-windows.zip`과 `.zip.sha256`입니다. 로컬 빌드의 예시는 `StrandedII-1.1.0-local-windows.zip`입니다. 패키징은 컴파일이나 게임 검사를 실행하지 않으므로 먼저 최신 소스로 `setup → build → check`를 완료하세요. CI 버전 정보는 `version-context`, 게임 ZIP은 `StrandedII-windows` 산출물로 저장됩니다.

### macOS 테스트 패키지

`macos` 작업은 `StrandedII-<표시 버전>-macos-apple-silicon.zip`과 `.sha256`을 만들어 `StrandedII-macos` 산출물로 저장합니다. `Stranded II.app`에는 변경하지 않은 Windows 게임, 네이티브 실행기, WineD3D와 필요한 Wine 라이브러리가 포함됩니다. 초기 대상은 macOS 15 이상과 Rosetta 2가 있는 Apple Silicon입니다. Intel Mac은 지원 검증 대상에 포함하지 않습니다. 플레이어는 Python·Homebrew·Wine을 별도로 설치할 필요가 없습니다.

[config/macos-wine.json](../config/macos-wine.json)에 공식 Sikarugir GitHub Release의 엔진·보조 라이브러리 아카이브 URL과 SHA-256을 고정합니다. 캐시에서도 두 다운로드의 해시를 검사합니다. 패키저는 필요한 런타임 라이브러리만 복사하고 템플릿의 실행기와 다른 렌더러는 제외하며, Mono·Gecko·FFmpeg/GStreamer 연결 모듈도 제외합니다. 라이선스 고지를 포함합니다. 고정 버전을 변경하면 의존성과 실제 플레이를 다시 검증해야 합니다.

Mac에서 패키징을 재현하려면 Python 3.13 이상과 Xcode Command Line Tools를 설치합니다. **같은 CI 실행**의 Windows ZIP·체크섬과 `version-context/version.json`을 받아 각각 `build/release/`와 `build/ci/`에 두고 실행합니다.

```sh
python3 scripts/package_macos.py download-wine
python3 scripts/package_macos.py package \
  --windows-zip build/release/StrandedII-<display-version>-windows.zip \
  --version-info build/ci/version.json \
  --wine-archive build/ci/macos-wine.tar.xz --smoke-test
python3 -m unittest discover -s tests -p 'test_ci*.py'
```

실행 전에 `<display-version>`을 바꾸세요. 엔진과 `macos-wine-libraries.tar.xz`는 같은 디렉터리에 있어야 합니다. 기본 출력은 `build/release/`이며 초기화 로그는 `macos-smoke.log`로 저장합니다. 스모크 검사는 새 임시 데이터 디렉터리에서 Wine을 초기화하고 콘솔 명령을 실행하며 게임을 실행하거나 플레이를 검증하지는 않습니다. 네이티브 테스트는 설치, 동시 실행 잠금, 갱신, 설정·저장·사용자 파일 보존, 갱신 중단과 한글 경로를 검사합니다. 패키저는 실행 권한, 라이브러리 의존성, 서명과 Windows ZIP 대비 게임 파일도 검사합니다.

앱은 임시 서명만 하며 Developer ID 서명·공증은 하지 않습니다. 압축을 풀고 응용 프로그램 폴더로 옮긴 뒤 처음 실행을 시도하면 **시스템 설정 → 개인정보 보호 및 보안 → 확인 없이 열기**가 필요할 수 있습니다. Rosetta가 없으면 실행기가 설치 필요를 안내합니다. 실제 Mac에서 화면·소리·키보드와 마우스 입력·한글 표시·창 전환·저장과 불러오기·이전 앱 교체를 확인하기 전에는 플레이 검증이 끝난 패키지로 안내하지 마세요.

실행기는 `~/Library/Application Support/Stranded II/`에 Wine 환경, `launcher.log`와 쓰기 가능한 게임 세대를 저장합니다. `current`가 현재 게임 디렉터리를 가리킵니다. Wine 안에서는 전용 `S:\` 드라이브로 연결해 macOS 계정 이름이 한글이어도 Blitz에는 ASCII 경로가 전달됩니다. 갱신 시 저장 파일, 모든 `.cfg`, `settings.inf`, `modern.ini`와 사용자가 추가한 파일을 보존하며, 이전 패키지에 포함된 사용자 데이터가 아닌 파일만 제거할 수 있습니다. 갱신 실패 시 이전 세대를 계속 사용합니다. 과거 세대는 복구용으로 남깁니다. 앱을 교체해도 이 데이터는 삭제되지 않습니다. Windows나 Extended 설정은 가져오지 않습니다.

출시는 macOS 작업 종료까지 기다리지만 Windows 빌드 성공만 필수입니다. macOS 패키징이 성공하면 같은 Release에 첨부하고, 실패하면 Windows만 게시하며 작업 요약에 macOS 결과를 기록합니다. 초안은 산출물 이름과 해시를 고정합니다. 업로드 중단은 원래 산출물로 복구하며, 다시 생성한 ZIP의 내용이 다르면 거부합니다. 공개된 Release는 변경하지 않으므로 Windows만 게시된 뒤 재실행에서 macOS 패키지가 생겨도 추가하지 않습니다. `scripts/ci.py release --platforms windows macos-apple-silicon`은 두 패키지를 선택하며, 기본값은 Windows만입니다.

## 생성물과 사용자 데이터 관리

`deps/`, `build/`, `.venv/`, Python 캐시와 로그는 [.gitignore](../.gitignore)로 제외합니다. 소스·번역·수정 리소스·스크립트·설정·문서·라이선스는 Git에서 관리합니다.

`build/runtime/`과 `build/extended/runtime/`에는 실행 결과뿐 아니라 실제 저장 파일과 사용자 설정도 있습니다. 폴더를 초기화하기 전에는 각 실행 폴더의 `mods/Stranded II/saves/`와 직접 만든 지도·스크린샷, `preserved/`가 있다면 그 내용도 백업하세요.
