# Create 애드온 호환 목록 (Fabric 26.x / Create Fly)

InfraCraft 는 차량과 기계를 직접 만들지 않고 Create 생태계와 함께 쓰는 것을 전제로 해요. 26.x Fabric 에서 Create 는 **Create Fly**(ZurrTum 의 Fabric 포트)로 돌아가고, 애드온도 대부분 *Create Fly 용 비공식 포트*로 나와 있어요. 이 문서는 2026-10-25 기준으로 조사한 목록이에요.

> ⚠️ 여기 실린 포트는 대부분 **원작자가 아닌 사람이 만든 비공식 포트**예요. 문제는 원본 모드가 아니라 각 포트의 페이지에 알려 주세요.
> Create Fly 는 Create, Flywheel, Ponder, Catnip 을 모두 품고 있으니 **원본 Create 와 함께 설치하면 안 돼요.** 애드온마다 요구하는 Create Fly 버전이 다르니 설치 전에 각 페이지의 요구 사항을 확인하세요.

## 표기
| 표시 | 뜻 |
|---|---|
| ✅ | Fabric 26.3 빌드 또는 소스 브랜치가 있음 |
| 🟡 | Fabric 26.2(또는 26.1)까지만 있음 |
| 🟠 | 26.3 은 NeoForge 포트만 있음 |
| 🧪 | InfraCraft CI 에서 함께 서버를 띄워 확인함 |

> 26.2 열에 🟡 로 표시된 애드온은 **InfraCraft 26.2 판**과 함께 쓸 수 있어요.

---

## 1. 기반: Create Fly
| 모드 | 상태 | 링크 | 메모 |
|---|---|---|---|
| **Create Fly** (ZurrTum, 공식) | 🟡 🧪 26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly) · [Modrinth](https://modrinth.com/mod/create-fly) · [GitHub](https://github.com/ZurrTum/Create-Fly) | 공식 빌드는 26.2, 26.1.2, 1.21.11, 1.21.10, 1.21.8. **InfraCraft 26.2 권장**(`26.2-6.0.9-2`) |
| **Create Fly 비공식 26.3 포트** (zedekvdb) | ✅ 🧪 | [GitHub](https://github.com/zedekvdb/create-fly-unofficial-port) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-unofficial-port) | **InfraCraft 26.3 권장.** CI 에서 함께 띄워 Create 연동을 확인해요 |
| Create Fly 26.3 팩 빌드 (meakaandre-tech) | ✅ | [GitHub](https://github.com/meakaandre-tech/Create-Fly) · [릴리스](https://github.com/meakaandre-tech/Create-Fly/releases/tag/pack-26.3-latest) | 아래 meakaandre-tech 의 26.3 애드온 포트와 짝을 이뤄요 |
| Create Fly NeoForge 26.3 (zoom-packers) | 🟠 | [GitHub](https://github.com/zoom-packers/Create-Fly) | NeoForge 용. Fabric 인 InfraCraft 와는 함께 쓸 수 없어요 |
| Gravis's Flying Hotfixes | 🟡 26.2 | [GitHub](https://github.com/GravisLudio/gravis-flying-hotfixes) | Create Fly 버그 수정(블록·아이템 없음) |
| Gravis's Flying Config Menu | 🟡 26.2 | [GitHub](https://github.com/GravisLudio/gravis-flying-config-menu) | Create Fly 게임 내 설정 화면(클라이언트) |

## 2. 교통·철도·이동
| 애드온 | 상태 | 링크 | InfraCraft 와의 관계 |
|---|---|---|---|
| **Steam 'n' Rails** | 🟡 🧪 26.2 Fabric · 🟠 26.3 NeoForge | [GitHub (cat4blep)](https://github.com/cat4blep/Create-Steam-n-Rails-Fly) · [CurseForge: Continued](https://www.curseforge.com/minecraft/mc-mods/create-fly-steam-n-rails-continued) · [CurseForge: SnR Fly](https://www.curseforge.com/minecraft/mc-mods/steam-n-rails-fly) · [Modrinth](https://modrinth.com/project/f20AygJv) · [NeoForge 26.3 (zoom-packers)](https://github.com/zoom-packers/Create-Steam-n-Rails-Fly) | 같은 Create 선로망을 써서 **역 등록·노선 측량이 그대로 적용**돼요. 지하철 키트는 기본 `create:track` 만 깔아요 |
| Blocks & Bogies | 🟡 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-blocks-bogies) | 대차(bogie) 스타일 추가. 기차 연동에 영향 없음 |
| **Create Hypertubes** | ✅ (소스 브랜치) | [fabric-26.3 브랜치](https://github.com/meakaandre-tech/CreateHypertubes/tree/fabric-26.3) · [fabric-26.2](https://github.com/meakaandre-tech/CreateHypertubes/tree/fabric-26.2) | 사람이 타는 튜브 교통. 함께 쓸 수 있지만 노선망 연동은 아직 없어요 |
| **Create Aeronautics / Simulated / Offroad + Sable** | ✅ | [GitHub (LyXith)](https://github.com/LyXith/create-aeronautics-fly) · [Sable Fabric 26.3 (nedogimov-m)](https://github.com/nedogimov-m/sable-fabric) | 비행선·차량. **겉날개 금지와 무관**하게 쓸 수 있어요. Forge Config API Port, Team Reborn Energy 가 필요해요 |
| Tramways, Train Physics | ❌ Fly 포트 없음 | [Tramways (cat4blep 포크)](https://github.com/cat4blep/Tramways) · [Train Physics Reloaded](https://github.com/cat4blep/CreateTrainPhysicsReloaded) | 1.20.1/1.21.1 NeoForge·Forge 뿐이라 26.x Fabric 에서는 못 써요 |

## 3. 전력·에너지·발전
| 애드온 | 상태 | 링크 | 메모 |
|---|---|---|---|
| **Crafts & Additions** | 🟡 26.2 Fabric · 🟠 26.3 NeoForge | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-crafts-additions) · [Modrinth](https://modrinth.com/project/7bkhocI9) · [GitHub (zoom-packers)](https://github.com/zoom-packers/createaddition) | 전기 ↔ 회전력. 26.3 브랜치는 NeoForge 예요 |
| **Diesel Generators** | ✅ | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-diesel-generators) · [Modrinth](https://modrinth.com/project/AfUI0xBG) · [Fabric 26.3 빌드](https://github.com/meakaandre-tech/Create-Diesel-Generators/releases/tag/fabric-26.3-latest) | 디젤 엔진·연료 |
| **Create: New Age** | ✅ (소스 브랜치) | [fabric-26.3 브랜치](https://github.com/meakaandre-tech/Create-New-Age/tree/fabric-26.3) | 전기·원자력·자기 |
| Power Grid | 🟡 26.2 Fabric · 🟠 26.3 NeoForge | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-power-grid) · [Modrinth](https://modrinth.com/mod/create-fly-power-grid-unofficial-port) · [GitHub](https://github.com/zoom-packers/PowerGrid) | 전력망 |
| Electro Energetics | 🟡 26.2 | [GitHub](https://github.com/cat4blep/Creaty-fly-Electro-Energetics) · [릴리스](https://github.com/cat4blep/Creaty-fly-Electro-Energetics/releases/tag/v1.2.0%2Bfabric.26.2) | 전기 확장(Sable/Aeronautics 연동 포함) |
| Create Nuclear | 🟡 26.2 | [GitHub](https://github.com/GravisLudio/create-nuclear-fly-26.2) | 원자로 |

## 4. 기계·물류·인챈트
| 애드온 | 상태 | 링크 | 메모 |
|---|---|---|---|
| **Create: Connected** | 🟡 26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-connected-fly-port) · [GitHub (GravisLudio)](https://github.com/GravisLudio/create-connected-fly) · [GitHub (zoom-packers)](https://github.com/zoom-packers/create-connected-fly) | 기어박스·배터리·사일로·카피캣 |
| **Copycats+** | 🟡 26.1–26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/createfly-copycats-port) · [GitHub](https://github.com/zoom-packers/copycats-createfly) | 카피캣 블록. 원작 팀 허가를 받은 포트 |
| Bits 'n' Bobs | 🟡 26.1.2–26.2 · 🟠 26.3 | [GitHub (fabric-26.2)](https://github.com/zoom-packers/Create-Bits-n-Bobs/tree/fabric-26.2) | 잡다한 기계·장식 |
| Dragons Plus | 🟡 26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-dragons-plus-fly) · [GitHub](https://github.com/GravisLudio/create-dragons-plus-fly-26.2) | Enchantment Industry 의 필수 의존 모드 |
| **Enchantment Industry** | 🟡 26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-enchantment-industry-fly) · [GitHub](https://github.com/GravisLudio/create-enchantment-industry-fly-26.2) | 경험치 액체, 블레이즈 인챈터(자동 인챈트), 블레이즈 대장간(레벨 합성), 인쇄기. 아래 「인챈트 연동」 참고 |

## 5. 장식·건축
| 애드온 | 상태 | 링크 | 메모 |
|---|---|---|---|
| **Create Deco** | 🟡 26.1–26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-fly-deco) | 캣워크·철제 난간·케이지 램프 등. 역사·터널 장식에 잘 어울려요 |
| Bells & Whistles | 🟡 26.1.2–26.2 · 🟠 26.3 | [GitHub (fabric-26.2)](https://github.com/zoom-packers/BellsAndWhistlesMod/tree/fabric-26.2) | 기차·역 디테일 장식 |

## 6. 요리·기타
| 애드온 | 상태 | 링크 | 메모 |
|---|---|---|---|
| Central Kitchen | 🟡 26.2 | [GitHub](https://github.com/GravisLudio/create-central-kitchen-fly-26.2) | Create 기계 ↔ 요리 모드 연동(Farmer's Delight Refabricated 선택) |
| Create: Gunsmithing | ✅ (소스 브랜치) | [fabric-26.3 브랜치](https://github.com/meakaandre-tech/CreateGunMod/tree/fabric-26.3) | 총기 |

---

## 인챈트 연동 (Enchantment Industry)
소스를 확인한 결과, 따로 연동 코드 없이도 다음처럼 맞물려요(26.2 전용, 아직 CI 로 함께 띄워 보지는 않았어요).

| InfraCraft 인챈트 | Enchantment Industry 에서 |
|---|---|
| 새 인챈트 7종(로드러너, 관성, 기수, 차장, 넓은 포장, 굴착 반경, 절약) | `#minecraft:in_enchanting_table` 에 들어 있어서 **블레이즈 인챈터가 자동으로 붙일 수 있어요** |
| 바닐라 레벨 확장(영혼 가속 V, 차가운 걸음 III, 가벼운 착지 V) | 최대 레벨을 `getMaxLevel()` 로 읽기 때문에 **확장된 레벨을 그대로 따라요** |
| 블레이즈 대장간의 최대 레벨 +1(설정값) | 영혼 가속 VI, 로드러너 IV 처럼 한 단계 더 올라가요. 이렇게 넘친 레벨에서도 효과가 뒤집히지 않도록 InfraCraft 쪽에 상한을 두었어요(관성의 감소 억제 85%, 절약 60%, 넓은 포장·굴착 반경 각각 +4·+3 단계까지) |

## 한국어 지원
배포된 jar 안의 `lang/ko_kr.json` 을 `en_us.json` 과 비교했어요(2026-10-26, `lang-probe` 워크플로). 원작 Create 는 99% 번역돼 있어요. 숫자는 한국어 키 수 / 영어 키 수예요.

| 표시 | 뜻 |
|---|---|
| ✅ | 95% 이상 |
| 🟡 | 70~95% (일부 영어로 보임) |
| 🟠 | 70% 미만 |
| ❌ | 한국어 없음 |

### InfraCraft 가 함께 넣어 주는 한국어 번역 🇰🇷
아래 모드는 한국어가 없어서 **InfraCraft 안에 한국어 번역을 넣었어요.** 해당 모드를 함께 설치하면 따로 할 일 없이 한국어로 보여요.
Create 공식 한국어의 용어(블레이즈 버너, 기계 팔, 택배, 깎인 안산암 벽돌 반 블록 …)와 바닐라 용어(마법부여, 모루, 피뢰침 …), 합니다체를 따랐어요.

| 모드 | 번역 | 주요 용어 |
|---|---|---|
| Enchantment Industry | 336/336 (Fly 포트 기준) | 블레이즈 마법부여기, 블레이즈 단조기, 액체 경험치, 초월 마법부여, 마법부여 견본, 기계식 숫돌, 인쇄기 |
| Dragons Plus | 103/103 (Fly 포트 기준) | 유체 해치, 액체 염료, 염색·냉동·연마·엔드화(선풍기 가공) |
| Create Deco | 411/411 (2.1.3 기준) | 캣워크, 판금, 기차 외판, 보호망 등, 화물 컨테이너, 데칼, 금 간/이끼 낀 벽돌 |

번역 원본과 생성 스크립트는 `tools/translations/` 에 있어요(`python3 tools/translations/build.py`). 모드가 업데이트되어 문구가 바뀌면 원문을 갱신하고 다시 생성하면 돼요. 이 번역은 아직 게임 안에서 직접 훑어보지 않았으니, 어색한 곳이 있으면 알려 주세요.

### 한국어가 없는 모드 ❌
| 모드 | 영어 키 수 | 확인한 대상 |
|---|---|---|
| ~~Enchantment Industry~~ → InfraCraft 번역 포함 | 599 | 원작 jar(1.20.1), Fly 포트 저장소 |
| ~~Dragons Plus~~ → InfraCraft 번역 포함 | 338 | 원작 jar(1.20.1), Fly 포트 저장소 |
| ~~Create Deco~~ → InfraCraft 번역 포함 | 411 | 원작 jar(1.21.1) |
| Bits 'n' Bobs | 244 | 원작 jar(1.21.1), Fly 포트 저장소 |
| Electro Energetics | 520 | 원작 jar(1.21.1), Fly 포트 저장소 |
| Central Kitchen | 63 | 원작 jar(1.21.1), Fly 포트 저장소 |
| Create: Gunsmithing | 99 | 원작 jar(1.21.1), 26.3 포트 저장소 |
| Sable (Aeronautics 계열의 물리 엔진) | 128 | 원작 jar(1.21.1). 함께 들어 있는 Veil, Forge Config API Port 도 한국어 없음 |
| Create Nuclear (Fly 포트) | 430 | 포트 저장소엔 없음. 원작은 62%(269/437) |
| Gravis's Flying Config Menu | 40 | 저장소 |

### 일부만 번역된 모드
| 모드 | 번역 | 비고 |
|---|---|---|
| Diesel Generators | 🟠 12% (35/293) | 원작·Fly 포트 모두 같음. 사실상 영어 |
| Power Grid | 🟠 64% (666/1042) | 원작·Fly 포트 같음 |
| Ponder (Create Fly 내장) | 🟠 34% (21/62) | Create 본문은 99% 라서 체감은 적어요 |
| Flywheel (Create Fly 내장) | ❌ (0/12) | 설정 화면 문구뿐 |
| Create: Connected (Fly 포트) | 🟡 72% (307/425) | 원작과 같음 |
| Bells & Whistles | 🟡 72% (28/39) | |
| Blocks & Bogies (Fly 포트) | 🟡 76% (99/130) | |
| Create Hypertubes | 🟡 78% (50/64) | |
| Crafts & Additions | 🟡 80% (237/297) | 원작·Fly 포트 같음 |
| Create: New Age | 🟡 94% (157/167) | |

### 한국어가 거의 다 된 모드 ✅
| 모드 | 번역 |
|---|---|
| Create Fly (26.2 공식, 26.3 포트) | 99% (3623/3641) |
| Steam 'n' Rails (Fly 포트) | 99% (2541/2573) |
| Copycats+ (Fly 포트) | 100% (113/113) |
| Create Aeronautics · Simulated · Offroad (원작 1.21.1) | 100% (219, 650, 49). 단, **26.3 Fabric 포트(LyXith) 저장소에는 한국어 파일이 없어요.** 포트 jar 에 빠졌을 수 있으니 확인이 필요해요 |
| InfraCraft | 100% |

> 저장소만 보면 Steam 'n' Rails 처럼 Crowdin 번역을 배포할 때만 넣는 모드는 "없음"으로 잘못 보여요. 그래서 위 표는 **배포 jar 기준**이에요. CurseForge 에만 올라온 Fly 포트(Deco, Enchantment Industry, Dragons Plus)는 jar 를 받을 수 없어 원작 jar 와 포트 저장소로 판단했어요.

## InfraCraft 에서 이미 연동되는 것
- **Create 기차역 → 노선망 역 자동 등록**, **Create 기차 운행 → 노선 자동 측량**: 경로 안내와 이정표에 반영돼요. Steam 'n' Rails 같은 트랙 애드온도 같은 선로망이라 그대로 적용돼요.
- **지하철 키트**는 Create 트랙(`create:track`)을 우선 깔아요.
- **Create 장식 돌**(자른 돌, 벽돌, 기둥 등)은 도로 2등급이에요.
- **Create 마인카트 장치**는 바닐라 마인카트라서 등급 선로, 역 정차, 분기기가 적용돼요.
- **네더 1:1** 은 Create 기차의 포탈 통과에도 똑같이 적용돼요.

## InfraCraft 버전 고르기
InfraCraft 는 **26.3** 과 **26.2** 두 버전으로 빌드해요.

| | 26.2 | 26.3 |
|---|---|---|
| Create Fly | 공식 (ZurrTum `26.2-6.0.9-2`) | 비공식 포트 (zedekvdb) |
| 쓸 수 있는 애드온 | 🟡 와 ✅ 거의 전부 (Steam 'n' Rails, Crafts & Additions, Connected, Copycats+, Deco, Enchantment Industry …) | ✅ 만 (Aeronautics 계열, Diesel Generators, New Age, Hypertubes, Gunsmithing) |
| CI 확인 | 🧪 Create Fly + Steam 'n' Rails 와 함께 서버 기동 | 🧪 Create Fly 와 함께 서버 기동 |

**애드온을 많이 쓰려면 26.2**, 최신 버전이 필요하면 26.3 을 고르세요.

### 26.2 Fabric 조합 예시
1. InfraCraft (26.2) + Fabric API 0.161+ + Fabric Language Kotlin
2. Create Fly `26.2-6.0.9-2` (공식) — 🧪 확인됨
3. Steam 'n' Rails Fly — 🧪 확인됨. 그 밖에 🟡 표시 애드온(각 페이지의 Create Fly 요구 버전 확인)

## 26.3 Fabric 으로 지금 바로 꾸릴 수 있는 조합
1. InfraCraft (26.3) + Fabric API 0.165+ + Fabric Language Kotlin
2. Create Fly 비공식 26.3 포트(zedekvdb) — 🧪 확인됨
3. ✅ 표시된 애드온: Aeronautics/Simulated/Offroad + Sable, Diesel Generators, New Age, Hypertubes, Gunsmithing

🟡(26.2 전용) 애드온은 26.3 포트가 나올 때까지 함께 쓸 수 없어요. 각 애드온이 요구하는 Create Fly 빌드가 서로 다를 수 있으니, 같은 계열(예: meakaandre-tech 의 26.3 빌드끼리)로 맞추는 게 안전해요.
