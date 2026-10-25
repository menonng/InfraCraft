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
| Enchantment Industry | 🟡 26.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-enchantment-industry-fly) · [GitHub](https://github.com/GravisLudio/create-enchantment-industry-fly-26.2) | 경험치 액체·자동 인챈트. InfraCraft 의 새 인챈트도 책으로 다룰 수 있어요 |

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

## InfraCraft 에서 이미 연동되는 것
- **Create 기차역 → 노선망 역 자동 등록**, **Create 기차 운행 → 노선 자동 측량**: 경로 안내, 이정표, 급행에 반영돼요. Steam 'n' Rails 같은 트랙 애드온도 같은 선로망이라 그대로 적용돼요.
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
