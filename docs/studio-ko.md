<!-- dev-docs-ai-translation: {"document":"docs/studio.md","language":"ko","syncedHash":"d7eeee7299a494ac2944f8be8c823c704f29421853cd5bac4bab430b03a3b397"} -->

# Stranded II Studio

[English](studio.md) · [한국어](studio-ko.md)

Extended 콘텐츠를 만드는 독립 개발 앱입니다. Electron·React 화면과 Python CLI가 같은 프로젝트·편집 명령을 사용합니다. 3D 장면과 테스트 플레이는 `StrandedIIStudio.bb`에서 공유 Blitz 엔진과 Extended 모듈을 WebAssembly/WebGPU로 실행합니다. 기본판 게임의 실행·배포 경로는 그대로입니다.

## 준비와 실행

Python 3.12 이상, Node.js 22.12 이상, Git이 필요합니다. macOS는 Xcode Command Line Tools와 PATH에 있는 FFmpeg/ffprobe, Windows는 MSVC C++ 빌드 도구와 Windows SDK가 필요합니다. 3D 화면은 Electron의 WebGPU와 JSPI 지원을 사용합니다. 현재 저장소에서 실행하는 개발 버전이며 설치 패키지는 제공하지 않습니다.

Windows에서는 루트의 [Studio.cmd](../Studio.cmd)를 더블 클릭하고, macOS/Linux에서는 `sh ./Studio.sh`로 [Studio.sh](../Studio.sh)를 실행합니다. 다른 폴더에서도 실행 파일의 경로를 지정해 호출할 수 있습니다. 두 파일은 [공통 실행 스크립트](../scripts/studio_launch.py)로 `.venv` 생성, 누락되거나 변경된 Python·Node 의존성 설치, 리소스 준비, 엔진 빌드·검증, 앱 빌드·실행을 순서대로 처리합니다. Windows 이외에서는 누락된 CMake·Ninja도 가상 환경에 설치합니다. 준비가 실패하면 앱을 열지 않습니다.

실행할 때마다 리소스와 엔진을 갱신하므로 엔진 명령을 따로 실행할 필요가 없습니다. 게임·폰트 경로는 기존 Studio·웹·Windows 준비 설정 순서로 찾고, 유효한 경로가 없으면 `deps/game`과 `deps/fonts/NotoSansKR[wght].ttf`를 사용합니다. 외부 경로는 실행 파일에 `--game PATH --font PATH`로 전달합니다. 공식 게임·고정 폰트 준비는 [개발 가이드](development-ko.md)를 참고하세요. 프로젝트와 개인 세이브는 초기화하지 않습니다.

앱을 열지 않고 전체 준비를 확인하려면 `Studio.cmd --prepare-only` 또는 `sh ./Studio.sh --prepare-only`를 사용합니다. 각 단계를 직접 실행하려면 저장소 루트에서 Python 가상 환경을 활성화한 뒤 아래 명령을 사용합니다.

```sh
python -m pip install -r requirements.txt cmake ninja
python scripts/studio.py engine setup --game deps/game --font "deps/fonts/NotoSansKR[wght].ttf"
python scripts/studio.py engine build
python scripts/studio.py engine check
npm --prefix studio ci
npm --prefix studio start
```

이미 웹/Studio 준비 설정이 있으면 `engine setup`의 경로 인자를 생략할 수 있습니다. 최초 엔진 준비는 고정된 Blitz 웹 툴체인과 SDK를 내려받습니다. 엔진 산출물은 `build/studio/`, 앱 산출물은 `studio/dist/`에 생성합니다. 실행 파일과 앱은 저장소의 `.venv` Python을 우선 사용하며, 다른 인터프리터는 `S2_STUDIO_PYTHON`으로 지정합니다.

시작 화면에서 빈 프로젝트 폴더를 선택해 만들거나 기존 프로젝트를 엽니다. 3D 미리보기용 기본 리소스는 **`build/studio/runtime`**으로 지정합니다. 다른 준비된 기본 리소스도 CLI 편집·검증·내보내기에 사용할 수 있지만, 미리보기는 빌드에 포함된 리소스와 같은 기본 경로를 요구합니다.

## 작업 흐름

| 영역 | 사용법 |
| --- | --- |
| 월드 | `+`로 32×32 섬 생성, `↥`로 준비된 UTF-8 `.s2` 가져오기. 다른 크기는 CLI에서 지정합니다. |
| 장면 | 우클릭 드래그로 시점, WASD/QE로 이동, Shift로 가속, 휠로 줌, F로 선택 개체 보기. |
| 배치 | 콘텐츠를 장면으로 드래그하거나 배치 도구로 지형 클릭. 새 정의는 **리소스 적용** 후 배치할 수 있습니다. Shift로 여러 개체를 선택합니다. |
| 이동 | 굵은 X/Z 축으로 방향 이동, 가운데 사각형으로 지형 위 이동. 오브젝트가 없는 선택은 Y 축도 표시합니다. 마지막 선택 개체가 스냅 기준이며, Esc 또는 포인터 캡처 상실로 취소합니다. |
| 속성 | 좌표·방향·체력·수량을 수정하고 **적용**. 오브젝트의 높이는 게임처럼 지형에서 정해집니다. |
| 지형 | 높이기·낮추기·평탄화·부드럽게 중 선택하고 지형 클릭. 반경·강도 조절, 높이기/낮추기에 강도 사용. |
| 콘텐츠 | 아이템·오브젝트·유닛·맵 정보·건축·레시피 검색, 정의 복제, 속성·재료 편집, 모델·이미지·오디오 가져오기. |
| 스크립트 | 정의·개체 이벤트·맵 전체 스크립트 편집. 게임 명령 자동완성을 제공하며 **스크립트 적용**으로 저장합니다. |
| 리소스 | 정의·모델·번역·폰트 수정은 저장 후 **리소스 적용 필요**로 표시합니다. **리소스 적용**을 누르면 변경을 한 번에 반영하고 카메라·선택·도구를 복원합니다. 적용 전에는 마지막으로 적용한 모습으로 표시합니다. |
| 테스트 | 편집기에 올라온 Blitz 엔진으로 최신 저장 맵을 플레이합니다. 화면 클릭으로 마우스 캡처, Esc로 해제, **정지**로 편집 맵과 카메라를 복원합니다. 플레이 중 변화는 프로젝트에 기록하지 않습니다. |
| 진단 | 정의 ID, 참조, 에셋 경로, 조합 구성, 시작 위치 등을 검사합니다. 게임 스크립트 실행 결과까지 정적으로 보장하지 않습니다. |

**테스트**는 현재 엔진과 로드된 리소스를 그대로 사용합니다. 시작 직전 최신 저장 맵을 확인해 같은 엔진 안에서 플레이용 월드를 구성하며 `on:preload`·`on:start`·`on:load`를 기존 순서로 실행합니다. 엔진·리소스를 다시 초기화하지 않지만 맵 로딩과 시작 스크립트 실행 시간은 필요합니다. 정의·모델·번역·폰트가 변경됐다면 **리소스 적용** 후 테스트하세요. 적용하지 않은 초안은 테스트에 포함하지 않습니다.

전환 중에는 **시작 취소**를 누를 수 있습니다. **정지**는 저장 맵을 다시 불러오고 편집기의 카메라·선택·도구·초안을 유지합니다. 별도 플레이 엔진이나 백그라운드 사전 로딩은 사용하지 않습니다.

배치·속성·삭제·지형·실행 취소·다시 실행과 CLI 편집은 장면 차분으로 반영하며 맵을 다시 로드하지 않습니다. 하이라키 스크롤과 카메라·선택·도구도 유지됩니다. 드래그 중에는 임시 위치만 표시하고, 놓을 때 한 번 저장하여 한 번의 실행 취소로 되돌립니다. 다중 선택은 같은 이동량을 사용하며 가운데 핸들은 각 개체의 지형 위 높이 차이를 유지합니다. 드래그 중 외부 변경은 대기하고 저장 버전이 충돌하면 임시 이동을 취소한 뒤 최신 상태를 반영합니다.

적용된 명령은 즉시 프로젝트에 저장됩니다. 적용 전 속성·스크립트 초안은 앱에 별도로 보관되어 선택 변경과 재시작 후에도 남습니다. 다른 작업이나 CLI 편집으로 버전이 달라졌으면 오래된 초안의 적용을 거부합니다. 필요한 내용을 복사한 뒤 **초안 취소**로 최신 값을 불러와 다시 편집하세요.

Ctrl/Cmd+Z는 실행 취소, Ctrl/Cmd+Shift+Z는 다시 실행, Ctrl/Cmd+D는 개체 복제, Delete는 삭제입니다. 스크립트 편집창에서는 자체 텍스트 실행 취소가 우선합니다. 개체 복제는 배치 속성과 이벤트 스크립트를 복사하며 연결된 상태·저장 아이템은 복제하지 않습니다. 상태·저장 아이템·스크립트가 붙은 개체는 삭제를 거부합니다. 스크립트는 내용을 비워 적용하면 분리됩니다.

## 에이전트와 CLI

CLI는 GUI 없이 실행되며 표준 출력에 JSON을 반환합니다. 오류와 검증 실패는 종료 코드 1입니다. `serve`는 요청 ID를 돌려주는 JSON Lines 프로토콜이며 GUI도 이를 사용합니다.

```sh
python scripts/studio.py schema
python scripts/studio.py init build/studio-example --base build/studio/runtime --name "My island"
python scripts/studio.py apply build/studio-example --file studio/examples/first-island.json --expect-revision 0
python scripts/studio.py inspect build/studio-example --map island
python scripts/studio.py catalog build/studio-example --kind item --query 나무
python scripts/studio.py validate build/studio-example
python scripts/studio.py undo build/studio-example --expect-revision 1
python scripts/studio.py redo build/studio-example --expect-revision 2
python scripts/studio.py export build/studio-example --out build/studio-example-export
```

`apply`는 명령 하나 또는 명령 배열을 받으며 `--file -`는 표준 입력입니다. 배열은 한 트랜잭션으로 처리하고 실패하면 변경을 저장하지 않습니다. `--dry-run`은 변경 없이 결과·진단을 확인합니다. GUI와 함께 작업할 때 `inspect`/`revision`에서 받은 버전을 `--expect-revision`으로 지정하세요. `schema`가 지원 명령과 필드를 안내합니다.

`entities.move`는 `selection: [{"kind":"unit","id":100}, ...]`, 월드 단위 `delta: {"x":64,"y":0,"z":0}`, 선택적인 `terrain: true`를 받습니다. 오브젝트가 포함된 그룹의 Y 이동은 거부합니다.

`preview PROJECT --map NAME`은 전체 파일과 `token`을 반환합니다. 이후 `--since TOKEN`을 전달하면 그 토큰의 장면과 현재 저장 상태 사이의 이진 `patch`, 최신 네이티브 맵, 리소스 대기 상태를 반환합니다. 엔진 적용 ACK 이후에만 기준 토큰을 교체하세요. 정의·에셋·언어 파일은 차분에 포함하지 않으며, 새 정의가 필요한 장면은 `resourcesRequired`로 표시됩니다. 토큰 만료·손상이나 엔진 반영 실패 시 자동 재로드하지 않으며 **리소스 적용**으로 명시적으로 다시 연결합니다.

정의 ID와 맵 개체 ID는 별개입니다. 정의 복제는 빈 ID를 할당하며 ID 한도를 넘으면 거부합니다. 레시피 식별자는 `경로#순번`입니다. 건축 이름은 결과 오브젝트/유닛에서, 레시피 이름은 `genname` 또는 결과 아이템에서 가져옵니다. 새 정의·레시피는 `content.create`, 반복 속성은 문자열 배열, 속성 제거는 `null`을 사용합니다. 지형 좌표는 샘플 단위이며 월드 좌표는 `(sample-size/2)*64`입니다.

## 프로젝트와 내보내기

`project.json`에 정의 수정과 맵 편집 명령을, `sources/`에 해시로 이름 붙인 원본 맵·에셋을 저장합니다. 둘 다 프로젝트의 소스입니다. `.studio/`는 최근 100개 실행 취소 기록·잠금·폰트 캐시·최근 미리보기 토큰 캐시입니다. 원본 기본 리소스는 수정하지 않습니다. `project.json`의 `base`는 보통 상대 경로이며 다른 컴퓨터에서는 준비된 기본 리소스 위치에 맞추세요.

내보내기는 빈 폴더만 사용합니다. `overrides/`에는 네이티브 `.inf`·`.s2`·에셋, `locales/`에는 프로젝트 번역, `manifest.json`에는 버전·파일 해시를 만듭니다. 검토한 콘텐츠는 `resources/editions/extended/overrides/`, 번역은 `locales/editions/extended/<code>.tsv`에 병합한 뒤 Extended를 준비·빌드합니다. 기존 파일이 있으면 통째로 덮어쓰지 말고 변경 내용을 병합하세요. 일반 빌드·CI·배포는 자동 실행하지 않습니다.

`locale.set`과 `[[loc:key]]`로 새 문구를 등록하고 영어 대체 번역도 제공하세요. 프로젝트가 새 글자를 사용하면 기존 폰트 생성기를 재사용해 미리보기용 글꼴을 보충합니다. 이때 준비에 사용한 폰트 파일이 필요합니다. 보충 글꼴은 내보내기의 `fonts/<code>/`에도 포함되며, 내보낸 콘텐츠를 별도 런타임에서 시험할 때 `core/locales/<code>/`에 적용할 수 있습니다. 정식 리소스 통합은 [현지화 가이드](localization-ko.md)의 카탈로그·폰트 갱신 절차를 따릅니다.

기존 맵의 수정 대상 밖 데이터·첨부 파일은 보존합니다. 암호 맵은 기존 에디터에서 암호를 해제한 뒤 가져옵니다. 현재 범위는 기존 맵 형식의 콘텐츠 제작입니다. 프리팹·완전한 도킹·회전/크기 기즈모·청크 스트리밍은 구현하지 않았습니다.

## 검증

```sh
python -m unittest discover -s tests -p "test_studio.py"
python scripts/studio.py engine check
npm --prefix studio run check
npm --prefix studio test
```

테스트는 데이터 보존·트랜잭션·차분/ACK·리소스 대기·이동 수학·취소/충돌·IPC와 동일 엔진 재사용·최신 저장 맵 반영·오래된 요청 무시·시작 취소·맵과 카메라 복원을 확인하며 GUI를 실행하지 않습니다. `engine check`는 실제 Blitz 빌드 산출물, Extended 카탈로그, 허용 목록에 포함된 리소스와 오디오 변환을 검사합니다. 공통 맵 로더를 변경했다면 두 판을 빌드·검사하고 `python scripts/verify_game.py`와 `python scripts/verify_game.py --edition extended`로 기존 저장·불러오기도 확인합니다. 화면 전환·선택·드래그·마우스 캡처·Alt-Tab·배율·소리·한국어 표시는 앱에서 별도로 확인해야 합니다. 진단 탭의 시작 요청부터 첫 게임 프레임까지 시간은 FPS 측정이 아닙니다.
