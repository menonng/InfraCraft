# InfraCraft 모드팩 (Minecraft 26.2 · Fabric)

InfraCraft 와 Create Fly 26.2, 그리고 26.2 에서 함께 돌아가는 Create 애드온을 묶은 **Modrinth 모드팩(.mrpack)** 이에요.
**모드와는 별개**로, 편하게 한 번에 설치하려고 만든 묶음이에요. 릴리스마다 모드 jar 와 따로 올려요.

- **`InfraCraft-Modpack-<버전>-26.2.zip`(권장, 한 파일)**: CurseForge 모드팩 형식. Modrinth 와 CurseForge 양쪽에서 모은 모드를 한 zip 에 담았어요. CurseForge 앱·Prism Launcher·ATLauncher 에서 "모드팩 가져오기".
  - CurseForge 에 있는 모드는 `manifest.json` 에 번호로 적혀 있어 런처가 직접 받아요(재배포가 아니에요).
  - Modrinth·GitHub 의 모드는 **라이선스가 재배포를 허락하는 것만** `overrides/mods` 에 jar 로 들어 있어요(GPL 계열은 소스 주소를 README 에 적어요). 나머지는 zip 안 `README-INFRACRAFT-MODPACK.txt` 에 링크로 안내해요.
  - CurseForge 쪽은 공식 API 키가 필요해요: 저장소 Settings → Secrets and variables → Actions 에 `CURSEFORGE_API_KEY`(https://console.curseforge.com 에서 발급). 없으면 CurseForge 모드는 안내로만 들어가요.
- `InfraCraft-Modpack-<버전>-26.2.mrpack`: 같은 모드팩의 Modrinth 형식
- `InfraCraft-KO-ResourcePack-<버전>.zip`: 한국어 보완 리소스팩
`modpack` 워크플로가 모드를 찾아 팩을 만들고, **모든 모드를 넣은 서버를 실제로 띄워** 함께 켜지는지 확인해요(마지막 빌드: 통과).

- 받기: [`InfraCraft-26.2.mrpack`](https://github.com/menonng/InfraCraft/raw/modpack/InfraCraft-26.2.mrpack) (modpack 브랜치) · 빌드 보고서: [`REPORT.md`](https://github.com/menonng/InfraCraft/blob/modpack/REPORT.md)
- 설치: Modrinth 앱, Prism Launcher 등에서 .mrpack 을 가져오기
- 모드 목록 원본: [`tools/modpack/pack.json`](../tools/modpack/pack.json) · 만드는 스크립트: [`tools/modpack/build_pack.py`](../tools/modpack/build_pack.py)

## 들어 있는 모드
| 모드 | 출처 |
|---|---|
| Fabric API, Fabric Language Kotlin | Modrinth |
| Create Fly `26.2-6.0.9-2` | Modrinth |
| Steam 'n' Rails Fly (SNR.FLY-STABLE-1.4) | Modrinth · **클라이언트(싱글플레이) 전용으로 넣었어요**(아래 참고) |
| Crafts & Additions Fly, Diesel Generators Fly, Power Grid Fly | Modrinth |
| Electro Energetics Fly, Create: Connected Fly, Create Hypertubes (26.2) | GitHub 릴리스 |
| Alloyed, Create: Cyber Goggles(클라이언트) | Modrinth |
| Gravis's Flying Config Menu(클라이언트) | GitHub 릴리스 |
| 의존 모드(Team Reborn Energy, Kaleido Config, Create REI Viewer …) | Modrinth 의존성으로 자동 |
| **InfraCraft** | 팩 안에 직접 포함 |

## 직접 설치해야 하는 모드
아래는 다운로드 가능한 jar 가 GitHub 릴리스·Modrinth 에 없거나(소스만 공개), 재배포가 허락되지 않은 모드예요. 각 페이지에서 받아 `mods` 폴더에 넣으세요.

| 모드 | 이유 | 링크 |
|---|---|---|
| Create Deco Fly, Blocks & Bogies Fly | CurseForge 전용 | [Deco](https://www.curseforge.com/minecraft/mc-mods/create-fly-deco) · [Blocks & Bogies](https://www.curseforge.com/minecraft/mc-mods/create-fly-blocks-bogies) |
| Copycats+ Fly | 원작 ARR(원작 팀 허가 포트), 재배포 불가 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/createfly-copycats-port) |
| Enchantment Industry Fly, Dragons Plus Fly | 릴리스 jar 없음(소스만) · CurseForge 판 있음 | [EI](https://www.curseforge.com/minecraft/mc-mods/create-enchantment-industry-fly) · [DP](https://www.curseforge.com/minecraft/mc-mods/create-dragons-plus-fly) |
| Create Nuclear, Central Kitchen, Gravis's Hotfixes | 릴리스 jar 없음(소스만) | [Nuclear](https://github.com/GravisLudio/create-nuclear-fly-26.2) · [Kitchen](https://github.com/GravisLudio/create-central-kitchen-fly-26.2) · [Hotfixes](https://github.com/GravisLudio/gravis-flying-hotfixes) |
| Bits 'n' Bobs, Bells & Whistles | 릴리스 jar 없음(fabric-26.2 브랜치 소스만) | [Bits 'n' Bobs](https://github.com/zoom-packers/Create-Bits-n-Bobs/tree/fabric-26.2) · [B&W](https://github.com/zoom-packers/BellsAndWhistlesMod/tree/fabric-26.2) |

## 알려진 문제
- **Steam 'n' Rails Fly (Modrinth, SNR.FLY-STABLE-1.4)는 전용 서버에서 켜지지 않아요.** 서버 쪽에서 클라이언트 전용 클래스(`BlockStateModel`)를 불러 멈춰요. 싱글플레이(통합 서버)는 정상이라 팩에는 클라이언트 전용으로 넣었어요. 같은 포트를 소스(`cat4blep/Create-Steam-n-Rails-Fly`, fabric-26.2 브랜치)에서 빌드한 jar 는 전용 서버에서도 정상이에요(InfraCraft CI 에서 확인).

## 한국어
- 팩에는 **한국어 보완 리소스팩**(`InfraCraft-KO.zip`)이 들어 있고, 처음 실행할 때 언어가 한국어, 이 팩이 켜진 상태로 시작해요.
- 원칙: Fly 포트에 한국어가 없거나 모자라면 **원작 Create 애드온(Create Fly 가 아닌 원작)의 한국어**로 채워요. 단, 원작 라이선스가 재배포를 허락할 때만(MIT·Apache·LGPL/GPL·CC0 등) 가져오고, 모든 권리 보유(Create 에셋 등)는 가져오지 않아요. 리소스팩 안 `CREDITS.txt` 에 출처와 라이선스를 적어요.
- 확인해 보니 이번 목록의 포트들은 원작과 한국어 상태가 같아서(원작도 같은 만큼만 번역됨), 원작에서 더 가져올 문장은 없었어요.
- Enchantment Industry, Dragons Plus, Create Deco 는 원작에도 한국어가 없어서 InfraCraft 가 직접 번역해 넣어 두었어요(InfraCraft 안에 포함).

| 모드 | 한국어 |
|---|---|
| Create Fly, Steam 'n' Rails, Electro Energetics, InfraCraft | 98~100% |
| Crafts & Additions 79%, Hypertubes 76%, Create: Connected 72%, Power Grid 63% | 원작과 같음 |
| Diesel Generators 8%, Alloyed 0%, Cyber Goggles 0%, Flying Config Menu 0% | 원작에도 거의 없음 |
