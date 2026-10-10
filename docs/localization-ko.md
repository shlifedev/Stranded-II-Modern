<!-- dev-docs-ai-translation: {"document":"docs/localization.md","language":"ko","syncedHash":"cde9f1a82eb5340c7b7e65fb54757fa10734bf33f1120d7feaef1ba9d1c8d49d"} -->

# 게임 번역하기

[English](localization.md) · [한국어](localization-ko.md)

[README로 돌아가기](../README-ko.md) · [개발 가이드](development-ko.md) · [폰트 가이드](fonts-ko.md)

**번역할 언어의 `locales/<코드>.tsv`를 수정하면 됩니다.** 영어 원문은 [locales/en.tsv](../locales/en.tsv), 한국어 번역은 [locales/ko.tsv](../locales/ko.tsv)에 있습니다. 번역·언어·폰트 변경에는 EXE 재빌드가 필요하지 않습니다.

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
.\Play.cmd
```

**설정 → 그래픽 → 언어**에서 대상 언어를 선택하고 적용한 뒤 게임을 재시작합니다. 바꾼 문구가 나오는 화면에서 글자 누락, 잘림, 줄바꿈과 변수 값을 확인하세요. 글자가 없다는 오류가 나면 [폰트 지정](fonts-ko.md)을 확인합니다.

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

기본 폰트는 Noto Sans KR입니다. 다른 폰트가 필요하면 JSON에 `"font": "deps/fonts/MyFont.ttf"`를 추가하고 해당 파일을 준비하세요. 폰트 경로는 저장소 루트 기준입니다. 기본 영어·한국어 팩은 유지합니다. 일본어 번역은 현재 기본 제공하지 않습니다.

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
.\Play-Extended.cmd
```

## 번역 공유하기

번역 기여에는 수정한 TSV와, 새 언어라면 JSON을 함께 전달하세요. 별도 폰트를 지정했다면 폰트 입수 방법과 라이선스도 알려주세요.

플레이어가 바로 설치할 언어팩을 만들려면 [언어팩 배포](fonts-ko.md#배포할-파일)를 참고하세요. 게임 코드에 새 문구를 연결하는 방법은 [개발자용 현지화 안내](development-ko.md#새-문구와-리소스-현지화)에 있습니다.
