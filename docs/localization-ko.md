<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"ko","syncedHash":"0ee6e4b078c775cb8af84a5ed0913c79a5312f8643d6f4616e969565478449d4"} -->

# 게임 번역하기

[English](localization.md) · [한국어](localization-ko.md)

[README로 돌아가기](../README-ko.md) · [개발 가이드](development-ko.md)

**번역할 언어의 `locales/<코드>.tsv`를 수정하면 됩니다.** 영어 원문은 [locales/en.tsv](../locales/en.tsv), 한국어 번역은 [locales/ko.tsv](../locales/ko.tsv)에 있습니다. 번역·언어·폰트 변경에는 EXE 재빌드가 필요하지 않습니다.

두 판 모두 영어(`en`), 한국어(`ko`), 독일어(`de`, **Deutsch**)를 기본 제공합니다. [독일어 번역](../locales/de.tsv)은 [공식 독일어 Gold 1.0.0.1 문구](https://www.unrealsoftware.de/get.php?get=stranded2.zip)를 활용하고, 이 프로젝트의 기능과 확장판에 맞게 조정·보충했습니다. 독일어 팩은 기본 폰트로 표시할 수 있습니다.

## 1. 번역 파일 수정하기

1. `locales/en.tsv`에서 번역할 문구를 찾습니다.
2. 대상 언어의 TSV에서 **같은 키의 오른쪽 문구만** 번역합니다. 키가 없으면 같은 키로 행을 추가합니다.
3. 파일을 **UTF-8, BOM 없이** 저장합니다.

한 줄은 `키<TAB>문구`입니다. 아래 `<TAB>`은 실제 탭 한 글자로 바꾸세요.

```text
quest.example.greeting<TAB>Welcome, $name!
quest.example.greeting<TAB>$name님, 환영합니다!
```

첫 줄은 영어 파일, 둘째 줄은 한국어 파일에 넣는 예시입니다. 한 파일에 같은 키를 두 번 넣지 마세요.

- 키와 `$1`, `$wood`, `$key_*`, `$img=...`, `!0`~`!6` 같은 변수·표식은 유지합니다.
- 문구 안의 줄바꿈은 `\n`, 탭은 `\t`, 백슬래시는 `\\`로 적습니다.
- 번역하지 않은 키는 행을 생략하면 영어로 표시됩니다. **탭 뒤를 비우면 빈 문구로 표시됩니다.**
- 명령어·이벤트·ID·경로는 번역하지 않습니다. 일반 번역 작업은 TSV 안에서 진행하세요.

## 2. 적용하고 확인하기

로컬 확인은 [개발 환경 준비와 최초 빌드](development-ko.md#개발-환경-준비)를 한 번 마친 뒤 할 수 있습니다. 번역 파일만 기여하려면 이 준비 없이 TSV를 수정해 공유해도 됩니다.

테스트할 게임을 종료하고 **저장소 루트의 PowerShell**에서 실행하세요.

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\build\runtime\StrandedII.exe
```

**설정 → 그래픽 → 언어**에서 대상 언어를 선택하고 적용한 뒤 게임을 재시작합니다. 바꾼 문구가 나오는 화면에서 글자 누락, 잘림, 줄바꿈과 변수 값을 확인하세요. 글자가 없다는 오류가 나면 [폰트 지정](#폰트-지정)을 확인합니다.

## 새 언어 추가하기

예를 들어 일본어의 코드는 `ja`입니다. 언어 코드는 소문자 영문·숫자·하이픈을 사용합니다(`ja`, `pt-br` 등).

1. `locales/ja.tsv`를 만들고 `en.tsv`에서 가져온 키와 번역을 넣습니다. 번역한 행만 넣어도 됩니다.
2. `locales/ja.json`을 다음처럼 만듭니다.

```json
{
  "name": "日本語"
}
```

3. 위의 `localize → check → 실행` 절차를 따릅니다. 새 언어는 설정 목록에 자동으로 나타납니다.

기본 영어·한국어·독일어 팩은 유지합니다. 일본어 번역은 현재 기본 제공하지 않습니다.

## 폰트 지정

기본 폰트는 Noto Sans KR입니다. 다른 폰트가 필요하면 `.ttf` 또는 `.otf` 파일을 `deps/fonts/`에 넣고, 대상 언어의 `locales/<코드>.json`에 `"font": "deps/fonts/MyFont.ttf"`를 추가합니다. 경로는 저장소 루트 기준이며 Windows에 설치할 필요는 없습니다.

- 변경 후에는 위의 `localize → check → 실행` 절차를 따릅니다. `font` 필드를 삭제하면 최초 `setup --font`로 지정한 기본 폰트로 돌아갑니다.
- 지정 폰트에 없는 글자는 기본 Noto, 다른 언어팩의 지정 폰트 순으로 보완합니다. 그래도 없으면 문자·`U+XXXX`·번역 키를 표시하고 적용을 중단하며 기존 언어팩을 유지합니다. 해당 글자가 있는 폰트를 지정해 다시 실행하세요.
- 가변 폰트도 지원하지만 `.ttc`, 보조 평면 이모지, 복잡한 문자 결합과 오른쪽에서 왼쪽으로 쓰기는 지원하지 않습니다.

미리보기는 `build/runtime/core/locales/<코드>/preview.png`, 사용한 폰트 정보는 `build/runtime/core/locales/fonts-report.json`에 있습니다. 확장판은 `build/extended/runtime/` 아래에서 확인합니다. 최종 표시는 게임에서 확인하세요.

개발자 메뉴는 영어에 ProggyForever(ProggyClean 계열), 한글에 Pretendard를 사용합니다. `setup` / `localize`가 체크섬이 고정된 Pretendard를 준비하고 각 언어의 `ui.ttf`, 선택한 게임 폰트의 `fallback.ttf`를 함께 생성합니다. 사용자 폰트를 지정하면 기본 Noto도 `fallback-extra.ttf`로 포함합니다. ImGui는 UTF-8 입력과 32비트 유니코드 코드포인트를 처리하며 필요한 글리프를 동적으로 생성합니다. 실제로 표시할 수 있는 문자는 포함된 폰트에 따라 달라집니다. 복잡한 문자 조형과 색상 이모지는 지원을 보장하지 않습니다.

## 기본판·확장판 전용 번역

`locales/<코드>.tsv`는 기본판과 확장판이 함께 사용합니다. 한 판에서만 바꿀 문구는 다음 파일에 **바꿀 행만** 넣습니다. 폴더가 없으면 만드세요.

| 적용할 판 | 파일 |
| --- | --- |
| 기본판 | `locales/editions/classic/<코드>.tsv` |
| 확장판 | `locales/editions/extended/<코드>.tsv` |

같은 키는 공통 번역을 덮어씁니다. 새 키를 추가하면 해당 판의 `en.tsv`에도 영어 원문을 넣습니다. 언어 이름·폰트는 공통 `locales/<코드>.json`에서 지정합니다.

명령에서 판을 생략하면 기본판입니다. 확장판은 [확장판 준비](development-ko.md#기본판과-확장판)를 마친 뒤 다음 명령을 사용합니다. 공통 번역을 두 판에 적용하려면 각 판에서 한 번씩 실행하세요.

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe --edition extended
.\dev.ps1 check -Python .\.venv\Scripts\python.exe --edition extended
.\build\extended\runtime\StrandedII-Extended.exe
```

## 번역 공유하기

번역 기여에는 수정한 TSV와, 새 언어라면 JSON을 함께 전달하세요. 별도 폰트를 지정했다면 폰트 입수 방법과 라이선스도 알려주세요.

플레이어에게 배포하려면 `localize`와 확인을 마친 해당 판의 **`core/locales/` 전체**를 묶습니다. 기본판은 `build/runtime/core/locales/`, 확장판은 `build/extended/runtime/core/locales/`입니다.

- `languages.dat`, TSV, 언어별 `glyphs.dat`, BMP 페이지, `ui.ttf`와 fallback TTF 파일을 모두 포함합니다. `preview.png`와 개발 PC의 절대 경로가 담긴 `fonts-report.json`은 제외해도 됩니다.
- 폰트의 이미지 생성·배포 허용 여부를 확인하고 필요한 라이선스 고지를 동봉합니다. Pretendard와 Proggy 폰트 고지도 포함하세요. 기본 Noto 고지는 [licenses/](../licenses/)에 있습니다.

받는 사람은 게임을 종료하고 기존 `core/locales/`를 백업한 뒤 교체하고, 게임에서 언어를 선택해 재시작합니다. 같은 판의 언어팩 지원 엔진이 필요하며 구형 EXE에는 설치하지 않습니다. Python이나 원본 폰트를 별도로 설치할 필요는 없습니다.

게임 코드에 새 문구를 연결하는 방법은 [개발자용 현지화 안내](development-ko.md#새-문구와-리소스-현지화)에 있습니다.
