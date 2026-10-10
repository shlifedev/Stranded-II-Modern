<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"ko","syncedHash":"c7c293a37ab8a16678e260fbdde8944f1d45a66b59313bbc47f889a97b7bced9"} -->

# 현지화 가이드

[English](localization.md) · [한국어](localization-ko.md)

[README로 돌아가기](../README-ko.md) · [실행 및 설정](playing-ko.md) · [개발 가이드](development-ko.md)

## 현재 지원 범위

현재 한국어(`ko`)와 영어(`en`) 카탈로그를 지원합니다. 하나의 실행 파일과 공통 리소스를 사용하고, 게임을 시작할 때 선택한 언어를 읽습니다. 옵션에서 언어를 바꾸면 다음 실행부터 적용됩니다.

번역·폰트 수정과 새 언어 추가에는 EXE 재컴파일이 필요하지 않습니다. `locales/<코드>.tsv`와 `locales/<코드>.json`을 함께 준비하고 `localize`를 실행하면 언어 선택 목록에 자동으로 등록됩니다. 폰트 지정은 [폰트 가이드](fonts-ko.md)를 참고하세요.

## 파일별 역할

| 파일 | 역할 |
| --- | --- |
| [locales/en.tsv](../locales/en.tsv) | 기준 영어 문구와 전체 키 |
| [locales/ko.tsv](../locales/ko.tsv) | 한국어 번역 |
| `locales/<코드>.json` | 언어 표시 이름과 선택적인 폰트 파일 경로 |
| [locales/legacy-index.json](../locales/legacy-index.json) | 기존 2,261개 번역 번호와 고정 키의 대응 |
| [resources/source-keys.json](../resources/source-keys.json) | 원본 게임의 표시 문구 → 번역 키 |
| [resources/help-terms.json](../resources/help-terms.json) | 스크립트 도움말 용어 → 번역 키 |
| `resources/overrides/` | 직접 수정한 리소스. 필요할 때 생성 |
| [src/engine/includes/localization.bb](../src/engine/includes/localization.bb) | 카탈로그 로딩과 문구 참조 해석 |

일반 번역 수정은 `en.tsv` 또는 `ko.tsv`에서 합니다. 원본 문구와 키의 대응 파일은 원본 리소스의 번역 대상 자체를 바꿀 때 사용합니다.

## 기존 문구 번역하기

1. `locales/en.tsv`에서 원문과 키를 찾습니다.
2. 같은 키의 `locales/ko.tsv` 문구를 수정합니다. 키가 없으면 같은 키로 추가합니다.
3. 게임을 종료하고 저장소 루트에서 다음을 실행합니다.

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\Play.cmd
```

`localize`가 번역 파일과 언어 목록, 언어별 글꼴을 함께 갱신합니다. 게임 리소스와 세이브·설정은 건드리지 않습니다. 게임에서 해당 문구의 줄바꿈, 잘림, 색상과 치환 변수도 확인하세요. 최초 개발 환경 준비는 [개발 가이드](development-ko.md)를 참고합니다.

## TSV 형식

UTF-8, BOM 없이 저장합니다. 한 줄은 `키<TAB>문구`이며, `<TAB>`은 실제 탭 한 글자입니다.

```text
quest.example.greeting<TAB>Welcome, $name!
```

한국어 파일에서는 같은 키에 다음 문구를 넣을 수 있습니다.

```text
quest.example.greeting<TAB>$name님, 환영합니다!
```

- 키에는 영문자, 숫자, 밑줄(`_`), 마침표(`.`), 하이픈(`-`)을 사용합니다. 같은 파일에서 키를 중복하지 않습니다.
- 빈 줄과 `#`으로 시작하는 줄은 무시합니다.
- 문구 안의 줄바꿈은 `\n`, 캐리지 리턴은 `\r`, 탭은 `\t`, 백슬래시는 `\\`로 적습니다.
- 영어가 기준입니다. 한국어에 키가 없으면 영어로 표시합니다. 탭 뒤가 비어 있는 값은 의도적으로 빈 문구이며 영어로 대체되지 않습니다.
- 양쪽 모두 키가 없으면 `[missing: 키]`를 표시하고 실행 폴더의 `core/localization.log`에 기록합니다. 정상적인 변경은 `check`에서 누락 참조를 먼저 해결합니다.

기존 `legacy.0000` 등의 키와 이관된 해시 키는 이름과 번호를 유지합니다. 새 문구에는 `quest.island1.arrival`처럼 용도를 알 수 있는 키를 사용하세요.

## 새 문구 연결하기

새 키와 문구를 영어·한국어 카탈로그에 추가하고, 표시할 위치에 `[[loc:키]]`를 넣습니다.

엔진 코드 예:

```blitzbasic
bmpf_txt(20,20,"[[loc:ui.language]]")
```

게임 스크립트 예:

```text
msg "[[loc:quest.example.greeting]]";
```

여러 참조와 일반 문자열을 함께 사용할 수 있습니다. 참조 중첩은 최대 8단계이며 순환 참조를 만들면 안 됩니다.

리소스 수정본은 실행 폴더를 기준으로 같은 경로를 `resources/overrides/` 아래에 만듭니다. 예를 들어 `resources/overrides/mods/Stranded II/sys/custom.s2s`는 `setup` 시 `build/runtime/mods/Stranded II/sys/custom.s2s`로 복사됩니다. 새 파일은 게임에서 읽거나 실행하도록 별도로 연결해야 합니다.

텍스트 리소스는 UTF-8로 작성합니다. 원본 모델·음악을 통째로 복제할 필요는 없습니다. `overrides`로 세이브나 `modern.ini`, `settings.cfg`, `settings.inf`를 덮어쓰는 것은 허용하지 않습니다.

`.s2` 지도는 문자열 길이가 포함된 바이너리이므로 텍스트 편집기로 일괄 치환하지 않습니다. 지도 편집기 또는 [scripts/s2map.py](../scripts/s2map.py)를 이용하고 지형, 숫자 ID, 비문자열 필드와 첨부 자료를 보존합니다.

## 번역에서 유지할 것

`$1` 같은 UI 인자와 `$wood` 같은 스크립트 변수는 영어 문구와 같은 이름·집합을 유지합니다. 번역에 맞춰 순서는 바꿀 수 있습니다.

`$key_*` 키 이름, `$img=경로` 이미지 표식, `!0`~`!6` 색상·글꼴 표식도 보존합니다. 명령어, 이벤트명, 객체 ID, 파일 경로와 조건식은 번역 대상이 아닙니다.

일지 제목은 조회 키로도 사용합니다. 원본 제목을 바꾸지 않고 표시 별칭을 통해 번역합니다.

원본 게임 리소스는 CP1252, 준비된 리소스와 엔진 소스는 UTF-8입니다. 준비 도구는 문자열의 기존 구분자 166/182/191을 31/30/29로 변환합니다. 숫자 ID까지 일괄 치환하지 않습니다.

## 저장 파일과 문구 참조

스크립트의 치환 변수는 명령 실행 시점의 값을 보존합니다. 엔진이 만드는 저장 형태는 다음과 같습니다.

```text
[[loc:key|name=HEX_UTF8_BYTES]]
```

이 형태는 엔진이 생성하므로 번역자가 직접 작성하지 않습니다. 문구 참조는 표시할 때 현재 언어로 해석되며, 저장된 변수 값은 유지됩니다.

기존 한국어판 저장 파일의 일반 한국어 문장은 번역 키가 없으므로 언어 변경 후에도 그대로 남을 수 있습니다. 실제 세이브의 언어 전환과 호환성 확인은 [개발 가이드](development-ko.md)의 게임 검증 절차를 따릅니다.

## 일본어 등 새 언어 추가

1. `locales/ja.json`을 만듭니다. 언어 코드는 소문자 영문·숫자와 하이픈 조합입니다. 예: `ja`, `pt-br`. 표시 이름은 해당 언어로 작성할 수 있습니다.

```json
{
  "name": "日本語",
  "font": "deps/fonts/MyJapaneseFont.ttf"
}
```

2. 폰트를 위 경로에 넣습니다. 기본 Noto를 사용하려면 `font` 필드를 생략합니다. 경로는 저장소 루트 기준입니다.
3. `locales/ja.tsv`를 UTF-8, BOM 없이 만듭니다. 영어 파일을 복사해 번역하거나, 번역한 키만 넣어도 됩니다. 아래 `<TAB>`을 실제 탭으로 바꾸세요.

```text
ui.language<TAB>言語
```

4. `localize → check → Play.cmd`를 실행합니다. **설정 → 그래픽 → 언어**의 선택 칸을 왼쪽 클릭하면 다음 언어, 오른쪽 클릭하면 이전 언어로 바뀝니다. 한국어·영어 다음에는 추가 언어가 코드 순으로 나옵니다.
5. 적용 후 게임을 종료하고 다시 실행합니다. 번역하지 않은 키는 영어로 표시됩니다. 제거됐거나 설치되지 않은 언어가 설정에 남아 있으면 영어로 시작합니다. 취소하면 이번 선택은 저장되지 않습니다.

엔진 소스와 EXE는 언어를 추가할 때마다 수정하지 않습니다. 이 기능 이전의 EXE를 사용한다면 최초 한 번은 새 엔진을 빌드해야 합니다. 영어·한국어 기본 팩은 유지하세요. 새 영어 키를 만들 때는 `en.tsv`를 먼저 갱신하며, 다른 언어에 없는 키는 영어가 표시됩니다. 탭 뒤의 빈 값은 의도적인 빈 문구이므로 미번역 항목은 행 자체를 생략하세요.

일본어 전체 번역은 기본 제공하지 않습니다. Unicode BMP 범위의 글자를 지원하며, 실제 메뉴·일지·긴 문구·기존 저장을 새 언어로 확인하세요. 보조 평면 이모지, 복잡한 문자 결합·오른쪽에서 왼쪽으로 쓰기, 한글 IME 입력·파일명 지원 추가는 이번 기능에 포함하지 않습니다. 글자 보완·미리보기·배포 절차는 [폰트 가이드](fonts-ko.md)에 있습니다.
