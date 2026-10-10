<!-- dev-docs-ai-translation: {"document":"docs/fonts.md","language":"ko","syncedHash":"19bbd4585000d085d7a0bf7b0316843ce963fad2b6d795e0378176abeef7dd8d"} -->

# 폰트 바꾸기

[English](fonts.md) · [한국어](fonts-ko.md)

[README로 돌아가기](../README-ko.md) · [현지화 가이드](localization-ko.md) · [개발 가이드](development-ko.md)

기본 폰트는 Noto Sans KR입니다. 다른 모양의 폰트를 쓰거나 새 언어에 필요한 글자가 없을 때 아래 절차를 따르세요. [개발 환경 준비](development-ko.md#개발-환경-준비)를 마친 상태에서 진행합니다.

## 지정하고 적용하기

1. 사용할 `.ttf` 또는 `.otf` 파일을 `deps/fonts/`에 넣습니다. Windows에 설치할 필요는 없습니다.
2. 대상 언어의 JSON에 `font`를 추가합니다. 예를 들어 `locales/ko.json`:

```json
{
  "name": "한국어",
  "font": "deps/fonts/MyFont.ttf"
}
```

3. 테스트할 게임을 종료하고 저장소 루트의 PowerShell에서 실행합니다.

```powershell
.\dev.ps1 localize -Python .\.venv\Scripts\python.exe
.\dev.ps1 check -Python .\.venv\Scripts\python.exe
.\Play.cmd
```

4. 해당 언어를 선택해 재시작하고 메뉴·일지·숫자·긴 문장의 줄바꿈을 확인합니다.

경로는 저장소 루트 기준이며 `/`를 쓰면 편합니다. 기본 폰트로 돌아가려면 `font` 필드를 삭제하고 같은 명령을 실행합니다. 기본 폰트는 최초 `setup --font`로 지정한 Noto Sans KR입니다.

확장판은 `localize`와 `check`에 `--edition extended`를 붙이고 `Play-Extended.cmd`로 실행합니다. 폰트 설정은 공통이지만 각 판의 실행 폴더는 따로 갱신해야 합니다.

## 글자가 없거나 표시가 이상할 때

- 파일을 찾을 수 없으면 JSON의 경로와 실제 파일명을 확인합니다.
- 지정 폰트에 없는 글자는 기본 Noto, 다른 언어팩의 지정 폰트 순으로 보완합니다.
- 어느 폰트에도 글자가 없으면 문자·`U+XXXX`·번역 키를 보여주고 적용을 중단합니다. 그 글자가 있는 폰트를 지정한 뒤 다시 실행하세요. 실패하면 기존 정상 언어팩을 유지합니다.
- `.ttf`·`.otf`와 가변 폰트를 지원합니다. `.ttc`, 보조 평면 이모지, 복잡한 문자 결합·오른쪽에서 왼쪽으로 쓰기, IME 입력 추가는 지원하지 않습니다.

미리보기는 `build/runtime/core/locales/ko/preview.png`에서 확인합니다. 다른 언어는 `ko`를 해당 코드로 바꾸세요. 어떤 폰트가 사용됐는지는 같은 `core/locales/`의 `fonts-report.json`에 기록됩니다. 확장판은 경로 앞부분을 `build/extended/runtime/`으로 바꿉니다. 최종 표시는 게임에서 확인하세요.

## 배포할 파일

`localize`와 확인을 마치면 해당 판의 **`core/locales/` 폴더 전체**를 묶습니다. 기본판에서는 `build/runtime/core/locales/`, 확장판에서는 `build/extended/runtime/core/locales/`입니다.

받는 사람은 게임을 종료하고 기존 `core/locales/`를 백업한 뒤, 받은 폴더로 교체하고 게임에서 언어를 선택합니다. 이 언어팩 기능을 포함하는 같은 판의 엔진이 필요합니다. 구형 EXE에는 설치하지 마세요. 플레이어는 Python이나 원본 폰트를 설치할 필요가 없습니다.

- `languages.dat`, TSV, 언어별 `glyphs.dat`와 BMP 페이지는 모두 포함합니다.
- `preview.png`와 `fonts-report.json`은 제외해도 됩니다. 보고서에는 개발 PC의 절대 경로가 들어 있습니다.
- 폰트가 이미지 생성·배포를 허용하는지 확인하고 필요한 라이선스 고지를 동봉합니다. 기본 Noto 고지는 [licenses/](../licenses/)에 있습니다.
