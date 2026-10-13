<!-- dev-docs-ai-translation: {"document":"README.md","language":"ko","syncedHash":"d1ff9f7732c95e224bd374eac41ff8f0976d84a6ffecdf44240af82cd369dc6e"} -->

# Stranded II : Modern

[English](README.md) · [한국어](README-ko.md)

<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/d7553612-613b-4a5f-8ba7-77e4774af408" />

Stranded II를 최신 Windows 환경에서 즐기기 위한 비공식 개선판입니다. Blitz3D 1.118로 빌드합니다.

## 시작하기

운영체제에 맞는 버전을 다운로드하거나, 웹 브라우저에서 바로 플레이하세요.

- **[Windows 10 & 11](https://github.com/shlifedev/Stranded-II-Modern/releases/latest):** GitHub Releases에서 Windows용 ZIP을 다운로드하세요.
- **[Web Browser](https://shlifedev.github.io/Stranded-II-Modern/):** 설치 없이 플레이하는 실험용 WebGPU 버전입니다. WebGPU와 JSPI를 지원하는 데스크톱 Chrome/Edge가 필요합니다.
- **[macOS (Apple Silicon)](https://github.com/shlifedev/Stranded-II-Modern/releases/latest):** GitHub Releases에서 macOS용 ZIP을 다운로드하세요.

## 기술적 개선사항

주요 기술적 변경사항은 다음과 같습니다.

- **그래픽 개선:** 기존 Direct3D 7 렌더러를 WebGPU 기반으로 전환했습니다. (DirectX 12/Metal)
  픽셀 단위 조명, 태양광 그림자, 수면 반사와 대기 효과를 선택형 옵션으로 추가했습니다.
- **주사율 개선:** 시뮬레이션의 갱신량을 실제 경과 시간에 맞춰 계산해, 높은 FPS에서도 원래 게임 속도를 유지하도록 했습니다. (60·120·144 Hz)
- **플레이 개선:** 조합법 도감, 보관함 빠른 이동, 빈 시체 자동 닫기, 자동 정렬, 건축 연속 입력 등 개별적으로 켜고 끌 수 있는 QOL 기능을 추가했습니다. 자세한 사용법은 [QOL 설정 문서](docs/qol-ko.md)를 참조해 주세요.
- **기타 버그 수정:** Alt-Tab 후 그래픽 깨짐과 빠른 저장·불러오기 오류를 수정했습니다.
- **현지화**: 이제 손쉽게 유저가 직접 게임을 번역할 수 있습니다. (언어 추가 지원)
 
그 외 기술적 변경사항은 다음 문서들을 참고하세요. 

[WebGPU 렌더러](docs/wgpu-migration-ko.md) · [웹 버전 구현 원리](docs/web-architecture-ko.md) · [macOS 구현 원리](docs/macos-architecture-ko.md) · [Studio](docs/studio-ko.md) · [개발 가이드](docs/development-ko.md) · [현지화 가이드](docs/localization-ko.md)

## 필요한 안내 찾기

| 안내 | 문서 |
| --- | --- |
| 번역, 언어 추가, 글꼴 변경, 언어팩 배포 | [현지화 가이드](docs/localization-ko.md) |
| 빌드, 기능 개발, 검증, 배포 | [개발 가이드](docs/development-ko.md) |

문서는 영어 또는 한국어 파일을 직접 수정해도 됩니다. [문서 수정 안내](docs/development-ko.md#프로젝트-문서-수정)를 참고하세요. 안정판 이전의 별도 기여 규칙은 아직 없습니다.

아래 노트는 개발 배경과 앞으로의 구상입니다. 현재 구현 범위는 위 기능 목록을 기준으로 확인하세요.

<details>
  <summary>개발자 노트</summary>

안녕하세요!

저는 어린 시절 **Stranded II**를 정말 좋아했던 사람입니다. 이 게임은 제게 특별한 의미가 있고, 즐거운 추억도 많습니다.

최근 문득 무인도 생존 게임을 다시 하고 싶어졌습니다. 하지만 2026년이 된 지금도 Stranded II와 같은 느낌을 주는 게임을 찾기 어려웠습니다.

아쉽게도 최신 환경에서 게임을 실행해 보니 원활하게 동작하지 않았습니다. 50 FPS 제한 때문에 어색하게 느껴졌고, Alt-Tab으로 다른 창에 갔다 돌아오면 텍스처가 깨지는 문제도 있었습니다. 커뮤니티의 다른 플레이어들도 비슷한 문제를 겪고 있었습니다.

기술적으로 아주 어려운 문제들은 아니었습니다. 다만 수정해야 할 곳이 너무 많았습니다. 예를 들어 프레임 문제는 `20ms` 제한을 없애는 것만으로 해결되지 않습니다. 가변 프레임에서도 올바르게 움직이도록 모든 개체의 업데이트 시간도 조정해야 합니다.

수작업으로 처리하려면 상당한 노력이 들고, 많은 코드를 바꾸는 만큼 새로운 버그가 생길 위험도 큽니다. 최근 AI의 발전 덕분에 이 작업을 훨씬 수월하게 진행할 수 있었습니다.

### 앞으로의 계획

두 가지 버전을 별도로 유지할 계획입니다.

- **오리지널 버전:** 원작의 게임플레이와 콘텐츠를 유지합니다.
- **확장 버전:** 편의성 개선, 밸런스 조정, 새 콘텐츠를 추가합니다.

오리지널 버전의 핵심 게임 경험은 바꾸지 않되 다음 개선을 포함하려고 합니다.

- **언어 지원:** 원하는 언어를 선택하고 직접 번역을 추가할 수 있습니다.
- **프레임 제한 해제:** 기존 제한을 없애고 프레임에 관계없이 개체의 업데이트 시간을 올바르게 계산합니다.
- **그래픽 설정:** 추가 그래픽 옵션을 제공합니다.
- **빠른 저장/불러오기 수정:** 빠른 저장과 불러오기가 동작하지 않던 문제를 수정합니다.
- **자동 저장 개선:** 최소 세 개의 자동 저장 슬롯을 유지하고 메인 메뉴에 계속하기 버튼을 추가합니다.

### 원작을 넘어

장기적으로 원작을 확장하고 싶습니다. 기존 방식의 `mod`보다는 게임의 핵심 소스코드를 직접 수정하고 확장하는 형태가 될 것입니다.

이 버전의 이름은 아직 정하지 않았습니다. 구상 중인 기능은 다음과 같습니다.

- **편의성 개선:** 설정에서 켜고 끌 수 있는 QOL 기능.
- **리밸런싱과 새 콘텐츠:** 밸런스 조정과 콘텐츠 추가.
- **멀티플레이어:** 함께 플레이하는 기능.
- **무작위 섬 경험 개선:** 시나리오뿐 아니라 무작위로 생성한 섬에서의 생존도 더 재미있게 만들기.

아직은 앞으로의 계획이며, 현재 개선 사항을 넘어서는 기능은 확정된 것이 아닙니다.

**즐겁게 플레이하세요!**
</details>


### 버그 및 기능요청

버그, 기능요청, 번역요청 등은 모두 Github에 제출해주세요.

## 저작권

- 원본 Stranded II 게임과 소스코드의 권리는 Peter Schauß / [Unreal Software](https://www.unrealsoftware.de/)에 있습니다.
- 이 프로젝트는 [Stranded II 원본 소스](https://github.com/ttyborg/Stranded2)를 바탕으로 제작했습니다.
- 원작·엔진·글꼴 등의 라이선스는 [저작권 및 라이선스 고지](licenses/THIRD-PARTY-NOTICES.txt)를 참고하세요.
